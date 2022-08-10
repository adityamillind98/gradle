/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.notations;

import com.google.common.collect.Interner;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DependencyVariant;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationConverterToNotationParserAdapter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.Map;

public class DependencyNotationParser {
    public static DependencyNotationParser create(Instantiator instantiator,
                                                  DefaultProjectDependencyFactory dependencyFactory,
                                                  ClassPathRegistry classPathRegistry,
                                                  FileCollectionFactory fileCollectionFactory,
                                                  RuntimeShadedJarFactory runtimeShadedJarFactory,
                                                  CurrentGradleInstallation currentGradleInstallation,
                                                  Interner<String> stringInterner,
                                                  ImmutableAttributesFactory attributesFactory,
                                                  NotationParser<Object, Capability> capabilityNotationParser) {
        NotationConverter<String, ? extends ExternalModuleDependency> stringNotationConverter =
            new DependencyStringNotationConverter<>(instantiator, DefaultExternalModuleDependency.class, stringInterner);
        NotationConverter<MinimalExternalModuleDependency, ? extends ExternalModuleDependency> minimalExternalDependencyNotationConverter =
            new MinimalExternalDependencyNotationConverter(instantiator, attributesFactory, capabilityNotationParser);
        MapNotationConverter<? extends ExternalModuleDependency> mapNotationConverter =
            new DependencyMapNotationConverter<>(instantiator, DefaultExternalModuleDependency.class);
        NotationConverter<FileCollection, ? extends FileCollectionDependency> filesNotationConverter =
            new DependencyFilesNotationConverter(instantiator);
        NotationConverter<Project, ? extends ProjectDependency> projectNotationConverter =
            new DependencyProjectNotationConverter(dependencyFactory);
        NotationParser<Object, Dependency> notationParser = NotationParserBuilder
            .toType(Dependency.class)
            .noImplicitConverters()
            .fromCharSequence(stringNotationConverter)
            .fromType(MinimalExternalModuleDependency.class, minimalExternalDependencyNotationConverter)
            .converter(mapNotationConverter)
            .fromType(FileCollection.class, filesNotationConverter)
            .fromType(Project.class, projectNotationConverter)
            .fromType(DependencyFactoryInternal.ClassPathNotation.class, new DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation))
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
            .toComposite();
        return new DependencyNotationParser(
            notationParser,
            new NotationConverterToNotationParserAdapter<>(stringNotationConverter),
            new NotationConverterToNotationParserAdapter<>(minimalExternalDependencyNotationConverter),
            new NotationConverterToNotationParserAdapter<>(mapNotationConverter),
            new NotationConverterToNotationParserAdapter<>(filesNotationConverter),
            new NotationConverterToNotationParserAdapter<>(projectNotationConverter)
        );
    }

    private final NotationParser<Object, Dependency> notationParser;
    private final NotationParser<String, ? extends ExternalModuleDependency> stringNotationParser;
    private final NotationParser<MinimalExternalModuleDependency, ? extends ExternalModuleDependency> minimalExternalModuleDependencyNotationParser;
    private final NotationParser<Map<String, ?>, ? extends ExternalModuleDependency> mapNotationParser;
    private final NotationParser<FileCollection, ? extends FileCollectionDependency> fileCollectionNotationParser;
    private final NotationParser<Project, ? extends ProjectDependency> projectNotationParser;

    private DependencyNotationParser(NotationParser<Object, Dependency> notationParser,
                                     NotationParser<String, ? extends ExternalModuleDependency> stringNotationParser,
                                     NotationParser<MinimalExternalModuleDependency, ? extends ExternalModuleDependency> minimalExternalModuleDependencyNotationParser,
                                     NotationParser<Map<String, ?>, ? extends ExternalModuleDependency> mapNotationParser,
                                     NotationParser<FileCollection, ? extends FileCollectionDependency> fileCollectionNotationParser,
                                     NotationParser<Project, ? extends ProjectDependency> projectNotationParser
    ) {
        this.notationParser = notationParser;
        this.stringNotationParser = stringNotationParser;
        this.minimalExternalModuleDependencyNotationParser = minimalExternalModuleDependencyNotationParser;
        this.mapNotationParser = mapNotationParser;
        this.fileCollectionNotationParser = fileCollectionNotationParser;
        this.projectNotationParser = projectNotationParser;
    }

    public NotationParser<Object, Dependency> getNotationParser() {
        return notationParser;
    }

    public NotationParser<String, ? extends ExternalModuleDependency> getStringNotationParser() {
        return stringNotationParser;
    }

    public NotationParser<MinimalExternalModuleDependency, ? extends ExternalModuleDependency> getMinimalExternalModuleDependencyNotationParser() {
        return minimalExternalModuleDependencyNotationParser;
    }

    public NotationParser<Map<String, ?>, ? extends ExternalModuleDependency> getMapNotationParser() {
        return mapNotationParser;
    }

    public NotationParser<FileCollection, ? extends FileCollectionDependency> getFileCollectionNotationParser() {
        return fileCollectionNotationParser;
    }

    public NotationParser<Project, ? extends ProjectDependency> getProjectNotationParser() {
        return projectNotationParser;
    }

    private static class MinimalExternalDependencyNotationConverter implements NotationConverter<MinimalExternalModuleDependency, DefaultExternalModuleDependency> {
        private final Instantiator instantiator;
        private final ImmutableAttributesFactory attributesFactory;
        private final NotationParser<Object, Capability> capabilityNotationParser;

        public MinimalExternalDependencyNotationConverter(Instantiator instantiator, ImmutableAttributesFactory attributesFactory, NotationParser<Object, Capability> capabilityNotationParser) {
            this.instantiator = instantiator;
            this.attributesFactory = attributesFactory;
            this.capabilityNotationParser = capabilityNotationParser;
        }

        @Override
        public void convert(MinimalExternalModuleDependency notation, NotationConvertResult<? super DefaultExternalModuleDependency> result) throws TypeConversionException {
            DefaultExternalModuleDependency moduleDependency = instantiator.newInstance(DefaultExternalModuleDependency.class, notation.getModule(), notation.getVersionConstraint());
            if (notation instanceof DependencyVariant) {
                moduleDependency.setAttributesFactory(attributesFactory);
                moduleDependency.setCapabilityNotationParser(capabilityNotationParser);
                DependencyVariant dependencyVariant = (DependencyVariant) notation;
                moduleDependency.attributes(dependencyVariant::mutateAttributes);
                moduleDependency.capabilities(dependencyVariant::mutateCapabilities);
                String classifier = dependencyVariant.getClassifier();
                String artifactType = dependencyVariant.getArtifactType();
                if (classifier != null || artifactType != null) {
                    ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency, artifactType, classifier);
                }
            }
            result.converted(moduleDependency);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
        }


    }
}
