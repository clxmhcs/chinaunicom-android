from pathlib import Path

screen_path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowAccountDetailScreen.kt')
text = screen_path.read_text()

# Imports.
text = text.replace(
    'import androidx.compose.foundation.clickable\n',
    'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.text.BasicTextField\n',
    1,
)
text = text.replace(
    'import androidx.compose.ui.text.font.FontFamily\n',
    'import androidx.compose.ui.text.TextStyle\nimport androidx.compose.ui.text.font.FontFamily\n',
    1,
)
text = text.replace(
    'import com.clxmhcs.chinaunicom.core.model.DisplayUnit\n',
    'import com.clxmhcs.chinaunicom.core.model.DisplayPlacement\nimport com.clxmhcs.chinaunicom.core.model.DisplayUnit\n',
    1,
)

# Add selected flow package editor state.
old = '''    var editMode by rememberSaveable { mutableStateOf(false) }\n    var selectedGroupID by rememberSaveable { mutableStateOf<String?>(null) }\n    var resourceChoiceKey by rememberSaveable { mutableStateOf<String?>(null) }\n'''
new = '''    var editMode by rememberSaveable { mutableStateOf(false) }\n    var selectedGroupID by rememberSaveable { mutableStateOf<String?>(null) }\n    var selectedFlowPackageID by rememberSaveable { mutableStateOf<String?>(null) }\n    var resourceChoiceKey by rememberSaveable { mutableStateOf<String?>(null) }\n'''
if text.count(old) != 1:
    raise SystemExit(f'state block match count={text.count(old)}')
text = text.replace(old, new, 1)

# Flow candidate opens full PackageEdit parity page; voice keeps existing selector for now.
old = '                                            onClick = { resourceChoiceKey = packageValue.id },\n'
new = '                                            onClick = { selectedFlowPackageID = packageValue.id },\n'
if text.count(old) != 1:
    raise SystemExit(f'flow candidate click match count={text.count(old)}')
text = text.replace(old, new, 1)

# Normal flow package management row also opens the same editor.
old = '''                                    formatter = formatter,\n                                    editMode = editMode,\n                                    onHide = { viewModel.setPackageHidden(account.id, packageValue.id, true) },\n'''
new = '''                                    formatter = formatter,\n                                    editMode = editMode,\n                                    onOpen = { selectedFlowPackageID = packageValue.id },\n                                    onHide = { viewModel.setPackageHidden(account.id, packageValue.id, true) },\n'''
if text.count(old) != 1:
    raise SystemExit(f'package management call match count={text.count(old)}')
text = text.replace(old, new, 1)

# Present full flow package editor before the legacy voice resource selector.
anchor = '''    resourceChoiceKey?.let { key ->\n        FlowResourceKindDialog(\n'''
insert = '''    selectedFlowPackageID?.let { key ->\n        val packageValue = account.packages.firstOrNull { it.id == key }\n            ?: account.sortedPackages.firstOrNull { it.id == key }\n        if (packageValue != null) {\n            FlowPackageEditDialog(\n                account = account,\n                packageValue = packageValue,\n                viewModel = viewModel,\n                onDismiss = { selectedFlowPackageID = null },\n            )\n        }\n    }\n\n    resourceChoiceKey?.let { key ->\n        FlowResourceKindDialog(\n'''
if text.count(anchor) != 1:
    raise SystemExit(f'editor presentation anchor match count={text.count(anchor)}')
text = text.replace(anchor, insert, 1)

# Package management rows become navigable.
old = '''private fun FlowPackageManagementRow(\n    account: UnicomAccount,\n    packageValue: FlowPackage,\n    formatter: FlowFormatter,\n    editMode: Boolean,\n    onHide: () -> Unit,\n'''
new = '''private fun FlowPackageManagementRow(\n    account: UnicomAccount,\n    packageValue: FlowPackage,\n    formatter: FlowFormatter,\n    editMode: Boolean,\n    onOpen: () -> Unit,\n    onHide: () -> Unit,\n'''
if text.count(old) != 1:
    raise SystemExit(f'package management signature match count={text.count(old)}')
