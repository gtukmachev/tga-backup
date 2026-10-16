package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tga.backup.utils.ConsoleMultiThreadWorkers
import tga.backup.utils.WorkerPrinter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class MoveFilesParallelTest {

    private class TestableFileOps(
        private val log: ConcurrentLinkedQueue<Pair<String, Int>>,
        private val failOnPaths: Set<String> = emptySet(),
        private val delayMs: Long = 5,
    ) : FileOps(filesSeparator = "/") {
        private val sequence = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        private val running = AtomicInteger(0)

        override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> = emptySet()
        override fun mkDirs(dirPath: String) {}
        override fun copyFile(action: String, from: String, to: String, srcFileOps: FileOps, printer: WorkerPrinter, syncStatus: SyncStatus) {}
        override fun deleteFileOrFolder(path: String) {}
        override fun close() {}

        override fun moveFileOrFolder(fromPath: String, toPath: String) {
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                if (fromPath in failOnPaths) throw RuntimeException("Simulated failure: $fromPath")
                if (delayMs > 0) Thread.sleep(delayMs)
                log.add(fromPath to sequence.getAndIncrement())
            } finally {
                running.decrementAndGet()
            }
        }
    }

    @Test
    fun `independent moves run in parallel`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, delayMs = 50)
        val moves = (1..8).map { MoveOp("a/$it.jpg", "b/$it.jpg", isDirectory = false, size = 1) }

        val results = ops.moveFiles(moves, "/dst", dryRun = false, workers = ConsoleMultiThreadWorkers(4))

        assertThat(results).hasSize(8).allMatch { it.isSuccess }
        assertThat(log.map { it.first }).containsExactlyInAnyOrderElementsOf(moves.map { "/dst/${it.from}" })
        assertThat(ops.maxConcurrent.get()).isGreaterThan(1)
    }

    @Test
    fun `dependent move runs after the move it depends on`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, delayMs = 20)
        val moves = listOf(
            MoveOp("old/x", "new/x", isDirectory = true, size = 10),
            MoveOp("other/f.jpg", "new/x/f.jpg", isDirectory = false, size = 1),
            MoveOp("other/g.jpg", "elsewhere/g.jpg", isDirectory = false, size = 1),
        )

        val results = ops.moveFiles(moves, "/dst", dryRun = false, workers = ConsoleMultiThreadWorkers(3))

        assertThat(results).allMatch { it.isSuccess }
        val order = log.toList().toMap()
        assertThat(order["/dst/old/x"]).isLessThan(order["/dst/other/f.jpg"])
    }

    @Test
    fun `failure is reported with the failed move and does not stop others`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, failOnPaths = setOf("/dst/a/bad.jpg"))
        val bad = MoveOp("a/bad.jpg", "b/bad.jpg", isDirectory = false, size = 1)
        val moves = listOf(MoveOp("a/good.jpg", "b/good.jpg", false, 1), bad)

        val results = ops.moveFiles(moves, "/dst", dryRun = false, workers = ConsoleMultiThreadWorkers(2))

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertThat(failure).isInstanceOf(MoveFailedException::class.java)
        assertThat((failure as MoveFailedException).op).isEqualTo(bad)
    }

    @Test
    fun `dry run does not call moveFileOrFolder`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log)

        val results = ops.moveFiles(listOf(MoveOp("a", "b", true, 10)), "/dst", dryRun = true, workers = ConsoleMultiThreadWorkers(2))

        assertThat(results).hasSize(1).allMatch { it.isSuccess }
        assertThat(log).isEmpty()
    }
}
