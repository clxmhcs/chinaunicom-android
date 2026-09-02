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


replace_once(
    "import androidx.compose.foundation.layout.navigationBarsPadding\nimport androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.navigationBarsPadding\nimport androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.padding\n",
    "offset import",
)

old_items = '''            item(key = "refresh-time") {
                Text(
                    text = "刷新时间 ${flowDetailRefreshTime(account.lastUpdatedAt)}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = FlowDetailTertiary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }

            item(key = "account-header") {
                FlowAccountHeaderCard(account = account, mobile = mobile)
            }'''

new_items = '''            item(key = "account-header") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FlowAccountHeaderCard(account = account, mobile = mobile)
                    Text(
                        text = "刷新时间 ${flowDetailRefreshTime(account.lastUpdatedAt)}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = (-14).dp)
                            .padding(end = 4.dp),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = FlowDetailTertiary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }'''
replace_once(old_items, new_items, "refresh overlay")

nav = '''@Composable
private fun FlowAccountDetailNavigationHeader(
    mobile: String,
    menuExpanded: Boolean,
    onBack: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDisplaySettings: () -> Unit,
    onEditCard: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 7.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light)
            }
        }

        Text(
            text = mobile,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onMenuOpen),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 7.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, FlowDetailBlue, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(FlowDetailBlue, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(text = { Text("刷新") }, onClick = onRefresh)
                DropdownMenuItem(text = { Text("显示设置") }, onClick = onDisplaySettings)
                DropdownMenuItem(text = { Text("编辑卡片") }, onClick = onEditCard)
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowAccountDetailNavigationHeader(",
    "@Composable\nprivate fun FlowAccountHeaderCard",
    nav,
    "navigation header",
)

account_header = '''@Composable
private fun FlowAccountHeaderCard(account: UnicomAccount, mobile: String) {
    val statusText = when {
        !account.lastErrorMessage.isNullOrBlank() -> "刷新失败"
        account.lastUpdatedAt != null -> "刷新成功"
        else -> ""
    }
    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(FlowDetailBlue.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(17.dp)
                        .height(19.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(FlowDetailBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(9.dp)
                            .height(7.dp)
                            .border(1.dp, Color.White, RoundedCornerShape(1.dp)),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        mobile,
                        fontSize = 15.1.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (statusText.isNotEmpty()) {
                        Text(
                            statusText,
                            modifier = Modifier.weight(1f),
                            fontSize = 10.07.sp,
                            lineHeight = 13.sp,
                            color = FlowDetailTertiary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    account.packageName.ifBlank { "联通套餐" },
                    fontSize = 13.3.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                )
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowAccountHeaderCard",
    "@Composable\nprivate fun FlowPackageListCard",
    account_header,
    "account header",
)

package_list = '''@Composable
private fun FlowPackageListCard(
    account: UnicomAccount,
    formatter: FlowFormatter,
    onManageDisplay: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("套餐包明细", fontSize = 11.3.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "管理显示",
                    modifier = Modifier.clickable(onClick = onManageDisplay),
                    fontSize = 10.sp,
                    color = FlowDetailBlue,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (account.visibleDetailPackages.isEmpty()) {
                Text(
                    if (account.packages.isEmpty()) "此号码未订购流量包，套餐内也未包含流量。" else "所有套餐包均已隐藏",
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontSize = 10.sp,
                    color = FlowDetailSecondary,
                )
            } else {
                account.visibleDetailPackages.forEachIndexed { index, packageValue ->
                    FlowPackageDetailRow(account, packageValue, formatter)
                    if (index < account.visibleDetailPackages.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    }
                }
            }
        }
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowPackageListCard(",
    "@Composable\nprivate fun FlowPackageDetailRow",
    package_list,
    "package list",
)

package_row = '''@Composable
private fun FlowPackageDetailRow(
    account: UnicomAccount,
    packageValue: FlowPackage,
    formatter: FlowFormatter,
) {
    val quotaType = account.quotaType(packageValue)
    val fraction = packageValue.detailDisplayFraction(quotaType)
    val remainingText = if (quotaType == QuotaType.UNLIMITED) "∞ 不限量" else formatter.string(packageValue.remainingMB)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    account.displayName(packageValue),
                    fontSize = 12.5.sp,
                    lineHeight = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlowTinyTag(if (quotaType == QuotaType.UNLIMITED) "不限量" else "有限", FlowDetailBlue)
                    FlowTinyTag(flowCategoryShortTitle(account.category(packageValue)), FlowDetailBlue)
                    packageValue.resolvedShareScope.title?.let { FlowTinyTag(it, FlowTagPink) }
                    packageValue.resolvedCarryForwardScope.title?.let { FlowTinyTag(it, FlowTagPurple) }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    remainingText,
                    fontSize = 11.3.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                Text(
                    if (quotaType == QuotaType.UNLIMITED) "已用 ${formatter.string(packageValue.usedMB)}" else "剩余",
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
            }
            Text(
                "›",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FlowDetailTertiary,
            )
        }

        if (!packageValue.endDateText.isNullOrBlank()) {
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
    }
}'''
replace_section(
    "@Composable\nprivate fun FlowPackageDetailRow(",
    "@Composable\nprivate fun FlowTinyTag",
    package_row,
    "package detail row",
)

replace_once(
    'private fun FlowTinyTag(text: String, tint: Color) {\n    Text(text, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)\n}',
    'private fun FlowTinyTag(text: String, tint: Color) {\n    Text(text, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold, color = tint)\n}',
    "tiny tag",
)

PATH.write_text(text)
