/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.api.tasks.internal.JavaExecutableUtils;
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec;

import javax.annotation.Nullable;

public class TestExecutableUtils {

    @Nullable
    public static JavaToolchainSpec getExecutableToolchainSpec(Test task, ObjectFactory objectFactory) {
        String customExecutable = task.getExecutable();
        if (customExecutable != null) {
            return SpecificInstallationToolchainSpec.fromJavaExecutable(objectFactory, JavaExecutableUtils.resolveExecutable(task.getProject().getLayout(), customExecutable));
        }

        return null;
    }
}
