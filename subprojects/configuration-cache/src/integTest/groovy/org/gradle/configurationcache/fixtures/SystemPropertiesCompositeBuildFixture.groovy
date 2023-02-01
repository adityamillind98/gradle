/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.test.fixtures.file.TestFile

class SystemPropertiesCompositeBuildFixture {

    static Set<List<SystemPropertyDefinition>> definitions() {
        [
            new RootBuildDefinition() as SystemPropertyDefinition,
            new BuildSrcDefinition(),
            new IncludedBuildDefinition("included-build"),
            new IncludedBuildDefinition("included-build-2")
        ].subsequences()
    }

    static List<Spec> specs() {
        [
            definitions(),
            [new CliOverride()],
            SystemPropertyAccess.values()
        ]
            .combinations()
            .findAll { List<SystemPropertyDefinition> definitions, SystemPropertyOverride override, SystemPropertyAccess access ->
                switch (access) {
                    case SystemPropertyAccess.ROOT_BUILD_SCRIPT:
                    case SystemPropertyAccess.ROOT_SETTINGS_SCRIPT:
                        return definitions.any { it instanceof RootBuildDefinition }
                    case SystemPropertyAccess.BUILDSRC_BUILD_SCRIPT:
                    case SystemPropertyAccess.BUILDSRC_SETTINGS_SCRIPT:
                        return definitions.any { it instanceof BuildSrcDefinition }
                    case SystemPropertyAccess.INCLUDED_BUILD_SCRIPT:
                    case SystemPropertyAccess.INCLUDED_SETTINGS_SCRIPT:
                        return definitions.any { it instanceof IncludedBuildDefinition }
                    default:
                        return false
                }
            }.collect { List<SystemPropertyDefinition> definitions, SystemPropertyOverride override, SystemPropertyAccess access ->
            new Spec(definitions, override, access)
        }
    }

    static class Spec {
        private final List<SystemPropertyDefinition> systemPropertyDefinitions
        private final SystemPropertyOverride systemPropertyOverride
        private final SystemPropertyAccess systemPropertyAccess

        Spec(List<SystemPropertyDefinition> systemPropertyDefinitions, SystemPropertyOverride systemPropertyOverride, SystemPropertyAccess systemPropertyAccess) {
            this.systemPropertyOverride = systemPropertyOverride
            this.systemPropertyDefinitions = systemPropertyDefinitions
            this.systemPropertyAccess = systemPropertyAccess
        }

        @Override
        String toString() {
            return "definitions: ${systemPropertyDefinitions.join(", ")}, access: $systemPropertyAccess, override: $systemPropertyOverride"
        }

        SystemPropertiesCompositeBuildFixture createFixtureFor(AbstractConfigurationCacheIntegrationTest test, String propertyKey) {
            return new SystemPropertiesCompositeBuildFixture(this, test, propertyKey)
        }
    }

    enum SystemPropertyAccess {
        ROOT_BUILD_SCRIPT,
        ROOT_SETTINGS_SCRIPT,
        BUILDSRC_BUILD_SCRIPT,
        BUILDSRC_SETTINGS_SCRIPT,
        INCLUDED_BUILD_SCRIPT,
        INCLUDED_SETTINGS_SCRIPT,
        NO_ACCESS
    }

    interface SystemPropertyOverride {

        void apply(String propertyKey)

        String[] runArgs(String propertyKey)

        String overriddenValue()
    }

    static class CliOverride implements SystemPropertyOverride {

        @Override
        void apply(String propertyKey) {}

        @Override
        String[] runArgs(String propertyKey) {
            return ["-D$propertyKey=${overriddenValue()}"]
        }

        @Override
        String overriddenValue() {
            return "overridden property"
        }

        @Override
        String toString() {
            return "CLI"
        }
    }

    static class GlobalOverride implements SystemPropertyOverride {

        @Override
        void apply(String propertyKey) {
            System.setProperty(propertyKey, overriddenValue())
        }

        @Override
        String[] runArgs(String propertyKey) {
            return []
        }

        @Override
        String overriddenValue() {
            return "overridden property"
        }

        @Override
        String toString() {
            return "Global"
        }
    }

    static interface SystemPropertyDefinition {

        void setup(AbstractIntegrationSpec spec, String propertyKey)

        String propertyValue()
    }

    static class RootBuildDefinition implements SystemPropertyDefinition {

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
        }

        @Override
        String propertyValue() {
            return "root build property"
        }

