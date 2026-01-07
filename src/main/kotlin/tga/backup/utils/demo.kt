package tga.backup.utils

import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

fun main() {
    val totalProgress = AtomicInteger(0)
    val totalTasks = 15
    val iterationsPerTask = 20
    val maxProgress = totalTasks * iterationsPerTask

    val workers = ConsoleMultiThreadWorkers<String>(7)

    val futures: List<Future<Result<String>>> = (1..totalTasks)
        .map { n ->
            workers.submit { updateStatus, updateGlobalStatus ->
                var message = "Task $n - "
                val speed = 100L + Random.nextLong(200)
                repeat(iterationsPerTask) { i ->
                    val ratio = i / iterationsPerTask.toDouble()
                    val colorCode = when {
                        ratio < 0.25 -> "\u001b[90m" // Dark Gray
                        ratio < 0.50 -> "\u001b[34m" // Blue
                        ratio < 0.75 -> "\u001b[94m" // Light Blue
                        else -> "\u001b[96m"         // Light Cyan
                    }
                    message += "*"
                    updateStatus("$colorCode$message\u001b[0m")
                    
                    val currentTotal = totalProgress.incrementAndGet()
                    val globalRatio = (currentTotal.toDouble() / maxProgress) * 100
                    updateGlobalStatus("Total progress: ${"%.2f".format(globalRatio)}%")

                    Thread.sleep(speed)
                }
                if (n % 5 == 0) throw RuntimeException("Simulated error in task $n")
                "Result of task $n"
            }
        }

    workers.waitForCompletion()
    
    println("\n\nAll tasks finished. Results:")
    futures.forEachIndexed { index, future ->
        val res = future.get()
        println("Task $index: $res")
    }
    
    workers.shutdown()
}