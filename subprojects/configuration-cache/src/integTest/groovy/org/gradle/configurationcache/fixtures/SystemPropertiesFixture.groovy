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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SystemPropertiesFixture {

    static List<Spec> specs() {
        [
            new RootBuildDefinition(),
            new BuildSrcDefinition(),
            new IncludedBuildDefinition("included-build"),
            new IncludedBuildDefinition("included-build-2")
        ].subsequences().collect { new Spec(it) }
    }


    static class Spec {
        private final List<SystemPropertyDefinition> systemPropertyDefinitions

        Spec(List<SystemPropertyDefinition> systemPropertyDefinitions) {
            this.systemPropertyDefinitions = systemPropertyDefinitions
        }

        void setup(AbstractIntegrationSpec spec, String propertyKey) {
            systemPropertyDefinitions.collect {
                it.setup(spec, propertyKey)
            }
        }

        String expectedPropertyValue() {
            SystemPropertyDefinition buildSrcDefinition = systemPropertyDefinitions
                .find { it instanceof BuildSrcDefinition }
            return buildSrcDefinition ? buildSrcDefinition.propertyValue() : systemPropertyDefinitions.last().propertyValue()
        }

        @Override
        String toString() {
            return "${systemPropertyDefinitions.join(", ")}"
        }
    }


    interface SystemPropertyDefinition {

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

        private final String name

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
}
