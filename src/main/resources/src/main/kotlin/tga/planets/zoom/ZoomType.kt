package tga.planets.zoom

import androidx.compose.ui.geometry.Offset
import tga.planets.physics_state.earth
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

private val startPlanet = earth
enum class Zoomers(val zoomer: Zoomer) {
    linear(LinearZoomer),
    simpleSqrt(SimpleSqrtZoomer),
    sqrt(SqrtZoomer),
    sqrtSqrt(SqrtSqrtZoomer),
    log2(Log2Zoomer)
}

interface Zoomer {
    val initialScreenZoom: Double
    fun transform(x: Float): Float
    fun transform(p: Offset) = transformSurface(p)

    fun transformSurface(p: Offset): Offset {
        val distance = p.getDistance()
        if (distance == 0f) return p
        val newDistance = SqrtSqrtZoomer.transform(distance)
        val k = newDistance / distance
        val newOffset = p*k
        return newOffset
    }
}

object LinearZoomer : Zoomer {
    override val initialScreenZoom = startPlanet.p.x / 800
    override fun transform(x: Float) = x
    override fun transform(p: Offset) = p
    override fun transformSurface(p: Offset) = p
}




object SimpleSqrtZoomer : Zoomer {
    override val initialScreenZoom = startPlanet.p.x / (800*800)
    override fun transform(x: Float) = fullSqrt(x)
    override fun transform(p: Offset) = Offset(transform(p.x), transform(p.y))
}

object SqrtZoomer : Zoomer {
    override val initialScreenZoom = startPlanet.p.x / (800*800)
    override fun transform(x: Float) = fullSqrt(x)
}

object SqrtSqrtZoomer : Zoomer {
    override val initialScreenZoom = startPlanet.p.x / (800*800)
    override fun transform(x: Float) = safe(x){ sqrt(sqrt(it)) }
}

object Log2Zoomer : Zoomer {
    override val initialScreenZoom = startPlanet.p.x / (800*800)
    override fun transform(x: Float) = safe(x){ log2(it) }
}

fun fullSqrt(x: Float): Float = safe(x){ sqrt(it) }

fun safe(x: Float, f: (Float) -> Float): Float = when {
    x == 0f -> x
    x  > 0f -> f(x * 100_000_000)
    else    -> -f(abs(x))
}