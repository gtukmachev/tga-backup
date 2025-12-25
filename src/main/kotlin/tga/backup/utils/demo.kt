package tga.backup.utils

fun main() {
    val workers = ConsoleMultiThreadWorkers(5)

    repeat(15) { n ->
        workers.submit { updateStatus ->
            var message = "Task $n - "
            repeat(10) { i ->
                message += "$i "
                updateStatus(message)
                Thread.sleep(300)
            }
        }
    }

    workers.waitForCompletion()
    workers.shutdown()
}