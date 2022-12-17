/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.integration

import org.gradle.util.internal.ToBeImplemented
import org.junit.Test
import spock.lang.Issue


class GradleKotlinDslRegressionsTest : AbstractPluginIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/9919")
    fun `gradleKotlinDsl dependency declaration does not throw`() {

        withBuildScript(
            """
            plugins { java }
            dependencies {
                compileOnly(gradleKotlinDsl())
            }
            """
        )

        build("help")
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-44303")
    fun `can configure ext extension`() {
        withBuildScript(
            """
            ext {
                set("foo", "bar")
            }
            """
        )

        build("help")
    }

    /**
     * When this issue gets fixed in a future Kotlin version, remove -XXLanguage:+DisableCompatibilityModeForNewInference from Kotlin DSL compiler arguments.
     */
    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-44303")
    @ToBeImplemented
    fun `kotlin resolution and inference issue KT-44303`() {
        withBuildScript("""
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins { `embedded-kotlin` }
            $repositoriesBlock
            dependencies {
                implementation(gradleKotlinDsl())
            }
        """)

        withFile("src/main/kotlin/code.kt", """
            import org.gradle.api.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    ext {
                        set("foo", "bar")
                    }
                }
            }
        """)

        val result = buildAndFail("classes")

        result.assertHasFailure("Execution failed for task ':compileKotlin'.") {
            it.assertHasCause("Compilation error. See log for more details")
        }
        result.assertHasErrorOutput("src/main/kotlin/code.kt:7:25 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch")
    }


    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55068")
    fun `kotlin ir backend issue kt-55068`() {

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            data class Container(val property: Property<String> = objects.property())
        """)
        withBuildScript("""plugins { id("my-plugin") }""")

        build("help")
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55065")
    fun `kotlin ir backend issue kt-55065`() {

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            tasks.withType<DefaultTask>().configureEach {
                val p: String by project
            }
        """)
        withBuildScript("""plugins { id("my-plugin") }""")

        build("help")
    }

    /**
     * When this issue gets fixed in a future Kotlin version, remove -XXLanguage:-TypeEnhancementImprovementsInStrictMode from Kotlin DSL compiler arguments.
     */
    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55542")
    @ToBeImplemented
    fun `nullable type parameters on non-nullable member works without disabling Koltlin type enhancement improvements in strict mode`() {
        withBuildScript("""
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins { `embedded-kotlin` }
            $repositoriesBlock
            dependencies {
                implementation(gradleKotlinDsl())
            }
            tasks.withType<KotlinCompile>().configureEach {
                kotlinOptions.freeCompilerArgs += "-Xjsr305=strict"
            }
        """)

        withFile("src/main/kotlin/code.kt", """
            import org.gradle.api.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    provider { "thing" }.map { null }
                }
            }
        """)

        val result = buildAndFail("classes")

        result.assertHasFailure("Execution failed for task ':compileKotlin'.") {
            it.assertHasCause("Compilation error. See log for more details")
        }
        result.assertHasErrorOutput("src/main/kotlin/code.kt:6:48 Null can not be a value of a non-null type Nothing")
    }
}
