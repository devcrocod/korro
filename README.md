# Korro

[![Apache license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/github/devcrocod/korro/maven-metadata.xml.svg?label=Gradle+plugin)](https://plugins.gradle.org/plugin/io.github.devcrocod.korro)

Kotlin source code documentation plugin.

Inspired by [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit).

Korro injects Kotlin sample snippets into Markdown and MDX documents. You point it at some `.md`/`.mdx` files and some
Kotlin source files, mark regions with `<!---FUN ...-->` directives (or `{/*---FUN ...--*/}` in MDX), and Korro fills
those regions with the body of the referenced function.

<!---TOC-->

* [Setup](#setup)
    * [Baseline](#baseline)
    * [Tasks](#tasks)
    * [DSL](#dsl)
    * [Behavior flags](#behavior-flags)
    * [Grouping samples](#grouping-samples)
* [Directives](#directives)
    * [IMPORT](#import)
    * [FUN](#fun)
    * [FUNS](#funs)
    * [END](#end)
* [Example](#example)
* [What changed in 0.2](#what-changed-in-02)

<!---END-->

## Setup

```kotlin
plugins {
    id("io.github.devcrocod.korro") version "0.2.0"
}
```

The legacy `buildscript { classpath … }` installation form is no longer supported. 0.2.0 requires the `plugins { }` DSL.

### Baseline

| Requirement                   | Version |
|-------------------------------|---------|
| Gradle                        | 8.5+    |
| JDK (build + runtime)         | 17+     |
| Kotlin Analysis API (bundled) | 2.3.20  |

The bundled Kotlin version is pinned inside the plugin. Your consumer project's own `org.jetbrains.kotlin.*` plugin
version is irrelevant — Korro runs the Analysis API inside a worker with an isolated classloader. Your sample code can
be authored against any Kotlin version that the 2.3.20 Analysis API can parse.

### Tasks

| Task            | Purpose                                                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `korroGenerate` | Regenerates markdown into `build/korro/docs/`. Cacheable. Never touches source files.                                                     |
| `korro`         | Applies generated output from `build/korro/docs/` onto `docs.baseDir`. Depends on `korroGenerate`, so one command regenerates and copies. |
| `korroCheck`    | Regenerates docs into a temp directory and fails the build if the committed source tree is out of date. Run this in CI.                   |

There is no `korroClean` — use `./gradlew clean` or delete `build/korro/`. There is no `korroTest`.

Typical workflow:

```bash
# Local authoring:
./gradlew korro               # regenerate and update source markdown in one step

# CI:
./gradlew korroCheck          # fail if docs drift from samples
```

### DSL

```kotlin
korro {
    docs {
        from(fileTree("docs") { include("**/*.md", "**/*.mdx") })
        baseDir.set(layout.projectDirectory.dir("docs"))   // REQUIRED
    }
    samples {
        from(fileTree("src/test/samples"))
        outputs.from(fileTree("build/sampleOutputs"))      // optional
    }
    behavior {
        rewriteAsserts.set(false)
        ignoreMissing.set(false)
    }
}
```

- `docs.from(...)` is the set of markdown files to process.
- `docs.baseDir` is **mandatory**. Output files land at `<buildDir>/korro/docs/<path-relative-to-baseDir>`, and the
  `korro` task mirrors that tree back onto `baseDir`. Set it to whichever directory the paths in `docs.from` are rooted
  under — typically `layout.projectDirectory` or `layout.projectDirectory.dir("docs")`.
- `samples.from(...)` is the set of Kotlin source files scanned for `FUN`/`FUNS` targets.
- `samples.outputs.from(...)` is optional. A file in this collection whose name exactly equals a resolved `FUN`
  fully-qualified name is appended verbatim after the generated snippet.

### Behavior flags

- `rewriteAsserts` (default `false`) — when `true`, sample bodies have their `assertPrints` / `assertTrue` /
  `assertFalse` / `assertFails` / `assertFailsWith` calls rewritten into a commented `println`. Enable this only if your
  samples use `kotlin.test` idioms.
- `ignoreMissing` (default `false`) — strict by default. Unresolved `FUN`/`FUNS`, unclosed `//SampleStart`, and
  non-function targets fail the task with a collected diagnostic list. Set `true` to degrade those errors to warnings
  and keep the old snippet lines in the output.

### Grouping samples

Use `groupSamples` to wrap multiple related snippets (for example, HTML tabs). Semantics are unchanged from 0.1.x; only
the property API moved from `= ...` to `.set(...)`.

```kotlin
korro {
    groupSamples {
        beforeGroup.set("<tabs>\n")
        afterGroup.set("</tabs>")
        beforeSample.set("<tab title=\"NAME\">\n")
        afterSample.set("\n</tab>")
        funSuffix("_v1") { replaceText("NAME", "Version 1") }
        funSuffix("_v2") { replaceText("NAME", "Version 2") }
    }
}
```

For new docs, prefer a single `FUNS myFun_v*` directive over two `FUN myFun_v1` / `FUN myFun_v2` directives.

## Directives

Korro does not parse markdown; it recognizes _directives_ only. A directive:

- starts at column 0 after `String.trim()`,
- opens with three dashes after an HTML- or MDX-comment prefix — `<!---` for `.md` or `{/*---` for `.mdx` (standard HTML comments `<!--` and standard MDX comments `{/*` are deliberately not recognized),
- closes on the **same line** with `-->` (in `.md`) or `--*/}` (in `.mdx`); multi-line directives are an error,
- has a name matching `[_a-zA-Z.]+`.

The syntax is selected per file by extension — `.mdx` uses the JSX-expression form, everything else uses the
HTML-comment form. Both encode the same four directives (`IMPORT`, `FUN`, `FUNS`, `END`) with identical semantics;
examples below show the `.md` form.

MDX equivalents (for Mintlify, Docusaurus, etc.):

```mdx
{/*---IMPORT samples.Test--*/}
{/*---FUN exampleTest--*/}
{/*---END--*/}
```

### IMPORT

```
<!---IMPORT samples.Test-->
```

Pushes `"samples.Test."` onto the prefix list used by subsequent `FUN`/`FUNS` lookups. Multiple `IMPORT`s are allowed;
when more than one prefix resolves a short name, the **first** import wins.

Package wildcards (`samples.*`) are not supported.

### FUN

```
<!---FUN exampleTest-->
<!---END-->
```

Inserts the body of the referenced Kotlin function between the directives, wrapped in a ```` ```kotlin ```` fence.

If the function contains `//SampleStart` / `//SampleEnd` comments, only the region between them is emitted; multiple
pairs are concatenated, separated by a blank line. If the function has no markers, the whole body is emitted (without
the outer `{ }`).

Only `fun` declarations (`KtNamedFunction`) are valid targets. Properties, classes, top-level expressions, and `.kts`
scripts are not. Don't wrap function names in backticks.

### FUNS

```
<!---FUNS sample_v*-->
<!---END-->
```

Expands to every function matching the Ant-style glob (`*`, `?`) over the fully-qualified names reachable from the
current `IMPORT` prefixes. Matches are emitted in deterministic order: first by containing file path, then by source
offset.

When `groupSamples.beforeGroup` / `afterGroup` are set and there are two or more matches, the whole group is wrapped by
those strings; each individual match is wrapped by `beforeSample` / `afterSample`.

Zero matches: fails the task in strict mode, or warns under `ignoreMissing`.

### END

Closes `FUN` or `FUNS`.

## Example

Minimal end-to-end setup (lifted from `integration-tests/fixtures/basic/`):

`settings.gradle.kts`:

```kotlin
rootProject.name = "korro-example"
```

`build.gradle.kts`:

```kotlin
plugins {
    id("io.github.devcrocod.korro") version "0.2.0"
}

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

korro {
    docs {
        from(fileTree("docs"))
        baseDir.set(layout.projectDirectory.dir("docs"))
    }
    samples {
        from(fileTree("samples"))
    }
}
```

`samples/Example.kt`:

```kotlin
package samples

fun example() {
    //SampleStart
    println("hello")
    //SampleEnd
}
```

`docs/foo.md` (before `korro`):

```markdown
# Example

<!---IMPORT samples-->

<!---FUN example-->
<!---END-->
```

After `./gradlew korro`:

````markdown
# Example

<!---IMPORT samples-->

<!---FUN example-->

```kotlin
println("hello")
```

<!---END-->
````

## What changed in 0.2

- The analysis backend moved from Dokka 1.x (K1) to the Kotlin Analysis API (K2, standalone mode).
- The DSL is now nested and Property-based (config-cache safe). `docs = …` / `samples = …` became
  `docs { from(…); baseDir.set(…) }` / `samples { from(…); outputs.from(…) }`.
- `korroGenerate` is cacheable and writes out-of-place to `build/korro/docs/`. `korro` depends on it and applies the
  output onto the source tree; use `korroCheck` in CI.
- Strict-by-default: unresolved `FUN`/`FUNS` fails the build. Opt back in to the old warn-and-continue behavior with
  `behavior { ignoreMissing.set(true) }`.
- Assert rewriting is off by default. Restore with `behavior { rewriteAsserts.set(true) }`.
- `FUNS` is now implemented as a glob-filter directive.
- MDX files (`.mdx`) are supported natively via a JSX-expression directive form `{/*---FUN ...--*/}`.
- `korroClean` is removed; `korroTest` is deferred.

Full upgrade guide: [MIGRATION.md](MIGRATION.md).

A ready-to-copy consumer project lives at [`integration-tests/fixtures/basic/`](integration-tests/fixtures/basic).
