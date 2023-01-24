/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import kotlinx.metadata.Flag
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmMethodSignature
import org.gradle.api.Project
import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionsSchema.ExtensionSchema
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.execution.UnitOfWork.OutputFileValueSupplier
import org.gradle.internal.file.TreeType.DIRECTORY
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.cache.KotlinDslWorkspaceProvider
import org.gradle.kotlin.dsl.codegen.fileHeader
import org.gradle.kotlin.dsl.codegen.fileHeaderFor
import org.gradle.kotlin.dsl.codegen.kotlinDslPackagePath
import org.gradle.kotlin.dsl.codegen.pluginEntriesFrom
import org.gradle.kotlin.dsl.codegen.sourceNameOfBinaryName
import org.gradle.kotlin.dsl.concurrent.IO
import org.gradle.kotlin.dsl.concurrent.withAsynchronousIO
import org.gradle.kotlin.dsl.concurrent.withSynchronousIO
import org.gradle.kotlin.dsl.concurrent.writeFile
import org.gradle.kotlin.dsl.provider.kotlinScriptClassPathProviderOf
import org.gradle.kotlin.dsl.support.PluginDependenciesSpecScopeInternal
import org.gradle.kotlin.dsl.support.ScriptHandlerScopeInternal
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.DUP
import org.gradle.kotlin.dsl.support.bytecode.GETFIELD
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESPECIAL
import org.gradle.kotlin.dsl.support.bytecode.INVOKEVIRTUAL
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.InternalNameOf
import org.gradle.kotlin.dsl.support.bytecode.KmTypeBuilder
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.NEW
import org.gradle.kotlin.dsl.support.bytecode.PUTFIELD
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicClass
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicMethod
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import java.io.BufferedWriter
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KClass


/**
 * Produces an [AccessorsClassPath] with type-safe accessors for `plugins {}` blocks
 * providing content-assist for quick navigation to the source code.
 *
 * Generates accessors for:
 * - dependency version catalogs found in this build,
 * - plugin spec builders for all plugin ids found in the `buildSrc` classpath.
 */
class PluginAccessorClassPathGenerator @Inject internal constructor(
    private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    private val fileCollectionFactory: FileCollectionFactory,
    private val executionEngine: ExecutionEngine,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider
) {
    fun pluginSpecBuildersClassPath(project: ProjectInternal): AccessorsClassPath = project.owner.owner.projects.rootProject.mutableModel.let { rootProject ->

        rootProject.getOrCreateProperty("gradleKotlinDsl.stage1AccessorsClassPath") {
            val buildSrcClassLoaderScope = baseClassLoaderScopeOf(rootProject)
            val classLoaderHash = requireNotNull(classLoaderHierarchyHasher.getClassLoaderHash(buildSrcClassLoaderScope.exportClassLoader))
            val versionCatalogPluginAccessors = generateVersionCatalogPluginAccessors(rootProject, buildSrcClassLoaderScope, classLoaderHash)
            val pluginSpecBuildersAccessors = generatePluginSpecBuildersAccessors(rootProject, buildSrcClassLoaderScope, classLoaderHash)
            versionCatalogPluginAccessors + pluginSpecBuildersAccessors
        }
    }

    private
    fun generatePluginSpecBuildersAccessors(
        rootProject: Project,
        buildSrcClassLoaderScope: ClassLoaderScope,
        classLoaderHash: HashCode,
    ): AccessorsClassPath {
        val work = GeneratePluginSpecsAccessors(
            rootProject,
            buildSrcClassLoaderScope,
            classLoaderHash,
            fileCollectionFactory,
            inputFingerprinter,
            workspaceProvider
        )
        val result = executionEngine.createRequest(work).execute()
        return result.execution.get().output as AccessorsClassPath
    }

    private
    fun generateVersionCatalogPluginAccessors(
        rootProject: Project,
        buildSrcClassLoaderScope: ClassLoaderScope,
        classLoaderHash: HashCode,
    ): AccessorsClassPath =
        rootProject.extensions.extensionsSchema
            .filter { catalogExtensionBaseType.isAssignableFrom(it.publicType) }
            .takeIf { it.isNotEmpty() }
            ?.let { versionCatalogExtensionSchemas ->

                val work = GenerateVersionCatalogPluginAccessors(
                    versionCatalogExtensionSchemas,
                    rootProject,
                    buildSrcClassLoaderScope,
                    classLoaderHash,
                    fileCollectionFactory,
                    inputFingerprinter,
                    workspaceProvider
                )
                val result = executionEngine.createRequest(work).execute()
                result.execution.get().output as AccessorsClassPath
            }
            ?: AccessorsClassPath.empty

    private
    val catalogExtensionBaseType = typeOf<ExternalModuleDependencyFactory>()
}


