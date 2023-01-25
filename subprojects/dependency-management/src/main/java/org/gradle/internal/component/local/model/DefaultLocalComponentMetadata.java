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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata {

    private final ModuleVersionIdentifier moduleVersionId;
    private final ComponentIdentifier componentId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private final ModelContainer<?> model;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ModuleSources moduleSources = ImmutableModuleSources.of();
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;

    private Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal;

    // TODO: Should these be concurrent maps, maybe a ConcurrentSkipListMap?
    // Implementations of LocalComponentMetadata should be thread-safe, but we
    // also want to maintain performance and memory usage.
    Map<String, Boolean> consumable = new LinkedHashMap<>();
    Map<String, Lazy<LocalConfigurationMetadata>> allConfigurations = new LinkedHashMap<>();

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier moduleVersionId, ComponentIdentifier componentId, String status, AttributesSchemaInternal attributesSchema, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory, LocalConfigurationMetadataBuilder configurationMetadataBuilder) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
        this.model = model;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.configurationMetadataBuilder = configurationMetadataBuilder;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return allConfigurations.keySet();
    }

    public void registerConfiguration(ConfigurationInternal configuration) {
        configuration.preventFromFurtherMutation();

        consumable.put(configuration.getName(), configuration.isCanBeConsumed());
        allConfigurations.put(configuration.getName(), Lazy.locking().of(() ->
            configurationMetadataBuilder.create(configuration, this, model, calculatedValueContainerFactory)
        ));
    }

    /**
     * Eagerly adds a configuration to this component metadata. This method should be avoided except for
     * use with the configuration cache.
     *
     * @return The newly added configuration metadata.
     */
    public LocalConfigurationMetadata addConfiguration(LocalConfigurationMetadata conf) {
        consumable.put(conf.getName(), conf.isCanBeConsumed());
        allConfigurations.put(conf.getName(), Lazy.locking().of(() -> conf));
        return conf;
    }

    @Override
    public LocalConfigurationMetadata getConfiguration(final String name) {
        Lazy<LocalConfigurationMetadata> value = allConfigurations.get(name);
        if (value == null) {
            return null;
        }
        return value.get();
    }

    /**
     * Creates a copy of this metadata, transforming the artifacts of this component.
     */
    @Override
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer) {
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema, model, calculatedValueContainerFactory, configurationMetadataBuilder);

        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();
        Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> cachedTransformer = oldArtifact ->
            transformedArtifacts.computeIfAbsent(oldArtifact, transformer::transform);

        // TODO: This is extremely costly. This breaks all laziness and realizes each configuration metadata.
        // Is such a strict copy really that necessary here?
        allConfigurations.forEach((key, value) -> {
            LocalConfigurationMetadata confCopy = value.get().copy(cachedTransformer);
            copy.consumable.put(key, confCopy.isCanBeConsumed());
            copy.allConfigurations.put(key, Lazy.locking().of(() -> confCopy));
        });

        return copy;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public ModuleSources getSources() {
        return moduleSources;
    }

    @Override
    public ComponentResolveMetadata withSources(ModuleSources source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMissing() {
        return false;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public List<String> getStatusScheme() {
        return DEFAULT_STATUS_SCHEME;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    /**
     * For a local project component, the `variantsForGraphTraversal` are any _consumable_ configurations that have attributes defined.
     */
    @Override
    public synchronized Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal() {
        if (variantsForGraphTraversal == null) {
            ImmutableList.Builder<VariantGraphResolveMetadata> builder = new ImmutableList.Builder<>();
            for (Map.Entry<String, Lazy<LocalConfigurationMetadata>> entry : allConfigurations.entrySet()) {
                if (consumable.get(entry.getKey())) {
                    LocalConfigurationMetadata actual = entry.getValue().get();
                    if (!actual.getAttributes().isEmpty()) {
                        builder.add(actual);
                    }
                }
            }

            ImmutableList<VariantGraphResolveMetadata> variants = builder.build();
            variantsForGraphTraversal = !variants.isEmpty() ? Optional.of(variants) : Optional.absent();
        }
        return variantsForGraphTraversal;
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        // a local component cannot have attributes (for now). However, variants of the component
        // itself may.
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public void reevaluate(ConfigurationsProvider configurations) {
        // Un-realize all configurations.
        for (String name : ImmutableList.copyOf(allConfigurations.keySet())) {
            registerConfiguration(configurations.findByName(name));
        }
    }

    @Override
    public boolean isConfigurationRealized(String configName) {
        return allConfigurations.get(configName).isRealized();
    }
}
