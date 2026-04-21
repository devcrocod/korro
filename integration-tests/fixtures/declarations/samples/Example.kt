package samples

object Greeter {
    //SampleStart
    const val GREETING = "hello"

    fun say() {
        println(GREETING)
    }
    //SampleEnd
}

class Counter(val start: Int) {
    //SampleStart
    var value: Int = start
        private set

    fun increment() {
        value++
    }
    //SampleEnd
}

interface DbDemo {
    //SampleStart
    object HSQLDB {
        val driver: String = "org.hsqldb.jdbcDriver"
    }
    //SampleEnd
}

val slogan: String
    get() {
        //SampleStart
        return "declarations are first-class"
        //SampleEnd
    }

object Version1 {
    //SampleStart
    const val NAME = "one"
    //SampleEnd
}

object Version2 {
    //SampleStart
    const val NAME = "two"
    //SampleEnd
}
