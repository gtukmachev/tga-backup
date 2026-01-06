package tga.backup.utils

import kotlin.random.Random

fun main() {
    val workers = ConsoleMultiThreadWorkers<String>(5)
    val futures = mutableListOf<java.util.concurrent.Future<Result<String>>>()

    repeat(15) { n ->
        futures.add(workers.submit { updateStatus ->
            var message = "Task $n - "
            val speed = 100L + Random.nextLong(200)
            repeat(20) { i ->
                val ratio = i / 20.0
                val colorCode = when {
                    ratio < 0.25 -> "\u001b[90m" // Dark Gray
                    ratio < 0.50 -> "\u001b[34m" // Blue
                    ratio < 0.75 -> "\u001b[94m" // Light Blue
                    else -> "\u001b[96m"        // Light Cyan
                }
                message += "*"
                updateStatus("$colorCode$message\u001b[0m")
                Thread.sleep(speed)
            }
            if (n % 5 == 0) throw RuntimeException("Simulated error in task $n")
            "Result of task $n"
        })
    }

    workers.waitForCompletion()
    
    println("\n\nAll tasks finished. Results:")
    futures.forEachIndexed { index, future ->
        val res = future.get()
        println("Task $index: $res")
    }
    
    workers.shutdown()
}