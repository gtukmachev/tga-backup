package tga.backup.utils

import tga.backup.terminal.Color
import tga.backup.terminal.style
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
                    val color = when {
                        ratio < 0.25 -> Color.MUTED
                        ratio < 0.50 -> Color.INFO
                        ratio < 0.75 -> Color.ACCENT
                        else -> Color.SUCCESS
                    }
                    message += "*"
                    updateStatus(style(message, color))

                    val currentTotal = totalProgress.incrementAndGet()
                    val globalRatio = (currentTotal.toDouble() / maxProgress) * 100
                    updateGlobalStatus("${style("Total progress:", bold = true)} ${style("${"%.2f".format(globalRatio)}%", Color.ACCENT)}")

                    Thread.sleep(speed)
                }
                if (n % 5 == 0) throw RuntimeException("Simulated error in task $n")
                "Result of task $n"
            }
        }

    workers.waitForCompletion()

    println("\n\n${style("All tasks finished. Results:", bold = true)}")
    futures.forEachIndexed { index, future ->
        val res = future.get()
        val text = if (res.isSuccess) style("Task $index: $res", Color.SUCCESS) else style("Task $index: $res", Color.ERROR)
        println(text)
    }

    workers.shutdown()
}
