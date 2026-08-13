plugins {
    // 在根工程声明插件版本，子模块按需 apply（避免版本目录生成步骤，兼容受限环境）
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}
