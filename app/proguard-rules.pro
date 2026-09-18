# Keep line numbers + source file names so Crashlytics can symbolicate.
-keepattributes SourceFile, LineNumberTable

# Firebase / GMS SDKs ship their own consumer rules; keep annotations used at runtime.
-keepattributes Signature, *Annotation*

# Keep all app classes. The app is small; this prevents any reflection/ServiceLoader
# surprises from R8 shrinking while still allowing libs to be optimized.
-keep class com.pblock.app.** { *; }
-keep class kotlinx.coroutines.** { *; }