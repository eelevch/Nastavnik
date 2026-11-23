package app.nastavnik.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nastavnik.R
import kotlinx.coroutines.delay

/**
 * Живой маскот-робот проекта «Наставник»:
 * - Пока человек пишет конспект: робот машет рукой в значке.
 * - Пока наставник думает/отвечает: робот думает и пишет формулы.
 * - В покое: стильный логотип с наушниками и очками.
 */
@Composable
fun MascotIcon(
    writing: Boolean = false,
    thinking: Boolean = false,
    sizeDp: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()

    when {
        // Человек пишет на листе конспекта — робот в значке дружелюбно машет рукой
        writing -> {
            val infinite = rememberInfiniteTransition(label = "mascotWave")
            val rot by infinite.animateFloat(
                initialValue = -12f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "waveRot",
            )
            val swayX by infinite.animateFloat(
                initialValue = -1.2f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "waveSwayX",
            )
            val res = if (isDark) R.drawable.ic_mascot_wave_dark else R.drawable.ic_mascot_wave
            Image(
                painter = painterResource(res),
                contentDescription = "Наставник машет",
                modifier = modifier
                    .size(sizeDp)
                    .graphicsLayer {
                        rotationZ = rot
                        translationX = swayX
                        translationY = -swayX * 0.5f
                    },
            )
        }
        // Наставник думает и пишет ответ — робот чередует размышления и письмо
        thinking -> {
            var phase by remember { mutableIntStateOf(0) }
            LaunchedEffect(thinking) {
                while (true) {
                    delay(850)
                    phase = (phase + 1) % 2
                }
            }
            val infinite = rememberInfiniteTransition(label = "mascotThink")
            val bobY by infinite.animateFloat(
                initialValue = -1.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "thinkBobY",
            )
            val res = if (phase == 0) {
                if (isDark) R.drawable.ic_mascot_think_dark else R.drawable.ic_mascot_think
            } else {
                if (isDark) R.drawable.ic_mascot_write_dark else R.drawable.ic_mascot_write
            }
            Crossfade(targetState = res, animationSpec = tween(250), label = "mascotCrossfade") { drawableRes ->
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = "Наставник думает и пишет",
                    modifier = modifier
                        .size(sizeDp)
                        .graphicsLayer {
                            translationY = bobY
                        },
                )
            }
        }
        // Состояние покоя — классический логотип маскота
        else -> {
            val res = if (isDark) R.drawable.ic_mascot_think_dark else R.drawable.ic_mascot_think
            Image(
                painter = painterResource(res),
                contentDescription = "Наставник",
                modifier = modifier.size(sizeDp),
            )
        }
    }
}

/** Круглый бейдж с живым маскотом для шапки панели наставника и карточек. */
@Composable
fun MascotBadge(
    writing: Boolean = false,
    thinking: Boolean = false,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Brand.Glass.copy(alpha = 0.45f) else Brand.Paper
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        MascotIcon(
            writing = writing,
            thinking = thinking,
            sizeDp = (size.value * 0.78f).dp,
        )
    }
}
