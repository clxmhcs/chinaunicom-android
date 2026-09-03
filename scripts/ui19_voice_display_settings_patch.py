from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/VoiceDashboardScreen.kt')
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one match, got {count}: {old[:120]!r}')
    text = text.replace(old, new, 1)


def replace_n(old: str, new: str, expected: int) -> None:
    global text
    count = text.count(old)
    if count != expected:
        raise SystemExit(f'expected {expected} matches, got {count}: {old[:120]!r}')
    text = text.replace(old, new)

replace_once(
    'import androidx.compose.foundation.Image\n',
    'import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.Image\n',
)

replace_once(
'''            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),''',
'''            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),''',
)

replace_once(
'''                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = VoiceSecondary,''',
'''                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = VoiceSecondary,''',
)
replace_once(
'''                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = VoiceSecondary,''',
'''                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = VoiceSecondary,''',
)

replace_once(
'''                .size(52.dp)
                .clickable(onClick = onBack),''',
'''                .size(44.dp)
                .clickable(onClick = onBack),''',
)
replace_once(
'''                Text("‹", fontSize = 43.sp, lineHeight = 43.sp, fontWeight = FontWeight.Light)''',
'''                Text("‹", fontSize = 37.sp, lineHeight = 37.sp, fontWeight = FontWeight.Light)''',
)
replace_once(
'''            fontSize = 19.sp,
            lineHeight = 24.sp,''',
'''            fontSize = 17.sp,
            lineHeight = 21.sp,''',
)
replace_once(
'''                .height(52.dp)
                .width(78.dp)
                .clickable(onClick = onToggleEdit),
            shape = RoundedCornerShape(26.dp),''',
'''                .height(44.dp)
                .width(74.dp)
                .clickable(onClick = onToggleEdit),
            shape = RoundedCornerShape(22.dp),''',
)
replace_once(
'''                Text(if (editMode) "完成" else "编辑", fontSize = 17.sp, color = VoiceBlue)''',
'''                Text(if (editMode) "完成" else "编辑", fontSize = 14.5.sp, color = VoiceBlue)''',
)

replace_once(
'''        modifier = Modifier.padding(start = 18.dp, top = 6.dp),
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,''',
'''        modifier = Modifier.padding(start = 18.dp, top = 2.dp),
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,''',
)

replace_once(
'''        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {''',
'''        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {''',
)
replace_once(
'''                    modifier = Modifier.padding(vertical = 8.dp),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = VoiceSecondary,''',
'''                    modifier = Modifier.padding(vertical = 6.dp),
                    fontSize = 10.5.sp,
                    lineHeight = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoiceSecondary,''',
)
replace_once('''                        icon = "▮▮▮",''', '''                        isVoice = false,''')
replace_once('''                        icon = "☎",''', '''                        isVoice = true,''')

replace_once(
'''private fun VoiceAmbiguousResourceRow(
    name: String,
    kind: String,
    value: String,
    icon: String,
    onClick: () -> Unit,
) {''',
'''private fun VoiceAmbiguousResourceRow(
    name: String,
    kind: String,
    value: String,
    isVoice: Boolean,
    onClick: () -> Unit,
) {''',
)
replace_once(
'''    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),''',
'''    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),''',
)
replace_once(
'''                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))''',
'''                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))''',
)
replace_once(
'''            Text(icon, fontSize = 18.sp, color = VoiceBlue, fontWeight = FontWeight.Bold)''',
'''            VoiceCandidateGlyph(isVoice = isVoice)''',
)
replace_once(
'''            Text(name, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(kind, fontSize = 11.5.sp, color = VoiceSecondary)''',
'''            Text(name, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(kind, fontSize = 10.5.sp, lineHeight = 13.sp, color = VoiceSecondary)''',
)
replace_once(
'''        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VoiceSecondary, maxLines = 2, textAlign = TextAlign.End)
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f))''',
'''        Text(
            value,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = VoiceSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        Text(
            "›",
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
        )''',
)

