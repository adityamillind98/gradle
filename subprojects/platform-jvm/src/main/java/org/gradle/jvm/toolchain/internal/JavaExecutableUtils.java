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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.InvalidUserDataException;

import java.io.File;

public class JavaExecutableUtils {

    public static File validateExecutable(String executable) {
        File executableFile = new File(executable);
        if (!executableFile.isAbsolute()) {
            throw new InvalidUserDataException("Configuring an executable via a relative path (" + executable + ") is not allowed, resolving it might yield unexpected results");
        }
        if (!executableFile.exists()) {
            throw new InvalidUserDataException("The configured executable does not exist (" + executableFile.getAbsolutePath() + ")");
        }
        if (executableFile.isDirectory()) {
            throw new InvalidUserDataException("The configured executable is a directory (" + executableFile.getAbsolutePath() + ")");
        }
        return executableFile;
    }

}
