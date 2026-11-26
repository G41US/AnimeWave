# Libraries used here (Compose, Media3, Coil, OkHttp) ship their own R8 rules; nothing app-specific needed.

# The stream player's hidden page talks to the app through these bridge methods.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
