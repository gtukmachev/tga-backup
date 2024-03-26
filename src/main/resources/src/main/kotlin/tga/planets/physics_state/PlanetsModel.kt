package tga.planets.physics_state

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


data class SpaceObject(
    val i: Int,
    var m: Double,      // kg
    val r: Double,      // km
    var p: Vector,      // m, m
    //var prevVisualPosition: Vector = p,
    var speed: Vector,  // m/s
    var color: Color,
    var rK: Double = 1.0
)

val sunInitialMass = 1.98847e30
val sun     = SpaceObject(0, m= sunInitialMass,   r=696340.0, p= v(0,0), speed= v(0,0), Color(0xFFF9FF88), rK = 2e-2)


val mercury = SpaceObject(1, m=   0.32868e24, r= 2439.0, p= v( 57.911014e9, 0), speed= v(0.0, 47.870e3    ), Color(0xFFFFC797) )
val venera  = SpaceObject(2, m=   4.81068e24, r= 6052.0, p= v(108.307820e9, 0), speed= v(0.0, 35.020e3    ), Color(0xFFACFFD4) )
val earth   = SpaceObject(3, m=   5.97600e24, r= 6378.0, p= v(149.597870e9, 0), speed= v(0.0, 29.765e3 ), Color(0xFF17CBFF) )
val mars    = SpaceObject(4, m=   0.63345e24, r= 3397.0, p= v(223.400000e9, 0), speed= v(0.0, 24.130e3 ), Color(0xFFFF9334) )
val jupiter = SpaceObject(5, m=1876.64328e24, r=71490.0, p= v(778.740000e9, 0), speed= v(0.0, 13.070e3 ), Color.Gray )
val saturn  = SpaceObject(6, m= 561.80376e24, r=60270.0, p= v( (13495e9 + 15040e9)/2, 0), speed= v(0.0,  9.670e3 ), Color.Blue )
val uran    = SpaceObject(7, m=  86.05440e24, r=25560.0, p= v( (27356e9 + 30064e9)/2, 0), speed= v(0.0,  6.840e3 ), Color.Red )
val neptun  = SpaceObject(8, m= 101.59200e24, r=24760.0, p= v( (44596e9 + 45369e9)/2, 0), speed= v(0.0,  5.480e3 ), Color.Yellow )
val pluton  = SpaceObject(9, m=   0.01195e24, r= 1151.0, p= v( (44368e9 + 73759e9)/2, 0), speed= v(0.0,  4.750e3 ), Color.Cyan )

val spaceObjects = arrayOf(
    sun,
    mercury,
    venera,
    earth,
    mars,
    jupiter,
    saturn,
    uran,
    neptun,
    pluton,
)
val n = spaceObjects.size

val obj = spaceObjects

//val aK = 0.015
const val Gk = 1

// index of mass multiplying
const val G = 6.67259e-11 * Gk
val  GM: Array<Double> = Array(n){ i -> G * obj[i].m }
var tSeconds: Long = 0L

suspend fun simulationStep(dtSeconds: Long) {
    coroutineScope {
        val dt = dtSeconds.toDouble()
        for (i in 1 until spaceObjects.size) {
            launch {
                val planet = spaceObjects[i]

                val distanceToSunPow2  = planet.p.lenPow2
                val a = (GM[sun.i] / distanceToSunPow2)

                val aDirection = -planet.p.norm()
                val aVector = aDirection * a
                val aVectorPow2Vector = aDirection * (a * a)

                planet.speed += (aVector * dt)
                val dp = (planet.speed * dt) + (aVectorPow2Vector * dt)/2.0
                planet.p += dp
            }
        }
        tSeconds += dtSeconds
    }
}