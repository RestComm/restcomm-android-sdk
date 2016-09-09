# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/Antonis/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Needed for TestFairy (important: remove for production builds)
-keep class com.testfairy.** { *; }
-dontwarn com.testfairy.**
-keepattributes Exceptions, Signature, LineNumberTable

# For some reason, when I try to minify I get 'unresolved references to classes or interfaces'. Yet the code functions just fine. Let's remove the warnings
#-dontwarn
#-libraryjars mylibrary.jar(!someunusedpackage/**)
