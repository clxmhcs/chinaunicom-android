from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowAccountDetailScreen.kt"
text = PATH.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


def replace_section(start_marker: str, end_marker: str, new_section: str, label: str) -> None:
    global text
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    text = text[:start] + new_section.rstrip() + "\n\n" + text[end:]

# Imports needed by the real China Unicom logo + adaptive watermark preview.
replace_once(
    "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.Canvas\n",
    "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.Image\n",
    "Image import",
)
replace_once(
    "import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.ColorFilter\n",
    "ColorFilter import",
)
replace_once(
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Brush\n",
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.graphics.Brush\n",
    "layout/density imports",
)
replace_once(
    "import androidx.compose.ui.text.font.FontFamily\n",
    "import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.text.font.FontFamily\n",
    "painter import",
)
replace_once(
    "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
    "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
    "IntSize import",
)
replace_once(
    "import com.clxmhcs.chinaunicom.core.model.DisplayUnit\n",
    "import com.clxmhcs.chinaunicom.R\nimport com.clxmhcs.chinaunicom.core.model.DisplayUnit\n",
    "R import",
)

# Sheet geometry: iOS standard NavigationStack sheet density rather than oversized custom toolbar/list gaps.
replace_once(
    ".statusBarsPadding()\n                    .padding(top = 12.dp)\n                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))",
    ".statusBarsPadding()\n                    .padding(top = 8.dp)\n                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))",
    "sheet top inset",
)
replace_once(
    "contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 36.dp),\n                    verticalArrangement = Arrangement.spacedBy(22.dp),",
    "contentPadding = PaddingValues(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 36.dp),\n                    verticalArrangement = Arrangement.spacedBy(18.dp),",
    "list density",
)
replace_once(
    "FlowDisplaySection(title = \"首页预览\") {",
    "FlowDisplaySection(title = \"首页预览\", wrapsContentInCard = false) {",
    "preview transparent list row",
)
replace_once(
    "modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),\n                                        fontSize = 12.sp,",
    "modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),\n                                        fontSize = 10.5.sp,",
    "ambiguous group label",
)
replace_once(
    ".padding(horizontal = 16.dp, vertical = 14.dp),\n                                fontSize = 15.sp,",
    ".padding(horizontal = 14.dp, vertical = 11.dp),\n                                fontSize = 13.sp,",
    "new summary action density",
)