internal
abstract class AbstractPluginAccessorsUnitOfWork(
    protected val rootProject: Project,
    protected val buildSrcClassLoaderScope: ClassLoaderScope,
    protected val classLoaderHash: HashCode,
    private val fileCollectionFactory: FileCollectionFactory,
    private val inputFingerprinter: InputFingerprinter,
    private val workspaceProvider: KotlinDslWorkspaceProvider,
) : UnitOfWork {

    companion object {
        const val BUILD_SRC_CLASSLOADER_INPUT_PROPERTY = "buildSrcClassLoader"
        const val SOURCES_OUTPUT_PROPERTY = "sources"
        const val CLASSES_OUTPUT_PROPERTY = "classes"
    }

    override fun identify(identityInputs: MutableMap<String, ValueSnapshot>, identityFileInputs: MutableMap<String, CurrentFileCollectionFingerprint>) =
        UnitOfWork.Identity { "$classLoaderHash-$identitySuffix" }

    protected
    abstract val identitySuffix: String

    override fun loadAlreadyProducedOutput(workspace: File) = AccessorsClassPath(
        DefaultClassPath.of(getClassesOutputDir(workspace)),
        DefaultClassPath.of(getSourcesOutputDir(workspace))
    )

    override fun getWorkspaceProvider() = workspaceProvider.accessors

    override fun getInputFingerprinter() = inputFingerprinter

    override fun visitIdentityInputs(visitor: InputVisitor) {
        visitor.visitInputProperty(BUILD_SRC_CLASSLOADER_INPUT_PROPERTY) { classLoaderHash }
    }

    override fun visitOutputs(workspace: File, visitor: UnitOfWork.OutputVisitor) {
        val sourcesOutputDir = getSourcesOutputDir(workspace)
        val classesOutputDir = getClassesOutputDir(workspace)
        visitor.visitOutputProperty(SOURCES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(sourcesOutputDir, fileCollectionFactory.fixed(sourcesOutputDir)))
        visitor.visitOutputProperty(CLASSES_OUTPUT_PROPERTY, DIRECTORY, OutputFileValueSupplier.fromStatic(classesOutputDir, fileCollectionFactory.fixed(classesOutputDir)))
    }
}


internal
class GenerateVersionCatalogPluginAccessors(
    private val versionCatalogExtensionSchemas: List<ExtensionSchema>,
    rootProject: Project,
    buildSrcClassLoaderScope: ClassLoaderScope,
    classLoaderHash: HashCode,
    fileCollectionFactory: FileCollectionFactory,
    inputFingerprinter: InputFingerprinter,
    workspaceProvider: KotlinDslWorkspaceProvider,
) : AbstractPluginAccessorsUnitOfWork(
    rootProject, buildSrcClassLoaderScope, classLoaderHash, fileCollectionFactory, inputFingerprinter, workspaceProvider
) {

    override fun getDisplayName(): String = "Kotlin DSL version catalog plugin accessors for classpath '$classLoaderHash'"

    override val identitySuffix: String = "VC"

    override fun execute(executionRequest: UnitOfWork.ExecutionRequest): UnitOfWork.WorkOutput {
        val workspace = executionRequest.workspace
        kotlinScriptClassPathProviderOf(rootProject).run {
            withAsynchronousIO(rootProject) {
                buildVersionCatalogPluginAccessorsFor(
                    versionCatalogs = versionCatalogAccessorFrom(versionCatalogExtensionSchemas),
                    srcDir = getSourcesOutputDir(workspace),
                    binDir = getClassesOutputDir(workspace),
                )
            }
        }
        return object : UnitOfWork.WorkOutput {
            override fun getDidWork() = UnitOfWork.WorkResult.DID_WORK

            override fun getOutput() = loadAlreadyProducedOutput(workspace)
        }
    }
}


