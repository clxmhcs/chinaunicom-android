from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowHomeScreen.kt')
text = path.read_text()
old = '''    val attribution = account.displayName.trim().takeIf {
        it.isNotEmpty() && it != account.mobile && it != account.packageName
    }
'''
new = '''    val attribution = account.displayName
        .trim()
        .removePrefix("联通号码")
        .trim()
        .takeIf { it.isNotEmpty() && it != account.mobile && it != account.packageName }
'''
if text.count(old) != 1:
    raise SystemExit(f'expected one attribution block, found {text.count(old)}')
path.write_text(text.replace(old, new, 1))
