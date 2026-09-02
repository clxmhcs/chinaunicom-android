from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowHomeScreen.kt"

text = PATH.read_text()

old_top = "top = ChinaUnicomDimensions.AccountCardTop,"
new_top = "top = 15.2.dp,"
if text.count(old_top) != 1:
    raise SystemExit(f"expected exactly one card-top anchor, found {text.count(old_top)}")
text = text.replace(old_top, new_top, 1)

old_spacing = "modifier = Modifier.padding(top = 18.dp),\n                        verticalArrangement = Arrangement.spacedBy(17.dp),"
new_spacing = "modifier = Modifier.padding(top = 18.dp),\n                        verticalArrangement = Arrangement.spacedBy(14.1667.dp),"
if text.count(old_spacing) != 1:
    raise SystemExit(f"expected exactly one flow-summary spacing anchor, found {text.count(old_spacing)}")
text = text.replace(old_spacing, new_spacing, 1)

PATH.write_text(text)
