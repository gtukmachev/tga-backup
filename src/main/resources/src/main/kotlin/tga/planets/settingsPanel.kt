package tga.planets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tga.planets.physics_state.G
import tga.planets.physics_state.GM
import tga.planets.physics_state.sun
import tga.planets.physics_state.sunInitialMass

@Composable
fun settingsPanel(
    dt:                          Long,    dtChange                          : (Long) -> Unit,
    simulationStepsPerSession:   Int,     simulationStepsPerSessionChange   : (Int) -> Unit,
    simulationSessionsPerSecond: Int,     simulationSessionsPerSecondChange : (Int) -> Unit,
    visualZoom:                  Float,   visualZoomChange                  : (Float) -> Unit,
    isLogCoordinatesOn:          Boolean, isLogCoordinatesOnChange          : (Boolean) -> Unit,
    sunMass:                     Float,   sunMassChange                     : (Float) -> Unit,
){
    propertyBorder { Button(modifier = Modifier.fillMaxWidth(), onClick = { isSimulationActive = !isSimulationActive }) { Text( "run/pause" ) } }
    propertySlider("Zoom", value = visualZoom, valueRange = 0.1f..300f, onValueChange = visualZoomChange)
    propertySlider("Sun mass k", value = sunMass, valueRange = 0.1f..10f, onValueChange = {
        sunMassChange(it)
        sun.m = sunInitialMass * it
        GM[sun.i] = G * sun.m

    })
    propertySlider("precision (dt)", strValue = dt.toReadableTime(), value = dt.toFloat(), valueRange = 1f..3600f*24*30, steps = 3600, onValueChange = { dtChange(it.toLong()) })
    propertySlider("dt batch", value = simulationStepsPerSession.toFloat(), valueRange = 1f..500f, onValueChange = { simulationStepsPerSessionChange(it.toInt()) })
}

private fun Long.toReadableTime(): String {
    if (this == 0L) return "0"

    val seconds = this % 60
    var rest = this / 60

    val minutes = if (rest  == 0L) 0 else rest % 60
    rest /= 60

    val hours = if (rest  == 0L) 0 else rest % 24
    rest /= 24

    val days = if (rest  == 0L) 0 else rest % 7
    rest /= 7

    val weeks = rest

    val sb = StringBuilder()

    var l = false
    if (weeks > 0) { sb.append("${weeks}w"); l = true }
    if (days  > 0) { if (l) { sb.append(" ") }; sb.append("${days}d"); l = true }
    if (hours > 0) { if (l) { sb.append(" ") }; sb.append("${hours}h"); l = true }
    if (minutes > 0) { if (l) { sb.append(" ") }; sb.append("${minutes}m"); l = true }
    if (seconds > 0) { if (l) { sb.append(" ") }; sb.append("${seconds}s") }

    return sb.toString()
}

@Composable fun propertySlider(
    label: String,
    strValue: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    propertyBorder {
        Text(text = "$label: ${strValue ?: value}")
        Slider(value = value, valueRange = valueRange, onValueChange = onValueChange, steps = steps)
    }
}

@Composable fun propertyBorder(innerContent: @Composable () -> Unit) {
    Column (modifier = Modifier.padding(8.dp)) {
        innerContent()
        Divider(modifier = Modifier.fillMaxWidth(), color = Color.Gray)
    }
}
