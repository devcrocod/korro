# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Korro is a Gradle plugin (Kotlin/JVM), published as `io.github.devcrocod.korro`, that injects Kotlin function bodies into `.md`/`.mdx` docs via `<!---FUN ...-->` or `{/*---FUN ...--*/}` directives. Consumer-facing syntax and the DSL are in `README.md`; the 0.1.x→0.2.0 migration is in `MIGRATION.md`. **Read both before changing the directive parser or the extension DSL** — they are the downstream contract.

## Commands

Gradle wrapper (9.4.1):

- `./gradlew build` — compile and assemble both modules.
- `./gradlew :integration-tests:test` — GradleTestKit + golden-file tests under `integration-tests/fixtures/`. This is the only meaningful test suite in this repo.
- `./gradlew publishToMavenLocal` — install **both** artifacts to `~/.m2/` for consumer testing. Both must be installed together: `korroAnalysisRuntime` resolves `korro-analysis` at the plugin's own version at task-execution time.
- `./gradlew -Prelease build` — release-versioned artifact. Without `-Prelease`, `detectVersion()` in the root `build.gradle.kts` appends `-dev` (or `-dev-<build.number>`) to the version in `gradle.properties`.
- `./gradlew :korro-analysis:shadowJar` — build only the fat jar.
- `./gradlew publishPlugins` — publish to the Gradle Plugin Portal (requires credentials).

The `korroGenerate` / `korro` / `korroCheck` tasks the plugin registers are **not** runnable from this repo's root — only from a consumer project or an `integration-tests/fixtures/*` fixture.

## Architecture

Two modules separated by a Gradle worker boundary.

### `korro-gradle-plugin/` — Gradle-facing layer (thin)

Runs in the Gradle daemon classloader. No Analysis API imports at compile time — only `compileOnly(gradleApi())` + `implementation(kotlin("stdlib"))`. Contains `KorroPlugin`, `KorroExtension`, the three tasks, and the markdown directive parser (`Korro.kt`).

The parser lives here, not in `korro-analysis`, because `<!---…-->` / `{/*---…--*/}` parsing doesn't need the Analysis API. Per-file marker form is selected by extension through `DirectiveSyntax`: `.mdx` uses JSX-expression comments (required — Mintlify/Docusaurus reject raw HTML comments); everything else uses the HTML-comment form.

Analysis code is pulled in at task-execution time: `KorroPlugin` creates a detached `korroAnalysisRuntime` configuration with a dependency on `io.github.devcrocod:korro-analysis:<pluginVersion>`, and tasks submit work via `WorkerExecutor.classLoaderIsolation { classpath.from(korroRuntimeClasspath) }`.

**Task shape to preserve:**

- `korroGenerate` (`@CacheableTask`) writes out-of-place to `build/korro/docs/`.
- `korro` extends `Copy` (never `Sync`), depends on `korroGenerate`, and copies its output onto `docs.baseDir`. This is the only source-mutation point. **Must stay `Copy`:** `docs.baseDir` is typically the repo or project root and contains many files Korro does not manage — `Sync`'s delete-unknown semantics would wipe the working tree.
- `korroCheck` (`@CacheableTask`) regenerates into `build/korro/check/`, diffs against the source tree, and fails the build with the first differing line per file. CI entry point.
- Every task has an `@Input korroPluginVersion` so cached outputs invalidate on plugin bump (which is also the Analysis API bump).

### `korro-analysis/` — Analysis layer (shadowed fat jar)

Runs inside the worker's isolated classloader. Bundles the Kotlin Analysis API (K2 standalone), low-level FIR, and the IntelliJ platform. `com.intellij.*` and `org.jetbrains.kotlin.*` are **intentionally unrelocated** — the Analysis API is already uniquely namespaced, and relocating it breaks reflection lookups inside the platform.

- One `StandaloneAnalysisAPISession` per `KorroWorkAction.execute()` call, disposed in a `try/finally`. Do **not** call `disposeGlobalStandaloneApplicationServices()` — it's a one-shot that invalidates all future Analysis API use in the JVM. `classLoaderIsolation` gives a fresh classloader per task run, so singletons are reloaded naturally.
- FQN resolution is two-tier: a fast-path short-name index over `KtNamedFunction`s for unambiguous bare names, then a dummy-KDoc `/** [fqn] */` fallback for qualified/ambiguous names. First-import-wins on ambiguity.

### Worker boundary

`KorroWorkParameters` is serialized across the classloader boundary (even under `classLoaderIsolation`, Gradle serializes parameters). All fields must stay `Serializable` — `Set<File>`, primitives, strings, and the `SamplesGroup` DTO only. No `Project` / `Task` / `Logger` references.

## Version wiring

- Korro's own version lives in `gradle.properties` (`version=...`). Both subprojects inherit it via `subprojects { version = rootProject.version }` in the root `build.gradle.kts`. At runtime the plugin reads it from a generated `META-INF/korro-gradle-plugin.properties` resource (`KorroPlugin.readKorroPluginVersion`).
- Every other version lives in `gradle/libs.versions.toml`. The catalog is the single source of truth — do not hard-code versions in subproject scripts; add to the catalog and reference as `libs.*` / `libs.plugins.*`.
- `libs.versions.kotlin` — pinned Kotlin / Analysis API version. `libs.versions.kotlinLanguage` — Kotlin `languageVersion`/`apiVersion` used to compile Korro itself; unrelated to the bundled Analysis API. JVM target is hard-coded to `17` in the root `build.gradle.kts`.

## Invariants to preserve

These are contracts for every consumer's docs; breaking any of them silently breaks downstream projects.

- **Directives start at column 0 after `String.trim()`.** `parseDirective` returns `null` otherwise.
- **Three dashes to open, two to close.** `<!---NAME VALUE-->` for `.md` (and anything non-`.mdx`); `{/*---NAME VALUE--*/}` for `.mdx`. Do not collapse the open marker to two dashes — that becomes a standard HTML/MDX comment, and consumer docs rely on the distinction.
- **Directive name regex is `[_a-zA-Z.]+`.** Broadening it changes parsing for every consumer.
- **First `IMPORT` wins** on ambiguous short names (`firstNotNullOfOrNull` over the `imports` list).
- **`KtNamedFunction`, `KtClassOrObject`, and `KtProperty`** are valid `FUN`/`FUNS` targets. Enum entries, type aliases, local declarations, and `.kts` scripts are not; resolving to a non-target produces a diagnostic, not a silent empty snippet. Class/object/property targets rely on `//SampleStart` / `//SampleEnd` markers inside their body for non-empty output.
- **`behavior.ignoreMissing=false` is the strict-by-default contract.** Don't silently lower severity on unresolved references without an explicit opt-in.
