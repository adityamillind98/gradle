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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata
import spock.lang.Specification

class DefaultLocalComponentMetadataBuilderTest extends Specification {
    def configurationMetadataBuilder = Mock(LocalConfigurationMetadataBuilder)
    def converter = new DefaultLocalComponentMetadataBuilder(configurationMetadataBuilder)

    def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "name"), "rev")

    def "adds artifacts from each configuration"() {
        def emptySet = new HashSet<String>()
        def metaData = Mock(BuildableLocalComponentMetadata)
        def sourceConfig1 = Stub(ConfigurationInternal)
        def sourceConfig2 = Stub(ConfigurationInternal)
        def targetConfig1 = Mock(BuildableLocalConfigurationMetadata)
        def targetConfig2 = Mock(BuildableLocalConfigurationMetadata)
        def artifacts1 = [Stub(PublishArtifact)] as Set
        def artifacts2 = [Stub(PublishArtifactSet)] as Set

        given:
        sourceConfig1.name >> "config1"
        sourceConfig1.collectVariants(_) >> { ConfigurationInternal.VariantVisitor visitor ->
            visitor.visitArtifacts(artifacts1)
            visitor.visitChildVariant("child1", null, null, null, null)
            visitor.visitChildVariant("child2", null, null, null, null)
        }
        sourceConfig2.name >> "config2"
        sourceConfig2.collectVariants(_) >> { ConfigurationInternal.VariantVisitor visitor ->
            visitor.visitArtifacts(artifacts2)
        }

        when:
        converter.addConfiguration(metaData, sourceConfig1)
        converter.addConfiguration(metaData, sourceConfig2)

        then:
        _ * metaData.id >> Stub(ComponentIdentifier)
        1 * metaData.addConfiguration("config1", '', emptySet, emptySet, false, false, _, false, _, false, ImmutableCapabilities.EMPTY) >> targetConfig1
        1 * metaData.addDependenciesAndExcludesForConfiguration(sourceConfig1, configurationMetadataBuilder)
        1 * metaData.addConfiguration("config2", '', emptySet, emptySet, false, false, _, false, _, false, ImmutableCapabilities.EMPTY) >> targetConfig2
        1 * metaData.addDependenciesAndExcludesForConfiguration(sourceConfig2, configurationMetadataBuilder)
        1 * targetConfig1.addArtifacts(artifacts1)
        1 * targetConfig1.addVariant("config1-child1", _, _, _, _, _)
        1 * targetConfig1.addVariant("config1-child2", _, _, _, _, _)
        1 * targetConfig2.addArtifacts(artifacts2)
        0 * metaData._
    }
}
