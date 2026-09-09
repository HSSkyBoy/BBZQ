# ==========================================
# R8 / ProGuard Configuration for BILIZ
# ==========================================

# 1. 基础属性保留与堆栈行号还原
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 2. 优化策略
-repackageclasses ''
-allowaccessmodification

# 3. Xposed / LibXposed 核心入口
# Xposed loads this class by name from META-INF/xposed/java_init.list
-keep,allowoptimization class io.github.biliz.BilizModule {
    <init>();
    void onModuleLoaded(io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
    void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
}

-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# 保留 Manifest 组件与显式 Intent 打开的 Activity，防止 -repackageclasses 拍平类名导致 ActivityNotFoundException
-keep class io.github.biliz.BilizApplication { *; }
-keep class io.github.biliz.SettingsActivity { *; }
-keep class io.github.biliz.IntroActivity { *; }
-keep class io.github.biliz.WoMicIntroActivity { *; }
-keep class io.github.biliz.ReadEraIntroActivity { *; }
-keep class io.github.biliz.NPatchFileProvider { *; }
-keep class io.github.biliz.ModuleSettingsProvider { *; }

# 4. DexKit 原生及 Java 接口规则
-keep class org.luckypray.dexkit.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.luckypray.dexkit.**

# 5. 反射与模型类保护
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留动态解析及数据结构类
-keepclassmembers class io.github.biliz.feats.symbol.** {
    <fields>;
    <methods>;
}

-keepclassmembers class io.github.biliz.ConfigPorter$* {
    <fields>;
    <methods>;
}

-keepclassmembers class io.github.biliz.UpdateChecker$* {
    <fields>;
    <methods>;
}

# 6. OkHttp 与网络依赖规则
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
