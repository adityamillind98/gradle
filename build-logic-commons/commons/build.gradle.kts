plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides common code used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    doFirst {
        println("""classpathSnapshotProperties:
            |${classpathSnapshotProperties.useClasspathSnapshot.orNull}
            |${classpathSnapshotProperties.classpathSnapshot.files}
            |${classpathSnapshotProperties.classpath.files}
            |${classpathSnapshotProperties.classpathSnapshotDir.orNull}""".trimMargin())
        println("toolchain: ${kotlinJavaToolchainProvider.get().javaVersion.orNull}")
    }
}