private
fun versionCatalogAccessorFrom(versionCatalogExtensionSchemas: List<ExtensionSchema>): List<VersionCatalogAccessor> =
    versionCatalogExtensionSchemas.map {
        VersionCatalogAccessor(
            it.name,
            it.publicType,
            ExtensionSpec(it.name, scriptHandlerScopeTypeSpec, TypeSpec(it.publicType.simpleName, it.publicType.concreteClass.internalName)),
            ExtensionSpec(it.name, pluginDependenciesSpecScopeTypeSpec, TypeSpec(it.publicType.simpleName, it.publicType.concreteClass.internalName)),
        )
    }


internal
data class VersionCatalogAccessor(
    val name: String,
    val publicType: TypeOf<*>,
    val buildscriptExtension: ExtensionSpec,
    val pluginsExtension: ExtensionSpec,
)


internal
class GeneratePluginSpecsAccessors(
    rootProject: Project,
    buildSrcClassLoaderScope: ClassLoaderScope,
    classLoaderHash: HashCode,
    fileCollectionFactory: FileCollectionFactory,
    inputFingerprinter: InputFingerprinter,
    workspaceProvider: KotlinDslWorkspaceProvider,
) : AbstractPluginAccessorsUnitOfWork(
    rootProject, buildSrcClassLoaderScope, classLoaderHash, fileCollectionFactory, inputFingerprinter, workspaceProvider
) {

    override fun getDisplayName(): String = "Kotlin DSL plugin specs accessors for classpath '$classLoaderHash'"

    override val identitySuffix: String = "PS"

    override fun execute(executionRequest: UnitOfWork.ExecutionRequest): UnitOfWork.WorkOutput {
        val workspace = executionRequest.workspace
        kotlinScriptClassPathProviderOf(rootProject).run {
            withAsynchronousIO(rootProject) {
                buildPluginAccessorsFor(
                    pluginDescriptorsClassPath = exportClassPathFromHierarchyOf(buildSrcClassLoaderScope),
                    srcDir = getSourcesOutputDir(workspace),
                    binDir = getClassesOutputDir(workspace),
                )
            }
        }
        return object : UnitOfWork.WorkOutput {
            override fun getDidWork() = UnitOfWork.WorkResult.DID_WORK

            override fun getOutput() = loadAlreadyProducedOutput(workspace)
        }
    }
}


private
fun getClassesOutputDir(workspace: File) = File(workspace, "classes")


private
fun getSourcesOutputDir(workspace: File): File = File(workspace, "sources")


internal
fun IO.buildVersionCatalogPluginAccessorsFor(
    versionCatalogs: List<VersionCatalogAccessor>,
    srcDir: File,
    binDir: File
) {
    makeAccessorOutputDirs(srcDir, binDir, kotlinDslPackagePath)

    val baseFileName = "$kotlinDslPackagePath/VersionCatalogPluginAccessors"
    val sourceFile = srcDir.resolve("$baseFileName.kt")

    writeVersionCatalogPluginAccessorsSourceCodeTo(sourceFile, versionCatalogs)

    val fileFacadeClassName = InternalName(baseFileName + "Kt")
    val moduleName = "kotlin-dsl-version-catalog-accessors"
    val moduleMetadata = moduleMetadataBytesFor(listOf(fileFacadeClassName))
    writeFile(
        moduleFileFor(binDir, moduleName),
        moduleMetadata
    )

    val buildscriptProperties = ArrayList<Pair<VersionCatalogAccessor, JvmMethodSignature>>(versionCatalogs.size)
    val pluginsProperties = ArrayList<Pair<VersionCatalogAccessor, JvmMethodSignature>>(versionCatalogs.size)
    val header = writeFileFacadeClassHeader(moduleName) {
        versionCatalogs.forEach { catalog ->

            val buildscriptExtension = catalog.buildscriptExtension
            val buildscriptGetterSignature = jvmGetterSignatureFor(
                propertyName = buildscriptExtension.name,
                desc = "(L${buildscriptExtension.receiverType.internalName};)L${buildscriptExtension.returnType.internalName};"
            )
            writePropertyOf(
                receiverType = buildscriptExtension.receiverType.builder,
                returnType = buildscriptExtension.returnType.builder,
                propertyName = buildscriptExtension.name,
                getterSignature = buildscriptGetterSignature,
                getterFlags = nonInlineGetterFlags
            )
            buildscriptProperties.add(catalog to buildscriptGetterSignature)

            val pluginsExtension = catalog.pluginsExtension
            val pluginsGetterSignature = jvmGetterSignatureFor(
                propertyName = pluginsExtension.name,
                desc = "(L${pluginsExtension.receiverType.internalName};)L${pluginsExtension.returnType.internalName};"
            )
            writePropertyOf(
                receiverType = pluginsExtension.receiverType.builder,
                returnType = pluginsExtension.returnType.builder,
                propertyName = pluginsExtension.name,
                getterSignature = pluginsGetterSignature,
                getterFlags = nonInlineGetterFlags
            )
            pluginsProperties.add(catalog to pluginsGetterSignature)
        }
    }

    val classBytes = publicKotlinClass(fileFacadeClassName, header) {
        buildscriptProperties.forEach { (versionCatalogAccessor, signature) ->
            emitVersionCatalogAccessorMethodFor(
                versionCatalogAccessor.buildscriptExtension,
                signature,
                scriptHandlerScopeInternalInternalName,
                scriptHandlerScopeInternalVersionCatalogExtensionMethodDesc,
            )
        }
        pluginsProperties.forEach { (versionCatalogAccessor, signature) ->
            emitVersionCatalogAccessorMethodFor(
                versionCatalogAccessor.pluginsExtension,
                signature,
                pluginDependenciesSpecScopeInternalInternalName,
                pluginDependenciesSpecScopeInternalVersionCatalogExtensionMethodDesc,
            )
        }
    }

    writeClassFileTo(binDir, fileFacadeClassName, classBytes)
}


