from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowAccountDetailScreen.kt')
text = path.read_text()

old_import = 'import androidx.compose.foundation.layout.Box\n'
new_import = 'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.BoxWithConstraints\n'
if text.count(old_import) != 1:
    raise SystemExit(f'Box import match count={text.count(old_import)}')
text = text.replace(old_import, new_import, 1)

old = '''        if (!packageValue.endDateText.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "有效期：${packageValue.endDateText}",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                ) {
                    if (fraction != null && fraction > 0.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(FlowDetailGreen),
                        )
                    }
                }
            }
        }
'''
new = '''        if (!packageValue.endDateText.isNullOrBlank()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val usageBarWidth = maxWidth * 0.5f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "有效期：${packageValue.endDateText}",
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = FlowDetailSecondary,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .width(usageBarWidth)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                    ) {
                        if (fraction != null && fraction > 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(FlowDetailGreen),
                            )
                        }
                    }
                }
            }
        }
'''
if text.count(old) != 1:
    raise SystemExit(f'package validity block match count={text.count(old)}')
text = text.replace(old, new, 1)
path.write_text(text)
