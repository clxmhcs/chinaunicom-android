from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowAccountDetailScreen.kt')
text = path.read_text()
marker = 'UI-17: iOS SummaryGroupEditView parity'
if marker in text:
    print('UI-17 already applied')
    raise SystemExit(0)

old_call = '''            FlowSummaryGroupEditorDialog(\n                account = account,\n                group = group,\n                viewModel = viewModel,\n                onDismiss = { selectedGroupID = null },\n            )\n'''
new_call = '''            FlowSummaryGroupEditorDialog(\n                account = account,\n                group = group,\n                formatter = formatter,\n                viewModel = viewModel,\n                onDismiss = { selectedGroupID = null },\n            )\n'''
if text.count(old_call) != 1:
    raise SystemExit(f'caller match count={text.count(old_call)}')
text = text.replace(old_call, new_call, 1)

start_anchor = '@Composable\nprivate fun FlowSummaryGroupEditorDialog('
end_anchor = '@Composable\nprivate fun FlowPackageEditDialog('
start = text.index(start_anchor)
end = text.index(end_anchor, start)

new_block = r'''/** UI-17: iOS SummaryGroupEditView parity. */
@Composable
private fun FlowSummaryGroupEditorDialog(
    account: UnicomAccount,
    group: FlowSummaryGroup,
    formatter: FlowFormatter,
    viewModel: FlowDisplaySettingsViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var selected by remember(group.id) { mutableStateOf(group.packageKeys.toSet()) }
    var visible by remember(group.id) { mutableStateOf(group.isVisibleOnHome) }
    var suggestedNamesExpanded by remember(group.id) { mutableStateOf(false) }
    var showingDeleteConfirmation by remember(group.id) { mutableStateOf(false) }
    val suggestedNames = remember { listOf("国内流量", "省内流量", "小区流量", "校区流量", "校园流量") }

    fun save(
        nextName: String = name,
        nextSelected: Set<String> = selected,
        nextVisible: Boolean = visible,
    ) {
        viewModel.updateSummaryGroup(
            account.id,
            group.copy(
                name = nextName,
                packageKeys = account.visibleDetailPackages.filter { it.id in nextSelected }.map { it.id },
                isVisibleOnHome = nextVisible,
            ),
        )
    }

    val previewGroup = group.copy(
        name = name,
        packageKeys = account.visibleDetailPackages.filter { it.id in selected }.map { it.id },
        isVisibleOnHome = visible,
    )
    val preview = account.summary(previewGroup)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x55000000)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                    .background(FlowDetailBackground),
            ) {
                FlowSummaryGroupEditTopBar(onBack = onDismiss)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "name") {
                        FlowSummaryEditSection(title = "分类名称") {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (name.isBlank()) {
                                            Text(
                                                "例如：国内流量、校区流量",
                                                fontSize = 15.sp,
                                                color = FlowDetailTertiary.copy(alpha = 0.72f),
                                            )
                                        }
                                        BasicTextField(
                                            value = name,
                                            onValueChange = { value ->
                                                name = value
                                                save(nextName = value)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.clickable { suggestedNamesExpanded = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        Text("⌃\n⌄", fontSize = 11.sp, lineHeight = 8.sp, color = FlowDetailBlue)
                                        Text("选择", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FlowDetailBlue)
                                    }
                                }
                                DropdownMenu(
                                    expanded = suggestedNamesExpanded,
                                    onDismissRequest = { suggestedNamesExpanded = false },
                                ) {
                                    suggestedNames.forEach { suggestedName ->
                                        DropdownMenuItem(
                                            text = { Text(suggestedName, fontSize = 15.sp) },
                                            onClick = {
                                                name = suggestedName
                                                save(nextName = suggestedName)
                                                suggestedNamesExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "preview") {
                        FlowSummaryEditSection(title = "汇总预览") {
                            FlowSummaryGroupEditPreview(summary = preview, formatter = formatter)
                        }
                    }

                    item(key = "packages") {
                        FlowSummaryEditSection(
                            title = "选择流量包（可多选）",
                            footer = "已选择 ${selected.size} 个流量包。有限套餐会汇总已用量和总量；包含不限量套餐时，首页显示“本月已用 / 不限量”。",
                        ) {
                            account.visibleDetailPackages.forEachIndexed { index, packageValue ->
                                FlowSummaryPackageSelectionRow(
                                    account = account,
                                    packageValue = packageValue,
                                    formatter = formatter,
                                    isSelected = packageValue.id in selected,
                                    onToggle = {
                                        val next = if (packageValue.id in selected) {
                                            selected - packageValue.id
                                        } else {
                                            selected + packageValue.id
                                        }
                                        selected = next
                                        save(nextSelected = next)
                                    },
                                )
                                if (index < account.visibleDetailPackages.lastIndex) {
                                    Divider(
                                        modifier = Modifier.padding(start = 54.dp, end = 18.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                    )
                                }
                            }
                        }
                    }

                    item(key = "home") {
                        FlowSummaryEditSection(title = "首页") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("在首页卡片显示", modifier = Modifier.weight(1f), fontSize = 15.sp)
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { value ->
                                        visible = value
                                        save(nextVisible = value)
                                    },
                                )
                            }
                        }
                    }

                    item(key = "delete") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showingDeleteConfirmation = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
                        ) {
                            Text(
                                "删除此统计分类",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                fontSize = 15.sp,
                                color = Color(0xFFFF3B30),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showingDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showingDeleteConfirmation = false },
            title = { Text("删除统计分类？") },
            text = { Text("只会删除本机的汇总设置，不会修改联通套餐。") },
            confirmButton = {
                TextButton(onClick = {
                    showingDeleteConfirmation = false
                    viewModel.deleteSummaryGroup(account.id, group.id)
                    onDismiss()
                }) { Text("删除", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FlowSummaryGroupEditTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 18.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light)
            }
        }
        Text(
            "编辑统计分类",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FlowSummaryEditSection(
    title: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 18.dp),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = FlowDetailSecondary,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
        ) {
            Column { content() }
        }
        if (footer != null) {
            Text(
                footer,
                modifier = Modifier.padding(horizontal = 18.dp),
                fontSize = 11.5.sp,
                lineHeight = 15.5.sp,
                color = FlowDetailSecondary,
            )
        }
    }
}

@Composable
private fun FlowSummaryGroupEditPreview(summary: FlowSummary, formatter: FlowFormatter) {
    val valueText = when {
        summary.packageCount == 0 -> "未选择"
        summary.isUnlimited -> "${formatter.string(summary.usedMB)} / 不限量"
        else -> "${formatter.string(summary.usedMB)} / ${formatter.string(summary.totalMB)}"
    }
    val fraction = summary.usedFraction?.toFloat()?.coerceIn(0f, 1f) ?: 0f
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.name.ifBlank { "未命名分类" },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                valueText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FlowDetailSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(if (summary.packageCount == 0) Color(0xFFFF3B30).copy(alpha = 0.12f) else FlowDetailBlue.copy(alpha = 0.12f)),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(FlowDetailBlue),
                )
            }
        }
    }
}

@Composable
private fun FlowSummaryPackageSelectionRow(
    account: UnicomAccount,
    packageValue: FlowPackage,
    formatter: FlowFormatter,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                account.displayName(packageValue),
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    account.quotaType(packageValue).title,
                    account.category(packageValue).title,
                    packageValue.resolvedShareScope.title,
                    packageValue.resolvedCarryForwardScope.title,
                ).joinToString(" · "),
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                color = FlowDetailSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (account.quotaType(packageValue) == QuotaType.UNLIMITED) {
                "已用\n${formatter.string(packageValue.usedMB)}"
            } else {
                "${formatter.string(packageValue.usedMB)}\n/ ${formatter.string(packageValue.totalMB)}"
            },
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlowDetailSecondary,
            textAlign = TextAlign.End,
            maxLines = 2,
        )
    }
}

'''

text = text[:start] + new_block + text[end:]
path.write_text(text)
print('UI-17 patch applied')
