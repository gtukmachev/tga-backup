package tga.planets.visual_state

import androidx.compose.ui.geometry.Offset
import tga.planets.physics_state.SpaceObject
import tga.planets.physics_state.Vector
import kotlin.math.max


private const val moveToMinDistance = 2f * 2f
private const val tailLength = 10*2048


data class VisualState(
    val bodies: List<VisualBody>
)


data class VisualBody(
    val position: Offset,
    val radius: Float,
    val tail:  ArrayDeque<Offset>
) {
    fun moveTo(newPosition: Offset): VisualBody {
        val distanceSquared = (newPosition - position).getDistanceSquared()
        if (distanceSquared < moveToMinDistance) return this
        tail.addFirst(position)
        if (tail.size > tailLength) tail.removeLast()
        return copy(position = newPosition)
    }
}

fun Array<SpaceObject>.asVisualState(zoom: Double, zoomRadius: Double) = VisualState(bodies = map { it.asVisualBody(zoom, zoomRadius) })

private fun SpaceObject.asVisualBody(zoom: Double, zoomRadius: Double) = VisualBody(
    position = p.toOffset(zoom),
    radius = max((r*rK/zoomRadius).toFloat(), 1f),
    tail = ArrayDeque(tailLength)
)

fun Vector.toOffset(zoom: Double) = Offset( (x/zoom).toFloat(), (y/zoom).toFloat() )
