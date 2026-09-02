from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_flow() -> None:
    path = ROOT / "app/src/main/java/com/clxmhcs/chinaunicom/ui/FlowHomeScreen.kt"
    text = path.read_text()

    text = replace_once(
        text,
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\n",
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui.platform.LocalDensity\n",
        "flow imports",
    )
    text = replace_once(
        text,
        "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
        "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
        "flow IntSize import",
    )
    text = replace_once(
        text,
        "    val cardSurface = MaterialTheme.colorScheme.surface\n    val lastErrorMessage = account.lastErrorMessage\n\n    Box(\n",
        "    val cardSurface = MaterialTheme.colorScheme.surface\n    val lastErrorMessage = account.lastErrorMessage\n    var cardSize by remember { mutableStateOf(IntSize.Zero) }\n    val watermarkSide = with(LocalDensity.current) {\n        (minOf(cardSize.width, cardSize.height) * 0.74f).toDp()\n    }\n\n    Box(\n",
        "flow card size state",
    )
    text = replace_once(
        text,
        "    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .shadow(\n                elevation = 15.dp,\n                shape = shape,\n",
        "    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .onSizeChanged { cardSize = it }\n            .shadow(\n                elevation = 15.dp,\n                shape = shape,\n",
        "flow card onSizeChanged",
    )
    text = replace_once(
        text,
        "            modifier = Modifier\n                .align(Alignment.Center)\n                .fillMaxWidth(0.48f),\n            alpha = 0.045f,\n",
        "            modifier = Modifier\n                .align(Alignment.Center)\n                .size(watermarkSide),\n            alpha = 0.045f,\n",
        "flow adaptive watermark",
    )
    path.write_text(text)


def patch_voice() -> None:
    path = ROOT / "app/src/main/java/com/clxmhcs/chinaunicom/ui/VoiceDashboardScreen.kt"
    text = path.read_text()

    text = replace_once(
        text,
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.alpha\n",
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.alpha\nimport androidx.compose.ui.layout.onSizeChanged\nimport androidx.compose.ui.platform.LocalDensity\n",
        "voice imports",
    )
    text = replace_once(
        text,
        "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
        "import androidx.compose.ui.unit.IntSize\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n",
        "voice IntSize import",
    )
    text = replace_once(
        text,
        "    val mobileText = if (settings.hideMobileMiddleDigits) voiceMaskedMobile(account.mobile) else account.mobile\n    val cardSurface = MaterialTheme.colorScheme.surface\n\n    Box(\n",
        "    val mobileText = if (settings.hideMobileMiddleDigits) voiceMaskedMobile(account.mobile) else account.mobile\n    val cardSurface = MaterialTheme.colorScheme.surface\n    var cardSize by remember { mutableStateOf(IntSize.Zero) }\n    val watermarkSide = with(LocalDensity.current) {\n        (minOf(cardSize.width, cardSize.height) * 0.74f).toDp()\n    }\n\n    Box(\n",
        "voice card size state",
    )
    text = replace_once(
        text,
        "    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .alpha(if (refreshState is RefreshState.Loading) 0.90f else 1f)\n",
        "    Box(\n        modifier = Modifier\n            .fillMaxWidth()\n            .onSizeChanged { cardSize = it }\n            .alpha(if (refreshState is RefreshState.Loading) 0.90f else 1f)\n",
        "voice card onSizeChanged",
    )
    text = replace_once(
        text,
        "            modifier = Modifier\n                .align(Alignment.Center)\n                .fillMaxWidth(0.45f),\n            alpha = 0.045f,\n",
        "            modifier = Modifier\n                .align(Alignment.Center)\n                .size(watermarkSide),\n            alpha = 0.045f,\n",
        "voice adaptive watermark",
    )
    path.write_text(text)


patch_flow()
patch_voice()
print("UI-10 adaptive card watermark patch applied")
