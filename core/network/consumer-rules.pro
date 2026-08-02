# :core:network 消费者 ProGuard 规则
# OkHttp / Retrofit / Moshi 的基础规则由各自 consumer-rules 提供，无需重复

# Moshi codegen：保留 @JsonClass 注解的数据类及其字段。
# moshi-kotlin-codegen 生成的 JsonAdapter 通过反射读取构造函数与属性，
# 混淆后会抛出 IllegalArgumentException（缺少必需字段）。本模块 AssrtModels 等模型依赖此规则。
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
