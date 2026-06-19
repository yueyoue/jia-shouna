# 家收纳 ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.jiashouna.app.model.** { *; }
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn com.google.gson.**
