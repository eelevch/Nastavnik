package app.nastavnik.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.R
import app.nastavnik.app
import app.nastavnik.data.DeviceProfile
import app.nastavnik.theme.TabBarPosition

enum class TabletTab(val title: String, val icon: Int) {
    HOME("Главная", R.drawable.ic_home),
    SCHEDULE("Расписание", R.drawable.ic_calendar),
    GRADES("Оценки", R.drawable.ic_chart),
    STUDY("Учёба", R.drawable.ic_cards),
}

data class CapsuleTabItem(
    val title: String,
    val icon: Int,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Планшетная оболочка с плавающей полупрозрачной капсульной панелью вкладок (слева или справа по выбору).
 */
@Composable
fun TabletShell(
    tab: TabletTab,
    onTab: (TabletTab) -> Unit,
    tabBarPosition: TabBarPosition,
    resumeTick: Int,
    go: (String) -> Unit,
    openNotebook: (Long) -> Unit,
) {
    val context = LocalContext.current
    val isWeak = remember(context) { DeviceProfile.isWeakDevice(context) }
    val useRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isWeak
    val contentLayer = rememberGraphicsLayer()
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    val side = TabBarPosition.forTablet(tabBarPosition.id)
    val railItems = TabletTab.entries.map { entry ->
        CapsuleTabItem(
            title = entry.title,
            icon = entry.icon,
            isSelected = entry == tab,
            onClick = { onTab(entry) },
        )
    }

    Box(Modifier.fillMaxSize().paperBackdrop()) {
        CompositionLocalProvider(LocalBottomBarInset provides 16.dp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootOrigin = it.positionInWindow() }
                    .then(
                        if (useRealBlur) {
                            Modifier.drawWithContent {
                                contentLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(contentLayer)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = if (side == TabBarPosition.LEFT) 96.dp else 0.dp,
                        end = if (side == TabBarPosition.RIGHT) 96.dp else 0.dp,
                    ),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240, easing = Motion.Decelerate)) +
                            slideInVertically(tween(280, easing = Motion.Emphasized)) { (it * 0.06f * dir).toInt() })
                            .togetherWith(
                                fadeOut(tween(160, easing = Motion.Accelerate)) +
                                    slideOutVertically(tween(260, easing = Motion.Emphasized)) { (-it * 0.06f * dir).toInt() },
                            )
                    },
                    label = "tabletTab",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        TabletTab.HOME -> HomeScreen(resumeTick, go, openNotebook)
                        TabletTab.SCHEDULE -> ScheduleScreen(onBack = null)
                        TabletTab.GRADES -> GradesScreen(
                            onBack = null,
                            onSettings = { go(MainActivity.SCREEN_SETTINGS) },
                            onLks = { go(MainActivity.SCREEN_LKS) },
                        )
                        TabletTab.STUDY -> TabletStudyTab(resumeTick, go)
                    }
                }
            }
        }

        CapsuleSideRail(
            items = railItems,
            contentLayer = if (useRealBlur) contentLayer else null,
            rootOriginInWindow = rootOrigin,
            modifier = Modifier
                .align(if (side == TabBarPosition.RIGHT) Alignment.CenterEnd else Alignment.CenterStart)
                .systemBarsPadding()
                .padding(horizontal = 12.dp),
        )
    }
}

/**
 * Единый полупрозрачный контейнер «матового стекла» с живым размытием фона (Android 12+) и световой кромкой.
 */
@Composable
private fun FrostedCapsuleBox(
    shape: Shape,
    contentLayer: GraphicsLayer?,
    rootOriginInWindow: Offset,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val canBlur = contentLayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val glassTint = if (Brand.dark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF232834).copy(alpha = if (canBlur) 0.46f else 0.64f),
                Color(0xFF141821).copy(alpha = if (canBlur) 0.36f else 0.54f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (canBlur) 0.52f else 0.72f),
                Brand.Glass.copy(alpha = if (canBlur) 0.38f else 0.58f),
            ),
        )
    }

    val rimBorder = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (Brand.dark) 0.28f else 0.85f),
            Brand.Line.copy(alpha = if (Brand.dark) 0.26f else 0.42f),
            Color.White.copy(alpha = if (Brand.dark) 0.14f else 0.48f),
        ),
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { boxOrigin = it.positionInWindow() }
            .clip(shape),
    ) {
        if (canBlur) {
            Box(
                Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .graphicsLayer {
                        val r = with(density) { 24.dp.toPx() }
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    }
                    .drawWithContent {
                        if (contentLayer.size.width > 0 && contentLayer.size.height > 0) {
                            translate(
                                left = -(boxOrigin.x - rootOriginInWindow.x),
                                top = -(boxOrigin.y - rootOriginInWindow.y),
                            ) {
                                drawLayer(contentLayer)
                            }
                        }
                    },
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .background(glassTint)
                .border(1.dp, rimBorder, shape),
        )

        content()
    }
}

