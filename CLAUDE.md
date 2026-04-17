# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Korro is a Gradle plugin (Kotlin/JVM) that injects code snippets from Kotlin sample/test source files into Markdown docs. It is published on the Gradle Plugin Portal as `io.github.devcrocod.korro`. User-facing directive syntax and consumer configuration are documented in `README.md`; the 0.2.0 upgrade contract is in `SPEC.md` and the 0.1.x→0.2.0 migration in `MIGRATION.md` — read those before changing the directive parser or the extension DSL.

The repository is a multi-module Gradle build:

- `korro-gradle-plugin/` — thin plugin (published to the Gradle Plugin Portal). Only Gradle API + Kotlin stdlib at compile time. No Analysis API imports.
- `korro-analysis/` — shadowed fat jar with the Kotlin Analysis API (K2 standalone mode), IntelliJ platform, and Korro's PSI-based snippet extraction. Published to Maven Central and pulled in at task-execution time through the `korroAnalysisRuntime` configuration.
- `integration-tests/` — GradleTestKit + golden-file tests under `integration-tests/fixtures/`.

## Commands

Build uses the Gradle wrapper (currently 9.4.1):

- `./gradlew build` — compile and assemble both modules.
- `./gradlew :korro-analysis:shadowJar` — build only the shadowed analysis jar. In 0.2.0 the **plugin** module is thin; the **analysis** module is the fat one.
- `./gradlew publishToMavenLocal` — install both artifacts to `~/.m2/repository` for local testing in a consumer project (the plugin's `korroAnalysisRuntime` looks up `korro-analysis` at its own version, so both must be installed together).
- `./gradlew publishPlugins` — publish the plugin to the Gradle Plugin Portal (requires credentials).
- `./gradlew -Prelease build` — produce a release-versioned artifact. Without `-Prelease`, `detectVersion()` in `build.gradle.kts` appends `-dev` (or `-dev-<build.number>`) to the version in `gradle.properties`.
- `./gradlew :integration-tests:test` — run the GradleTestKit integration tests under `integration-tests/fixtures/*`.

The `korro` / `korroApply` / `korroCheck` tasks the plugin registers are only runnable from a *consumer* project that applies this plugin (or from one of the integration-test fixtures). They are not runnable from this repo's root.

## Version wiring

Korro's own version lives in `gradle.properties` as `version`. Both modules inherit it through `subprojects { version = rootProject.version }` in the root `build.gradle.kts`. At runtime the plugin reads this from a generated `META-INF/korro-gradle-plugin.properties` resource on the plugin classpath (see `KorroPlugin.readKorroPluginVersion`).

All other versions live in the Gradle version catalog at `gradle/libs.versions.toml`. The catalog is the single source of truth — don't hard-code versions in subproject build scripts, add them to the catalog and reference as `libs.*` / `libs.plugins.*`. Key entries:

- `kotlin` — the pinned Kotlin / Analysis API version used by `korro-analysis` and for the `kotlin("jvm")` plugin.
- `kotlinLanguage` — sets both Kotlin `languageVersion` and `apiVersion` in the subproject Kotlin compilation. Read in the root `build.gradle.kts` via `libs.versions.kotlinLanguage.get()`. Unrelated to the pinned Analysis API version. JVM target is hard-coded to `17` in the root `build.gradle.kts`.
- `shadow`, `pluginPublish` — Gradle plugin versions consumed via `alias(libs.plugins.*)`.
- `kotlinxSerialization`, `caffeine`, `junit` — runtime/test library versions.

A new cache key: every task has an `@Input korroPluginVersion` property, so cached outputs are invalidated on plugin bump (which is also a bundled Analysis API bump).

## Architecture

Two layers separated by a worker boundary.

**Gradle-facing layer** — `korro-gradle-plugin/`, runs in the Gradle daemon's classloader, no Analysis API imports.

- `KorroPlugin` creates the `korro` extension, creates the detached `korroAnalysisRuntime` configuration (with a dependency on `io.github.devcrocod:korro-analysis:<pluginVersion>`), and in `afterEvaluate` registers three tasks: `korro`, `korroApply`, `korroCheck`. No `korroClean`.
- `KorroExtension` (`KorroExtension.kt`) exposes the nested DSL: `docs { from(...); baseDir.set(...) }`, `samples { from(...); outputs.from(...) }`, `behavior { rewriteAsserts.set(...); ignoreMissing.set(...) }`, `groupSamples { ... }`. All properties use Gradle's `Property<T>` / `ConfigurableFileCollection` / `DirectoryProperty` for config-cache safety.
- Tasks:
  - `KorroTask` (`@CacheableTask`, extends `AbstractKorroTask`) — `@InputFiles docs`/`samples`/`samplesOutputs`, `@Input` flags, `@Classpath korroRuntimeClasspath`, `@OutputDirectory outputDirectory` (defaults to `build/korro/docs`). On `@TaskAction`, submits a `KorroWorkAction` via `WorkerExecutor.classLoaderIsolation { classpath.from(korroRuntimeClasspath) }`.
  - `KorroApplyTask` (`@DisableCachingByDefault`, extends `Sync`) — wired to copy the `korro` task's output directory onto `docs.baseDir`. This is the only mutation point.
  - `KorroCheckTask` (`@CacheableTask`, extends `AbstractKorroTask`) — **currently a stub.** The action logs "not implemented" and writes a placeholder `build/korro/check.report`. Full diff-against-source implementation is pending a follow-up phase; the task is already registered with the same inputs as `korro` so CI callers don't change later.
- `AbstractKorroTask.buildDocsToOutputs(outDir)` computes each input doc's output path relative to `docs.baseDir` — fails loudly if an input is outside `baseDir`.
- `Korro.kt` is the markdown rewriter (parser + state machine). It lives in the plugin module, not the analysis module — parsing `<!---…-->` doesn't need Analysis API, so the parser can run without spinning up a worker.

**Worker layer** — `korro-analysis/`, runs in a fresh classloader with the Analysis API, IntelliJ platform, and stdlib on the classpath.

- `KorroWorkAction` (`KorroAction.kt`) receives serialized `KorroWorkParameters` (docs→output map, sample files, sampleOutput files, `SamplesGroup` list, boolean flags, task name, plugin version), builds a `KorroContext`, and drives it.
- `KorroContext` wires the markdown rewriter to a single `SamplesTransformer` constructed once per `execute()`.
- `SamplesTransformer` / `FqnResolver` / `SampleExtractor` (in `korro-analysis/`) drive the K2 Analysis API. One `StandaloneAnalysisAPISession` per worker `execute()` (disposed in a `try/finally`). FQN resolution uses the two-tier strategy from SPEC §9.3: a fast-path short-name index over `KtNamedFunction`s, then a dummy-KDoc fallback for qualified / ambiguous names.
- `Korro.kt` markdown rewriter behavior:
  - `IMPORT` pushes a dotted prefix onto an `imports` list; `FUN` / `FUNS` are tried against each prefix until one resolves. First-import-wins on ambiguity.
  - `FUN` opens a block; the loop consumes lines into `oldSampleLines` until `END`, EOF, or the next `FUN` / `FUNS`. On close, `processFun` asks `SamplesTransformer` for replacement text. File is rewritten only if any block changed (`rewrite` flag preserved).
  - `FUNS` is fully implemented as an Ant-style glob over FQNs. `renderFunsBody(glob)` asks the transformer for all matches, emits them in deterministic order (file path, then source offset), and wraps the group with `groupSamples.beforeGroup`/`afterGroup` when 2+ matches exist.
  - Unresolved `FUN` / `FUNS`, unclosed `//SampleStart`, and non-function targets are collected into a `DiagnosticList`. Under `behavior.ignoreMissing=false` (default) the task fails with a single `GradleException` containing the formatted table; under `ignoreMissing=true` everything degrades to `WARN` and the task succeeds with the old snippet lines retained.
  - If a `samples.outputs` file named `<fqName>` exists, its contents are appended to the generated snippet.
- Sample extraction (from SPEC §9.4):
  - Markers are detected by trimming comment text — `//SampleStart`, `// SampleStart`, and `/* SampleStart */` all match. Marker comments never appear in the output.
  - Multiple `//SampleStart`/`//SampleEnd` pairs in one function are concatenated in source order, separated by a blank line.
  - Zero markers → emit the whole body (minus the outer `{ }`).
  - Unclosed `//SampleStart` → diagnostic.
  - Assert-rewriting (`assertPrints`, `assertTrue`, `assertFalse`, `assertFails`, `assertFailsWith` → commented `println`) runs only when `behavior.rewriteAsserts=true`.
  - Output is wrapped in a ```` ```kotlin ```` fence.

## Packaging detail

- `korro-gradle-plugin` has minimal runtime dependencies — `compileOnly(gradleApi())`, `implementation(kotlin("stdlib"))`, and an `implementation` edge on `korro-analysis` that is *not* on the plugin's runtime classpath (the consumer resolves `korro-analysis` at task-execution time via the `korroAnalysisRuntime` configuration). This keeps the plugin jar on the Gradle Portal small and avoids classpath conflicts with other Kotlin-compiler-based plugins the consumer might apply.
- `korro-analysis` is the fat/shaded jar, built via the Shadow plugin. It bundles the Analysis API, low-level FIR, and the IntelliJ platform bits needed by standalone mode. `com.intellij.*` and `org.jetbrains.kotlin.*` are left unrelocated intentionally (the Analysis API is already uniquely namespaced under those packages).

## Consumer-project behavior to preserve when refactoring

- Directive lines must start at column 0 after `trim()`; `parseDirective` returns `null` otherwise.
- When multiple `IMPORT`s resolve the same short name, the **first** one wins (`firstNotNullOfOrNull` over `imports`).
- The directive regex only allows `[_a-zA-Z.]+` for the directive name — changing it affects parsing of every consumer's docs.
- The open marker is **four dashes** `<!---`. Do not broaden to three (that's a normal HTML comment and consumer markdown relies on the distinction).
- `FUN`/`FUNS` targets must be `KtNamedFunction`s. Properties, classes, top-level expressions, and `.kts` scripts produce a diagnostic, not a silent empty snippet.
- `behavior.ignoreMissing=false` is the strict-by-default contract. Don't silently lower severity on unresolved references without an opt-in.
