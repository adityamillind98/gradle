The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
 The list is rendered as is, so use commas after each contributor's name, and a period at the end.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

[Andrei Nevedomskii](https://github.com/monosoul),
[Björn Kautler](https://github.com/Vampire),
[Clara Guerrero](https://github.com/cguerreros),
[David Marin](https://github.com/dmarin),
[Denis Buzmakov](https://github.com/bacecek),
[Dmitry Pogrebnoy](https://github.com/DmitryPogrebnoy),
[Dzmitry Neviadomski](https://github.com/nevack),
[Eliezer Graber](https://github.com/eygraber),
[Fedor Ihnatkevich](https://github.com/Jeffset),
[Gabriel Rodriguez](https://github.com/gabrielrodriguez2746),
[Guruprasad Bagade](https://github.com/prasad-333),
[Herbert von Broeuschmeul](https://github.com/HvB),
[Matthew Haughton](https://github.com/3flex),
[Michael Torres](https://github.com/torresmi),
[Pankaj Kumar](https://github.com/p1729),
[Ricardo Jiang](https://github.com/RicardoJiang),
[Siddardha Bezawada](https://github.com/SidB3),
[Stephen Topley](https://github.com/stopley),
[Victor Maldonado](https://github.com/vmmaldonadoz),
[Vinay Potluri](https://github.com/vinaypotluri),
[Jeff Gaston](https://github.com/mathjeff),
[David Morris](https://github.com/codefish1),
[Cédric Champeau](https://github.com/melix).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->


### JVM

#### Introduced `projectInternalView()` dependency for test suites with access to project internals

The [JVM test suite](userguide/jvm_test_suite_plugin.html) `dependencies` block now
supports depending on the internal view of the current project at compile-time.
Previously it was only possible to depend on the current project's API. This allows
test suites to access project internals that are not declared on
the `api` or `compileOnlyApi` configurations. This functionality can be useful when
testing internal classes that use dependencies which are not exposed as part of a
project's API, like those declared on the `implementation` and `compileOnly` configurations.

For example, the following snippet uses the new `projectInternalView()` API to define a
test suite with access to project internals:

```kotlin
testing {
    suites {
        val unitLikeTestSuite by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(projectInternalView())
            }
        }
    }
}
```

For more information about warning modes, see [JVM test suite](userguide/jvm_test_suite_plugin.html).


###  Kotlin DSL

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the traditional Groovy DSL with an enhanced editing experience in supported IDEs, with superior content assist, refactoring, documentation, and more.

#### Updated the Kotlin DSL to Kotlin API Level 1.8

Previously, the Kotlin DSL used Kotlin API level 1.4.
Starting with Gradle 8.0, the Kotlin DSL uses Kotlin API level 1.8.
This change brings all the improvements made to the Kotlin language and standard library since Kotlin 1.4.0.

For information about breaking and nonbreaking changes in this upgrade, see the [upgrading guide](userguide/upgrading_version_7.html#kotlin_language_1_8).

##### Enhanced script compilation to use the Gradle JVM as Kotlin JVM Target

Previously, the compilation of `.gradle.kts` scripts always used Java 8 as the Kotlin JVM target.
Starting with Gradle 8.0, it now uses the version of the JVM running the build.

If your team is using e.g. Java 11 to run Gradle, this allows you to use Java 11 libraries and language features in your build scripts.

Note that this doesn't apply to precompiled script plugins, see below.

##### Precompiled script plugins now use the configured Java Toolchain

Previously, the compilation of [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins) used the JVM target as configured on `kotlinDslPluginOptions.jvmTarget`.
Starting with Gradle 8.0, it now uses the configured Java Toolchain, or Java 8 if none is configured.

See the [`kotlin-dsl` plugin manual](userguide/kotlin_dsl.adoc#sec:kotlin-dsl_plugin) for more information on how to configure the Java Toolchain for precompiled script plugins and the [migration guide](userguide/upgrading_version_7.html#kotlin_dsl_plugin_toolchains) for more information on changed behaviour.

##### Improved Script compilation performance 

This Gradle version introduces an interpreter for [declarative `plugins {}` blocks](userguide/plugins.html#sec:constrained_syntax) in `.gradle.kts` scripts.
It allows to avoid calling the Kotlin compiler for declarative `plugins {}` blocks and is enabled by default.

On a build with declarative `plugins {}` blocks, a Gradle invocation that needs to compile all scripts, the interpreter makes the overall build time around 20% faster.
As usual, compiled scripts are stored in the build cache and can be reused by other builds.

Here is what is supported in declarative `plugins {}` blocks:

```kotlin
plugins {
    id("java-library")                               // <1>
    id("com.acme.example") version "1.0 apply false" // <2>
    kotlin("jvm") version "1.7.21"                   // <3>
}
```
1. Plugin specification by plugin identifier string
2. Plugin specification with version and/or the plugin application flag
3. Kotlin plugin specification helper

Note that using version catalog aliases for plugins or plugin specification type-safe accessors is not supported by the `plugins {}` block interpreter.

Here are examples of unsupported constructs:

```kotlin
plugins {
    val v = "2"
    id("some") version v    // <1>

    `java-library`          // <2>
    alias(libs.plugins.jmh) // <3>
}
```
1. Non-declarative code, unsupported
2. Plugin specification type-safe accessor, unsupported
3. Version catalog plugin reference, unsupported

In the cases above, Gradle falls back to the Kotlin compiler, providing the same performance as previous Gradle releases.


### General Improvements

#### Enhanced warning modes `all` and `fail` are now more verbose

Warning modes that are supposed to print all warnings were printing only one for each specific warning message.

If there were two warnings with the same message, but originating from different steps of the build process (i.e. different stack traces), only one of them was printed. 

Now one gets printed for each combination of message and stack trace.

For more information about warning modes, see [Showing or hiding warnings](userguide/command_line_interface.html#sec:command_line_warnings).


#### Improved Dependency verification metadata 

The following nodes with dependency verification metadata file `verification-metadata.xml` now support a `reason` attribute:

- the `trust` xml node under `trusted-artifacts`
- the `md5`, `sha1`, `sha256` and `sha512` nodes under `component`

A reason is helpful to provide more details on why an artifact is trusted or why a selected checksum verification is required for an artifact directly in the `verification-metadata.xml`.

#### Improved Dependency verification CLI

You can now use the `export-keys` flag to export all already trusted keys:

```asciidoc
./gradlew --export-keys
```

There is no longer a need to write verification metadata when exporting trusted keys. 

For more information, see [Exporting keys](userguide/dependency_verification.html#sec:local-keyring).

### Dependency Management

### Configuration Cache

#### Consistent task execution for configuration cache hit and configuration cache miss builds

When the configuration cache is enabled and Gradle is able to locate a compatible configuration cache entry for the requested tasks, it loads the tasks to run from the 
cache entry and runs them a so called 'isolated' tasks. Isolated tasks are able to run in parallel (subject to dependency constraints).

When Gradle is unable to locate a configuration cache entry to use, it runs the 'configuration' phase to calculate the set of tasks to run and then stores these tasks to a new cache entry.
In previous versions, Gradle would then run these tasks directly. However, as these tasks are not isolated, they would not run in parallel.

In this release, Gradle now loads the set of tasks from the cache entry after storing them on a cache miss. These tasks are isolated and can run in parallel.

There are some additional advantages to this new behaviour:

- Any problems that happen during deserialization will be reported in the cache miss build, making it easier to spot such problems.
- Tasks have access to the same state in cache miss and cache hit builds.
- Gradle can release all heap used by the configuration state prior to task execution in the cache miss build. Previously it would retain this state because the non-isolated tasks were able to access it. This reduces the peak heap usage for a given set of tasks. 

This consistent behavior for cache miss and cache hit builds should help people who are migrating to use the configuration cache, as many more problems can now be discovered on the first (cache miss) build.

#### Improved compatibility with core plugins 

The [`gradle init` command](userguide/build_init_plugin.html) can be used with the configuration cache enabled.

The [ANTLR plugin](userguide/antlr_plugin.html) and [Groovy DSL precompiled scripts](userguide/custom_plugins.html#sec:precompiled_plugins) are now compatible with the configuration cache.

The current status of the configuration cache support for all core Gradle plugins can be found in the [configuration cache documentation](userguide/configuration_cache.html#config_cache:plugins).

### Plugin Development

#### Enhanced CodeNarc Plugin to automatically detects appropriate version for current Groovy runtime

The [CodeNarc](https://codenarc.org/) project now publishes separate versions for use with Groovy 4.
Gradle still currently ships with Groovy 3.

To ensure future compatibility, the [CodeNarcPlugin](userguide/codenarc_plugin.html) now automatically detects the appropriate version of CodeNarc for the current Groovy runtime.

You can still explicitly specify a CodeNarc version with the `toolVersion` property on the [CodeNarcExtension](dsl/org.gradle.api.plugins.quality.CodeNarcExtension.html#org.gradle.api.plugins.quality.CodeNarcExtension).

#### Enhanced PMD and CodeNarc tasks to execute in parallel by default

The [PMD](userguide/pmd_plugin.html) and [CodeNarc](userguide/codenarc_plugin.html) plugins now use the Gradle worker API and JVM toolchains. These tools now perform analysis via an external worker process and therefore their tasks may now run in parallel within one project.

In Java projects, these tools will use the same version of Java required by the project. In other types of projects, they will use the same version of Java that is used by the Gradle daemon.

### IDE Integration

#### Run `buildSrc` tasks
#### Improvements for `buildSrc`
#### Improvements for `buildSrc` builds
This release includes several improvements for [`buildSrc`](userguide/organizing_gradle_projects#sec:build_sources) builds to make them behave similarly to an [included build](userguide/composite_builds#composite_build_intro).

##### Run `buildSrc` tasks directly
It is now possible to run the tasks of `buildSrc` from the command-line, using the same syntax used for the tasks of included builds.
For example, you can use `gradle buildSrc:build` to run the `build` task in the `buildSrc` build.

TODO - link to running included build tasks

#### `buildSrc` can include other builds
The `buildSrc` build can now include other builds by declaring them in `buildSrc/settings.gradle.kts` or `buildSrc/settings.gradle`.
You can use `pluginsManagement { includeBuild(someDir) }` or `includeBuild(someDir)` in this settings script to make other builds available for `buildSrc`

TODO - link to declaring included builds

#### Tests for `buildSrc` are no longer automatically run
When Gradle builds the output of `buildSrc` it only runs the tasks that produce that output. It no longer runs the `build` task.
In particular, this means that the tests of `buildSrc` and its subprojects are not built and executed when they are not needed.

TODO - you can run these tasks from the command-line or edit buildSrc to restore the old behaviour; link to upgrade guide 

#### Init scripts are applied to `buildSrc`
Init scripts specified on the command-line using `--init-script` are now applied to `buildSrc`, in addition to the main build and all included builds.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

#### Improved Gradle User Home Cache Cleanup
Previously, cleanup of the caches in Gradle User Home used fixed retention periods (30 days or 7 days depending on the cache).  
These retention periods can now be configured via the [Settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle User Home.

```groovy
beforeSettings { settings ->
    settings.caches {
        downloadedResources.removeUnusedEntriesAfterDays = 45
    }
}
```

Furthermore, it was previously only possible to partially disable cache cleanup via the `org.gradle.cache.cleanup` Gradle property in Gradle User Home.  
Disabling cache cleanup now affects more caches under Gradle User Home and can also be configured via the [Settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle User Home.

```groovy
beforeSettings { settings ->
    settings.caches {
        cleanup = Cleanup.DISABLED
    }
}
```

See [Configuring cleanup of caches and distributions](userguide/directory_layout.html#dir:gradle_user_home:configure_cache_cleanup) for more information.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Promoted features in the Tooling API

- The `GradleConnector.disconnect()` method is now considered stable.

### Promoted features in the antlr plugin

- The `AntlrSourceDirectorySet` interface is now considered stable.

### Promoted features in the ear plugin

- The `Ear.getAppDirectory()` method is now considered stable.

### Promoted features in the eclipse plugin

- The `EclipseClasspath.getContainsTestFixtures()` method is now considered stable.

### Promoted features in the groovy plugin

The following type and method are now considered stable:
- `GroovySourceDirectorySet`
- `GroovyCompileOptions.getDisabledGlobalASTTransformations()`

### Promoted features in the scala plugin

- The `ScalaSourceDirectorySet` interface is now considered stable.

### Promoted features in the war plugin

- The `War.getWebAppDirectory()` method is now considered stable.

### Promoted features in the `Settings` API

- The methods `Settings.dependencyResolutionManagement(Action)`  and `Settings.getDependencyResolutionManagement()` are now considered stable.
  - All the methods in `DependencyResolutionManagement` are now stable, except the ones for central repository declaration.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
