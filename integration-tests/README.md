# Korro integration tests

GradleTestKit-driven golden-file tests. One fixture per scenario under `fixtures/`,
executed in a temp directory to avoid polluting the source tree.

## Running

```
./gradlew :integration-tests:test
```

## Adding a fixture

1. Create `fixtures/<name>/` with `settings.gradle.kts`, `build.gradle.kts`
   (applying `id("io.github.devcrocod.korro")` and `mavenCentral()`), source docs
   under `docs/in/`, samples under `samples/`, and an empty
   `docs/expected/foo.md` placeholder.
2. Add a `@Test` method to `KorroIntegrationTest` calling `loadFixture(<name>, tempDir)`.
3. Regenerate the golden file:

   ```
   ./gradlew :integration-tests:test -Pkorro.regenerate.expected=true
   ```

   The test writes the produced markdown back into `fixtures/<name>/docs/expected/`
   and short-circuits the assertion.
4. Inspect the diff, commit, and re-run without the flag to confirm the
   assertion passes.
