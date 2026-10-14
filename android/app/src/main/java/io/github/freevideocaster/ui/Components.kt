package io.github.freevideocaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Miniatura con la inicial del título y un color propio de cada video (no hay carátulas sin internet). */
@Composable
fun PosterTile(title: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = remember(title, dark) {
        val hue = ((title.hashCode().toLong() and 0x7fffffffL) % 360).toFloat()
        Color.hsv(hue, 0.45f, if (dark) 0.52f else 0.74f)
    }
    val letter = remember(title) { title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "▶" }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Dispara [onFire] al tocar y, si se mantiene pulsado, lo repite (como las teclas de un control remoto
 * real: volumen, flechas). Vibra levemente en cada disparo.
 */
fun Modifier.repeatOnPress(onFire: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(onFire)
    pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                current()
                coroutineScope {
                    val repeater = launch {
                        delay(450)
                        while (true) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            current()
                            delay(130)
                        }
                    }
                    tryAwaitRelease()
                    repeater.cancel()
                }
            },
        )
    }
}

/** Botón circular de control remoto. [label] sirve para accesibilidad y pruebas automáticas. */
@Composable
fun RoundButton(
    label: String,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    repeat: Boolean = false,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val behavior = when {
        !enabled -> Modifier
        repeat -> Modifier.repeatOnPress(onFire)
        else -> Modifier.clickable(onClick = onFire)
    }
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(container)
            .then(behavior)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                if (enabled) onClick(label) { onFire(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { icon() }
    }
}

@Composable
fun IconInButton(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, modifier = modifier)
}

/** Flecha circular con los segundos dentro: retroceder o avanzar [seconds] s. */
@Composable
fun SkipButton(seconds: Int, forward: Boolean, modifier: Modifier = Modifier, onFire: () -> Unit) {
    RoundButton(
        label = if (forward) "Adelantar $seconds segundos" else "Retroceder $seconds segundos",
        onFire = onFire,
        modifier = modifier,
        size = 56.dp,
        repeat = true,
    ) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(36.dp)) {
            val strokeWidth = 2.6.dp.toPx()
            val inset = strokeWidth + 3.dp.toPx()
            val diameter = size.minDimension - inset * 2
            // «Retroceder» gira hacia la izquierda (flecha arriba a la izquierda); «adelantar» es su reflejo.
            scale(scaleX = if (forward) -1f else 1f, scaleY = 1f, pivot = center) {
                val start = -70f
                val sweep = 300f
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                // Punta de flecha al inicio del arco, apuntando en el sentido contrario a las agujas.
                val radius = diameter / 2f
                val angle = Math.toRadians(start.toDouble())
                val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                val tangent = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                val normal = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val head = 5.5.dp.toPx()
                val path = Path().apply {
                    moveTo(tip.x + tangent.x * head, tip.y + tangent.y * head)
                    lineTo(tip.x + normal.x * head * 0.9f, tip.y + normal.y * head * 0.9f)
                    lineTo(tip.x - normal.x * head * 0.9f, tip.y - normal.y * head * 0.9f)
                    close()
                }
                drawPath(path, color, style = Fill)
            }
        }
        Text(seconds.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Acción pequeña con ícono, etiqueta y, opcionalmente, un valor actual debajo (p. ej. el idioma). */
@Composable
fun QuickAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoundButton(label = label, onFire = onClick, size = 48.dp, enabled = enabled, icon = icon)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cruceta con «OK» al centro, como la de un control remoto de Roku. */
@Composable
fun DPad(onKey: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)) {
        val arrow = size * 0.27f
        DPadArrow(Alignment.TopCenter, arrow, "Arriba", Icons.Filled.KeyboardArrowUp) { onKey("Up") }
        DPadArrow(Alignment.BottomCenter, arrow, "Abajo", Icons.Filled.KeyboardArrowDown) { onKey("Down") }
        DPadArrow(Alignment.CenterStart, arrow, "Izquierda", Icons.AutoMirrored.Filled.KeyboardArrowLeft) { onKey("Left") }
        DPadArrow(Alignment.CenterEnd, arrow, "Derecha", Icons.AutoMirrored.Filled.KeyboardArrowRight) { onKey("Right") }
        RoundButton(
            label = "OK",
            onFire = { onKey("Select") },
            modifier = Modifier.align(Alignment.Center),
            size = size * 0.36f,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("OK", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun BoxScope.DPadArrow(alignment: Alignment, size: Dp, label: String, icon: ImageVector, onFire: () -> Unit) {
    Box(
        Modifier
            .align(alignment)
            .padding(4.dp)
            .size(size)
            .clip(CircleShape)
            .repeatOnPress(onFire)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label) { onFire(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size * 0.8f))
    }
}

/** Balancín de volumen: subir arriba, silencio al centro, bajar abajo. */
@Composable
fun VolumeRocker(onKey: (String) -> Unit, modifier: Modifier = Modifier, height: Dp = 176.dp) {
    Surface(
        modifier.width(64.dp).size(width = 64.dp, height = height),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            RockerZone("Subir volumen", AppIcons.VolumeUp, Modifier.weight(1f)) { onKey("VolumeUp") }
            RockerZone("Silenciar", AppIcons.VolumeOff, Modifier.weight(0.8f), repeat = false) { onKey("VolumeMute") }
            RockerZone("Bajar volumen", AppIcons.VolumeDown, Modifier.weight(1f)) { onKey("VolumeDown") }
        }
    }
}

@Composable
private fun RockerZone(label: String, icon: ImageVector, modifier: Modifier, repeat: Boolean = true, onFire: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (repeat) Modifier.repeatOnPress(onFire) else Modifier.clickable(onClick = onFire))
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick(label) { onFire(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
