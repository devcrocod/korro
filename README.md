# Korro
[![Apache license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/github/devcrocod/korro/maven-metadata.xml.svg?label=Gradle+plugin)]

Kotlin source code documentation plugin.

Inspired by [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit).

This plugin produces code snippets into markdown documents from tests.

<!---TOC-->
* [Setup](#setup)
    * [Tasks](#tasks)
    * [Parameters](#parameters)
* [Docs](#docs)
  * [Directives](#directives)
    * [IMPORT](#import)
    * [FUN](#fun)
    * [FUNS](#funs)
    * [END](#end)
* [Sample](#sample)
<!---END-->

## Setup
```groovy
plugins {
    id("io.github.devcrocod.korro") version "0.0.3"
}
```

or 

```groovy
buildscript {
    dependencies {
        classpath "io.github.devcrocod:korro:0.0.3"
    }
}
                    
apply plugin: 'io.github.devcrocod.korro'
```

### Tasks

* `korro` - create/update samples in documentation
* `korroClean` - remove inserted code snippets in documentation.
Removes everything between the `FUN`/`END` and `FUNS`/`END` directives.
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

To insert several samples by single reference in markdown use `groupSamples`. For example, to wrap samples that have the same function name prefix followed by `_v1` or `_v2` within HTML tabs use the following configuration:
```groovy
korro {
  groupSamples {

    beforeSample = "<tab title=\"NAME\">\n"
    afterSample = "\n</tab>"

    funSuffix("_v1") {
      replaceText("NAME", "Version 1")
    }
    funSuffix("_v2") {
      replaceText("NAME", "Version 2")
    }
    beforeGroup = "<tabs>\n"
    afterGroup = "</tabs>"
  }
}
```

## Docs
### Directives

Korro does not parse the document and only recognizes _directives_.
Directives must always start at the beginning of a line, start with
```
<!---
```
and end with

```
-->
```
There are also two types of directives that require and don't require the `END` closing directive.

#### IMPORT
The `IMPORT` directive is used to import a class containing test functions.
```
<!---IMPORT org.example.Test-->
```
Multiple imports can be specified in the documentation file.

_**Note**_:

_Import will not include the entire package, that is, such a path is not recognized - `org.example.*`._

_You can specify the same classes._
```
<!---IMPORT org.example.test.Test-->
<!---IMPORT org.example.test2.Test-->
```

_If two classes contain the same function names, then the function will be taken from the first imported class._

#### FUN

FUN directive is used to insert code into documentation:
```
<!---FUN fully qualified name -->
<!---END-->
```
Code will be inserted between these two directives.

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

_Do not use function names with spaces enclosed in backticks_

#### FUNS

#### END

The `END` directive is the closing directive for `FUN` and `FUNS`.

## Sample

`build.gradle`
```groovy
plugins {
    id("io.github.devcrocod.korro") version "0.0.3"
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
<!---IMPORT samples.Test-->

Some text.

Example:
<!---FUN exampleTest-->
<!---END-->

Some text.

```

After you run `korro` you get the following file `doc.md`:
```
# Docs
<!---IMPORT samples.Test-->

Some text.

Example:
<!---FUN exampleTest-->
```kotlin
c = a + b
``'
<!---END-->

Some text.

```
