package tga.backup.utils

import kotlin.random.Random

fun main() {
    val workers = ConsoleMultiThreadWorkers(5)

    repeat(15) { n ->
        workers.submit { updateStatus ->
            var message = "Task $n - "
            val speed = 200L + Random.nextLong(500)
            repeat(20) { i ->
                val ratio = i / 20.0
                val colorCode = when {
                    ratio < 0.25 -> "\u001b[90m" // Dark Gray
                    ratio < 0.50 -> "\u001b[34m" // Blue
                    ratio < 0.75 -> "\u001b[94m" // Light Blue
                    else -> "\u001b[96m"        // Light Cyan (very light blueish)
                }
                message += "*"
                updateStatus("$colorCode$message\u001b[0m")
                Thread.sleep(speed)
            }
        }
    }

    workers.waitForCompletion()
    workers.shutdown()
}