private
fun IO.writeVersionCatalogPluginAccessorsSourceCodeTo(
    sourceFile: File,
    versionCatalogs: List<VersionCatalogAccessor>,
    format: AccessorFormat = AccessorFormats.default,
    header: String = fileHeader,
) = io {
    sourceFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(header)
        appendSourceCodeForVersionCatalogPluginAccessors(versionCatalogs, format)
    }
}


private
fun BufferedWriter.appendSourceCodeForVersionCatalogPluginAccessors(
    versionCatalogs: List<VersionCatalogAccessor>,
    format: AccessorFormat
) {
    appendReproducibleNewLine(
        """
        import ${ScriptHandlerScope::class.qualifiedName}
        import ${PluginDependenciesSpecScope::class.qualifiedName}
        import ${ScriptHandlerScopeInternal::class.qualifiedName}
        import ${PluginDependenciesSpecScopeInternal::class.qualifiedName}
        """.trimIndent()
    )

    versionCatalogs.map { it.publicType }.forEach {
        appendReproducibleNewLine("import ${it.fullyQualifiedName}")
    }

    fun appendCatalogExtension(extSpec: ExtensionSpec, receiverInternalType: KClass<*>) {
        write("\n\n")
        appendReproducibleNewLine(
            format("""
                /**
                 * The `${extSpec.name}` version catalog.
                 */
                val ${extSpec.receiverType.sourceName}.`${extSpec.name}`: ${extSpec.returnType.sourceName}
                    get() = (this as ${receiverInternalType.simpleName}).versionCatalogExtension("${extSpec.name}") as ${extSpec.returnType.sourceName}
            """)
        )
    }

    versionCatalogs.forEach { catalog ->
        appendCatalogExtension(catalog.buildscriptExtension, ScriptHandlerScopeInternal::class)
        appendCatalogExtension(catalog.pluginsExtension, PluginDependenciesSpecScopeInternal::class)
    }
}


private
fun ClassWriter.emitVersionCatalogAccessorMethodFor(
    extensionSpec: ExtensionSpec,
    signature: JvmMethodSignature,
    receiverInternalTypeInternalName: InternalName,
    receiverVersionCatalogExtensionMethodDesc: String,
) {
    publicStaticMethod(signature) {
        ALOAD(0)
        CHECKCAST(receiverInternalTypeInternalName)
        LDC(extensionSpec.name)
        INVOKEVIRTUAL(receiverInternalTypeInternalName, "versionCatalogExtension", receiverVersionCatalogExtensionMethodDesc)
        CHECKCAST(extensionSpec.returnType.internalName)
        ARETURN()
    }
}


