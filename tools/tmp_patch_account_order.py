from pathlib import Path
import re

path = Path("app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsRootIosScreen.kt")
text = path.read_text()

import_replacements = {
    "import androidx.compose.foundation.Image\n": "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
    "import androidx.compose.ui.graphics.Color\n": "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.ColorFilter\n",
    "import androidx.compose.ui.platform.LocalContext\n": "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity\n",
    "import java.util.UUID\n": "import java.util.UUID\nimport kotlin.math.roundToInt\n",
}
for old, new in import_replacements.items():
    if old not in text:
        raise SystemExit(f"missing import anchor: {old!r}")
    text = text.replace(old, new, 1)

call_pattern = re.compile(
    r"(?m)^(\s*)settings = settings,\n"
    r"\1onMove = settingsViewModel::moveAccount,\n"
)
text, call_count = call_pattern.subn(
    lambda m: f"{m.group(1)}settings = settings,\n"
              f"{m.group(1)}locationForNumber = m11cViewModel::cachedLocation,\n"
              f"{m.group(1)}onMove = settingsViewModel::moveAccount,\n",
    text,
    count=1,
)
if call_count != 1:
    raise SystemExit(f"account-order call semantic anchor count={call_count}")

start_marker = "@Composable\nprivate fun IosAccountOrderScreen("
end_marker = "\n@Composable\nprivate fun IosBalanceGroupingScreen("
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit(f"account-order function markers start={start} end={end}")

new_function = '''@Composable
private fun IosAccountOrderScreen(
    accounts: List<UnicomAccount>,
    settings: AppSettings,
    locationForNumber: (String) -> String?,
    onMove: (UUID, Int) -> Unit,
    onBack: () -> Unit,
) {
    val rowHeight = 68.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsGroupedBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IosSubpageHeader("自定义排序", onBack)
        Spacer(Modifier.height(18.dp))

        Text(
            text = "首页卡片顺序",
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = SettingsSecondary,
            modifier = Modifier.padding(start = 20.dp, bottom = 9.dp),
        )

        if (accounts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
            ) {
                Text(
                    "暂无手机账号",
                    color = SettingsSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
            ) {
                Column {
                    accounts.forEachIndexed { index, account ->
                        val location = locationForNumber(account.mobile)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: account.displayName.trim()
                                .takeIf { it.isNotEmpty() && it != "联通号码" }
                            ?: "归属地未知"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.receipt_sim_card_icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = accountOrderMobileText(account.mobile, settings),
                                        fontSize = 17.sp,
                                        lineHeight = 21.sp,
                                        color = Color.Black,
                                    )
                                    Spacer(Modifier.size(5.dp))
                                    Image(
                                        painter = painterResource(R.drawable.china_unicom_knot_watermark),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        colorFilter = ColorFilter.tint(SettingsDanger),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Text(
                                    text = location,
                                    fontSize = 12.5.sp,
                                    lineHeight = 16.sp,
                                    color = SettingsSecondary,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            IosAccountDragHandle(
                                accountID = account.id,
                                rowHeightPx = rowHeightPx,
                                onMove = onMove,
                            )
                        }
                        if (index < accounts.lastIndex) IosDivider(start = 55.dp)
                    }
                }
            }
        }

        Text(
            text = "按住右侧拖动按钮调整顺序。修改会立即保存，并同步用于首页显示和刷新全部的号码顺序。",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = SettingsSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 14.dp, top = 10.dp),
        )
    }
}

private fun accountOrderMobileText(number: String, settings: AppSettings): String {
    val value = number.trim()
    if (!settings.hideMobileMiddleDigits || value.length < 7) return value
    return "${value.take(3)} **** ${value.takeLast(4)}"
}

@Composable
private fun IosAccountDragHandle(
    accountID: UUID,
    rowHeightPx: Float,
    onMove: (UUID, Int) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .size(width = 34.dp, height = 44.dp)
            .pointerInput(accountID, rowHeightPx) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = {
                        val delta = (dragDistance / rowHeightPx).roundToInt()
                        if (delta != 0) onMove(accountID, delta)
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                    onVerticalDrag = { _, dragAmount -> dragDistance += dragAmount },
                )
            },
    ) {
        val strokeWidth = 1.55.dp.toPx()
        repeat(3) { index ->
            val y = size.height * (0.37f + index * 0.13f)
            drawLine(
                color = SettingsChevron,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, y),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
'''

text = text[:start] + new_function + text[end:]
path.write_text(text)
