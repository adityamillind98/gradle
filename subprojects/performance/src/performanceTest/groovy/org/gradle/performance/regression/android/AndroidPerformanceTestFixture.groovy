/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.performance.regression.android

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext

@SelfType(AbstractCrossVersionPerformanceTest)
trait AndroidPerformanceTestFixture {

    AndroidTestProject getAndroidTestProject() {
        // We assume here already, since the test may try to cast the returned test project to `IncrementalAndroidTestProject`,
        // which fails when the test project is non-incremental.
        runner.assumeShouldRun()
        AndroidTestProject.projectFor(runner.testProject)
    }

    void configureProjectJavaHomeToJdk11() {
        def buildJavaHome = AvailableJavaHomes.jdk11.javaHome
        runner.addBuildMutator { invocation -> new JavaHomeMutator(invocation, buildJavaHome) }
    }

    static class JavaHomeMutator implements BuildMutator {
        private final File buildJavaHome
        private final InvocationSettings invocation

        JavaHomeMutator(InvocationSettings invocation, File buildJavaHome) {
            this.invocation = invocation
            this.buildJavaHome = buildJavaHome
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def gradleProps = new File(invocation.projectDir, "gradle.properties")
            gradleProps << "\norg.gradle.java.home=${buildJavaHome.absolutePath.replace("\\", "/")}\n"
        }
    }
}
