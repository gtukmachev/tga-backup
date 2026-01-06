package tga.backup.utils

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class ConsoleMultiThreadWorkers<T>(private val threadCount: Int) {

    private val executor = Executors.newFixedThreadPool(threadCount)
    private val activeTasks = AtomicInteger(0)
    private val workerLineMap = ConcurrentHashMap<Long, Int>()
    private val nextLineIndex = AtomicInteger(0)

    init {
        repeat(threadCount) { println() }
    }

    fun submit(task: TaskWithStatus<T>): Future<Result<T>> {
        return executor.submit(Callable {
            val threadId = Thread.currentThread().id
            val lineIndex = workerLineMap.getOrPut(threadId) { nextLineIndex.getAndIncrement() % threadCount }

            activeTasks.incrementAndGet()
            try {
                val result = task.run { status ->
                    outputStatus(lineIndex, status)
                }
                Result.success(result)
            } catch (e: Throwable) {
                outputStatus(lineIndex, "Error: ${e.message}")
                Result.failure(e)
            } finally {
                activeTasks.decrementAndGet()
            }
        })
    }

    fun submit(task: ((String) -> Unit) -> T): Future<Result<T>> {
        return submit(TaskWithStatus { task(it) })
    }

    @Synchronized
    private fun outputStatus(lineIndex: Int, status: String) {
        val linesToMoveUp = threadCount - lineIndex
        // ESC [ <n> A - Move cursor up n lines
        // ESC [ G - Move cursor to the beginning of the line
        // ESC [ K - Erase to end of line
        print("\u001b[${linesToMoveUp}A\r\u001b[K$status\u001b[${linesToMoveUp}B")
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
