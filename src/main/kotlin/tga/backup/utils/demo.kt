package tga.backup.utils

import kotlin.random.Random

fun main() {
    val workers = ConsoleMultiThreadWorkers(5)

    repeat(15) { n ->
        workers.submit { updateStatus ->
            var message = "Task $n - "
            val speed = 200L + Random.nextLong(500)
            repeat(20) { i ->
                message += "*"
                updateStatus(message)
                Thread.sleep(speed)
            }
        }
    }

    workers.waitForCompletion()
    workers.shutdown()
}