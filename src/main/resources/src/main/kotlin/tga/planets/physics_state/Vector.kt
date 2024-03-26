package tga.planets.physics_state

import kotlin.math.sqrt

data class Vector(val x: Double, val y: Double) {
    val len: Double get() = sqrt(lenPow2)
    val lenPow2: Double get() = x*x + y*y

    operator fun      times(d: Double) = Vector( x*d  , y*d  )
    operator fun        div(d: Double) = Vector( x/d  , y/d  )
    operator fun      plus (v: Vector) = Vector( x+v.x, y+v.y)
    operator fun      minus(v: Vector) = Vector( x-v.x, y-v.y)
    operator fun unaryMinus()          = Vector(-x    ,-y    )

    fun norm(): Vector {
        val l = len
        return Vector(x/l, y/l)
    }
}

fun v() = Vector(0.0, 0.0)
fun v(x: Double, y: Int   ) = Vector(x           , y.toDouble())
fun v(x: Int,    y: Int   ) = Vector(x.toDouble(), y.toDouble())
fun v(x: Long,   y: Long  ) = Vector(x.toDouble(), y.toDouble())
fun v(x: Double, y: Double) = Vector(x          ,  y           )
fun v(x: Float,  y: Float ) = Vector(x.toDouble(), y.toDouble())
