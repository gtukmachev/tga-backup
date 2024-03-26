package tga.planets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import tga.planets.physics_state.*
import tga.planets.visual_state.VisualBody
import tga.planets.visual_state.VisualState
import tga.planets.visual_state.asVisualState
import tga.planets.visual_state.toOffset
import tga.planets.zoom.Zoomers
import kotlin.math.abs
import kotlin.math.log2


val zoomType =  Zoomers.sqrt.zoomer

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = DpSize((2*1280).dp, 1400.dp),
    )

    fun hotKeysHandler(keyEvent: KeyEvent): Boolean {
        return when (keyEvent.key) {
            Key.Escape -> { exitApplication(); false}
            Key.Spacebar -> { isSimulationActive = !isSimulationActive; false }
            else -> true
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        onKeyEvent = ::hotKeysHandler,
        state = state
    ) { app() }


}

var isSimulationActive: Boolean = false
const val zoomRange = 300f

@Composable fun app() = MaterialTheme(
    colors = darkColors()
) {

    var dt:                          Long    by remember { mutableStateOf(60*30 ) }
    var simulationStepsPerSession:   Int     by remember { mutableStateOf(120   ) }
    var simulationSessionsPerSecond: Int     by remember { mutableStateOf(1000  ) }
    var visualZoom:                  Float   by remember { mutableStateOf( 1f   ) }
    var isLogCoordinatesOn:          Boolean by remember { mutableStateOf( false) }
    var sunMass:                     Float   by remember { mutableStateOf( 1f   ) }

    var planetsVisualState: VisualState by remember {
        mutableStateOf( spaceObjects.asVisualState(zoom = zoomType.initialScreenZoom, zoomRadius = zoomRadius) )
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (isSimulationActive) {
                repeat(simulationStepsPerSession) { simulationStep(dtSeconds = dt) }
                updateVisualState(planetsVisualState) { planetsVisualState = it }
            }
            delay((1000 / simulationSessionsPerSecond).toLong() )
        }
    }

    Row {
        Column(modifier = Modifier.width(250.dp).fillMaxHeight()) {
            settingsPanel(
                dt,                          { dt = it },
                simulationStepsPerSession,   { simulationStepsPerSession = it },
                simulationSessionsPerSecond, { simulationSessionsPerSecond = it },
                visualZoom,                  { visualZoom = it },
                isLogCoordinatesOn,          { isLogCoordinatesOn = it },
                sunMass,                     { sunMass = it },
            )
        }
        Column(
            modifier = Modifier
                //.width(500.dp)
                .fillMaxHeight()
                .fillMaxWidth()
                .background(color = Color.DarkGray)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { paint(planetsVisualState, visualZoom) }
        }
    }
}

val log2Zoom = pluton.p.x / 10000000000000000
val zoomRadius = earth.r / 15.0

fun updateVisualState(planetsVisualState: VisualState, stateChangeFunction: (VisualState) -> Unit) {
    var updated = false
    val newVisualBodyStates = mutableStateListOf<VisualBody>()
    for (i in planetsVisualState.bodies.indices) {
        val currState = planetsVisualState.bodies[i]
        val newState = currState.moveTo( spaceObjects[i].p.toOffset(zoomType.initialScreenZoom) )
        if (newState !== currState) updated = true
        newVisualBodyStates += newState
    }

    if (updated) {
        stateChangeFunction(VisualState(newVisualBodyStates))
    }
}

private fun Float.logScr() = when {
    this == 0f -> 0f
    this >0 -> log2(this*1000000+1)
    else -> -log2(abs(this)*1000000+1)
}

fun DrawScope.paint(planetsVisualState: VisualState, visualZoom: Float) {
    val tailStrokeStyle = Stroke(
        width = 1f * visualZoom
    )

    drawCoordinates()
    
    scale(scaleX = 1f/visualZoom, scaleY = -1f/visualZoom) {
        translate(left = this.size.width/2, top = this.size.height/2) {

            //drawCircle(Color.Yellow, radius = 30f, Offset.Zero)
            for (i in planetsVisualState.bodies.indices) {
                val planet = planetsVisualState.bodies[i]
                val color = spaceObjects[i].color
                var zoomedOffset = zoomType.transform(planet.position)
                drawCircle(color, 15f*visualZoom, zoomedOffset)

                val planetTail = Path()
                planetTail.moveTo(zoomedOffset.x, zoomedOffset.y)
                for (p in planet.tail) {
                    zoomedOffset = zoomType.transform(p)
                    planetTail.lineTo(zoomedOffset.x, zoomedOffset.y)
                }

                this.drawPath(path = planetTail, color = color, style = tailStrokeStyle)
            }
        }
    }


}

fun DrawScope.drawCoordinates() {
    val c = with(size / 2F) { Offset(width, height) }
    drawLine(color = Color.Gray, Offset(0f, c.y), Offset(size.width, c.y))
    drawLine(color = Color.Gray, Offset(c.x, 0f), Offset(c.x, size.height))
}

