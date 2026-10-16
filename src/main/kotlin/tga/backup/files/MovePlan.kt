package tga.backup.files

/**
 * A single move/rename inside the destination. Paths are relative to the destination root, '/'-separated.
 */
data class MoveOp(
    val from: String,
    val to: String,
    val isDirectory: Boolean,
    val size: Long,
)

class MoveFailedException(val op: MoveOp, cause: Throwable) :
    RuntimeException("Move failed: ${op.from}  --->  ${op.to}: ${cause.message}", cause)

/**
 * Builds the ordered list of moves for a sync plan: folders first (shallow targets before deep ones), then files.
 */
fun SyncActionCases.toMoveOps(): List<MoveOp> {
    fun ops(items: Set<Pair<FileInfo, String>>) = items
        .sortedWith(compareBy({ it.second.count { c -> c == '/' } }, { it.second }))
        .map { (src, dst) -> MoveOp(from = src.name, to = dst, isDirectory = src.isDirectory, size = src.size) }

    return ops(toRenameFolders) + ops(toMoveFolders) + ops(toRenameFiles) + ops(toMoveFiles)
}

/**
 * Splits moves into waves that can be executed one after another, each wave in parallel.
 *
 * Two moves conflict when a path of one (source or target) is equal to, an ancestor of, or a descendant of
 * a path of the other. A conflicting move is placed in a later wave than the earlier move it conflicts with,
 * so the input order defines priority (e.g. a folder move always completes before a file moves into it).
 * Moves within the same wave touch unrelated parts of the tree.
 */
fun buildMoveWaves(ops: List<MoveOp>): List<List<MoveOp>> {
    val pathWave = HashMap<String, Int>()     // latest wave of a move touching exactly this path
    val subtreeWave = HashMap<String, Int>()  // latest wave of a move touching a path strictly below this one
    val waves = mutableListOf<MutableList<MoveOp>>()

    for (op in ops) {
        val paths = listOf(op.from, op.to)

        var wave = 0
        for (path in paths) {
            pathWave[path]?.let { wave = maxOf(wave, it + 1) }
            subtreeWave[path]?.let { wave = maxOf(wave, it + 1) }
            for (ancestor in ancestorsOf(path)) {
                pathWave[ancestor]?.let { wave = maxOf(wave, it + 1) }
            }
        }

        for (path in paths) {
            pathWave.merge(path, wave, ::maxOf)
            for (ancestor in ancestorsOf(path)) subtreeWave.merge(ancestor, wave, ::maxOf)
        }

        while (waves.size <= wave) waves += mutableListOf<MoveOp>()
        waves[wave] += op
    }

    return waves
}

/**
 * Removes deletions that would destroy data left behind by failed moves:
 * the failed move's source itself and every folder containing it.
 */
fun Set<FileInfo>.withoutFailedMoveSources(failedMoves: Collection<MoveOp>): Set<FileInfo> {
    if (failedMoves.isEmpty()) return this
    val protectedPaths = failedMoves.flatMap { listOf(it.from) + ancestorsOf(it.from) }.toSet()
    return filterTo(mutableSetOf()) { it.name !in protectedPaths }
}

internal fun ancestorsOf(path: String): List<String> {
    val result = mutableListOf<String>()
    var current = path.substringBeforeLast('/', "")
    while (current.isNotEmpty()) {
        result += current
        current = current.substringBeforeLast('/', "")
    }
    return result
}
