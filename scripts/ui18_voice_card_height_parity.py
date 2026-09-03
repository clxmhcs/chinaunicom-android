from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/VoiceDashboardScreen.kt')
text = path.read_text()

replacements = [
    (
        '''        Column(\n            modifier = Modifier.padding(20.dp),\n            verticalArrangement = Arrangement.spacedBy(14.dp),\n        ) {''',
        '''        Column(\n            modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.14.dp),\n            verticalArrangement = Arrangement.spacedBy(12.dp),\n        ) {''',
    ),
    (
        '''                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {\n                        account.visibleVoicePackages.forEach { packageValue ->''',
        '''                    Column(verticalArrangement = Arrangement.spacedBy(15.43.dp)) {\n                        account.visibleVoicePackages.forEach { packageValue ->''',
    ),
    (
        '''        verticalArrangement = Arrangement.spacedBy(8.dp),\n    ) {\n        Row(''',
        '''        verticalArrangement = Arrangement.spacedBy(if (showChevron) 8.dp else 6.86.dp),\n    ) {\n        Row(''',
    ),
    (
        '''                fontSize = if (showChevron) 13.5.sp else 12.sp,\n                lineHeight = if (showChevron) 18.sp else 16.sp,''',
        '''                fontSize = if (showChevron) 13.5.sp else 10.29.sp,\n                lineHeight = if (showChevron) 18.sp else 13.71.sp,''',
    ),
    (
        '''                fontSize = if (showChevron) 11.5.sp else 10.sp,\n                lineHeight = 15.sp,''',
        '''                fontSize = if (showChevron) 11.5.sp else 8.57.sp,\n                lineHeight = if (showChevron) 15.sp else 12.86.sp,''',
    ),
    (
        '''                fontSize = 10.sp,\n                color = VoiceSecondary,''',
        '''                fontSize = if (showChevron) 10.sp else 8.57.sp,\n                lineHeight = if (showChevron) 14.sp else 12.29.sp,\n                color = VoiceSecondary,''',
    ),
    (
        '''                fontSize = 10.sp,\n                color = Color(0xFF2196F3),\n                modifier = Modifier\n                    .clip(CircleShape)\n                    .background(Color(0xFF2196F3).copy(alpha = 0.10f))\n                    .padding(horizontal = 4.dp, vertical = 1.dp),''',
        '''                fontSize = if (showChevron) 10.sp else 8.57.sp,\n                lineHeight = if (showChevron) 14.sp else 12.29.sp,\n                color = Color(0xFF2196F3),\n                modifier = Modifier\n                    .clip(CircleShape)\n                    .background(Color(0xFF2196F3).copy(alpha = 0.10f))\n                    .padding(\n                        horizontal = if (showChevron) 4.dp else 3.dp,\n                        vertical = if (showChevron) 1.dp else 0.dp,\n                    ),''',
    ),
    (
        '''                Text("有效期至 $it", fontSize = 10.sp, color = VoiceTertiary, maxLines = 1)''',
        '''                Text(\n                    "有效期至 $it",\n                    fontSize = if (showChevron) 10.sp else 8.57.sp,\n                    lineHeight = if (showChevron) 14.sp else 12.29.sp,\n                    color = VoiceTertiary,\n                    maxLines = 1,\n                )''',
    ),
    (
        '''            VoiceProgressBar(fraction)''',
        '''            VoiceProgressBar(fraction, compact = !showChevron)''',
    ),
    (
        '''private fun VoiceProgressBar(fraction: Double) {\n    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .height(5.dp)''',
        '''private fun VoiceProgressBar(fraction: Double, compact: Boolean = false) {\n    val barHeight = if (compact) 4.29.dp else 5.dp\n    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .height(barHeight)''',
    ),
    (
        '''                .height(5.dp)\n                .clip(CircleShape)\n                .background(''',
        '''                .height(barHeight)\n                .clip(CircleShape)\n                .background(''',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one match, got {count}: {old[:80]!r}')
    text = text.replace(old, new, 1)

path.write_text(text)
