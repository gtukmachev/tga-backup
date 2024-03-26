package tga.components.math_surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

fun MathDrawScope.drawFun(
    clr:    Color = this.lineColor,
    dx:     Float = this.step,
    radius: Float = this.thickness,
    f: Point.() -> Unit
) {
    val halfSize = with(size / 2F) { Offset(width.toLong().toFloat(), height.toLong().toFloat()) }
    val point = Point(-halfSize.x, 0f)

    while (point.x < halfSize.x) {
        point.f()
        val screenPoint = zoom(point.x, point.y)
        drawCircle(clr, radius, screenPoint)
        point.x += dx
    }
}



fun MathDrawScope.drawVerticalLine(
    clr:    Color = this.lineColor,
    dy:     Float = this.step,
    radius: Float = this.thickness,
    f: Point.() -> Unit
) {
    val halfSize = with(size / 2F) { Offset(width.toLong().toFloat(), height.toLong().toFloat()) }
    val point = Point()
    point.f()
    val x = point.x
    var y = -halfSize.y

    while (y < halfSize.y) {
        val screenPoint = zoom(x, y)
        drawCircle(clr, radius, screenPoint)
        y += dy
    }
}