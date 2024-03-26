package tga.transformations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import tga.components.math_surface.MathDrawScope
import tga.components.math_surface.drawFun
import tga.components.math_surface.drawVerticalLine
import tga.components.math_surface.mathCanvas
import tga.planets.zoom.Zoomers

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = DpSize((2*1280).dp, 1400.dp),
    )

    fun hotKeysHandler(keyEvent: KeyEvent): Boolean {
        return when (keyEvent.key) {
            Key.Escape -> { exitApplication(); false}
            else -> true
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        onKeyEvent = ::hotKeysHandler,
        state = state
    ) { app() }


}

@Composable fun app() = MaterialTheme(
    colors = darkColors()
) {
    Row {
        Column(modifier = Modifier.width(250.dp).fillMaxHeight()) {}
        Column(modifier = Modifier.fillMaxHeight().fillMaxWidth().background(color = Color.DarkGray)) {
            mathCanvas {
                drawFunctions()
            }
        }
    }
}

fun MathDrawScope.drawFunctions() {

    zoomer = Zoomers.sqrt.zoomer

    val start = -1100f
    val delta =   200f

    lineColor = Color.Green
    var xLine = start; repeat(12) { drawVerticalLine{ x = xLine }; xLine += delta }

    lineColor = Color.Yellow
    var yLine = start; repeat(12) { drawFun{ y = yLine  }; yLine += delta }


//    lineColor = Color.Yellow
//    var yLine = -1100f; repeat(12) { drawFun{ y = x +yLine  }; yLine += 200 }
//
//    lineColor = Color.Green
//    yLine = -1100f; repeat(12) { drawFun{ y = -x +yLine  }; yLine += 200 }

}


