-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.lunasdev.lunasdpi.vpn.NativeEngine { *; }
-keep class com.lunasdev.lunasdpi.vpn.NativeBridgeConfig { *; }
-keep class com.lunasdev.lunasdpi.vpn.EngineStats { *; }

-keepclassmembers class com.lunasdev.lunasdpi.vpn.NativeEngine {
    boolean protectSocket(int);
    byte[] resolveDns(byte[]);
}

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlinx.serialization.**
-keep class com.lunasdev.lunasdpi.data.model.** { *; }
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**
