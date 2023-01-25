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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata, LocalConfigurationGraphResolveMetadata {

    private final String name;
    private final String description;
    private final ComponentIdentifier componentId;
    private final boolean visible;
    private final boolean transitive;
    private final ImmutableSet<String> hierarchy;
    private final ImmutableAttributes attributes;
    private final ImmutableCapabilities capabilities;
    private final boolean canBeConsumed;
    private final DeprecationMessageBuilder.WithDocumentation consumptionDeprecation;
    private final boolean canBeResolved;
    private final List<LocalOriginDependencyMetadata> definedDependencies;
    private final List<LocalFileDependencyMetadata> definedFiles;
    private final List<ExcludeMetadata> definedExcludes;
    private final List<LocalOriginDependencyMetadata> allDependencies;
    private final Set<LocalFileDependencyMetadata> allFileDependencies;
    private final ImmutableList<ExcludeMetadata> allExcludes;

    // TODO: Move all this lazy artifact stuff to a "State" type.
    private final Set<LocalVariantMetadata> variants;
    private final ModelContainer<?> model;
    private final CalculatedValueContainerFactory factory;
    private final LocalComponentMetadata component;
    private final List<PublishArtifact> sourceArtifacts;
    private CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

    public DefaultLocalConfigurationMetadata(
        String name,
        String description,
        ComponentIdentifier componentId,
        boolean visible,
        boolean transitive,
        Set<String> hierarchy,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        boolean canBeConsumed,
        @Nullable DeprecationMessageBuilder.WithDocumentation consumptionDeprecation,
        boolean canBeResolved,
        List<LocalOriginDependencyMetadata> definedDependencies,
        List<LocalFileDependencyMetadata> definedFiles,
        List<ExcludeMetadata> definedExcludes,
        List<LocalOriginDependencyMetadata> allDependencies,
        Set<LocalFileDependencyMetadata> allFileDependencies,
        List<ExcludeMetadata> allExcludes,
        Set<LocalVariantMetadata> variants,
        final List<PublishArtifact> sourceArtifacts,
        ModelContainer<?> model,
        CalculatedValueContainerFactory factory,
        LocalComponentMetadata component
    ) {
        this.name = name;
        this.description = description;
        this.componentId = componentId;
        this.visible = visible;
        this.transitive = transitive;
        this.hierarchy = ImmutableSet.copyOf(hierarchy);
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.canBeConsumed = canBeConsumed;
        this.consumptionDeprecation = consumptionDeprecation;
        this.canBeResolved = canBeResolved;
        this.definedDependencies = definedDependencies;
        this.definedFiles = definedFiles;
        this.definedExcludes = definedExcludes;
        this.allDependencies = allDependencies;
        this.allFileDependencies = allFileDependencies;
        this.allExcludes = ImmutableList.copyOf(allExcludes);

        // TODO: These fields are related to artifact resolution and should be moved into a "State" type.
        this.variants = variants;
        this.sourceArtifacts = sourceArtifacts;
        this.model = model;
        this.factory = factory;
        this.component = component;
        this.artifacts = factory.create(Describables.of(description, "artifacts"), context -> {
            if (sourceArtifacts.isEmpty() && hierarchy.isEmpty()) {
                return ImmutableList.of();
            } else {
                return model.fromMutableState(m -> {
                    Set<LocalComponentArtifactMetadata> result = new LinkedHashSet<>(sourceArtifacts.size());
                    for (PublishArtifact sourceArtifact : sourceArtifacts) {
                        // The following line may realize tasks, so we wrap this code in a CalculatedValue.
                        result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                    }
                    for (String config : hierarchy) {
                        if (config.equals(name)) {
                            continue;
                        }
                        LocalConfigurationMetadata parent = component.getConfiguration(config);
                        result.addAll(parent.prepareToResolveArtifacts().getArtifacts());
                    }
                    return ImmutableList.copyOf(result);
                });
            }
        });
    }

    @Override
    public LocalConfigurationMetadata copy(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer) {
        ImmutableSet.Builder<LocalVariantMetadata> copiedVariants = ImmutableSet.builder();
        for (LocalVariantMetadata oldVariant : variants) {
            ImmutableList<LocalComponentArtifactMetadata> newArtifacts =
                oldVariant.prepareToResolveArtifacts().getArtifacts().stream()
                    .map(artifactTransformer::transform)
                    .collect(ImmutableList.toImmutableList());

            copiedVariants.add(new LocalVariantMetadata(
                oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(),
                newArtifacts, (ImmutableCapabilities) oldVariant.getCapabilities(), factory)
            );
        }

        DefaultLocalConfigurationMetadata copy = new DefaultLocalConfigurationMetadata(
            name, description, componentId, visible, transitive, hierarchy, attributes, capabilities,
            canBeConsumed, consumptionDeprecation, canBeResolved,
            definedDependencies, definedFiles, definedExcludes, allDependencies, allFileDependencies, allExcludes,
            copiedVariants.build(), sourceArtifacts, model, factory, component
        );

        copy.artifacts = factory.create(Describables.of(description, "artifacts"),
            prepareToResolveArtifacts().getArtifacts().stream()
                .map(artifactTransformer::transform)
                .collect(ImmutableList.toImmutableList())
        );

        return copy;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(componentId, "configuration", name);
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public ImmutableSet<String> getHierarchy() {
        return hierarchy;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean isCanBeConsumed() {
        return canBeConsumed;
    }

    @Override
    public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
        return consumptionDeprecation;
    }

    @Override
    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Override
    public List<LocalOriginDependencyMetadata> getDefinedDependencies() {
        return definedDependencies;
    }

    @Override
    public List<LocalFileDependencyMetadata> getDefinedFiles() {
        return definedFiles;
    }

    @Override
    public List<ExcludeMetadata> getDefinedExcludes() {
        return definedExcludes;
    }

    @Override
    public List<? extends LocalOriginDependencyMetadata> getDependencies() {
        return allDependencies;
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        return allFileDependencies;
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return allExcludes;
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }

    @Override
    public Set<? extends LocalVariantMetadata> getVariants() {
        return variants;
    }

    @Override
    public LocalConfigurationMetadata prepareToResolveArtifacts() {
        artifacts.finalizeIfNotAlready();
        for (LocalVariantMetadata variant : getVariants()) {
            variant.prepareToResolveArtifacts();
        }
        return this;
    }

    @Override
    public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
        return artifacts.get();
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName ivyArtifactName) {
        for (ComponentArtifactMetadata candidate : getArtifacts()) {
            if (candidate.getName().equals(ivyArtifactName)) {
                return candidate;
            }
        }

        return new MissingLocalArtifactMetadata(componentId, ivyArtifactName);
    }

}
