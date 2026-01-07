package tga.backup.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ConsoleMultiThreadWorkersTest {

    @Test
    fun demoTest() {
        val workers = ConsoleMultiThreadWorkers<Int>(5)
        val futures = mutableListOf<java.util.concurrent.Future<Result<Int>>>()

        repeat(15) { n ->
            futures.add(workers.submit { updateStatus, updateGlobalStatus ->
                var message = "Task $n - "
                repeat(10) { i ->
                    val ratio = i / 10.0
                    val colorCode = when {
                        ratio < 0.25 -> "\u001b[90m"
                        ratio < 0.50 -> "\u001b[34m"
                        ratio < 0.75 -> "\u001b[94m"
                        else -> "\u001b[96m"
                    }
                    message += "$i "
                    updateStatus("$colorCode$message\u001b[0m")
                    Thread.sleep(50)
                }
                if (n == 7) throw RuntimeException("Fail 7")
                n * 10
            })
        }

        workers.waitForCompletion()
        
        futures.forEachIndexed { index, future ->
            val result = future.get()
            if (index == 7) {
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message == "Fail 7")
            } else {
                assertTrue(result.isSuccess)
                assertTrue(result.getOrNull() == index * 10)
            }
        }
        
        workers.shutdown()
    }
}
