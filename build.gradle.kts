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
