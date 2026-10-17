// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.roundToInt

internal object PortalPalette {
    val Background = Color(0xFF080C14)
    val Surface = Color(0xFF101724)
    val Text = Color.White
    val Muted = Color(0xFFBAC4D2)
    val Focus = Color(0xFFB5E4FF)
    val Shape = RoundedCornerShape(24.dp)
    const val LaunchDurationMs = 720
    val LaunchEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 1f)
    val GlideEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    const val GlideDurationMs = 480
}

internal val LocalPortalMotion = staticCompositionLocalOf { true }

internal fun portalMotionEnabled(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 26) {
    ValueAnimator.areAnimatorsEnabled()
} else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** Shared glass, focus, hover and depth treatment for game, figure and system panels. */
@Composable
internal fun PortalSurface(
    modifier: Modifier = Modifier,
    accent: Color = PortalPalette.Focus,
    selected: Boolean = false,
    enabled: Boolean = true,
    breathe: Boolean = false,
    prominentMotion: Boolean = false,
    shadow: Boolean = true,
    shape: RoundedCornerShape = PortalPalette.Shape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val highlighted = selected || focused || hovered
    val motion = LocalPortalMotion.current
    val lift = animateFloatAsState(if (highlighted) 1f else 0f,
        tween(if (motion) 360 else 0, easing = PortalPalette.GlideEasing), label = "portalFocus")
    val breathingPhase: State<Float> = if (motion && breathe && highlighted) {
        rememberInfiniteTransition(label = "portalBreathing").animateFloat(
            0f, (2f * Math.PI).toFloat(),
            infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
            label = "portalBreath")
    } else remember { mutableFloatStateOf(0f) }
    Box(modifier
        .graphicsLayer {
            val breath = if (motion && breathe && highlighted) (1f - cos(breathingPhase.value)) * 0.5f else 0f
            val growth = if (onClick != null) {
                lift.value * (if (prominentMotion) 0.025f else 0.015f) +
                    breath * (if (prominentMotion) 0.025f else 0.005f)
            } else 0f
            scaleX = 1f + growth
            scaleY = 1f + growth
            translationY = if (prominentMotion) -6.dp.toPx() * breath else 0f
            rotationX = if (motion && breathe && highlighted) {
                if (prominentMotion) -1f + breath * 2f else -0.5f + breath * 0.6f
            } else 0f
            cameraDistance = 16f * density
            shadowElevation = if (shadow) (if (highlighted) 24f else 8f) * density else 0f
            this.shape = shape
            ambientShadowColor = accent.copy(alpha = if (highlighted) 0.35f else 0.08f)
            spotShadowColor = accent.copy(alpha = if (highlighted) 0.45f else 0.08f)
        }
        .clip(shape)
        .background(Brush.linearGradient(listOf(
            if (highlighted) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f),
            PortalPalette.Surface.copy(alpha = 0.78f))))
        .border(if (highlighted) 1.5.dp else 1.dp,
            if (highlighted) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f), shape)
        .onFocusChanged { focused = it.hasFocus }
        .hoverable(interactions, enabled)
        .then(if (onClick != null) Modifier.clickable(interactions, null, enabled = enabled,
            onClick = onClick) else Modifier), content = content)
}

@Composable
internal fun PortalIntro(kicker: String, title: String, subtitle: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 650.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kicker.uppercase(), color = PortalPalette.Focus.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Black,
                fontSize = if (wide) 44.sp else 32.sp,
                lineHeight = if (wide) 48.sp else 38.sp)
            Text(subtitle, color = PortalPalette.Muted, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
internal fun PortalHalo(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height * 0.55f)
        val radius = size.minDimension * 0.48f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent),
            center, radius), radius, center)
        drawOval(accent.copy(alpha = 0.36f), Offset(size.width * 0.14f, size.height * 0.76f),
            androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.13f), style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
internal fun PortalLaunchOverlay(game: ConsoleGame, progress: Float, coverBounds: Rect?) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "${game.displayTitle} wird gestartet" }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val bounds = coverBounds ?: Rect(width * 0.33f, height * 0.30f, width * 0.67f, height * 0.65f)
        val zoom = maxOf(width / bounds.width, height / bounds.height) * 1.05f
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = progress * 0.55f)))
        AsyncImage(game.sceneArtwork ?: "file:///android_asset/skylanders/images/${game.artwork}", null,
            modifier = Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .graphicsLayer {
                    scaleX = 1f + progress * (zoom - 1f)
                    scaleY = scaleX
                    translationX = (width / 2f - bounds.center.x) * progress
                    translationY = (height / 2f - bounds.center.y) * progress
                }, contentScale = ContentScale.Crop)
        // Keep the cover solid during the zoom, then blend into the emulator handoff.
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = ((progress - 0.78f) / 0.22f).coerceIn(0f, 1f)
        }.background(PortalPalette.Background))
    }
}

