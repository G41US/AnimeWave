package com.animewave.app.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.security.MessageDigest
import java.util.Locale

/**
 * Read-only discovery and compatibility preflight for installed Aniyomi-family anime extensions.
 *
 * This deliberately does not instantiate extension classes. That requires the matching
 * Aniyomi host ABI and trust store; discovery must not be mistaken for runtime loading.
 */
class AniyomiExtensionInspector(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun discoverInstalled(): List<ExtensionCandidate> {
        val flags = packageFlags()
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        return packages.asSequence()
            .filter(::hasAnimeExtensionFeature)
            .mapNotNull(::toCandidate)
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun toCandidate(info: PackageInfo): ExtensionCandidate? {
        val app = info.applicationInfo ?: return null
        val metadata = app.metaData
        val name = metadata?.getString(META_NAME)
            ?: runCatching { packageManager.getApplicationLabel(app).toString() }.getOrNull()
            ?: info.packageName
        val versionName = info.versionName.orEmpty()
        val libVersion = metadata?.getInt(META_EXTENSION_LIB, 0)
            ?.takeIf { it != 0 }
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        val signatures = signingCertificates(info)
        val supported = libVersion != null && libVersion in SUPPORTED_LIB_VERSIONS
        val hasEntryPoint = !metadata?.getString(META_SOURCE_CLASS).isNullOrBlank()
        val status = when {
            versionName.isBlank() -> CompatibilityStatus.MISSING_VERSION
            signatures.isEmpty() -> CompatibilityStatus.UNSIGNED
            !supported -> CompatibilityStatus.UNSUPPORTED_API
            !hasEntryPoint -> CompatibilityStatus.MISSING_ENTRY_POINT
            else -> CompatibilityStatus.REQUIRES_TRUST_AND_HOST
        }

        return ExtensionCandidate(
            packageName = info.packageName,
            name = name.removePrefix("Aniyomi: ").trim().ifBlank { info.packageName },
            versionName = versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            extensionLibVersion = libVersion,
            sourceClassNames = metadata?.getString(META_SOURCE_CLASS)
                ?.split(';')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                .orEmpty(),
            signerSha256 = signatures,
            status = status,
        )
    }

    private fun hasAnimeExtensionFeature(info: PackageInfo): Boolean =
        info.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    private fun signingCertificates(info: PackageInfo): List<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptyList()

        return certificates.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.distinct()
    }

    private fun packageFlags(): Int {
        @Suppress("DEPRECATION")
        return PackageManager.GET_META_DATA or
            PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }

    companion object {
        const val EXTENSION_FEATURE = "tachiyomi.animeextension"
        const val META_SOURCE_CLASS = "tachiyomi.animeextension.class"
        const val META_EXTENSION_LIB = "aniyomix.extensionLib"
        const val META_NAME = "aniyomix.name"
        val SUPPORTED_LIB_VERSIONS = setOf(14.0, 16.0, 17.0)
    }
}

data class ExtensionCandidate(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val extensionLibVersion: Double?,
    val sourceClassNames: List<String>,
    val signerSha256: List<String>,
    val status: CompatibilityStatus,
)

enum class CompatibilityStatus {
    /** Missing extension version metadata; reject before class loading. */
    MISSING_VERSION,
    /** Package does not expose a signer; never load it. */
    UNSIGNED,
    /** The extension API generation is outside the pinned host's supported set. */
    UNSUPPORTED_API,
    /** No declared extension source/factory entry point. */
    MISSING_ENTRY_POINT,
    /** Metadata passed preflight; still needs explicit trust and a compatible host runtime. */
    REQUIRES_TRUST_AND_HOST,
}
