import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color.Companion.hsv
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
//import dev.kdrag0n.colorkt.conversion.

import lightwizard.composeapp.generated.resources.Res
import lightwizard.composeapp.generated.resources.compose_multiplatform

//fun LchToColor(input : Oklch) : Color {
//    val lab = input.toOklab()
//     return Color(lab.L.toFloat(), lab.a.toFloat(), lab.b.toFloat() , 1f, ColorSpaces.Oklab)
//}



@Composable
@Preview
fun App(platform : Platform) {

    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Light Wizard 2 🧙🏽", fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            Text("Looking for lights...")

            Text("Bluetooth Enabled: ${platform.bluetoothEnabled()}")
            platform.requestPermissions()
            platform.doBluetoothThings()

//            Button(onClick = { showContent = !showContent }) {
//                Text("Connect")
//            }
//            AnimatedVisibility(showContent) {
////                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
////                    Image(painterResource(Res.drawable.compose_multiplatform), null)
////                }
//
//            }
            
            Canvas(modifier = Modifier
                    .height(40.dp)
                    .width(300.dp)
                    .clip(RoundedCornerShape(50))
            ) {
//                val canvasQuadrantSize = size / 2F
//                drawRect(
//                    color = Color.Magenta,
//                    size = canvasQuadrantSize
//                )
//                val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt(), ImageBitmapConfig.Argb8888)
//                val hueCanvas = androidx.compose.ui.graphics.Canvas(bitmap)
                
                
                drawIntoCanvas {
                    val huePanel = Rect(0f,0f, size.width, size.height)
                
                    val hueColors = Array<Color>( size.width.toInt(), {Int -> hsv(0f,0f,0f)} )
                    var hue : Float = 0.0f
                    for (i in hueColors.indices) {
//                        hueColors[i] = LchToColor( Oklch(-1.0,1.0,0.5) )
                        hueColors[i] = Color.hsl(hue, 1.0f, 0.5f);
                        hue += 360.0f / hueColors.size
                    }
                    val linePaint = Paint()
                    linePaint.strokeWidth = 0F
                    for (i in hueColors.indices) {
                        linePaint.color = hueColors[i]
                        it.drawLine( Offset(i.toFloat(), 0F), Offset(i.toFloat(), huePanel.bottom), linePaint)
                    }
                }
            }
        }
    }
}