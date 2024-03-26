package tga.components.math_surface

import kotlin.math.sqrt

/**
 * Mutable data class.
 * Provides a way to describe a function using pure mathematical syntax, like: `y = f(x)`.
 *
 * Example:
 * ```kotlin
 *    mathCanvas{
 *       drawFun{ y = sin(x) }
 *    }
 * ```
 */
data class Point(var x: Float = 0f, var y: Float = 0f) {
    operator fun minus(p: Point) = Point(p.x - x, p.y - y)
    fun len(): Float = sqrt(x*x + y*y)
}

