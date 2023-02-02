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
package org.gradle.api.plugins.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.internal.Cast;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.util.internal.TextUtil.camelToKebabCase;

/**
 * Helpers for Jvm plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
public class JvmPluginsHelper {

    /***
     * For compatibility with <a href="https://plugins.gradle.org/plugin/io.freefair.aspectj">AspectJ Plugin</a>
     */
    @Deprecated
    public static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, CompileOptions options, final Project target) {
        compile.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
        compile.setSource(sourceSet.getJava());

        compileAgainstJavaOutputs(compile, sourceSet, target.getObjects());
        configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, options, target);
    }

    /**
     * Configures {@code compileTask} to compile against {@code sourceSet}'s compile classpath
     * in addition to the outputs of the java compilation, as specified by {@link SourceSet#getJava()}
     *
     * @param compileTask The task to configure.
     * @param sourceSet The source set whose output contains the java classes to compile against.
     * @param objectFactory An {@link ObjectFactory}.
     */
    public static void compileAgainstJavaOutputs(AbstractCompile compileTask, final SourceSet sourceSet, final ObjectFactory objectFactory) {
        ConfigurableFileCollection classpath = objectFactory.fileCollection();
        classpath.from((Callable<Object>) () -> sourceSet.getCompileClasspath().plus(objectFactory.fileCollection().from(sourceSet.getJava().getClassesDirectory())));
        compileTask.getConventionMapping().map("classpath", () -> classpath);
    }

    public static void configureAnnotationProcessorPath(final SourceSet sourceSet, SourceDirectorySet sourceDirectorySet, CompileOptions options, final Project target) {
        final ConventionMapping conventionMapping = new DslObject(options).getConventionMapping();
        conventionMapping.map("annotationProcessorPath", sourceSet::getAnnotationProcessorPath);
        String annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        options.getGeneratedSourceOutputDirectory().convention(target.getLayout().getBuildDirectory().dir(annotationProcessorGeneratedSourcesChildPath));
    }

    /***
     * For compatibility with <a href="https://plugins.gradle.org/plugin/io.freefair.aspectj">AspectJ Plugin</a>
     */
    @Deprecated
    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        TaskProvider<? extends AbstractCompile> taskProvider = Cast.uncheckedCast(compileTask);
        configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, taskProvider, options);
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.getClassesDirs().from(sourceDirectorySet.getDestinationDirectory()).builtBy(compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(options.flatMap(CompileOptions::getGeneratedSourceOutputDirectory));
        sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
    }

    public static void configureJavaDocTask(@Nullable String featureName, SourceSet sourceSet, TaskContainer tasks, @Nullable JavaPluginExtension javaPluginExtension) {
        String javadocTaskName = sourceSet.getJavadocTaskName();
        if (!tasks.getNames().contains(javadocTaskName)) {
            tasks.register(javadocTaskName, Javadoc.class, javadoc -> {
                javadoc.setDescription("Generates Javadoc API documentation for the " + (featureName == null ? "main source code." : "'" + featureName + "' feature."));
                javadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
                javadoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
                javadoc.setSource(sourceSet.getAllJava());
                if (javaPluginExtension != null) {
                    javadoc.getConventionMapping().map("destinationDir", () -> javaPluginExtension.getDocsDir().dir(javadocTaskName).get().getAsFile());
                    javadoc.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
                }
            });
        }
    }

    public static Configuration createDocumentationVariantWithArtifact(
        String variantName,
        @Nullable String featureName,
        String docsType,
        List<Capability> capabilities,
        String jarTaskName,
        Object artifactSource,
        ProjectInternal project
    ) {
        Configuration variant = project.getConfigurations().maybeCreate(variantName);
        variant.setVisible(false);
        variant.setCanBeResolved(false);
        variant.setCanBeConsumed(true);
        variant.setDescription(docsType + " elements for " + (featureName == null ? "main" : featureName) + ".");

        ObjectFactory objectFactory = project.getObjects();
        AttributeContainer attributes = variant.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        capabilities.forEach(variant.getOutgoing()::capability);

        TaskContainer tasks = project.getTasks();

        if (!tasks.getNames().contains(jarTaskName)) {
            TaskProvider<Jar> jarTask = tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the " + (featureName == null ? "main " + docsType + "." : (docsType + " of the '" + featureName + "' feature.")));
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(artifactSource);
                jar.getArchiveClassifier().set(camelToKebabCase(featureName == null ? docsType : (featureName + "-" + docsType)));
            });
            if (tasks.getNames().contains(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)) {
                tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(jarTask));
            }
        }
        TaskProvider<Task> jar = tasks.named(jarTaskName);
        variant.getOutgoing().artifact(new LazyPublishArtifact(jar, project.getFileResolver(), project.getTaskDependencyFactory()));

        return variant;
    }

    /**
     * Gets the main Java component. This method assumes the Java plugin is applied.
     *
     * @throws GradleException If the {@code java} component does not exist.
     */
    public static JvmSoftwareComponentInternal getJavaComponent(Project project) {
        SoftwareComponent component = project.getComponents().findByName("java");

        if (!(component instanceof JvmSoftwareComponentInternal)) {
            throw new GradleException("The Java plugin must be applied to access the java component.");
        }

        return (JvmSoftwareComponentInternal) component;
    }

    /**
     * Gets the default test suite. This method assumes the Java plugin is applied.
     *
     * @throws GradleException If the default test suite does not exist.
     */
    public static JvmTestSuite getDefaultTestSuite(Project project) {
        String message = "The Java plugin must be applied to access the default test suite.";

        TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
        if (testing == null) {
            throw new GradleException(message);
        }

        TestSuite defaultTestSuite = testing.getSuites().findByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME);
        if (!(defaultTestSuite instanceof JvmTestSuite)) {
            throw new GradleException(message);
        }

        return (JvmTestSuite) defaultTestSuite;
    }

    public static Action<ConfigurationInternal> configureLibraryElementsAttributeForCompileClasspath(boolean javaClasspathPackaging, SourceSet sourceSet, TaskProvider<JavaCompile> compileTaskProvider, ObjectFactory objectFactory) {
        return conf -> {
            AttributeContainerInternal attributes = conf.getAttributes();
            if (!attributes.contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)) {
                String libraryElements;
                // If we are compiling a module, we require JARs of all dependencies as they may potentially include an Automatic-Module-Name
                if (javaClasspathPackaging || JavaModuleDetector.isModuleSource(compileTaskProvider.get().getModularity().getInferModulePath().get(), CompilationSourceDirs.inferSourceRoots((FileTreeInternal) sourceSet.getJava().getAsFileTree()))) {
                    libraryElements = LibraryElements.JAR;
                } else {
                    libraryElements = LibraryElements.CLASSES;
                }
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, libraryElements));
            }
        };
    }
}
