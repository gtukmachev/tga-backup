package tga.components.math_surface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tga.planets.zoom.Zoomer

@Composable
fun mathCanvas(
    showCoordinateLines: Boolean = true,
    content: MathDrawScope.() -> Unit
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val mathDrawScope = MathDrawScope(this)
        if (showCoordinateLines) {
            val c = with(size / 2F) { Offset(width, height) }
            drawLine(color = Color.Gray, Offset(0f, c.y), Offset(size.width, c.y))
            drawLine(color = Color.Gray, Offset(c.x, 0f), Offset(c.x, size.height))
            drawCircle(color = Color.Gray, 10f, c)
        }
        scale(scaleX = 1f, scaleY = -1f) {
            translate(left = this.size.width / 2, top = this.size.height / 2) {
                mathDrawScope.content()
            }
        }
    }
}


class MathDrawScope(val originalDrawScope: DrawScope): DrawScope {

    var zoomer: Zoomer? = null
    var lineColor = Color.White
    var step = 1f
    var thickness = 0.5f

    /////////////////////////////////////////////
    fun zoom(p: Offset) = zoomer?.transformSurface(p) ?: p
    fun zoom(x: Float, y: Float) = zoom(Offset(x,y))

    /////////////////////////////////////////////
    override val drawContext     = originalDrawScope.drawContext
    override val layoutDirection = originalDrawScope.layoutDirection
    override val density         = originalDrawScope.density
    override val fontScale       = originalDrawScope.fontScale
    override fun drawArc(brush: Brush,startAngle: Float,sweepAngle: Float,useCenter: Boolean,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)     = originalDrawScope.drawArc(brush,startAngle,sweepAngle,useCenter,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawArc(color: Color,startAngle: Float,sweepAngle: Float,useCenter: Boolean,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)     = originalDrawScope.drawArc(color,startAngle,sweepAngle,useCenter,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawCircle(brush: Brush,radius: Float,center: Offset,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                       = originalDrawScope.drawCircle(brush,radius,center,alpha,style,colorFilter,blendMode)
    override fun drawCircle(color: Color,radius: Float,center: Offset,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                       = originalDrawScope.drawCircle(color,radius,center,alpha,style,colorFilter,blendMode)
    override fun drawImage(image: ImageBitmap,topLeft: Offset,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                               = originalDrawScope.drawImage(image,topLeft,alpha,style,colorFilter,blendMode)
    override fun drawImage(image: ImageBitmap,srcOffset: IntOffset,srcSize: IntSize,dstOffset: IntOffset,dstSize: IntSize,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)   = originalDrawScope.drawImage(image,srcOffset,srcSize,dstOffset,dstSize,alpha,style,colorFilter,blendMode)
    override fun drawLine(brush: Brush,start: Offset,end: Offset,strokeWidth: Float,cap: StrokeCap,pathEffect: PathEffect?,alpha: Float,colorFilter: ColorFilter?,blendMode: BlendMode)                   = originalDrawScope.drawLine(brush,start,end,strokeWidth,cap,pathEffect,alpha,colorFilter,blendMode)
    override fun drawLine(color: Color,start: Offset,end: Offset,strokeWidth: Float,cap: StrokeCap,pathEffect: PathEffect?,alpha: Float,colorFilter: ColorFilter?,blendMode: BlendMode)                   = originalDrawScope.drawLine(color,start,end,strokeWidth,cap,pathEffect,alpha,colorFilter,blendMode)
    override fun drawOval(brush: Brush,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                           = originalDrawScope.drawOval(brush,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawOval(color: Color,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                           = originalDrawScope.drawOval(color,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawPath(path: Path,brush: Brush,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                                           = originalDrawScope.drawPath(path,brush,alpha,style,colorFilter,blendMode)
    override fun drawPath(path: Path,color: Color,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                                           = originalDrawScope.drawPath(path,color,alpha,style,colorFilter,blendMode)
    override fun drawPoints(points: List<Offset>,pointMode: PointMode,brush: Brush,strokeWidth: Float,cap: StrokeCap,pathEffect: PathEffect?,alpha: Float,colorFilter: ColorFilter?,blendMode: BlendMode) = originalDrawScope.drawPoints(points,pointMode,brush,strokeWidth,cap,pathEffect,alpha,colorFilter,blendMode)
    override fun drawPoints(points: List<Offset>,pointMode: PointMode,color: Color,strokeWidth: Float,cap: StrokeCap,pathEffect: PathEffect?,alpha: Float,colorFilter: ColorFilter?,blendMode: BlendMode) = originalDrawScope.drawPoints(points,pointMode,color,strokeWidth,cap,pathEffect,alpha,colorFilter,blendMode)
    override fun drawRect(brush: Brush,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                           = originalDrawScope.drawRect(brush,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawRect(color: Color,topLeft: Offset,size: Size,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                                                           = originalDrawScope.drawRect(color,topLeft,size,alpha,style,colorFilter,blendMode)
    override fun drawRoundRect(brush: Brush,topLeft: Offset,size: Size,cornerRadius: CornerRadius,alpha: Float,style: DrawStyle,colorFilter: ColorFilter?,blendMode: BlendMode)                           = originalDrawScope.drawRoundRect(brush,topLeft,size,cornerRadius,alpha,style,colorFilter,blendMode)
    override fun drawRoundRect(color: Color,topLeft: Offset,size: Size,cornerRadius: CornerRadius,style: DrawStyle,alpha: Float,colorFilter: ColorFilter?,blendMode: BlendMode)                           = originalDrawScope.drawRoundRect(color,topLeft,size,cornerRadius,style,alpha,colorFilter,blendMode)
}

