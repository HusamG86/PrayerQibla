# The WebView JavaScript bridge is called by name from app.js.
-keepclassmembers class com.sa.prayerqibla.MainActivity$Bridge {
    public *;
}

# Keep any other @JavascriptInterface methods if the bridge moves.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep annotations used by WebView / JSON reflection.
-keepattributes *Annotation*
