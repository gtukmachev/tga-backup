package tga.backup.utils

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

fun interface TaskWithStatus<T> {
    fun run(
        updateStatus: (String) -> Unit,
        updateGlobalStatus: (String) -> Unit
    ): T
}

class ConsoleMultiThreadWorkers<T>(
    private val threadCount: Int,
) {

    private val executor = Executors.newFixedThreadPool(threadCount)
    private val activeTasks = AtomicInteger(0)
    private val workerLineMap = ConcurrentHashMap<Long, Int>()
    private val nextLineIndex = AtomicInteger(0)

    init {
        repeat(threadCount + 1) { println() }
    }


    fun submit(task: TaskWithStatus<T>): Future<Result<T>> {
        return executor.submit(Callable {
            val threadId = Thread.currentThread().id
            val lineIndex = workerLineMap.getOrPut(threadId) { nextLineIndex.getAndIncrement() % threadCount }

            activeTasks.incrementAndGet()
            try {
                val result = task.run(
                    { status -> outputStatus(lineIndex, status) },
                    { globalStatus -> outputGlobalStatus(globalStatus) }
                )
                Result.success(result)
            } catch (e: Throwable) {
                outputStatus(lineIndex, "Error: ${e.message}")
                Result.failure(e)
            } finally {
                activeTasks.decrementAndGet()
            }
        })
    }


    fun submit(
            task: (
                    updateStatus: (String) -> Unit,
                    updateGlobalStatus: (String) -> Unit
                ) -> T
    ): Future<Result<T>> {
        return submit(TaskWithStatus { updateStatus, updateGlobalStatus -> task(updateStatus, updateGlobalStatus) })
    }

    @Synchronized
    private fun outputStatus(lineIndex: Int, status: String) {
        val linesToMoveUp = (threadCount + 1) - lineIndex
        // ESC [ <n> A - Move cursor up n lines
        // ESC [ G - Move cursor to the beginning of the line
        // ESC [ K - Erase to end of line
        print("\u001b[${linesToMoveUp}A\r\u001b[K$status\u001b[${linesToMoveUp}B")
        System.out.flush()
    }

    @Synchronized
    private fun outputGlobalStatus(status: String) {
        print("\u001b[1A\r\u001b[K$status\u001b[1B")
        System.out.flush()
    }

    fun waitForCompletion() {
        executor.shutdown()
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
