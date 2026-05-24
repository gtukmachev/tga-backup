package tga.backup.utils

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun interface TaskWithStatus<T> {
    fun run(
        updateStatus: (String) -> Unit,
        updateGlobalStatus: (String) -> Unit
    ): T
}

fun interface DynamicTask<T> {
    fun run(
        updateStatus: (String) -> Unit,
        updateGlobalStatus: (String) -> Unit,
        submitChild: (DynamicTask<T>) -> Unit
    ): T
}

class ConsoleMultiThreadWorkers<T>(
    private val threadCount: Int,
) {

    private val executor = Executors.newFixedThreadPool(threadCount)
    private val activeTasks = AtomicInteger(0)
    private val workerLineMap = ConcurrentHashMap<Long, Int>()
    private val nextLineIndex = AtomicInteger(0)

    private val phaser = Phaser(1)
    private val dynamicError = AtomicReference<Throwable?>(null)

    init {
        repeat(threadCount + 1) { println() }
    }


    fun submit(task: TaskWithStatus<T>): Future<Result<T>> {
        return executor.submit(Callable {
            @Suppress("DEPRECATION")
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

    fun submitDynamic(task: DynamicTask<T>) {
        phaser.register()
        executor.execute {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val lineIndex = workerLineMap.getOrPut(threadId) { nextLineIndex.getAndIncrement() % threadCount }

            try {
                if (dynamicError.get() != null) return@execute
                task.run(
                    { status -> outputStatus(lineIndex, status) },
                    { globalStatus -> outputGlobalStatus(globalStatus) },
                    { child -> submitDynamic(child) }
                )
            } catch (e: Throwable) {
                dynamicError.compareAndSet(null, e)
                outputStatus(lineIndex, "Error: ${e.message}")
            } finally {
                phaser.arriveAndDeregister()
            }
        }
    }

    fun submitDynamic(
        task: (
            updateStatus: (String) -> Unit,
            updateGlobalStatus: (String) -> Unit,
            submitChild: (DynamicTask<T>) -> Unit
        ) -> Unit
    ) {
        submitDynamic(DynamicTask { updateStatus, updateGlobalStatus, submitChild ->
            task(updateStatus, updateGlobalStatus, submitChild)
            @Suppress("UNCHECKED_CAST")
            Unit as T
        })
    }

    fun awaitDynamic() {
        phaser.arriveAndAwaitAdvance()
        dynamicError.get()?.let { throw it }
    }

    @Synchronized
    private fun outputStatus(lineIndex: Int, status: String) {
        val linesToMoveUp = (threadCount + 1) - lineIndex
        print("\u001b[${linesToMoveUp}A\r\u001b[K$status\u001b[${linesToMoveUp}B")
        System.out.flush()
    }

    fun outputGlobalStatus(status: String) {
        synchronized(this) {
            print("\u001b[1A\r\u001b[K$status\u001b[1B")
            System.out.flush()
        }
    }

    fun waitForCompletion() {
        executor.shutdown()
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
