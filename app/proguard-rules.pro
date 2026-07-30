# ============================================================
# R8 配置：只做代码/资源压缩（shrinking），不做混淆（obfuscation）
# 保留全部类名、方法名、字段名，便于调试与崩溃堆栈可读
# ============================================================

# 禁用混淆：保留所有名称
-dontobfuscate

# 保留源文件名与行号，崩溃堆栈可精确定位
-keepattributes SourceFile,LineNumberTable

# 保留注解、泛型签名（kotlinx-serialization / 反射需要）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---------- kotlinx-serialization ----------
# 保留 @Serializable 类的序列化器（HistoryRecord、DrandBeacon 等）
-keep,includedescriptorclasses class com.agon.app.**$$serializer { *; }
-keepclassmembers class com.agon.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.agon.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Coil (OkHttp) ----------
# OkHttp 平台探测的无害警告
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Compose ----------
# Compose 编译器生成的代码无需额外 keep，R8 默认规则已处理
