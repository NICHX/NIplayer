plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

// ==================== 工程化 E5：静态分析 ====================
//
// 只挂到真正编译 Kotlin 的模块上（`plugins.withId` 而非全量 subprojects），
// `player:ffmpeg` 这类纯 native 模块没有 Kotlin 源码，挂上只会多出空任务。
//
// 规则集完全由 config/detekt/detekt.yml 的**白名单**决定：
//   buildUponDefaultConfig = false → 未在该文件里显式启用的规则一律不跑。
// 这样 detekt 自身升级默认规则集时不会静默改变本项目的门禁范围。
//
// 本地运行：./gradlew detektMain detektTest
// 报告：    build/reports/detekt/detekt.html（每个模块各一份）
subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "dev.detekt")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig = false
            parallel = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        }
        enableDetektMixedSourceSetClasspath()
    }
    plugins.withId("com.android.application") {
        apply(plugin = "dev.detekt")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig = false
            parallel = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        }
        enableDetektMixedSourceSetClasspath()
    }
}

// ==================== Compose 编译器度量（重组优化基线） ====================
//
// 目的：把「哪些 composable 可跳过 / 哪些类被判为 unstable」变成可量化的报告，
// 而不是靠读代码猜。`*-composables.txt` 列出每个 composable 的 restartable / skippable
// 与不可跳过的原因；`*-classes.txt` 列出每个类的 stability 判定。
//
// 产物（每模块各一份）：<module>/build/compose-reports/、<module>/build/compose-metrics/
//   - app_debug-composables.txt / -classes.txt / -module.json
//   - app_debug-composables.csv、app_debug-compose-metrics.csv
//
// 注意：该扩展只对**应用了** `org.jetbrains.kotlin.plugin.compose` 的模块可用
// （本项目为 :app / :core:designsystem / :core:navigation / :feature:home / :feature:player）。
// 对未应用该插件的模块（如 :player:kernel）直接 configure 会抛异常，故用 plugins.withId 守卫。
subprojects {
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            reportsDestination = layout.buildDirectory.dir("compose-reports")
            metricsDestination = layout.buildDirectory.dir("compose-metrics")
            // 跨模块类型的稳定性声明（compose-stability.conf）。
            //
            // 背景：Compose 编译器对**跨模块**类型无法推断稳定性 —— 编译 A 模块时拿不到 B 模块
            // 的 IR，于是把来自 B 的类型一律当作 unstable。后果是只要某个 composable 的参数里
            // 出现这种类型，它就**永久失去跳过重组的能力**，与参数值是否变化无关。
            //
            // 为什么不就地加 @Immutable：这些类型定义在 :player:kernel / :core:storage 等
            // **无 Compose 依赖**的模块，加标注等于让内核层反向依赖 Compose，属依赖倒置，
            // 与项目已完成的 A1/A2 架构修复方向冲突。故只能在此集中声明。
            //
            // ⚠️ 写进该文件 = 对编译器做承诺「实例构造后不会变化」。承诺错了不报错，
            //    只会静默产生「界面不刷新」的 bug。每一项都必须人工核对字段是否全为 val。
            //
            // ⚠️ 该配置文件**不支持注释行**（Kotlin 2.4.10 实测）：解析器会把 `#` 开头或
            //    含空格的任意行当成类名去校验，报 `... is not a valid pattern`，且错误里的
            //    行号恒为 0（不是真实行号），极易误判。故说明只能写在本处，文件内只放类名。
            //
            // ⚠️ 必须用 .set(listOf(...))：ListProperty 只有 from(Iterable) / from(Provider)
            //    两个重载，**没有 varargs 版**（写成 from(file) 会编译失败）。
            //    单数属性 stabilityConfigurationFile 已废弃且为 error 级，勿用。
            stabilityConfigurationFiles.set(
                listOf(rootProject.layout.projectDirectory.file("compose-stability.conf")),
            )
        }
    }
}

/**
 * 混合 Java/Kotlin 模块的 detekt 类路径补丁（`:core:subtitle` 专用，2026-09-21 修复）。
 *
 * **问题**：detekt 内嵌的 Kotlin 编译器只编译 Kotlin 源码，解析不到**同一模块内**的 Java 类。
 * `:core:subtitle` 是全仓唯一的混合模块（9 个 Java + 5 个 Kotlin），实测每个 detekt 任务都报
 * `There were 48 compiler errors found during analysis. This affects accuracy of reporting.`，
 * 且 findings 恒为 0 —— 即该模块的「全绿」是假象，`UnusedImport` / `ForbiddenMethodCall` /
 * `ElseCaseInsteadOfExhaustiveWhen` 等依赖类型解析的规则在该模块**静默失效**。
 *
 * **修法**：把 AGP 的 javac 产物并入 detekt 类路径。
 *
 * ⚠️ **必须放在 `afterEvaluate` 里**。插件对类路径的填充逻辑是「集合为空时才填」：
 * 若在 `configureEach` 中直接 `classpath.from(...)`，插件会认为已有内容而放弃填充，
 * 结果类路径只剩注入的那一项（**丢掉 android.jar / kotlin-stdlib / R.jar 等 8 项**），
 * 编译错误反而从 48 条涨到 221 条（实测）。放进 `afterEvaluate` 后变为「原 8 项 + javac 产物」
 * 共 9 项，编译错误归零。
 *
 * 纯 Kotlin 模块没有 `compile*JavaWithJavac` 任务、该目录也不存在，
 * 因此 `tasks.matching {}` 为空、`classpath.from()` 指向不存在的目录会被编译器忽略，均无副作用。
 */
fun Project.enableDetektMixedSourceSetClasspath() {
    afterEvaluate {
        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            val variant = name.removePrefix("detekt")
            if (variant != "Debug" && variant != "Release") return@configureEach
            val lower = variant.lowercase()
            val javacTaskName = "compile${variant}JavaWithJavac"
            classpath.from(
                layout.buildDirectory.dir("intermediates/javac/$lower/$javacTaskName/classes"),
            )
            dependsOn(tasks.matching { it.name == javacTaskName })
        }
    }
}
