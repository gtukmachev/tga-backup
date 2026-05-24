package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class DeleteFilesParallelTest {

    private class TestableFileOps(
        private val deletionLog: ConcurrentLinkedQueue<Pair<String, Int>>,
        private val sequenceCounter: AtomicInteger = AtomicInteger(0),
        private val failOnPaths: Set<String> = emptySet(),
        private val delayMs: Long = 5
    ) : FileOps(filesSeparator = "/") {

        override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> = emptySet()
        override fun mkDirs(dirPath: String) {}
        override fun copyFile(action: String, from: String, to: String, srcFileOps: FileOps, updateStatus: (String) -> Unit, syncStatus: SyncStatus) {}
        override fun moveFileOrFolder(fromPath: String, toPath: String) {}
        override fun close() {}

        override fun deleteFileOrFolder(path: String) {
            if (path in failOnPaths) throw RuntimeException("Simulated failure: $path")
            if (delayMs > 0) Thread.sleep(delayMs)
            deletionLog.add(path to sequenceCounter.getAndIncrement())
        }
    }

    @Test
    fun `all files are deleted`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log)
        val workers = ConsoleMultiThreadWorkers<Unit>(3)

        val files = setOf(
            FileInfo("a.txt", false, 100),
            FileInfo("b.txt", false, 200),
            FileInfo("dir1/c.txt", false, 300),
            FileInfo("dir1/dir2/d.txt", false, 400),
        )

        val results = ops.deleteFiles(files, "/root", dryRun = false, workers = workers)

        assertThat(results).hasSize(4)
        assertThat(results).allMatch { it.isSuccess }
        assertThat(log.map { it.first }).containsExactlyInAnyOrder(
            "/root/a.txt", "/root/b.txt", "/root/dir1/c.txt", "/root/dir1/dir2/d.txt"
        )
    }

    @Test
    fun `folders are deleted deepest first`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, delayMs = 10)
        val workers = ConsoleMultiThreadWorkers<Unit>(2)

        val files = setOf(
            FileInfo("a", true, 10),
            FileInfo("a/b", true, 10),
            FileInfo("a/b/c", true, 10),
            FileInfo("x/y", true, 10),
            FileInfo("x", true, 10),
        )

        val results = ops.deleteFiles(files, "/root", dryRun = false, workers = workers)

        assertThat(results).hasSize(5)
        assertThat(results).allMatch { it.isSuccess }

        val deletionOrder = log.toList().associate { it.first to it.second }

        // a/b/c must be deleted before a/b, which must be before a
        assertThat(deletionOrder["/root/a/b/c"]).isLessThan(deletionOrder["/root/a/b"])
        assertThat(deletionOrder["/root/a/b"]).isLessThan(deletionOrder["/root/a"])
        // x/y must be deleted before x
        assertThat(deletionOrder["/root/x/y"]).isLessThan(deletionOrder["/root/x"])
    }

    @Test
    fun `mixed files and folders - files deleted before folders`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, delayMs = 5)
        val workers = ConsoleMultiThreadWorkers<Unit>(3)

        val items = setOf(
            FileInfo("dir1/file1.txt", false, 500),
            FileInfo("dir1/dir2/file2.txt", false, 300),
            FileInfo("dir1", true, 10),
            FileInfo("dir1/dir2", true, 10),
        )

        val results = ops.deleteFiles(items, "/dst", dryRun = false, workers = workers)

        assertThat(results).hasSize(4)
        assertThat(results).allMatch { it.isSuccess }

        val deletionOrder = log.toList().associate { it.first to it.second }

        // files are deleted in phase 1 (before folders)
        val maxFileSeq = maxOf(
            deletionOrder["/dst/dir1/file1.txt"]!!,
            deletionOrder["/dst/dir1/dir2/file2.txt"]!!
        )
        val minFolderSeq = minOf(
            deletionOrder["/dst/dir1"]!!,
            deletionOrder["/dst/dir1/dir2"]!!
        )
        assertThat(maxFileSeq).isLessThan(minFolderSeq)

        // deeper folder deleted before shallower
        assertThat(deletionOrder["/dst/dir1/dir2"]).isLessThan(deletionOrder["/dst/dir1"])
    }

    @Test
    fun `empty input returns empty results`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log)
        val workers = ConsoleMultiThreadWorkers<Unit>(2)

        val results = ops.deleteFiles(emptySet(), "/root", dryRun = false, workers = workers)

        assertThat(results).isEmpty()
        assertThat(log).isEmpty()
    }

    @Test
    fun `dry run does not call deleteFileOrFolder`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log)
        val workers = ConsoleMultiThreadWorkers<Unit>(2)

        val files = setOf(
            FileInfo("file.txt", false, 100),
            FileInfo("dir", true, 10),
        )

        val results = ops.deleteFiles(files, "/root", dryRun = true, workers = workers)

        assertThat(results).hasSize(2)
        assertThat(results).allMatch { it.isSuccess }
        assertThat(log).isEmpty()
    }

    @Test
    fun `error in one deletion does not stop others`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, failOnPaths = setOf("/root/bad.txt"))
        val workers = ConsoleMultiThreadWorkers<Unit>(2)

        val files = setOf(
            FileInfo("good1.txt", false, 100),
            FileInfo("bad.txt", false, 200),
            FileInfo("good2.txt", false, 300),
        )

        val results = ops.deleteFiles(files, "/root", dryRun = false, workers = workers)

        assertThat(results).hasSize(3)
        val failures = results.filter { it.isFailure }
        val successes = results.filter { it.isSuccess }
        assertThat(failures).hasSize(1)
        assertThat(successes).hasSize(2)
        assertThat(failures[0].exceptionOrNull()?.message).contains("bad.txt")
    }

    @Test
    fun `noDeletion flag returns empty results`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log)
        val workers = ConsoleMultiThreadWorkers<Unit>(2)

        val files = setOf(FileInfo("file.txt", false, 100))

        val results = ops.deleteFiles(files, "/root", dryRun = false, noDeletion = true, workers = workers)

        assertThat(results).isEmpty()
        assertThat(log).isEmpty()
    }

    @Test
    fun `only folders at different depths maintains ordering`() {
        val log = ConcurrentLinkedQueue<Pair<String, Int>>()
        val ops = TestableFileOps(log, delayMs = 10)
        val workers = ConsoleMultiThreadWorkers<Unit>(4)

        val folders = setOf(
            FileInfo("a", true, 10),
            FileInfo("a/b", true, 10),
            FileInfo("a/b/c", true, 10),
            FileInfo("a/b/c/d", true, 10),
            FileInfo("x", true, 10),
            FileInfo("x/y", true, 10),
            FileInfo("x/y/z", true, 10),
        )

        val results = ops.deleteFiles(folders, "/r", dryRun = false, workers = workers)

        assertThat(results).hasSize(7)
        assertThat(results).allMatch { it.isSuccess }

        val order = log.toList().associate { it.first to it.second }

        // Verify depth ordering for chain a > a/b > a/b/c > a/b/c/d
        assertThat(order["/r/a/b/c/d"]).isLessThan(order["/r/a/b/c"])
        assertThat(order["/r/a/b/c"]).isLessThan(order["/r/a/b"])
        assertThat(order["/r/a/b"]).isLessThan(order["/r/a"])

        // Verify depth ordering for chain x > x/y > x/y/z
        assertThat(order["/r/x/y/z"]).isLessThan(order["/r/x/y"])
        assertThat(order["/r/x/y"]).isLessThan(order["/r/x"])
    }
}