fun writeSourceCodeForPluginSpecBuildersFor(
    pluginDescriptorsClassPath: ClassPath,
    sourceFile: File,
    packageName: String
) {
    withSynchronousIO {
        writePluginAccessorsSourceCodeTo(
            sourceFile,
            pluginAccessorsFor(pluginDescriptorsClassPath),
            format = AccessorFormats.internal,
            header = fileHeaderFor(packageName)
        )
    }
}


private
fun pluginAccessorsFor(pluginDescriptorsClassPath: ClassPath): List<PluginAccessor> =
    pluginAccessorsFor(pluginTreesFrom(pluginDescriptorsClassPath)).toList()


internal
fun IO.buildPluginAccessorsFor(
    pluginDescriptorsClassPath: ClassPath,
    srcDir: File,
    binDir: File
) {
    makeAccessorOutputDirs(srcDir, binDir, kotlinDslPackagePath)

    val pluginTrees = pluginTreesFrom(pluginDescriptorsClassPath)

    val baseFileName = "$kotlinDslPackagePath/PluginAccessors"
    val sourceFile = srcDir.resolve("$baseFileName.kt")

    val accessorList = pluginAccessorsFor(pluginTrees).toList()
    writePluginAccessorsSourceCodeTo(sourceFile, accessorList)

    val fileFacadeClassName = InternalName(baseFileName + "Kt")
    val moduleName = "kotlin-dsl-plugin-spec-accessors"
    val moduleMetadata = moduleMetadataBytesFor(listOf(fileFacadeClassName))
    writeFile(
        moduleFileFor(binDir, moduleName),
        moduleMetadata
    )

    val properties = ArrayList<Pair<PluginAccessor, JvmMethodSignature>>(accessorList.size)
    val header = writeFileFacadeClassHeader(moduleName) {
        accessorList.forEach { accessor ->

            if (accessor is PluginAccessor.ForGroup) {
                val (internalClassName, classBytes) = emitClassForGroup(accessor)
                writeClassFileTo(binDir, internalClassName, classBytes)
            }

            val extensionSpec = accessor.extension
            val propertyName = extensionSpec.name
            val receiverType = extensionSpec.receiverType
            val returnType = extensionSpec.returnType
            val getterSignature = jvmGetterSignatureFor(propertyName, "(L${receiverType.internalName};)L${returnType.internalName};")
            writePropertyOf(
                receiverType = receiverType.builder,
                returnType = returnType.builder,
                propertyName = propertyName,
                getterSignature = getterSignature,
                getterFlags = nonInlineGetterFlags
            )
            properties.add(accessor to getterSignature)
        }
    }

    val classBytes = publicKotlinClass(fileFacadeClassName, header) {
        properties.forEach { (accessor, signature) ->
            emitAccessorMethodFor(accessor, signature)
        }
    }

    writeClassFileTo(binDir, fileFacadeClassName, classBytes)
}


internal
fun pluginTreesFrom(pluginDescriptorsClassPath: ClassPath): Map<String, PluginTree> =
    PluginTree.of(pluginSpecsFrom(pluginDescriptorsClassPath))


private
fun ClassWriter.emitAccessorMethodFor(accessor: PluginAccessor, signature: JvmMethodSignature) {
    val extension = accessor.extension
    val receiverType = extension.receiverType
    publicStaticMethod(signature) {
        when (accessor) {
            is PluginAccessor.ForGroup -> {
                val returnType = extension.returnType
                NEW(returnType.internalName)
                DUP()
                GETPLUGINS(receiverType)
                INVOKESPECIAL(returnType.internalName, "<init>", groupTypeConstructorSignature)
                ARETURN()
            }

            is PluginAccessor.ForPlugin -> {
                GETPLUGINS(receiverType)
                LDC(accessor.id)
                INVOKEINTERFACE(pluginDependenciesSpecInternalName, "id", pluginDependenciesSpecIdMethodDesc)
                ARETURN()
            }
        }
    }
}


private
fun IO.writePluginAccessorsSourceCodeTo(
    sourceFile: File,
    accessors: List<PluginAccessor>,
    format: AccessorFormat = AccessorFormats.default,
    header: String = fileHeader
) = io {
    sourceFile.bufferedWriter().useToRun {
        appendReproducibleNewLine(header)
        appendSourceCodeForPluginAccessors(accessors, format)
    }
}


