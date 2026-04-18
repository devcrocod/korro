package samples

import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleTest {
    @Test
    fun greeting() {
        //SampleStart
        val greeting = "hello, world"
        println(greeting)
        //SampleEnd
        assertEquals("hello, world", greeting)
    }
}
