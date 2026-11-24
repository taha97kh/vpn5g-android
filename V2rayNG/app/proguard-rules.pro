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
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.v2ray.ang.handler.vpn5g.AppConfigRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AnnouncementRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.PromoCodeRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AdsRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.InterstitialAdRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.UserAdRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.AdPricingRemote { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gAds$* { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus$Vpn5gUserSubscription { *; }
-keep class com.v2ray.ang.handler.vpn5g.Vpn5gPromoCodes$Redemption { *; }
-keep class com.v2ray.ang.dto.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.tencent.mmkv.** { *; }
-keep class com.google.protobuf.GeneratedMessageLite { *; }
