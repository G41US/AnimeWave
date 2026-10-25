package com.animewave.app.aniyomi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

/**
 * Local trust decisions for extension packages.
 *
 * Trust is pinned to both package name and signer fingerprint, so an update signed by a
 * different key does not inherit the old approval. This store does not install or execute APKs.
 */
class AniyomiExtensionTrustStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun isTrusted(candidate: ExtensionCandidate): Boolean {
        if (candidate.status != CompatibilityStatus.REQUIRES_TRUST_AND_HOST) return false
        val signer = candidate.primarySigner() ?: return false
        return preferences.getString(trustKey(candidate.packageName), null) == signer
    }

    /**
     * Records a user's explicit approval after the UI has shown package name, version,
     * signer fingerprint, and compatibility status.
     */
    fun trust(candidate: ExtensionCandidate, userConfirmed: Boolean) {
        require(userConfirmed) { "Explicit user confirmation is required to trust an extension." }
        require(candidate.status == CompatibilityStatus.REQUIRES_TRUST_AND_HOST) {
            "Only packages that pass compatibility preflight can be trusted."
        }
        val signer = requireNotNull(candidate.primarySigner()) {
            "The extension must have a signer fingerprint."
        }
        preferences.edit { putString(trustKey(candidate.packageName), signer) }
    }

    fun revoke(packageName: String) {
        preferences.edit { remove(trustKey(packageName)) }
    }

    private fun ExtensionCandidate.primarySigner(): String? =
        signerSha256.takeIf { it.isNotEmpty() && it.all(String::isNotBlank) }
            ?.distinct()
            ?.sorted()
            ?.joinToString("|")

    private fun trustKey(packageName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(packageName.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "trusted.$digest"
    }

    companion object {
        private const val PREFS_NAME = "aniyomi_extension_trust"
    }
}
