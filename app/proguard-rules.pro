# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# AquaLight production hardening
# Keep WorkManager workers and broadcast receivers used from manifest/WorkManager factories.
-keep class com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker { *; }
-keep class com.aqua.aqualight.data.care.reminder.CareTaskReminderReceiver { *; }
-keep class com.aqua.aqualight.data.care.reminder.CareTaskBootReceiver { *; }

# Preserve line numbers in release crash reports while keeping source obfuscation enabled.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AquaLight production log privacy
# Release builds must not emit Logcat entries. This prevents accidental exposure
# of Wi-Fi credentials, QR/claim payloads, runtime pairing tokens, endpoint data,
# or device/user identifiers from direct android.util.Log calls.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
