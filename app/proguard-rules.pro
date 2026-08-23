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

# Xposed loads this class by name from META-INF/xposed/java_init.list.
# Keep only the public entry points; R8 may optimize the private implementation.
-keep,allowoptimization class io.github.bbzq.BbzqModule {
    <init>();
    void onModuleLoaded(io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
    void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
}

# NPatchRemoteClient feeds the manager binder to XposedServiceHelper via reflection.
# R8 class merging/renaming removes io.github.libxposed.service.XposedServiceHelper in
# release builds, which silently breaks rootless Remote Store connection
# ("Xposed 服务尚未连接" in a release APK while debug works). Keep the whole class.
-keep class io.github.libxposed.service.XposedServiceHelper { *; }
-keep class io.github.libxposed.service.XposedService { *; }
