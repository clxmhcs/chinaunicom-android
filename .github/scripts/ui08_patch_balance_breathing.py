from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowHomeScreen.kt')
text = path.read_text()

import_anchor = 'import androidx.compose.foundation.Image\n'
animation_imports = (
    'import androidx.compose.animation.core.Animatable\n'
    'import androidx.compose.animation.core.CubicBezierEasing\n'
    'import androidx.compose.animation.core.tween\n'
)
if animation_imports not in text:
    if import_anchor not in text:
        raise SystemExit('missing foundation import anchor')
    text = text.replace(import_anchor, animation_imports + import_anchor, 1)

runtime_anchor = 'import androidx.compose.runtime.Composable\n'
if 'import androidx.compose.runtime.LaunchedEffect\n' not in text:
    if runtime_anchor not in text:
        raise SystemExit('missing runtime import anchor')
    text = text.replace(runtime_anchor, runtime_anchor + 'import androidx.compose.runtime.LaunchedEffect\n', 1)

graphics_anchor = 'import androidx.compose.ui.graphics.ColorFilter\n'
if 'import androidx.compose.ui.graphics.lerp\n' not in text:
    if graphics_anchor not in text:
        raise SystemExit('missing graphics import anchor')
    text = text.replace(graphics_anchor, graphics_anchor + 'import androidx.compose.ui.graphics.lerp\n', 1)

start_marker = '@Composable\nprivate fun FlowBalancePill('
end_marker = '\n@Composable\nprivate fun FlowRefreshAllPill('
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('FlowBalancePill markers not found')
old = text[start:end]
required_old_fragments = [
    'val pillBrush = when (state)',
    'BalanceRefreshState.LOADING -> Brush.linearGradient(',
    'Row(modifier = Modifier.matchParentSize())',
    'onClickLabel = "手动刷新余额"',
    'onClickLabel = "查看话费账单"',
]
missing = [fragment for fragment in required_old_fragments if fragment not in old]
if missing:
    raise SystemExit(f'unexpected FlowBalancePill baseline, missing: {missing}')

new = r'''@Composable
private fun FlowBalancePill(
    balance: Double?,
    state: BalanceRefreshState,
    canOpenBill: Boolean,
    onRefresh: () -> Unit,
    onOpenBill: () -> Unit,
) {
    // iOS DashboardView.BalancePillBackground parity:
    // while loading, choose 5 of 7 colors every 0.85 s and ease between 0.26/0.52 opacity.
    // The repository already keeps LOADING visible for at least five seconds.
    val loadingPalette = remember {
        listOf(
            Color(red = 1.00f, green = 0.25f, blue = 0.34f),
            Color(red = 1.00f, green = 0.56f, blue = 0.22f),
            Color(red = 1.00f, green = 0.84f, blue = 0.22f),
            Color(red = 0.38f, green = 0.78f, blue = 0.50f),
            Color(red = 0.20f, green = 0.66f, blue = 0.98f),
            Color(red = 0.46f, green = 0.42f, blue = 0.98f),
            Color(red = 0.86f, green = 0.36f, blue = 0.86f),
        )
    }
    val initialLoadingColors = remember { loadingPalette.shuffled().take(5) }
    val animationFraction = remember { Animatable(1f) }
    var fromLoadingColors by remember { mutableStateOf(initialLoadingColors) }
    var toLoadingColors by remember { mutableStateOf(initialLoadingColors) }
    var fromLoadingAlpha by remember { mutableStateOf(0.26f) }
    var toLoadingAlpha by remember { mutableStateOf(0.26f) }

    LaunchedEffect(state) {
        if (state != BalanceRefreshState.LOADING) {
            animationFraction.snapTo(1f)
            return@LaunchedEffect
        }

        var currentColors = loadingPalette.shuffled().take(5)
        var currentAlpha = 0.26f
        var brighten = true
        fromLoadingColors = currentColors
        toLoadingColors = currentColors
        fromLoadingAlpha = currentAlpha
        toLoadingAlpha = currentAlpha
        animationFraction.snapTo(1f)

        while (true) {
            val nextColors = loadingPalette.shuffled().take(5)
            val nextAlpha = if (brighten) 0.52f else 0.26f
            fromLoadingColors = currentColors
            toLoadingColors = nextColors
            fromLoadingAlpha = currentAlpha
            toLoadingAlpha = nextAlpha
            animationFraction.snapTo(0f)
            animationFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 850,
                    easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                ),
            )
            currentColors = nextColors
            currentAlpha = nextAlpha
            brighten = !brighten
        }
    }

    val fraction = animationFraction.value.coerceIn(0f, 1f)
    val loadingAlpha = fromLoadingAlpha + ((toLoadingAlpha - fromLoadingAlpha) * fraction)
    val loadingColors = List(5) { index ->
        lerp(fromLoadingColors[index], toLoadingColors[index], fraction).copy(alpha = loadingAlpha)
    }
    val text = balance?.let { "[余额：${String.format(Locale.US, "%.2f", it)}元]" } ?: "[余额：--]"

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .drawWithCache {
                val idleOverlay = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(red = 0.93f, green = 0.88f, blue = 1.00f).copy(alpha = 0.42f),
                        0.34f to Color(red = 0.93f, green = 0.88f, blue = 1.00f).copy(alpha = 0.34f),
                        0.66f to Color(red = 0.90f, green = 0.97f, blue = 0.88f).copy(alpha = 0.30f),
                        1.00f to Color(red = 0.90f, green = 0.97f, blue = 0.88f).copy(alpha = 0.40f),
                    ),
                )
                val loadingBrush = Brush.linearGradient(
                    colors = loadingColors,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val failedBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Red.copy(alpha = 0.10f),
                        Color(red = 0f, green = 1f, blue = 0f).copy(alpha = 0.12f),
                    ),
                )
                onDrawBehind {
                    when (state) {
                        BalanceRefreshState.LOADING -> drawRect(loadingBrush)
                        BalanceRefreshState.IDLE -> {
                            drawRect(Color.Red.copy(alpha = 0.09f))
                            drawRect(idleOverlay)
                        }
                        BalanceRefreshState.FAILED -> {
                            drawRect(failedBrush)
                            drawRect(idleOverlay)
                        }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text,
                fontSize = 15.43.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Red,
                maxLines = 1,
            )
            Text(
                "›",
                fontSize = 15.43.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight(467),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }

        Row(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        onClickLabel = "手动刷新余额",
                        onClick = onRefresh,
                    ),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = canOpenBill,
                        onClickLabel = "查看话费账单",
                        onClick = onOpenBill,
                    ),
            )
        }
    }
}
'''

text = text[:start] + new + text[end:]
path.write_text(text)