private
fun BufferedWriter.appendSourceCodeForPluginAccessors(
    accessors: List<PluginAccessor>,
    format: AccessorFormat
) {

    appendReproducibleNewLine(
        """
        import ${PluginDependenciesSpec::class.qualifiedName}
        import ${PluginDependencySpec::class.qualifiedName}
        """.trimIndent()
    )

    defaultPackageTypesIn(accessors).forEach {
        appendReproducibleNewLine("import $it")
    }

    accessors.runEach {

        // Keep accessors separated by an empty line
        write("\n\n")

        val extendedType = extension.receiverType.sourceName
        val pluginsRef = pluginDependenciesSpecOf(extendedType)
        when (this) {
            is PluginAccessor.ForPlugin -> {
                appendReproducibleNewLine(
                    format(
                        """
                        /**
                         * The `$id` plugin implemented by [${sourceNameOfBinaryName(implementationClass)}].
                         */
                        val `$extendedType`.`${extension.name}`: PluginDependencySpec
                            get() = $pluginsRef.id("$id")
                        """
                    )
                )
            }

            is PluginAccessor.ForGroup -> {
                val groupType = extension.returnType.sourceName
                appendReproducibleNewLine(
                    format(
                        """
                        /**
                         * The `$id` plugin group.
                         */
                        @org.gradle.api.Generated
                        class `$groupType`(internal val plugins: PluginDependenciesSpec)


                        /**
                         * Plugin ids starting with `$id`.
                         */
                        val `$extendedType`.`${extension.name}`: `$groupType`
                            get() = `$groupType`($pluginsRef)
                        """
                    )
                )
            }
        }
    }
}


private
fun defaultPackageTypesIn(pluginAccessors: List<PluginAccessor>) =
    defaultPackageTypesIn(
        pluginImplementationClassesExposedBy(pluginAccessors)
    )


private
fun pluginImplementationClassesExposedBy(pluginAccessors: List<PluginAccessor>) =
    pluginAccessors
        .filterIsInstance<PluginAccessor.ForPlugin>()
        .map { it.implementationClass }


private
const val pluginsFieldName = "plugins"


private
fun pluginDependenciesSpecOf(extendedType: String): String = when (extendedType) {
    "PluginDependenciesSpec" -> "this"
    else -> pluginsFieldName
}


private
inline fun <T> Iterable<T>.runEach(f: T.() -> Unit) {
    forEach { it.run(f) }
}


internal
data class TypeSpec(val sourceName: String, val internalName: InternalName) {

    val builder: KmTypeBuilder
        get() = { visitClass(internalName) }
}


internal
fun KmTypeVisitor.visitClass(internalName: InternalName) {
    visitClass(internalName.value)
}


private
val scriptHandlerScopeInternalName = ScriptHandlerScope::class.internalName


private
val scriptHandlerScopeTypeSpec = TypeSpec("ScriptHandlerScope", scriptHandlerScopeInternalName)


private
val scriptHandlerScopeInternalInternalName = ScriptHandlerScopeInternal::class.internalName


private
val scriptHandlerScopeInternalVersionCatalogExtensionMethodDesc = "(Ljava/lang/String;)L${ExternalModuleDependencyFactory::class.internalName};"


private
val pluginDependencySpecInternalName = PluginDependencySpec::class.internalName


private
val pluginDependenciesSpecInternalName = PluginDependenciesSpec::class.internalName


private
val pluginDependenciesSpecScopeInternalName = PluginDependenciesSpecScope::class.internalName


private
val pluginDependenciesSpecScopeInternalInternalName = PluginDependenciesSpecScopeInternal::class.internalName


internal
val pluginDependenciesSpecTypeSpec = TypeSpec("PluginDependenciesSpec", pluginDependenciesSpecInternalName)


private
val pluginDependenciesSpecScopeTypeSpec = TypeSpec("PluginDependenciesSpecScope", pluginDependenciesSpecScopeInternalName)


internal
val pluginDependencySpecTypeSpec = TypeSpec("PluginDependencySpec", pluginDependencySpecInternalName)


private
val pluginDependenciesSpecTypeDesc = "L$pluginDependenciesSpecInternalName;"


private
val groupTypeConstructorSignature = "($pluginDependenciesSpecTypeDesc)V"


