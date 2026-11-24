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
# v1.1.15 (anti-tamper, H2): R8 keep rules. The release build is
# obfuscated, but the classes below must keep their names/fields:
# Gson reads the remote config + persisted state by reflection, the
# Xray core is native/JNI, and MMKV/protobuf are binary contracts.
# If the CI release build breaks, the first revert is the
# isMinifyEnabled flag in build.gradle.kts.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.v2ray.ang.handler.vpn5g.AppConfigRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AnnouncementRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.PromoCodeRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AdsRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.InterstitialAdRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.UserAdRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AdPricingRemote { *; }
# v1.1.16 (crash fix): these Gson models were MISSING from the keeps,
# so on the R8 release build Gson saw only obfuscated field names and
# silently parsed ZERO entries — live_configs.json yielded no servers
# and the bundled fallback imported nothing, leaving the free list
# empty (1 server in the field report) and forcing the slow blocking
# sync on first launch. Every model Gson deserializes from server or
# bundled JSON must be listed here.
-keep class com.v2ray.ang.handler.vpn5g.LiveConfigEntry { *; }
-keep class com.v2ray.ang.handler.vpn5g.FallbackEntry { *; }
-keep class com.v2ray.ang.handler.vpn5g.PaymentGatewayRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.SubscriptionStatusRemote { *; }
# v1.1.16: the auth session round-trips through the same obfuscated
# build (toJson/fromJson use the same names), but keeping it removes a
# whole class of silent breakage if any other reader is added later.
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gAuthService$Vpn5gAuthSession { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gAds$* { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus$Vpn5gUserSubscription { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gPromoCodes$Redemption { *; }
# v1.1.17 (connect crash): the JNI contract. hev-socks5-tunnel's
# JNI_OnLoad (hev-socks5-tunnel/src/hev-jni.c) does
# FindClass("com/v2ray/ang/service/TProxyService") and RegisterNatives
# for TProxyStartService / TProxyStopService / TProxyIsRunning /
# TProxyGetStats — the class and method names live in C strings the
# shrinker cannot see. With R8 on (1.1.15/1.1.16) the class was
# renamed, FindClass returned NULL and JNI_OnLoad returned JNI_ERR,
# crashing EVERY connect (UnsatisfiedLinkError, 1.1.16 crash report).
# These keeps are documentation + insurance for any future re-enable;
# the 1.1.17 fix itself is minification OFF (build.gradle.kts). Never
# rename TProxyService or its native methods without rebuilding the
# .so with the same PKGNAME/CLSNAME.
-keep class com.v2ray.ang.service.TProxyService { *; }
-keep class com.v2ray.ang.service.CoreVpnService { *; }
-keep class com.v2ray.ang.service.CoreProxyOnlyService { *; }
-keep class com.v2ray.ang.service.CoreRootService { *; }
-keep class com.v2ray.ang.service.CoreTestService { *; }
-keep class com.v2ray.ang.dto.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.tencent.mmkv.** { *; }
-keep class com.google.protobuf.GeneratedMessageLite { *; }

# v1.1.16 (logging strain): strip verbose/debug/info logging from
# release builds — errors and warnings stay. LogUtil is a Kotlin
# object, so its methods are instance methods (no @JvmStatic); the
# rule below matches them as such. LogUtil.d/v/i only call the
# private log() helper, which only writes to logcat, so stripping the
# calls is side-effect-free; the app never depends on a log call's
# return value. Note Log.println is deliberately NOT stripped: it is
# the path LogUtil uses for errors and warnings too.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class com.v2ray.ang.util.LogUtil {
    public void d(...);
    public void v(...);
    public void i(...);
}