text = text.replace(old, new, 1)
old = '''        modifier = Modifier\n            .fillMaxWidth()\n            .padding(horizontal = 16.dp, vertical = 12.dp),\n'''
new = '''        modifier = Modifier\n            .fillMaxWidth()\n            .clickable(onClick = onOpen)\n            .padding(horizontal = 16.dp, vertical = 12.dp),\n'''
# This exact modifier exists in FlowPackageManagementRow once; avoid changing hidden rows by replacing after function declaration.
idx = text.find('private fun FlowPackageManagementRow(')
if idx < 0:
    raise SystemExit('FlowPackageManagementRow not found')
head, tail = text[:idx], text[idx:]
if tail.count(old) < 1:
    raise SystemExit('FlowPackageManagementRow modifier not found')
tail = tail.replace(old, new, 1)
text = head + tail

# Full iOS PackageEditView counterpart.
anchor = '''@Composable\nprivate fun FlowResourceKindDialog(\n'''
editor = r'''@Composable
private fun FlowPackageEditDialog(
    account: UnicomAccount,
    packageValue: FlowPackage,
    viewModel: FlowDisplaySettingsViewModel,
    onDismiss: () -> Unit,
) {
    val preference = account.preference(packageValue)
    var alias by remember(packageValue.id) { mutableStateOf(preference.alias.orEmpty()) }
    var resourceKind by remember(packageValue.id) {
        mutableStateOf(preference.resourceKindOverride ?: ResourceDisplayKind.AUTOMATIC)
    }
    var quotaType by remember(packageValue.id) { mutableStateOf(preference.quotaTypeOverride) }
    var category by remember(packageValue.id) { mutableStateOf(preference.categoryOverride) }
    var isVisibleInDetails by remember(packageValue.id) {
        mutableStateOf(preference.placement != DisplayPlacement.HIDDEN)
    }

    fun save(
        nextAlias: String = alias,
        nextResourceKind: ResourceDisplayKind = resourceKind,
        nextQuotaType: QuotaType = quotaType,
        nextCategory: PackageCategory = category,
        nextVisible: Boolean = isVisibleInDetails,
    ) {
        viewModel.updatePackagePreference(
            accountID = account.id,
            packageKey = packageValue.id,
            alias = nextAlias,
            resourceKind = nextResourceKind,
            quotaType = nextQuotaType,
            category = nextCategory,
            isVisibleInDetails = nextVisible,
        )
    }

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
                FlowPackageEditTopBar(onBack = onDismiss)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "name") {
                        FlowPackageEditSection(title = "名称") {
                            Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                                if (alias.isEmpty()) {
                                    Text(
                                        "显示名称",
                                        fontSize = 15.sp,
                                        color = FlowDetailTertiary.copy(alpha = 0.72f),
                                    )
                                }
                                BasicTextField(
                                    value = alias,
                                    onValueChange = { value ->
                                        alias = value
                                        save(nextAlias = value)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                            Divider(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            )
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("原始名称", fontSize = 14.sp, color = FlowDetailSecondary)
                                Text(
                                    packageValue.originalName,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    color = FlowDetailSecondary,
                                )
                            }
                        }
                    }

                    item(key = "recognition") {
                        FlowPackageEditSection(title = "识别规则") {
                            FlowPackageEditChoiceRow(
                                label = "资源类型",
                                value = resourceKind.title,
                                options = ResourceDisplayKind.entries.map { kind ->
                                    kind.title to {
                                        resourceKind = kind
                                        save(nextResourceKind = kind)
                                    }
                                },
                            )
                            Divider(modifier = Modifier.padding(start = 18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                            FlowPackageEditChoiceRow(
                                label = "流量类型",
                                value = quotaType.title,
                                options = QuotaType.entries.map { type ->
                                    type.title to {
                                        quotaType = type
                                        save(nextQuotaType = type)
                                    }
                                },
                            )
                            Divider(modifier = Modifier.padding(start = 18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                            FlowPackageEditChoiceRow(
                                label = "流量分类",
                                value = category.title,
                                options = PackageCategory.entries.map { item ->
                                    item.title to {
                                        category = item
                                        save(nextCategory = item)
                                    }
                                },
                            )
                        }
                    }

                    item(key = "groups") {
                        FlowPackageEditSection(
                            title = "统计归属",
                            footer = "返回“显示内容”，进入国内流量、校区流量等统计分类，通过复选框选择这个流量包。",
                        ) {
                            val groups = account.groupNamesContaining(packageValue.id)
                            if (groups.isEmpty()) {
                                Text(
                                    "尚未计入任何首页统计分类",
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                    fontSize = 14.sp,
                                    color = FlowDetailSecondary,
                                )
                            } else {
                                groups.forEachIndexed { index, groupName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(19.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(FlowDetailBlue),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Text(groupName, fontSize = 14.sp, color = FlowDetailBlue)
                                    }
                                    if (index < groups.lastIndex) {
                                        Divider(modifier = Modifier.padding(start = 49.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                    }
                                }
                            }
                        }
                    }

                    item(key = "visibility") {
                        FlowPackageEditSection(
                            footer = "隐藏只影响明细列表；已经勾选到统计分类中的流量包仍会参与汇总。",
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("在流量包明细中显示", modifier = Modifier.weight(1f), fontSize = 15.sp)
                                Switch(
                                    checked = isVisibleInDetails,
                                    onCheckedChange = { visible ->
                                        isVisibleInDetails = visible
                                        save(nextVisible = visible)
                                    },
                                )
                            }
                        }
                    }

                    item(key = "restore") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    alias = ""
                                    resourceKind = ResourceDisplayKind.AUTOMATIC
                                    quotaType = QuotaType.AUTOMATIC
                                    category = PackageCategory.AUTOMATIC
                                    isVisibleInDetails = true
                                    viewModel.restorePackagePreference(account.id, packageValue.id)
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
                        ) {
                            Text(
                                "恢复默认设置",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                fontSize = 15.sp,
                                color = FlowDetailBlue,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowPackageEditTopBar(onBack: () -> Unit) {
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
            "编辑流量包",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FlowPackageEditSection(
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
private fun FlowPackageEditChoiceRow(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
            Text(value, fontSize = 15.sp, color = FlowDetailBlue)
            Text("  ⌃⌄", fontSize = 11.sp, color = FlowDetailBlue)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun FlowResourceKindDialog(
'''
if text.count(anchor) != 1:
    raise SystemExit(f'FlowResourceKindDialog anchor match count={text.count(anchor)}')
