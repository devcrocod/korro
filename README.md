# Korro
[![Apache license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

Kotlin source code documentation plugin.

Inspired by [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit).

This plugin produces code snippets into markdown documents from tests.

## Setup
```groovy
plugins {
    id("com.github.devcrocod.korro") version "0.0.7"
}
```

or 

```groovy
buildscript {
    dependencies {
        classpath "io.github.devcrocod:korro:0.0.7"
    }
}
                    
apply plugin: 'io.github.devcrocod.korro'
```

### Tasks

* `korro` - create/update samples in documentation
* `korroCheck` - TODO
* `korroTest` - TODO

### Parameters

```groovy
korro {
    docs = fileTree(project.rootDir) {
        include '**/*.md'
    }

    samples = fileTree(project.rootDir) {
        include 'src/test/samples/*.kt'
    }
}
```

## Docs

Directives are used to insert code into the documentation:
```
<!--- fully qualified name -->
<!---END-->
```
Сode will be inserted between these two directives.

Only the part between the two comments `// SampleStart`, `// SampleEnd` will be taken from the test function:
```kotlin
fun test() {
    ...
    // SampleStart
    sample code
    // SampleEnd
    ...
}
```

_**Note**_: 
* Do not use function names with spaces enclosed in backticks
* Directives must always start at the beginning of the line.

## Sample

`build.gradle`
```groovy
plugins {
    id("com.github.devcrocod.korro") version "0.0.7"
}

...

korro {
    docs = fileTree(project.rootDir) {
        include 'docs/doc.md'
    }

    samples = fileTree(project.rootDir) {
        include 'src/test/samples/test.kt'
    }
}
```

`test.kt`
```kotlin
package samples

import org.junit.Test
import org.junit.Assert.assertEquals

class Test {
    
    @Test
    fun exampleTest() {
        val a = 1
        val b = 2
        val c: Int
        // SampleStart
        c = a + b
        // SampleEnd
        assertEquals(3, c)
    }
}
```

`doc.md`
```
# Docs

Some text.

Example:
<!---samples.Test.exampleTest-->
<!---END-->

Some text.

```

After you run `korro` you get the following file `doc.md`:
```
# Docs

Some text.

Example:
<!---samples.Test.exampleTest-->
```kotlin
c = a + b
``'
<!---END-->

Some text.

```