/**
 * Вертикальная плавающая полупрозрачная капсульная панель вкладок для планшета.
 */
@Composable
fun CapsuleSideRail(
    items: List<CapsuleTabItem>,
    contentLayer: GraphicsLayer? = null,
    rootOriginInWindow: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    FrostedCapsuleBox(
        shape = capsuleShape,
        contentLayer = contentLayer,
        rootOriginInWindow = rootOriginInWindow,
        modifier = modifier
            .width(76.dp)
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item ->
                CapsuleNavButton(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CapsuleNavButton(
    item: CapsuleTabItem,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(999.dp)
    val src = remember(item.title) { MutableInteractionSource() }
    val fillAlpha = if (item.isSelected) {
        if (Brand.dark) 0.28f else 0.17f
    } else {
        0f
    }
    val tint by animateColorAsState(
        targetValue = if (item.isSelected) Brand.Accent else Brand.Ink.copy(alpha = 0.70f),
        animationSpec = tween(220),
        label = "capsuleTint",
    )

    Column(
        modifier = modifier
            .clip(pillShape)
            .background(Brand.Accent.copy(alpha = fillAlpha))
            .then(
                if (item.isSelected) {
                    Modifier.border(
                        width = 0.8.dp,
                        color = Brand.Accent.copy(alpha = if (Brand.dark) 0.42f else 0.28f),
                        shape = pillShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = src, indication = ripple(bounded = true), onClick = item.onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp)
            .semantics {
                contentDescription = item.title
                selected = item.isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painterResource(item.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            item.title,
            fontSize = 10.sp,
            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Эргономичный боковой мини-док под большой палец на телефоне:
 * только значки в узкой полупрозрачной капсуле внизу сбоку и сворачивание в мини-кружок.
 */
@Composable
fun PhoneThumbSideCapsule(
    items: List<CapsuleTabItem>,
    isRight: Boolean,
    contentLayer: GraphicsLayer? = null,
    rootOriginInWindow: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val activeItem = items.firstOrNull { it.isSelected } ?: items.first()

    AnimatedContent(
        targetState = collapsed,
        transitionSpec = {
            (fadeIn(tween(200, easing = Motion.Decelerate)) + scaleIn(tween(220, easing = Motion.Emphasized), initialScale = 0.86f))
                .togetherWith(fadeOut(tween(150, easing = Motion.Accelerate)) + scaleOut(tween(160), targetScale = 0.86f))
        },
        label = "thumbCapsuleCollapse",
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 18.dp),
    ) { isCollapsed ->
        if (isCollapsed) {
            val orbSrc = remember { MutableInteractionSource() }
            FrostedCapsuleBox(
                shape = CircleShape,
                contentLayer = contentLayer,
                rootOriginInWindow = rootOriginInWindow,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = orbSrc, indication = ripple(bounded = false, radius = 22.dp)) {
                            collapsed = false
                        }
                        .semantics { contentDescription = "Развернуть панель вкладок" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(activeItem.icon),
                        contentDescription = null,
                        tint = Brand.Accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        } else {
            FrostedCapsuleBox(
                shape = RoundedCornerShape(999.dp),
                contentLayer = contentLayer,
                rootOriginInWindow = rootOriginInWindow,
                modifier = Modifier
                    .width(46.dp)
                    .height(IntrinsicSize.Min),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items.forEach { item ->
                        val src = remember(item.title) { MutableInteractionSource() }
                        val tint by animateColorAsState(
                            targetValue = if (item.isSelected) Brand.Accent else Brand.Ink.copy(alpha = 0.72f),
                            animationSpec = tween(220),
                            label = "thumbCapsuleTint",
                        )
                        val fillAlpha = if (item.isSelected) {
                            if (Brand.dark) 0.28f else 0.17f
                        } else {
                            0f
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brand.Accent.copy(alpha = fillAlpha))
                                .then(
                                    if (item.isSelected) {
                                        Modifier.border(
                                            width = 0.8.dp,
                                            color = Brand.Accent.copy(alpha = if (Brand.dark) 0.42f else 0.28f),
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(
                                    interactionSource = src,
                                    indication = ripple(bounded = false, radius = 18.dp),
                                    onClick = item.onClick,
                                )
                                .semantics {
                                    contentDescription = item.title
                                    selected = item.isSelected
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(item.icon),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Box(
                        Modifier
                            .padding(horizontal = 11.dp, vertical = 2.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Brand.Line.copy(alpha = 0.35f)),
                    )

                    val collapseSrc = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = collapseSrc,
                                indication = ripple(bounded = false, radius = 14.dp),
                            ) { collapsed = true }
                            .semantics { contentDescription = "Свернуть панель" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = Brand.InkSoft,
                            modifier = Modifier
                                .size(15.dp)
                                .graphicsLayer { rotationZ = if (isRight) 0f else 180f },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Компактная плавающая полупрозрачная капсула снизу для телефона:
 * активная вкладка мягко раскрывает подпись, остальные остаются аккуратными значками.
 */
@Composable
fun PhoneBottomCapsule(
    items: List<CapsuleTabItem>,
    contentLayer: GraphicsLayer? = null,
    rootOriginInWindow: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(999.dp)
    FrostedCapsuleBox(
        shape = capsuleShape,
        contentLayer = contentLayer,
        rootOriginInWindow = rootOriginInWindow,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .animateContentSize(tween(240, easing = Motion.Emphasized)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val pillShape = RoundedCornerShape(999.dp)
                val src = remember(item.title) { MutableInteractionSource() }
                val fillAlpha = if (item.isSelected) {
                    if (Brand.dark) 0.26f else 0.16f
                } else {
                    0f
                }
                val tint by animateColorAsState(
                    targetValue = if (item.isSelected) Brand.Accent else Brand.Ink.copy(alpha = 0.72f),
                    animationSpec = tween(220),
                    label = "bottomCapsuleTint",
                )

                val itemMod = if (item.isSelected) {
                    Modifier
                        .height(38.dp)
                        .clip(pillShape)
                        .background(Brand.Accent.copy(alpha = fillAlpha))
                        .border(
                            width = 0.8.dp,
                            color = Brand.Accent.copy(alpha = if (Brand.dark) 0.42f else 0.28f),
                            shape = pillShape,
                        )
                        .padding(horizontal = 14.dp)
                } else {
                    Modifier
                        .size(width = 42.dp, height = 38.dp)
                        .clip(pillShape)
                }

                Row(
                    modifier = itemMod
                        .clickable(interactionSource = src, indication = ripple(bounded = true), onClick = item.onClick)
                        .semantics {
                            contentDescription = item.title
                            selected = item.isSelected
                        },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(item.icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    if (item.isSelected) {
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Вкладка «Учёба» на планшете: карточки, прогресс, экзамены, чистовики и конспекты. */
@Composable
private fun TabletStudyTab(resumeTick: Int, go: (String) -> Unit) {
    val context = LocalContext.current
    val nowMs = remember(resumeTick) { System.currentTimeMillis() }
    val due by remember(nowMs) { context.app.db.cards().observeDueCount(nowMs) }.collectAsStateWithLifecycle(initialValue = 0)
    val total by remember { context.app.db.cards().observeCount() }.collectAsStateWithLifecycle(initialValue = 0)

    ScreenScaffold(title = "Учёба", onBack = null) {
        SectionCard("Карточки для повторения") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AnimatedCount(due)
                    Text("на сегодня из $total", style = MaterialTheme.typography.bodyMedium, color = Brand.InkSoft)
                }
                InkButton(if (due > 0) "Повторить" else "Открыть", icon = R.drawable.ic_cards) {
                    go(MainActivity.SCREEN_REVIEW)
                }
            }
        }
        SectionLabel("Разделы")
        SectionsGrid(go = go, columns = 2, items = TABLET_SECTIONS)
    }
}
