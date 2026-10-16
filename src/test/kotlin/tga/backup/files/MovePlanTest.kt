package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MovePlanTest {

    private fun file(from: String, to: String) = MoveOp(from, to, isDirectory = false, size = 1)
    private fun folder(from: String, to: String) = MoveOp(from, to, isDirectory = true, size = 10)

    private fun waveOf(waves: List<List<MoveOp>>, op: MoveOp) = waves.indexOfFirst { op in it }

    @Test
    fun `independent moves go to a single wave`() {
        val ops = listOf(
            file("a/1.jpg", "b/1.jpg"),
            file("a/2.jpg", "b/2.jpg"),
            folder("old/x", "new/x"),
            folder("old/y", "new/y"),
        )

        val waves = buildMoveWaves(ops)

        assertThat(waves).hasSize(1)
        assertThat(waves[0]).containsExactlyElementsOf(ops)
    }

    @Test
    fun `file moving into a moved folder waits for the folder move`() {
        val folderMove = folder("old/x", "new/x")
        val fileMove = file("other/f.jpg", "new/x/f.jpg")

        val waves = buildMoveWaves(listOf(folderMove, fileMove))

        assertThat(waveOf(waves, folderMove)).isLessThan(waveOf(waves, fileMove))
    }

    @Test
    fun `file moving out of a moved folder waits for the folder move`() {
        val folderMove = folder("old/x", "new/x")
        val fileMove = file("old/x/f.jpg", "somewhere/f.jpg")

        val waves = buildMoveWaves(listOf(folderMove, fileMove))

        assertThat(waveOf(waves, folderMove)).isLessThan(waveOf(waves, fileMove))
    }

    @Test
    fun `chained renames are serialized in input order`() {
        val first = file("b.jpg", "c.jpg")
        val second = file("a.jpg", "b.jpg")

        val waves = buildMoveWaves(listOf(first, second))

        assertThat(waves).containsExactly(listOf(first), listOf(second))
    }

    @Test
    fun `nested folder moves are ordered parent first`() {
        val parent = folder("_new/_arhive/2012", "_arhive/2012")
        val child = folder("_new/_arhive/2012/x", "_arhive/2012/x")

        val waves = buildMoveWaves(listOf(parent, child))

        assertThat(waveOf(waves, parent)).isLessThan(waveOf(waves, child))
    }

    @Test
    fun `sync plan puts folders before files and shallow before deep`() {
        val actions = SyncActionCases(
            toAddFiles = emptySet(),
            toDeleteFiles = emptySet(),
            toOverrideFiles = emptySet(),
            toMoveFiles = setOf(FileInfo("x/f.jpg", false, 1) to "y/f.jpg"),
            toRenameFiles = setOf(FileInfo("x/g.jpg", false, 1) to "x/h.jpg"),
            toMoveFolders = setOf(
                FileInfo("a/b/c", true, 10) to "d/e/c",
                FileInfo("a/b", true, 10) to "d/b",
            ),
            toRenameFolders = setOf(FileInfo("r", true, 10) to "s"),
        )

        assertThat(actions.toMoveOps().map { it.to }).containsExactly("s", "d/b", "d/e/c", "x/h.jpg", "y/f.jpg")
    }

    @Test
    fun `deletions holding sources of failed moves are skipped`() {
        val toDelete = setOf(
            FileInfo("_new", true, 10),
            FileInfo("_new/_arhive", true, 10),
            FileInfo("_new/_arhive/2012", true, 10),
            FileInfo("_new/_arhive/2013", true, 10),
            FileInfo("_new/dup.jpg", false, 1),
        )
        val failed = listOf(folder("_new/_arhive/2012/x", "_arhive/2012/x"))

        val result = toDelete.withoutFailedMoveSources(failed)

        assertThat(result.map { it.name }).containsExactlyInAnyOrder("_new/_arhive/2013", "_new/dup.jpg")
    }

    @Test
    fun `no failed moves keeps all deletions`() {
        val toDelete = setOf(FileInfo("a", true, 10), FileInfo("a/b.jpg", false, 1))

        assertThat(toDelete.withoutFailedMoveSources(emptyList())).isEqualTo(toDelete)
    }
}
