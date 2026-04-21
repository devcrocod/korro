# Declarations example

<!---IMPORT samples-->

## Top-level object

<!---FUN Greeter-->

```kotlin
const val GREETING = "hello"

fun say() {
    println(GREETING)
}
```

<!---END-->

## Top-level class

<!---FUN Counter-->

```kotlin
var value: Int = start
    private set

fun increment() {
    value++
}
```

<!---END-->

## Nested declaration (DataFrame pattern)

<!---FUN DbDemo-->

```kotlin
object HSQLDB {
    val driver: String = "org.hsqldb.jdbcDriver"
}
```

<!---END-->

## Top-level property

<!---FUN slogan-->

```kotlin
return "declarations are first-class"
```

<!---END-->

## Glob over class-kind declarations

<!---FUNS Version?-->

```kotlin
const val NAME = "one"
```

```kotlin
const val NAME = "two"
```

<!---END-->
