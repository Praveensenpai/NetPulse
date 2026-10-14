# Proguard rules for NetPulse
-keep class com.netpulse.app.** { *; }

# Preserve Coroutines, Compose, and DataStore internals
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn java.lang.invoke.MethodHandles$Lookup
-dontwarn sun.misc.Unsafe