# Toolbar sized to the iOS navigation bar rather than 78dp tall custom header.
topbar = '''@Composable
private fun FlowDisplaySettingsTopBar(editMode: Boolean, onToggleEdit: () -> Unit, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onToggleEdit),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Text(
                if (editMode) "结束编辑" else "编辑",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = FlowDetailBlue,
            )
        }

        Text(
            "显示内容",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onDone),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Text(
                "完成",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FlowDetailBlue,
            )
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowDisplaySettingsTopBar",
    "@Composable\nprivate fun FlowDisplaySection",
    topbar,
    "settings top bar",
)

# Section styling follows grouped List: compact header/footer and ~20pt card corners.
section = '''@Composable
private fun FlowDisplaySection(
    title: String,
    footer: String? = null,
    wrapsContentInCard: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 12.dp),
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            color = FlowDetailSecondary,
        )
        if (wrapsContentInCard) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
                shadowElevation = 0.dp,
            ) {
                Column { content() }
            }
        } else {
            content()
        }
        if (footer != null) {
            Text(
                footer,
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 11.sp,
                lineHeight = 15.dp.value.sp,
                color = FlowDetailSecondary,
            )
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowDisplaySection(",
    "@Composable\nprivate fun FlowPreviewAccountCard",
    section,
    "display section",
)

# Preview is the same visual system as iOS AccountCardView, with Android density compensation.
preview = '''@Composable
private fun FlowPreviewAccountCard(
    account: UnicomAccount,
    formatter: FlowFormatter,
    hideMobileMiddleDigits: Boolean,
) {
    val mobile = if (hideMobileMiddleDigits) flowDetailMaskMobile(account.mobile) else account.mobile
    val selectedIDs = account.visibleSummaryGroups.flatMap { it.packageKeys }.toSet()
    val selected = account.visibleDetailPackages.filter { it.id in selectedIDs }
    val used = selected.sumOf { it.safeUsedMB }
    val total = selected.mapNotNull { it.totalMB }.sum().takeIf { it > 0.0 }
    val cardShape = RoundedCornerShape(24.dp)
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val watermarkSide = with(LocalDensity.current) {
        (minOf(cardSize.width, cardSize.height) * 0.74f).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardSize = it }
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .drawWithCache {
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFE0B8).copy(alpha = 0.68f),
                        Color(0xFFFFE0B8).copy(alpha = 0.32f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width * 0.50f, size.height * 0.50f),
                )
                onDrawBehind { drawRect(gradient) }
            }
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), cardShape),
    ) {
        Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(watermarkSide),
            alpha = 0.045f,
        )

        Column(
            modifier = Modifier.padding(start = 17.dp, top = 16.dp, end = 17.dp, bottom = 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.5.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.china_unicom_knot_watermark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFFFF8F1F)),
                    modifier = Modifier.size(15.5.dp),
                )
                Text(
                    mobile,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    account.lastUpdatedAt?.let { flowDetailTimeOnly(it) } ?: "--",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
            }

            Column(
                modifier = Modifier.padding(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.5.dp),
            ) {
                Text(
                    account.packageName.ifBlank { "联通套餐" },
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
                if (selected.isNotEmpty()) {
                    Text(
                        "［ 已用：${formatter.string(used)}，总流量：${total?.let(formatter::string) ?: "不限量"} ］",
                        fontSize = 12.8.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFFF8F1F),
                        maxLines = 1,
                    )
                }
            }

            if (account.visibleSummaryGroups.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 15.5.dp),
                    verticalArrangement = Arrangement.spacedBy(14.5.dp),
                ) {
                    account.visibleSummaryGroups.forEach { group ->
                        FlowPreviewSummaryRow(account.summary(group), formatter)
                    }
                }
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowPreviewAccountCard(",
    "@Composable\nprivate fun FlowPreviewSummaryRow",
    preview,
    "preview account card",
)

preview_summary = '''@Composable
private fun FlowPreviewSummaryRow(summary: FlowSummary, formatter: FlowFormatter) {
    val used = summary.usedMB.coerceAtLeast(0.0)
    val fraction = if (summary.isUnlimited) {
        val step = 100.0 * 1024.0
        val shownTotal = ceil(used / step).coerceAtLeast(1.0) * step
        (used / shownTotal).coerceIn(0.0, 1.0)
    } else summary.usedFraction ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(5.5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${summary.name}：", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("已用 ${formatter.string(summary.usedMB)}", fontSize = 9.5.sp, lineHeight = 12.sp, color = FlowDetailSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                if (summary.isUnlimited) "不限量" else "剩余 ${formatter.string(summary.remainingMB)}/共 ${formatter.string(summary.totalMB)}",
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                color = FlowDetailSecondary,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(FlowDetailBlue.copy(alpha = 0.12f)),
        ) {
            if (fraction > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(FlowDetailBlue),
                )
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowPreviewSummaryRow",
    "@Composable\nprivate fun FlowAmbiguousResourceRow",
    preview_summary,
    "preview summary row",
)

ambiguous = '''@Composable
private fun FlowAmbiguousResourceRow(name: String, kind: String, value: String, glyph: String, onClick: () -> Unit) {
    val isVoice = kind == "语音候选"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FlowDetailBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            FlowCandidateGlyph(isVoice = isVoice)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                name,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(kind, fontSize = 10.5.sp, lineHeight = 13.sp, color = FlowDetailSecondary)
        }
        Text(
            value,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlowDetailSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text("›", fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = FlowDetailTertiary)
    }
}

@Composable
private fun FlowCandidateGlyph(isVoice: Boolean) {
    Canvas(modifier = Modifier.size(17.dp)) {
        if (isVoice) {
            val stroke = size.minDimension * 0.19f
            drawLine(
                color = FlowDetailBlue,
                start = Offset(size.width * 0.25f, size.height * 0.18f),
                end = Offset(size.width * 0.20f, size.height * 0.48f),
                strokeWidth = stroke,
            )
            drawLine(
                color = FlowDetailBlue,
                start = Offset(size.width * 0.20f, size.height * 0.48f),
                end = Offset(size.width * 0.53f, size.height * 0.80f),
                strokeWidth = stroke,
            )
            drawLine(
                color = FlowDetailBlue,
                start = Offset(size.width * 0.53f, size.height * 0.80f),
                end = Offset(size.width * 0.82f, size.height * 0.74f),
                strokeWidth = stroke,
            )
        } else {
            val barWidth = size.width * 0.18f
            val gap = size.width * 0.10f
            val bottom = size.height * 0.84f
            val left = size.width * 0.14f
            listOf(0.42f, 0.66f, 0.88f).forEachIndexed { index, heightFraction ->
                val h = size.height * heightFraction
                drawRoundRect(
                    color = FlowDetailBlue,
                    topLeft = Offset(left + index * (barWidth + gap), bottom - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.35f, barWidth * 0.35f),
                )
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowAmbiguousResourceRow",
    "@Composable\nprivate fun FlowSummaryManagementRow",
    ambiguous,
    "ambiguous rows",
)

# Summary management rows visible immediately below the user's screenshot.
replace_once(
    ".padding(horizontal = 16.dp, vertical = 13.dp),\n        verticalAlignment = Alignment.CenterVertically,\n        horizontalArrangement = Arrangement.spacedBy(12.dp),\n    ) {\n        Box(\n            modifier = Modifier\n                .size(34.dp)\n                .clip(RoundedCornerShape(9.dp))\n                .background(FlowDetailBlue.copy(alpha = 0.09f)),\n            contentAlignment = Alignment.Center,\n        ) {\n            Text(\"▥\", fontSize = 18.sp, color = FlowDetailBlue, fontWeight = FontWeight.Bold)\n        }",
    ".padding(horizontal = 14.dp, vertical = 10.dp),\n        verticalAlignment = Alignment.CenterVertically,\n        horizontalArrangement = Arrangement.spacedBy(12.dp),\n    ) {\n        Box(\n            modifier = Modifier\n                .size(30.dp)\n                .clip(RoundedCornerShape(9.dp))\n                .background(FlowDetailBlue.copy(alpha = 0.10f)),\n            contentAlignment = Alignment.Center,\n        ) {\n            FlowCandidateGlyph(isVoice = false)\n        }",
    "summary icon/row density",
)
replace_once(
    "Text(group.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)",
    "Text(group.name, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)",
    "summary name font",
)
replace_once("fontSize = 11.5.sp,\n                color = FlowDetailSecondary,", "fontSize = 10.5.sp,\n                color = FlowDetailSecondary,", "summary value font")
replace_once("Text(\"已选 ${summary.packageCount} 个流量包\", fontSize = 10.5.sp, color = FlowDetailTertiary)", "Text(\"已选 ${summary.packageCount} 个流量包\", fontSize = 9.5.sp, color = FlowDetailTertiary)", "summary count font")
replace_once("Text(\"›\", fontSize = 25.sp, color = FlowDetailTertiary)", "Text(\"›\", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FlowDetailTertiary)", "summary chevron")

PATH.write_text(text)
