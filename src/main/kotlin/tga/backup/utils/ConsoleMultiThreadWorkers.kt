package tga.backup.utils

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ConsoleMultiThreadWorkers(private val threadCount: Int) {

    private val queue: BlockingQueue<RunnableWithStringStatus> = LinkedBlockingQueue()
    private val workers = mutableListOf<Worker>()
    private val isRunning = AtomicBoolean(true)
    private val activeTasks = AtomicInteger(0)

    init {
        repeat(threadCount) { println() }
        repeat(threadCount) { index ->
            val worker = Worker(index)
            workers.add(worker)
            worker.start()
        }
    }

    fun submit(task: RunnableWithStringStatus) {
        queue.put(task)
    }

    fun submit(task: ( (String) -> Unit ) -> Unit) {
        submit(RunnableWithStringStatus { task(it) })
    }

    @Synchronized
    private fun outputStatus(workerIndex: Int, status: String) {
        val linesToMoveUp = threadCount - workerIndex
        // ESC [ <n> A - Move cursor up n lines
        // ESC [ G - Move cursor to the beginning of the line
        // ESC [ K - Erase to end of line
        print("\u001b[${linesToMoveUp}A\r\u001b[K$status\u001b[${linesToMoveUp}B")
        System.out.flush()
    }

    fun waitForCompletion() {
        while (queue.isNotEmpty() || activeTasks.get() > 0) {
            Thread.sleep(100)
        }
    }

    fun shutdown() {
        isRunning.set(false)
        workers.forEach { it.interrupt() }
        workers.forEach { it.join() }
    }

    private inner class Worker(val index: Int) : Thread("ConsoleWorker-$index") {
        override fun run() {
            while (isRunning.get() || queue.isNotEmpty()) {
                val task = try {
                    queue.poll(100, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                }

                if (task != null) {
                    activeTasks.incrementAndGet()
                    try {
                        task.run { status ->
                            outputStatus(index, status)
                        }
                    } catch (e: Exception) {
                        outputStatus(index, "Error in worker $index: ${e.message}")
                    } finally {
                        activeTasks.decrementAndGet()
                    }
                }
            }
        }
    }
}