text = text.replace(anchor, editor, 1)

screen_path.write_text(text)

# Extend persistence bridge to update/restore full PackageEditView preference.
vm_path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowDisplaySettingsViewModel.kt')
vm = vm_path.read_text()
vm = vm.replace(
    'import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference\n',
    'import com.clxmhcs.chinaunicom.core.model.PackageCategory\nimport com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference\nimport com.clxmhcs.chinaunicom.core.model.QuotaType\n',
    1,
)
anchor = '''    fun setResourceKind(accountID: UUID, packageKey: String, kind: ResourceDisplayKind?) {\n        mutatePreference(accountID, packageKey) { preference ->\n            preference.copy(resourceKindOverride = kind)\n        }\n    }\n\n'''
insert = '''    fun setResourceKind(accountID: UUID, packageKey: String, kind: ResourceDisplayKind?) {\n        mutatePreference(accountID, packageKey) { preference ->\n            preference.copy(resourceKindOverride = kind)\n        }\n    }\n\n    fun updatePackagePreference(\n        accountID: UUID,\n        packageKey: String,\n        alias: String,\n        resourceKind: ResourceDisplayKind,\n        quotaType: QuotaType,\n        category: PackageCategory,\n        isVisibleInDetails: Boolean,\n    ) {\n        mutatePreference(accountID, packageKey) { preference ->\n            preference.copy(\n                alias = alias.trim().ifBlank { null },\n                resourceKindOverride = resourceKind.takeUnless { it == ResourceDisplayKind.AUTOMATIC },\n                quotaTypeOverride = quotaType,\n                categoryOverride = category,\n                placement = if (resourceKind == ResourceDisplayKind.VOICE || isVisibleInDetails) {\n                    DisplayPlacement.DETAIL_ONLY\n                } else {\n                    DisplayPlacement.HIDDEN\n                },\n            )\n        }\n    }\n\n    fun restorePackagePreference(accountID: UUID, packageKey: String) {\n        mutateAccount(accountID) { account ->\n            account.copy(displayPreferences = account.displayPreferences.filterNot { it.packageKey == packageKey })\n        }\n    }\n\n'''
if vm.count(anchor) != 1:
    raise SystemExit(f'viewmodel resource kind anchor match count={vm.count(anchor)}')
vm = vm.replace(anchor, insert, 1)
vm_path.write_text(vm)
