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

package org.gradle.internal.component.local.model


import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification

/**
 * Tests {@link DefaultLocalComponentMetadata}.
 *
 * TODO: This class currently tests a lot of the functionality of
 * {@link DefaultLocalConfigurationMetadataBuilder}. That class should either be merged
 * with {@link DefaultLocalComponentMetadata}, or the relevant tests should be moved
 * to the builder's test class.
 */
class DefaultLocalComponentMetadataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)

    def metadataBuilder = new DefaultLocalConfigurationMetadataBuilder(
        new TestDependencyMetadataFactory(),
        new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
    )

    def component = new DefaultLocalComponentMetadata(
        id, componentIdentifier, "status", Mock(AttributesSchemaInternal),
        RootScriptDomainObjectContext.INSTANCE, TestUtil.calculatedValueContainerFactory(), metadataBuilder
    )

    def "can lookup configuration after it has been added"() {
        when:
        def parent = register("parent")
        register("conf", [parent])

        then:
        component.configurationNames == ['conf', 'parent'] as Set

        def confMd = component.getConfiguration('conf')
        confMd != null
        confMd.hierarchy == ['conf', 'parent'] as Set

        def parentMd = component.getConfiguration('parent')
        parentMd != null
        parentMd.hierarchy == ['parent'] as Set
    }

    def "configuration has no dependencies or artifacts when none have been added"() {
        when:
        def parent = register("parent")
        register("conf", [parent])

        then:
        def confMd = component.getConfiguration('conf')
        confMd.dependencies.empty
        confMd.excludes.empty
        confMd.files.empty

        when:
        confMd.prepareToResolveArtifacts()

        then:
        confMd.artifacts.empty
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = register("conf")
        conf.artifacts.add(createArtifact(artifact, file))

        when:
        def confMd = component.getConfiguration("conf")
        confMd.prepareToResolveArtifacts()

        then:
        confMd.artifacts.size() == 1
        def publishArtifact = confMd.artifacts.first()

        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == confMd.artifact(artifact)
    }

    def "artifact is attached to child configurations"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def artifact3 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")
        def file3 = new File("artifact-3.zip")

        given:
        def conf1 = register("conf1")
        def conf2 = register("conf2")
        def child1 = register("child1", [conf1, conf2])
        register("child2", [conf1])

        conf1.artifacts.add(createArtifact(artifact1, file1))
        conf2.artifacts.add(createArtifact(artifact2, file2))
        child1.artifacts.add(createArtifact(artifact3, file3))

        when:
        def conf1Md = component.getConfiguration("conf1")
        def conf2Md = component.getConfiguration("child1")
        def child2Md = component.getConfiguration("child2")
        conf1Md.prepareToResolveArtifacts()
        conf2Md.prepareToResolveArtifacts()
        child2Md.prepareToResolveArtifacts()

        then:
        conf1Md.artifacts.size() == 1
        conf2Md.artifacts.size() == 3
        child2Md.artifacts.size() == 1
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def publishArtifact = createArtifact(artifact, file)
        def conf1Conf = register("conf1")
        def conf2Conf = register("conf2")

        conf1Conf.artifacts.add(publishArtifact)
        conf2Conf.artifacts.add(publishArtifact)

        when:
        def conf1 = component.getConfiguration("conf1")
        def conf2 = component.getConfiguration("conf2")
        conf1.prepareToResolveArtifacts()
        conf2.prepareToResolveArtifacts()

        then:
        conf1.artifacts.size() == 1
        conf2.artifacts == component.getConfiguration("conf2").artifacts
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = register("conf")
        conf.artifacts.add(createArtifact(artifact, file))

        when:
        def confMd = component.getConfiguration("conf")
        confMd.prepareToResolveArtifacts()

        then:
        def ivyArtifact = artifactName()
        def resolveArtifact = confMd.artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()

        given:
        register("conf")

        when:
        def confMd = component.getConfiguration("conf")
        confMd.prepareToResolveArtifacts()

        then:
        def resolveArtifact = confMd.artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        def conf1 = register("conf1")
        def conf2 = register("conf2")

        conf1.artifacts.add(createArtifact(artifact1, file1))
        conf2.artifacts.add(createArtifact(artifact2, file2))

        when:
        def conf1Md = component.getConfiguration("conf1")
        def conf2Md = component.getConfiguration("conf2")
        conf1Md.prepareToResolveArtifacts()
        conf2Md.prepareToResolveArtifacts()

        then:
        def conf1Artifacts = conf1Md.artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = conf2Md.artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        component.getConfiguration("conf1").artifacts == [artifactMetadata1]
        component.getConfiguration("conf2").artifacts == [artifactMetadata2]
    }

    def "variants are attached to configuration but not its children"() {
        def variant1Attrs = Stub(ImmutableAttributes)
        def variant2Attrs = Stub(ImmutableAttributes)

        given:
        def conf1 = register("conf1")
        def conf2 = register("conf2", [conf1])
        ConfigurationVariant variant1 = conf1.outgoing.getVariants().create("variant1")
        ConfigurationVariant variant2 = conf2.outgoing.getVariants().create("variant2")
        variant1.attributes >> variant1Attrs
        variant2.attributes >> variant2Attrs
        variant1.artifacts.add(Stub(PublishArtifact))
        variant2.artifacts.add(Stub(PublishArtifact))
        variant2.artifacts.add(Stub(PublishArtifact))

        when:
        def config1 = component.getConfiguration("conf1")
        def config2 = component.getConfiguration("conf2")

        then:
        config1.variants*.name as List == ["conf1", "conf1-variant1"]
        config1.variants.find { it.name == "conf1-variant1" }.attributes == variant1Attrs

        config2.variants*.name as List == ["conf2", "conf2-variant2"]
        config2.variants.find { it.name == "conf2-variant2" }.attributes == variant2Attrs

        when:
        config1.prepareToResolveArtifacts()
        config2.prepareToResolveArtifacts()

        then:
        config1.variants.find { it.name == "conf1-variant1" }.artifacts.size() == 1
        config2.variants.find { it.name == "conf2-variant2" }.artifacts.size() == 2
    }

    def "files attached to configuration and its children"() {
        def files1 = Stub(FileCollectionDependency)
        def files2 = Stub(FileCollectionDependency)
        def files3 = Stub(FileCollectionDependency)

        given:
        def conf1 = register("conf1")
        def conf2 = register("conf2")
        def child1 = register("child1", [conf1, conf2])
        register("child2", [conf1])

        when:
        conf1.getDependencies().add(files1)
        conf2.getDependencies().add(files2)
        child1.getDependencies().add(files3)

        then:
        component.getConfiguration("conf1").files*.source == [files1]
        component.getConfiguration("conf2").files*.source == [files2]
        component.getConfiguration("child1").files*.source == [files1, files2, files3]
        component.getConfiguration("child2").files*.source == [files1]
    }

    def "dependency is attached to configuration and its children"() {
        def dependency1 = Mock(ExternalModuleDependency)
        def dependency2 = Mock(ExternalModuleDependency)
        def dependency3 = Mock(ExternalModuleDependency)

        when:
        def conf1 = register("conf1")
        def conf2 = register("conf2")
        def child1 = register("child1", [conf1, conf2])
        register("child2", [conf1])
        register("other")

        conf1.getDependencies().add(dependency1)
        conf2.getDependencies().add(dependency2)
        child1.getDependencies().add(dependency3)

        then:
        component.getConfiguration("conf1").dependencies*.source == [dependency1]
        component.getConfiguration("conf2").dependencies*.source == [dependency2]
        component.getConfiguration("child1").dependencies*.source == [dependency1, dependency2, dependency3]
        component.getConfiguration("child2").dependencies*.source == [dependency1]
        component.getConfiguration("other").dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        def compile = register("compile")
        def runtime = register("runtime", [compile])

        compile.getExcludeRules().add(new DefaultExcludeRule("group1", "module1"))
        runtime.getExcludeRules().add(new DefaultExcludeRule("group2", "module2"))

        expect:
        def compileMd = component.getConfiguration("compile")
        compileMd.excludes*.moduleId.group == ["group1"]
        compileMd.excludes*.moduleId.name == ["module1"]

        def runtimeMd = component.getConfiguration("runtime")
        runtimeMd.excludes*.moduleId.group == ["group1", "group2"]
        runtimeMd.excludes*.moduleId.name == ["module1", "module2"]
        runtimeMd.excludes.is(runtimeMd.excludes)
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }

    ConfigurationInternal register(String name, List<ConfigurationInternal> extendsFrom = []) {
        def conf = configuration(name, extendsFrom)
        component.registerConfiguration(conf)
        return conf
    }

    /**
     * Creates a minimal mocked {@link ConfigurationInternal} instance.
     *
     * TODO: We really should not need such a complex Configuration mock here. However, since some of
     * the tests here are testing functionality of {@link DefaultLocalConfigurationMetadataBuilder}, this
     * complex Configuration mock is required.
     *
     * TODO: And TBH if we're doing this much mocking, we really should be using real Configurations.
     */
    ConfigurationInternal configuration(String name, List<ConfigurationInternal> extendsFrom) {

        DependencySet dependencies = new DefaultDependencySet(Describables.of("dependencies"), Mock(ConfigurationInternal) {
            isCanBeDeclaredAgainst() >> true
        }, TestUtil.domainObjectCollectionFactory().newDomainObjectSet(Dependency))

        DependencyConstraintSet dependencyConstraints = Mock() {
            iterator() >> Collections.emptyIterator()
        }

        NamedDomainObjectContainer<ConfigurationVariant> variants = TestUtil.domainObjectCollectionFactory()
            .newNamedDomainObjectContainer(ConfigurationVariant.class, variantName -> Mock(ConfigurationVariant) {
                getName() >> variantName
                getArtifacts() >> artifactSet()
            })

        PublishArtifactSet artifacts = artifactSet()
        ConfigurationPublications outgoing = Mock(ConfigurationPublications) {
            getCapabilities() >> Collections.emptySet()
            getArtifacts() >> artifacts
            getVariants() >> variants
        }

        def conf = Mock(ConfigurationInternal) {
            getName() >> name
            getAttributes() >> ImmutableAttributes.EMPTY
            getDependencies() >> dependencies
            getDependencyConstraints() >> dependencyConstraints
            getExcludeRules() >> new LinkedHashSet<ExcludeRule>()
            collectVariants(_ as ConfigurationInternal.VariantVisitor) >> { ConfigurationInternal.VariantVisitor visitor ->
                visitor.visitArtifacts(artifacts)
                visitor.visitOwnVariant(Describables.of(name), ImmutableAttributes.EMPTY, Collections.emptySet(), artifacts)
                variants.each {
                    visitor.visitChildVariant(it.name, Describables.of(it.name), it.attributes as ImmutableAttributes, Collections.emptySet(), it.artifacts)
                }
            }
            getOutgoing() >> outgoing
            getExtendsFrom() >> extendsFrom
            getArtifacts() >> artifacts
        }
        conf.getHierarchy() >> [conf] + extendsFrom
        return conf
    }

    PublishArtifact createArtifact(IvyArtifactName name, File file, TaskDependency buildDeps = null) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)
        if (buildDeps != null) {
            publishArtifact.builtBy(buildDeps)
        }
        publishArtifact
    }

    PublishArtifactSet artifactSet() {
        new DefaultPublishArtifactSet(
            Describables.of("artifacts"),
            TestUtil.domainObjectCollectionFactory().newDomainObjectSet(PublishArtifact),
            TestFiles.fileCollectionFactory(),
            Mock(TaskDependencyFactory)
        )
    }

    LocalOriginDependencyMetadata dependencyMetadata(Dependency dependency) {
        return new DslOriginDependencyMetadataWrapper(Mock(LocalOriginDependencyMetadata), dependency)
    }

    class TestDependencyMetadataFactory implements DependencyDescriptorFactory {
        @Override
        LocalOriginDependencyMetadata createDependencyDescriptor(ComponentIdentifier componentId, String clientConfiguration, AttributeContainer attributes, ModuleDependency dependency) {
            return dependencyMetadata(dependency)
        }

        @Override
        LocalOriginDependencyMetadata createDependencyConstraintDescriptor(ComponentIdentifier componentId, String clientConfiguration, AttributeContainer attributes, DependencyConstraint dependencyConstraint) {
            throw new UnsupportedOperationException()
        }
    }
}
