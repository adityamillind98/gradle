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

package org.gradle.api.tasks.internal;

import org.gradle.api.file.ProjectLayout;

import javax.annotation.Nullable;
import java.io.File;

public class JavaExecutableUtils {
    public static File resolveExecutable(@Nullable ProjectLayout projectLayout, String executable) {
        File file = new File(executable);
        if (file.isAbsolute() || projectLayout == null) {
            return file;
        } else {
            return new File(projectLayout.getProjectDirectory().getAsFile(), executable);
        }
    }
}
