from pathlib import Path

path = Path("app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsRootIosScreen.kt")
text = path.read_text()

old_state = """    val balanceState by settingsViewModel.balanceState.collectAsState()\n    val accounts = appState.accounts.sortedBy { it.sortOrder }\n"""
new_state = """    val balanceState by settingsViewModel.balanceState.collectAsState()\n    val balanceRefreshPolicy by settingsViewModel.balanceRefreshPolicy.collectAsState()\n    val accounts = appState.accounts.sortedBy { it.sortOrder }\n"""
if text.count(old_state) != 1:
    raise SystemExit(f"state anchor count={text.count(old_state)}")
text = text.replace(old_state, new_state, 1)

old_route = """        SettingsIosPage.BALANCE_GROUPING -> {\n            IosBalanceGroupingScreen(\n                accounts = accounts,\n                groups = balanceState.balanceAccountGroups,\n                settings = settings,\n                onAddGroup = settingsViewModel::addBalanceGroup,\n                onDeleteGroup = settingsViewModel::deleteBalanceGroup,\n                onToggleMember = settingsViewModel::toggleBalanceGroupMember,\n                onBack = closePage,\n            )\n            return\n        }\n"""
new_route = """        SettingsIosPage.BALANCE_GROUPING -> {\n            IosBalanceGroupingRefinedScreen(\n                accounts = accounts,\n                groups = balanceState.balanceAccountGroups,\n                settings = settings,\n                locationFor = m11cViewModel::cachedLocation,\n                isUnicomFor = { number -> m11cViewModel.resolvedCarrierTitle(number) == \"联通\" },\n                balanceRefreshIntervalMinutes = balanceRefreshPolicy.intervalMinutes,\n                onAddGroup = settingsViewModel::addBalanceGroup,\n                onDeleteGroup = settingsViewModel::deleteBalanceGroup,\n                onToggleMember = settingsViewModel::toggleBalanceGroupMember,\n                onBack = closePage,\n            )\n            return\n        }\n"""
if text.count(old_route) != 1:
    raise SystemExit(f"route anchor count={text.count(old_route)}")
text = text.replace(old_route, new_route, 1)

path.write_text(text)
