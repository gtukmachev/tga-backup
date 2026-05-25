package tga.backup.utils

import tga.backup.terminal.Terminal
import tga.backup.terminal.TerminalCapabilities
import tga.backup.terminal.TerminalDetector
import tga.backup.terminal.stripAnsi
import tga.backup.terminal.truncateToWidth
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface WorkerPrinter {
    val width: Int
    fun updateStatus(status: String)
    fun updateGlobalStatus(status: String)
}

fun interface TaskWithStatus<T> {
    fun run(printer: WorkerPrinter): T
}

fun interface DynamicTask<T> {
    fun run(printer: WorkerPrinter, submitChild: (DynamicTask<T>) -> Unit): T
}

class ConsoleMultiThreadWorkers<T>(
    private val threadCount: Int,
    private val capabilities: TerminalCapabilities = TerminalDetector().detect(),
) {

    private val executor = Executors.newFixedThreadPool(threadCount)
    private val activeTasks = AtomicInteger(0)
    private val workerLineMap = ConcurrentHashMap<Long, Int>()
    private val nextLineIndex = AtomicInteger(0)

    private val phaser = Phaser(1)
    private val dynamicError = AtomicReference<Throwable?>(null)

    private val lastPrintTime = ConcurrentHashMap<Int, Long>()
    private val printInterval = ConcurrentHashMap<Int, Long>()
    private val lastPrintedStatus = ConcurrentHashMap<Int, String>()
    @Volatile private var globalLastPrintTime = 0L
    @Volatile private var globalPrintInterval = INITIAL_THROTTLE_MS

    init {
        if (capabilities.isInteractive) {
            repeat(threadCount + 1) { println() }
        }
    }


    fun submit(task: TaskWithStatus<T>): Future<Result<T>> {
        return executor.submit(Callable {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val lineIndex = workerLineMap.getOrPut(threadId) { nextLineIndex.getAndIncrement() % threadCount }

            activeTasks.incrementAndGet()
            var lastStatus = ""
            try {
                val printer = object : WorkerPrinter {
                    override val width: Int get() = Terminal.width
                    override fun updateStatus(status: String) { lastStatus = status; outputStatus(lineIndex, status) }
                    override fun updateGlobalStatus(status: String) { outputGlobalStatus(status) }
                }
                val result = task.run(printer)
                if (lastStatus.isNotEmpty()) outputStatus(lineIndex, lastStatus, force = true)
                Result.success(result)
            } catch (e: Throwable) {
                outputStatus(lineIndex, "Error: ${e.message}", force = true)
                Result.failure(e)
            } finally {
                activeTasks.decrementAndGet()
            }
        })
    }

    fun submitDynamic(task: DynamicTask<T>) {
        phaser.register()
        executor.execute {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val lineIndex = workerLineMap.getOrPut(threadId) { nextLineIndex.getAndIncrement() % threadCount }

            var lastStatus = ""
            try {
                if (dynamicError.get() != null) return@execute
                val printer = object : WorkerPrinter {
                    override val width: Int get() = Terminal.width
                    override fun updateStatus(status: String) { lastStatus = status; outputStatus(lineIndex, status) }
                    override fun updateGlobalStatus(status: String) { outputGlobalStatus(status) }
                }
                task.run(printer) { child -> submitDynamic(child) }
                if (lastStatus.isNotEmpty()) outputStatus(lineIndex, lastStatus, force = true)
            } catch (e: Throwable) {
                dynamicError.compareAndSet(null, e)
                outputStatus(lineIndex, "Error: ${e.message}", force = true)
            } finally {
                phaser.arriveAndDeregister()
            }
        }
    }

    fun submitDynamic(
        task: (
            printer: WorkerPrinter,
            submitChild: (DynamicTask<T>) -> Unit
        ) -> Unit
    ) {
        submitDynamic(DynamicTask { printer, submitChild ->
            task(printer, submitChild)
            @Suppress("UNCHECKED_CAST")
            Unit as T
        })
    }

    fun awaitDynamic() {
        phaser.arriveAndAwaitAdvance()
        dynamicError.get()?.let { throw it }
    }

    @Synchronized
    private fun outputStatus(lineIndex: Int, status: String, force: Boolean = false) {
        if (capabilities.isInteractive) {
            val truncated = truncateToWidth(status, Terminal.width)
            val linesToMoveUp = (threadCount + 1) - lineIndex
            print("[${linesToMoveUp}A\r[K$truncated[${linesToMoveUp}B")
            System.out.flush()
        } else {
            val stripped = stripAnsi(status)
            if (force) {
                if (lastPrintedStatus[lineIndex] != stripped) {
                    println(stripped)
                    lastPrintedStatus[lineIndex] = stripped
                }
            } else if (shouldPrint(lineIndex)) {
                println(stripped)
                lastPrintedStatus[lineIndex] = stripped
            }
        }
    }

    fun outputGlobalStatus(status: String, force: Boolean = false) {
        synchronized(this) {
            if (capabilities.isInteractive) {
                val truncated = truncateToWidth(status, Terminal.width)
                print("[1A\r[K$truncated[1B")
                System.out.flush()
            } else {
                val now = System.currentTimeMillis()
                if (force || now - globalLastPrintTime >= globalPrintInterval) {
                    println(stripAnsi(status))
                    globalLastPrintTime = now
                    globalPrintInterval = (globalPrintInterval * 2).coerceAtMost(MAX_THROTTLE_MS)
                }
            }
        }
    }

    private fun shouldPrint(lineIndex: Int): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastPrintTime[lineIndex]
        val interval = printInterval.getOrDefault(lineIndex, INITIAL_THROTTLE_MS)
        if (lastTime == null || now - lastTime >= interval) {
            lastPrintTime[lineIndex] = now
            printInterval[lineIndex] = (interval * 2).coerceAtMost(MAX_THROTTLE_MS)
            return true
        }
        return false
    }

    fun waitForCompletion() {
        executor.shutdown()
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    companion object {
        private const val INITIAL_THROTTLE_MS = 1000L
        private const val MAX_THROTTLE_MS = 10000L
    }
}