private
val pluginDependenciesSpecIdMethodDesc = "(Ljava/lang/String;)L$pluginDependencySpecInternalName;"


private
val pluginDependenciesSpecScopeInternalVersionCatalogExtensionMethodDesc = "(Ljava/lang/String;)L${ExternalModuleDependencyFactory::class.internalName};"


internal
fun pluginAccessorsFor(pluginTrees: Map<String, PluginTree>, extendedType: TypeSpec = pluginDependenciesSpecTypeSpec): Sequence<PluginAccessor> = sequence {

    for ((extensionName, pluginTree) in pluginTrees) {
        when (pluginTree) {
            is PluginTree.PluginGroup -> {
                val groupId = pluginTree.path.joinToString(".")
                val groupType = pluginGroupTypeName(pluginTree.path)
                val groupTypeSpec = typeSpecForPluginGroupType(groupType)
                yield(
                    PluginAccessor.ForGroup(
                        groupId,
                        ExtensionSpec(extensionName, extendedType, groupTypeSpec)
                    )
                )
                yieldAll(pluginAccessorsFor(pluginTree.plugins, groupTypeSpec))
            }

            is PluginTree.PluginSpec -> {
                yield(
                    PluginAccessor.ForPlugin(
                        pluginTree.id,
                        pluginTree.implementationClass,
                        ExtensionSpec(extensionName, extendedType, pluginDependencySpecTypeSpec)
                    )
                )
            }
        }
    }
}


internal
fun typeSpecForPluginGroupType(groupType: String) =
    TypeSpec(groupType, InternalName("$kotlinDslPackagePath/$groupType"))


internal
sealed class PluginAccessor {

    abstract val extension: ExtensionSpec

    data class ForPlugin(
        val id: String,
        val implementationClass: String,
        override val extension: ExtensionSpec
    ) : PluginAccessor()

    data class ForGroup(
        val id: String,
        override val extension: ExtensionSpec
    ) : PluginAccessor()
}


internal
data class ExtensionSpec(
    val name: String,
    val receiverType: TypeSpec,
    val returnType: TypeSpec
)


private
fun pluginSpecsFrom(pluginDescriptorsClassPath: ClassPath): Sequence<PluginTree.PluginSpec> =
    pluginDescriptorsClassPath
        .asFiles
        .asSequence()
        .filter { it.isFile && it.extension.equals("jar", true) }
        .flatMap { pluginEntriesFrom(it).asSequence() }
        .map { PluginTree.PluginSpec(it.pluginId, it.implementationClass) }


private
fun pluginGroupTypeName(path: List<String>) =
    path.joinToString(separator = "") { it.uppercaseFirstChar() } + "PluginGroup"


private
fun IO.writeClassFileTo(binDir: File, internalClassName: InternalName, classBytes: ByteArray) {
    val classFile = binDir.resolve("$internalClassName.class")
    writeFile(classFile, classBytes)
}


private
val nonInlineGetterFlags = flagsOf(Flag.IS_PUBLIC, Flag.PropertyAccessor.IS_NOT_DEFAULT)


private
fun MethodVisitor.GETPLUGINS(receiverType: TypeSpec) {
    ALOAD(0)
    if (receiverType !== pluginDependenciesSpecTypeSpec) {
        GETFIELD(receiverType.internalName, pluginsFieldName, pluginDependenciesSpecTypeDesc)
    }
}


private
fun emitClassForGroup(group: PluginAccessor.ForGroup): Pair<InternalName, ByteArray> = group.run {

    val className = extension.returnType.internalName
    val classBytes = publicClass(className) {
        packagePrivateField(pluginsFieldName, pluginDependenciesSpecTypeDesc)
        publicMethod("<init>", groupTypeConstructorSignature) {
            ALOAD(0)
            INVOKESPECIAL(InternalNameOf.javaLangObject, "<init>", "()V")
            ALOAD(0)
            ALOAD(1)
            PUTFIELD(className, pluginsFieldName, pluginDependenciesSpecTypeDesc)
            RETURN()
        }
    }

    className to classBytes
}


private
fun ClassWriter.packagePrivateField(name: String, desc: String) {
    visitField(0, name, desc, null, null).run {
        visitEnd()
    }
}


private
fun baseClassLoaderScopeOf(rootProject: Project) =
    (rootProject as ProjectInternal).baseClassLoaderScope
