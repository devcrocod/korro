# Migrating from Korro 0.1.x to 0.2.0

## TL;DR

The plugin id (`io.github.devcrocod.korro`) and the `<!---…-->` directive grammar in your markdown are **unchanged**.
What changed:

- The `korro { }` DSL is now nested and Property-based. Assignments (`docs = …`, `beforeGroup = …`) no longer compile.
- `korro` still mutates source files (end-to-end: regenerate + apply), but the heavy lifting moved to a new
  `korroGenerate` task that writes to `build/korro/docs/` and is cacheable/safe to run from CI. `korro` now depends on
  `korroGenerate` and copies its output onto the source tree. Use `korroCheck` in CI instead of `korro`.
- Unresolved `FUN` references now fail the build by default (was: silently kept the stale snippet).
- Minimum Gradle 8.5, JDK 17, Kotlin Analysis API 2.3.20 bundled.

Existing markdown files do not need to be edited.

## Baseline requirements

| Surface                       | 0.1.6                       | 0.2.0           |
|-------------------------------|-----------------------------|-----------------|
| Gradle                        | 7.0+                        | **8.5+**        |
| JDK (build + runtime)         | 8                           | **17**          |
| Bundled Kotlin / Analysis API | 1.9.22 (Dokka K1)           | **2.3.20 (K2)** |
| Plugin id                     | `io.github.devcrocod.korro` | unchanged       |
| Directive syntax              | `<!---NAME VALUE-->`        | unchanged       |

The Kotlin version is pinned inside Korro. Your consumer project can use any Kotlin plugin version — Korro runs Analysis
API in an isolated worker classloader, so there is no version alignment required.

## DSL migration

### `docs`

```diff
  korro {
-     docs = fileTree(project.rootDir) {
-         include "**/*.md"
-     }
+     docs {
+         from(fileTree(project.rootDir) { include("**/*.md") })
+         baseDir.set(project.rootDir)                    // REQUIRED
+     }
  }
```

`docs.baseDir` is mandatory. Korro 0.2.0 writes output out-of-place to `build/korro/docs/<path-relative-to-baseDir>`,
and the `korro` task (a `Copy` wrapper around `korroGenerate`) mirrors that tree back onto `baseDir`. Set it to whichever
directory the paths in `docs.from` are rooted under — usually `project.rootDir` or
`layout.projectDirectory.dir("docs")`.

### `samples` and `outputs`

```diff
  korro {
-     samples = fileTree("src/test/samples")
-     outputs = fileTree("build/sampleOutputs")
+     samples {
+         from(fileTree("src/test/samples"))
+         outputs.from(fileTree("build/sampleOutputs"))
+     }
  }
```

The top-level `outputs` property moved inside the `samples` block. Semantics are unchanged: a file whose name exactly
equals a resolved `FUN` fully-qualified name is appended verbatim after the generated snippet.

### `groupSamples`

```diff
  korro {
      groupSamples {
-         beforeGroup = "<tabs>\n"
-         afterGroup = "</tabs>"
-         beforeSample = "<tab title=\"NAME\">\n"
-         afterSample = "\n</tab>"
+         beforeGroup.set("<tabs>\n")
+         afterGroup.set("</tabs>")
+         beforeSample.set("<tab title=\"NAME\">\n")
+         afterSample.set("\n</tab>")
          funSuffix("_v1") { replaceText("NAME", "Version 1") }
          funSuffix("_v2") { replaceText("NAME", "Version 2") }
      }
  }
```

All string and boolean properties are now `Property<T>` — assign with `.set(...)`. The
`funSuffix(...) { replaceText(...) }` helper is unchanged.

### `behavior` (new)

Two flags moved into a dedicated `behavior { }` block. Both default to `false`:

```kotlin
korro {
    behavior {
        ignoreMissing.set(false)
        rewriteAsserts.set(false)
    }
}
```

See "Behavior changes" below for when you'll need to flip these.

## Task migration

| 0.1.x                              | 0.2.0                                                                                   |
|------------------------------------|-----------------------------------------------------------------------------------------|
| `./gradlew korro` (mutates source) | `./gradlew korro` (regenerates into `build/korro/docs/` via `korroGenerate`, then applies onto source) |
| —                                  | `./gradlew korroGenerate` — cacheable, out-of-place only; the task to wire into CI builds that don't want source mutation |
| `./gradlew korroClean`             | `./gradlew clean` or `rm -rf build/korro/`                                              |
| `korroCheck` (TODO)                | `./gradlew korroCheck` — fails when committed docs don't match regeneration. Use in CI. |
| `korroTest` (TODO)                 | Not implemented; deferred.                                                              |

The split between `korroGenerate` (cacheable, out-of-place) and `korro` (copies onto source) is what keeps regeneration
safe to run from CI without mutating the repo.

## Behavior changes

- **Unresolved `FUN` now fails the build.** 0.1.x silently kept the existing snippet text in the output. To restore that
  behavior:
  ```kotlin
  korro { behavior { ignoreMissing.set(true) } }
  ```
- **`assertPrints` / `assertTrue` / `assertFalse` / `assertFails` / `assertFailsWith` are no longer rewritten into
  commented `println` by default.** Restore with:
  ```kotlin
  korro { behavior { rewriteAsserts.set(true) } }
  ```
- **Unclosed `//SampleStart`** (a start marker with no matching `//SampleEnd` in the same function) is now a diagnostic
  error. 0.1.x silently included the tail of the function.
- **Functions with no `//SampleStart`/`//SampleEnd`** now emit the whole body (minus the outer `{ }`). 0.1.x returned an
  empty snippet.
- **Non-function targets** (properties, classes, top-level declarations, `.kts` scripts) now produce a diagnostic. Only
  `fun` declarations are valid `FUN` targets.

All new diagnostics are collected across the whole run and reported as a single table at the end of the task.

## Directive syntax — unchanged for `.md`, new MDX variant

`<!---IMPORT ...-->`, `<!---FUN ...-->`, `<!---END-->` and the three-dash open marker all work exactly as in 0.1.x.
Existing markdown files parse without modification. Three things worth knowing:

- The previously-reserved `<!---FUNS glob-->` directive is now live. See
  the [FUNS section of the README](README.md#funs).
- First-import-wins on ambiguous short names is preserved.
- `.mdx` files now have a dedicated directive form. MDX v2 parsers (Mintlify, Docusaurus) reject raw HTML comments, so
  Korro recognizes a JSX-expression variant in files with the `.mdx` extension:
  ```mdx
  {/*---IMPORT samples--*/}
  {/*---FUN exampleTest--*/}
  {/*---END--*/}
  ```
  Same three directives, same semantics; only the outer marker changes. Selection is automatic by file extension.

## Consumer-project template

A working 0.2.0 fixture lives at [`integration-tests/fixtures/basic/`](integration-tests/fixtures/basic). Copy its
`build.gradle.kts`, `settings.gradle.kts`, and directory layout as a starting point.
