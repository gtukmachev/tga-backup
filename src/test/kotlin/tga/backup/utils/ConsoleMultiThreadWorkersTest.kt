package tga.backup.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tga.backup.terminal.TerminalCapabilities
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

class ConsoleMultiThreadWorkersTest {

    private val nonInteractive = TerminalCapabilities(
        isInteractive = false,
        supportsAnsi = false,
        width = 120,
        isDarkTheme = true,
    )

    @Test
    fun demoTest() {
        val workers = ConsoleMultiThreadWorkers<Int>(5, nonInteractive)
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

    @Test
    fun `submitDynamic - tasks can spawn children and all complete`() {
        val workers = ConsoleMultiThreadWorkers<Unit>(3, nonInteractive)
        val completed = ConcurrentHashMap.newKeySet<String>()

        workers.submitDynamic { updateStatus, updateGlobalStatus, submitChild ->
            updateStatus("root task")
            completed.add("root")

            repeat(3) { i ->
                submitChild(DynamicTask { childStatus, childGlobal, submitGrandchild ->
                    childStatus("child-$i")
                    completed.add("child-$i")
                    Thread.sleep(20)

                    if (i == 0) {
                        repeat(2) { j ->
                            submitGrandchild(DynamicTask { gcStatus, _, _ ->
                                gcStatus("grandchild-$i-$j")
                                completed.add("grandchild-$i-$j")
                                Thread.sleep(10)
                                Unit
                            })
                        }
                    }
                    Unit
                })
            }
        }

        workers.awaitDynamic()
        workers.shutdown()

        assertThat(completed).containsExactlyInAnyOrder(
            "root", "child-0", "child-1", "child-2", "grandchild-0-0", "grandchild-0-1"
        )
    }

    @Test
    fun `submitDynamic - global status updates work`() {
        val workers = ConsoleMultiThreadWorkers<Unit>(2, nonInteractive)
        val counter = AtomicInteger(0)

        workers.submitDynamic { _, updateGlobalStatus, submitChild ->
            counter.incrementAndGet()
            updateGlobalStatus("Found: ${counter.get()} items")

            repeat(4) {
                submitChild(DynamicTask { _, childGlobal, _ ->
                    val count = counter.incrementAndGet()
                    childGlobal("Found: $count items")
                    Thread.sleep(10)
                    Unit
                })
            }
        }

        workers.awaitDynamic()
        workers.shutdown()

        assertThat(counter.get()).isEqualTo(5)
    }

    @Test
    fun `submitDynamic - error in child propagates`() {
        val workers = ConsoleMultiThreadWorkers<Unit>(2, nonInteractive)

        workers.submitDynamic { _, _, submitChild ->
            submitChild(DynamicTask { _, _, _ ->
                throw RuntimeException("child failed")
            })
            Thread.sleep(50)
        }

        assertThrows<RuntimeException> {
            workers.awaitDynamic()
        }.also { ex ->
            assertThat(ex.message).isEqualTo("child failed")
        }

        workers.shutdown()
    }

    @Test
    fun `non-interactive output contains no ANSI escape codes`() {
        val captured = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(captured))
        try {
            val workers = ConsoleMultiThreadWorkers<Int>(2, nonInteractive)
            workers.submit { updateStatus, updateGlobalStatus ->
                updateStatus("\u001b[32mgreen text\u001b[0m")
                updateGlobalStatus("progress: 50%")
                42
            }
            workers.waitForCompletion()
            workers.shutdown()
        } finally {
            System.setOut(originalOut)
        }
        val output = captured.toString()
        val esc = 27.toChar().toString()
        assertThat(output).doesNotContain(esc)
        assertThat(output).contains("green text")
    }

    @Test
    fun `non-interactive mode throttles repeated updates`() {
        val captured = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(captured))
        try {
            val workers = ConsoleMultiThreadWorkers<Int>(1, nonInteractive)
            workers.submit { updateStatus, _ ->
                repeat(50) { i ->
                    updateStatus("update $i")
                }
                99
            }
            workers.waitForCompletion()
            workers.shutdown()
        } finally {
            System.setOut(originalOut)
        }
        val output = captured.toString()
        val lineCount = output.lines().count { it.contains("update") }
        assertThat(lineCount).isLessThan(50)
        assertThat(lineCount).isGreaterThanOrEqualTo(2)
    }
}