        @Override
        String toString() {
            return "Root build"
        }
    }

    static class IncludedBuildDefinition implements SystemPropertyDefinition {

        final String name

        IncludedBuildDefinition(String name) {
            this.name = name
        }

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("$name/gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
            spec.settingsFile("""includeBuild('$name')\n""")
        }

        @Override
        String propertyValue() {
            return "'$name' build property"
        }

        @Override
        String toString() {
            return "'$name' build"
        }
    }

    static class BuildSrcDefinition implements SystemPropertyDefinition {

        @Override
        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            spec.testDirectory.file("buildSrc/gradle.properties") << "systemProp.$propertyKey=${propertyValue()}"
            spec.testDirectory.file("buildSrc/build.gradle").touch()
        }

        @Override
        String propertyValue() {
            return "buildSrc property"
        }

        @Override
        String toString() {
            return "BuildSrc"
        }
    }

    private final Spec spec
    private final AbstractConfigurationCacheIntegrationTest test
    private final String propertyKey

    SystemPropertiesCompositeBuildFixture(Spec spec, AbstractConfigurationCacheIntegrationTest test, String propertyKey) {
        this.spec = spec
        this.test = test
        this.propertyKey = propertyKey
    }

    void setup() {
        spec.systemPropertyDefinitions.collect {
            it.setup(test, propertyKey)
        }

        test.buildFile << """
            task echo(type: DefaultTask) {
                def property = providers.systemProperty('$propertyKey')
                doFirst {
                    println('Execution: ' + property.orNull)
                }
            }
            """

        TestFile systemPropertyAccessLocation

        switch (spec.systemPropertyAccess) {
            case SystemPropertyAccess.ROOT_BUILD_SCRIPT:
                systemPropertyAccessLocation = test.buildFile
                break
            case SystemPropertyAccess.ROOT_SETTINGS_SCRIPT:
                systemPropertyAccessLocation = test.file("settings.gradle")
                break
            case SystemPropertyAccess.BUILDSRC_BUILD_SCRIPT:
                systemPropertyAccessLocation = test.file("buildSrc/build.gradle")
                break
            case SystemPropertyAccess.BUILDSRC_SETTINGS_SCRIPT:
                systemPropertyAccessLocation = test.file("buildSrc/settings.gradle")
                break
            case SystemPropertyAccess.INCLUDED_BUILD_SCRIPT:
                IncludedBuildDefinition includedBuild = spec.systemPropertyDefinitions.find { it instanceof IncludedBuildDefinition }
                systemPropertyAccessLocation = test.file("${includedBuild.name}/build.gradle")
                break
            case SystemPropertyAccess.INCLUDED_SETTINGS_SCRIPT:
                IncludedBuildDefinition includedBuild = spec.systemPropertyDefinitions.find { it instanceof IncludedBuildDefinition }
                systemPropertyAccessLocation = test.file("${includedBuild.name}/settings.gradle")
                break
            default:
                systemPropertyAccessLocation = null
        }

        if (systemPropertyAccessLocation != null) {
            systemPropertyAccessLocation << "println('Configuration: ' + providers.systemProperty('$propertyKey').orNull)\n"
        }
    }

    void cleanPropertyRun() {
        System.clearProperty(propertyKey)
        test.configurationCacheRun("echo")
    }

    void assertAfterFirstRun() {
        if (spec.systemPropertyAccess != SystemPropertyAccess.NO_ACCESS) {
            test.outputContains "Configuration: ${expectedConfigurationTimeValue()}"
        }
        test.outputContains("Execution: ${expectedExecutionTimeValue()}")
    }

    void overriddenPropertyRun() {
        spec.systemPropertyOverride.apply(propertyKey)
        test.configurationCacheRun("echo", *spec.systemPropertyOverride.runArgs(propertyKey))
    }

    void assertAfterOverriddenRun(ConfigurationCacheBuildOperationsFixture configurationCache) {
        if (spec.systemPropertyAccess == SystemPropertyAccess.NO_ACCESS) {
            configurationCache.assertStateLoaded()
        } else {
            test.outputContains "Calculating task graph as configuration cache cannot be reused because system property '$propertyKey' has changed"
            test.outputContains "Configuration: ${spec.systemPropertyOverride.overriddenValue()}"
            test.outputContains "Execution: ${spec.systemPropertyOverride.overriddenValue()}"
        }
    }

    private String expectedConfigurationTimeValue() {
        String expectedValue
        switch (spec.systemPropertyAccess) {
            case SystemPropertyAccess.ROOT_SETTINGS_SCRIPT:
                RootBuildDefinition rootBuild = spec.systemPropertyDefinitions.find { it instanceof RootBuildDefinition }
                expectedValue = rootBuild.propertyValue()
                break
            case SystemPropertyAccess.BUILDSRC_SETTINGS_SCRIPT:
                BuildSrcDefinition buildSrcBuild = spec.systemPropertyDefinitions.find { it instanceof BuildSrcDefinition }
                expectedValue = buildSrcBuild.propertyValue()
                break
            case SystemPropertyAccess.INCLUDED_SETTINGS_SCRIPT:
                IncludedBuildDefinition includeBuild = spec.systemPropertyDefinitions.find { it instanceof IncludedBuildDefinition }
                expectedValue = includeBuild.propertyValue()
                break
            default:
                expectedValue = expectedExecutionTimeValue()
        }

        return expectedValue
    }

    private String expectedExecutionTimeValue() {
        SystemPropertyDefinition buildSrcDefinition =
            spec.systemPropertyDefinitions.find { it instanceof BuildSrcDefinition }
        return buildSrcDefinition ? buildSrcDefinition.propertyValue() : spec.systemPropertyDefinitions.last().propertyValue()
    }
}
