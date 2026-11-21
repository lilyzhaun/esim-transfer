-keep,allowoptimization class dev.quicktransfer.app.hook.EsimModule {
    public <init>();
    public void onModuleLoaded(io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
    public void onPackageReady(io.github.libxposed.api.XposedModuleInterface$PackageReadyParam);
}
-repackageclasses dev.quicktransfer.app.obfuscated
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keep,allowshrinking,allowobfuscation class !dev.quicktransfer.app.**,** { *; }
