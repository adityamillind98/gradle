/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultLocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class DefaultLocalConfigurationMetadataBuilder implements LocalConfigurationMetadataBuilder {
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ExcludeRuleConverter excludeRuleConverter;

    public DefaultLocalConfigurationMetadataBuilder(DependencyDescriptorFactory dependencyDescriptorFactory,
                                                    ExcludeRuleConverter excludeRuleConverter) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.excludeRuleConverter = excludeRuleConverter;
    }

    @Override
    public LocalConfigurationMetadata create(
        ConfigurationInternal configuration,
        DefaultLocalComponentMetadata parent,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        // Run any actions to add/modify dependencies
        configuration.runDependencyActions();

        ComponentIdentifier componentId = parent.getId();

        ImmutableList.Builder<LocalOriginDependencyMetadata> dependencyBuilder = ImmutableList.builder();
        ImmutableList.Builder<LocalFileDependencyMetadata> fileBuilder = ImmutableList.builder();
        ImmutableList.Builder<ExcludeMetadata> excludeBuilder = ImmutableList.builder();

        ImmutableAttributes attributes = configuration.getAttributes().asImmutable();
        for (Dependency dependency : configuration.getDependencies()) {
            if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;
                dependencyBuilder.add(dependencyDescriptorFactory.createDependencyDescriptor(
                    componentId, configuration.getName(), attributes, moduleDependency
                ));
            } else if (dependency instanceof FileCollectionDependency) {
                final FileCollectionDependency fileDependency = (FileCollectionDependency) dependency;
                fileBuilder.add(new DefaultLocalFileDependencyMetadata(fileDependency));
            } else {
                throw new IllegalArgumentException("Cannot convert dependency " + dependency + " to local component dependency metadata.");
            }
        }

        for (DependencyConstraint dependencyConstraint : configuration.getDependencyConstraints()) {
            dependencyBuilder.add(dependencyDescriptorFactory.createDependencyConstraintDescriptor(
                componentId, configuration.getName(), attributes, dependencyConstraint)
            );
        }

        for (ExcludeRule excludeRule : configuration.getExcludeRules()) {
            excludeBuilder.add(excludeRuleConverter.convertExcludeRule(excludeRule));
        }

        ComponentConfigurationIdentifier configurationIdentifier = new ComponentConfigurationIdentifier(componentId, configuration.getName());

        ImmutableList.Builder<PublishArtifact> artifactBuilder = ImmutableList.builder();
        ImmutableSet.Builder<LocalVariantMetadata> variantsBuilder = ImmutableSet.builder();
        configuration.collectVariants(new ConfigurationInternal.VariantVisitor() {
            @Override
            public void visitArtifacts(Collection<? extends PublishArtifact> artifacts) {
                artifactBuilder.addAll(artifacts);
            }

            @Override
            public void visitOwnVariant(DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                variantsBuilder.add(new LocalVariantMetadata(configuration.getName(), configurationIdentifier, componentId, displayName, attributes, artifacts, ImmutableCapabilities.of(capabilities), model, calculatedValueContainerFactory));
            }

            @Override
            public void visitChildVariant(String name, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                variantsBuilder.add(new LocalVariantMetadata(configuration.getName() + "-" + name, new NestedVariantIdentifier(configurationIdentifier, name), componentId, displayName, attributes, artifacts, ImmutableCapabilities.of(capabilities), model, calculatedValueContainerFactory));
            }
        });

        String name = configuration.getName();
        ImmutableSet<String> hierarchy = Configurations.getNames(configuration.getHierarchy());

        ImmutableList<LocalOriginDependencyMetadata> definedDependencies = dependencyBuilder.build();
        ImmutableList<LocalFileDependencyMetadata> definedFiles = fileBuilder.build();
        ImmutableList<ExcludeMetadata> definedExcludes = excludeBuilder.build();

        return new DefaultLocalConfigurationMetadata(
            name,
            configuration.getDescription(),
            componentId,
            configuration.isVisible(),
            configuration.isTransitive(),
            hierarchy,
            attributes,
            ImmutableCapabilities.of(Configurations.collectCapabilities(configuration, Sets.newHashSet(), Sets.newHashSet())),
            configuration.isCanBeConsumed(),
            configuration.getConsumptionDeprecation(),
            configuration.isCanBeResolved(),
            definedDependencies,
            definedFiles,
            definedExcludes,
            getAllDependencies(parent, name, hierarchy, attributes, definedDependencies),
            getAllFileDependencies(parent, name, hierarchy, definedFiles),
            getAllExcludes(parent, name, hierarchy, definedExcludes),
            variantsBuilder.build(),
            artifactBuilder.build(),
            model,
            calculatedValueContainerFactory,
            parent
        );
    }

    private static ImmutableList<LocalOriginDependencyMetadata> getAllDependencies(LocalComponentMetadata parent, String configName, ImmutableSet<String> hierarchy, ImmutableAttributes attributes, ImmutableList<LocalOriginDependencyMetadata> definedDependencies) {
        ImmutableList<LocalOriginDependencyMetadata> result = parent.getConfigurationNames().stream().filter(hierarchy::contains)
            .map(name -> {
                if (name.equals(configName)) {
                    return definedDependencies;
                }
                return parent.getConfiguration(name).getDefinedDependencies();
            })
            .flatMap(List::stream)
            .collect(ImmutableList.toImmutableList());

        AttributeValue<Category> attributeValue = attributes.findEntry(Category.CATEGORY_ATTRIBUTE);
        if (attributeValue.isPresent() && attributeValue.get().getName().equals(Category.ENFORCED_PLATFORM)) {
            // need to wrap all dependencies to force them
            ImmutableList.Builder<LocalOriginDependencyMetadata> forcedResult = ImmutableList.builder();
            for (LocalOriginDependencyMetadata rawDependency : result) {
                forcedResult.add(rawDependency.forced());
            }
            return forcedResult.build();
        }
        return result;
    }

    private static ImmutableSet<LocalFileDependencyMetadata> getAllFileDependencies(LocalComponentMetadata parent, String configName, ImmutableSet<String> hierarchy, ImmutableList<LocalFileDependencyMetadata> definedFiles) {
        return parent.getConfigurationNames().stream().filter(hierarchy::contains)
            .map(name -> {
                if (name.equals(configName)) {
                    return definedFiles;
                }
                return parent.getConfiguration(name).getDefinedFiles();
            })
            .flatMap(List::stream)
            .collect(ImmutableSet.toImmutableSet());
    }

    private static ImmutableList<ExcludeMetadata> getAllExcludes(LocalComponentMetadata parent, String configName, ImmutableSet<String> hierarchy, ImmutableList<ExcludeMetadata> definedExcludes) {
        return parent.getConfigurationNames().stream().filter(hierarchy::contains)
            .map(name -> {
                if (name.equals(configName)) {
                    return definedExcludes;
                }
                return parent.getConfiguration(name).getDefinedExcludes();
            })
            .flatMap(List::stream)
            .collect(ImmutableList.toImmutableList());
    }

    private static class NestedVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final VariantResolveMetadata.Identifier parent;
        private final String name;

        public NestedVariantIdentifier(VariantResolveMetadata.Identifier parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return parent.hashCode() ^ name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            NestedVariantIdentifier other = (NestedVariantIdentifier) obj;
            return parent.equals(other.parent) && name.equals(other.name);
        }
    }

    private static class DefaultLocalFileDependencyMetadata implements LocalFileDependencyMetadata {
        private final FileCollectionDependency fileDependency;

        DefaultLocalFileDependencyMetadata(FileCollectionDependency fileDependency) {
            this.fileDependency = fileDependency;
        }

        @Override
        public FileCollectionDependency getSource() {
            return fileDependency;
        }

        @Override @Nullable
        public ComponentIdentifier getComponentId() {
            return ((SelfResolvingDependencyInternal) fileDependency).getTargetComponentId();
        }

        @Override
        public FileCollectionInternal getFiles() {
            return (FileCollectionInternal) fileDependency.getFiles();
        }
    }
}