needle = '''}\n\n@Composable\nprivate fun VoicePackageManagementCard('''
glyph = '''}\n\n@Composable\nprivate fun VoiceCandidateGlyph(isVoice: Boolean) {\n    Canvas(modifier = Modifier.size(17.dp)) {\n        if (isVoice) {\n            val stroke = size.minDimension * 0.19f\n            drawLine(\n                color = VoiceBlue,\n                start = Offset(size.width * 0.25f, size.height * 0.18f),\n                end = Offset(size.width * 0.20f, size.height * 0.48f),\n                strokeWidth = stroke,\n            )\n            drawLine(\n                color = VoiceBlue,\n                start = Offset(size.width * 0.20f, size.height * 0.48f),\n                end = Offset(size.width * 0.53f, size.height * 0.80f),\n                strokeWidth = stroke,\n            )\n            drawLine(\n                color = VoiceBlue,\n                start = Offset(size.width * 0.53f, size.height * 0.80f),\n                end = Offset(size.width * 0.82f, size.height * 0.74f),\n                strokeWidth = stroke,\n            )\n        } else {\n            val barWidth = size.width * 0.18f\n            val gap = size.width * 0.10f\n            val bottom = size.height * 0.84f\n            val left = size.width * 0.14f\n            listOf(0.42f, 0.66f, 0.88f).forEachIndexed { index, heightFraction ->\n                val h = size.height * heightFraction\n                drawRoundRect(\n                    color = VoiceBlue,\n                    topLeft = Offset(left + index * (barWidth + gap), bottom - h),\n                    size = androidx.compose.ui.geometry.Size(barWidth, h),\n                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.35f, barWidth * 0.35f),\n                )\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun VoicePackageManagementCard('''
replace_once(needle, glyph)

# SwiftUI reuses VoicePackageRow typography in the dashboard and detail List.
replace_once(
'''        modifier = modifier.padding(vertical = if (showChevron) 10.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (showChevron) 8.dp else 6.86.dp),''',
'''        modifier = modifier.padding(vertical = if (showChevron) 6.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.86.dp),''',
)
replace_once(
'''                fontSize = if (showChevron) 13.5.sp else 10.29.sp,
                lineHeight = if (showChevron) 18.sp else 13.71.sp,''',
'''                fontSize = 10.29.sp,
                lineHeight = 13.71.sp,''',
)
replace_once(
'''                fontSize = if (showChevron) 11.5.sp else 8.57.sp,
                lineHeight = if (showChevron) 15.sp else 12.86.sp,''',
'''                fontSize = 8.57.sp,
                lineHeight = 12.86.sp,''',
)
replace_once(
'''                Text("›", fontSize = 24.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f))''',
'''                Text(
                    "›",
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                )''',
)
replace_n(
'''                fontSize = if (showChevron) 10.sp else 8.57.sp,
                lineHeight = if (showChevron) 14.sp else 12.29.sp,''',
'''                fontSize = 8.57.sp,
                lineHeight = 12.29.sp,''',
2,
)
replace_once(
'''                        horizontal = if (showChevron) 4.dp else 3.dp,
                        vertical = if (showChevron) 1.dp else 0.dp,''',
'''                        horizontal = 3.dp,
                        vertical = 0.dp,''',
)
replace_once(
'''                    fontSize = if (showChevron) 10.sp else 8.57.sp,
                    lineHeight = if (showChevron) 14.sp else 12.29.sp,''',
'''                    fontSize = 8.57.sp,
                    lineHeight = 12.29.sp,''',
)
replace_once(
'''            VoiceProgressBar(fraction, compact = !showChevron)''',
'''            VoiceProgressBar(fraction, compact = true)''',
)

replace_once(
'''        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {''',
'''        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {''',
)

path.write_text(text)
print('UI-19 patch applied')
