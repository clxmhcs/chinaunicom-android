from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsAccountScreen.kt')
text = path.read_text()


def replace_case(source: str, glyph: str, next_anchor: str, replacement: str) -> str:
    start = f'            CredentialActionGlyph.{glyph} -> {{\n'
    if source.count(start) != 1:
        raise SystemExit(f'expected exactly one {glyph} case, found {source.count(start)}')
    start_index = source.index(start)
    end_index = source.index(next_anchor, start_index)
    return source[:start_index] + replacement + source[end_index:]


login = '''            CredentialActionGlyph.LOGIN -> {
                // Match iOS SF Symbol `person.badge.key.fill`: filled person silhouette
                // with a separated key badge on the lower-right.
                drawCircle(
                    color = CredentialBlue,
                    radius = w * .125f,
                    center = Offset(w * .38f, h * .29f),
                )
                val personBody = Path().apply {
                    moveTo(w * .16f, h * .69f)
                    cubicTo(w * .17f, h * .51f, w * .27f, h * .43f, w * .38f, h * .43f)
                    cubicTo(w * .49f, h * .43f, w * .58f, h * .51f, w * .60f, h * .69f)
                    cubicTo(w * .54f, h * .74f, w * .22f, h * .74f, w * .16f, h * .69f)
                    close()
                }
                drawPath(personBody, CredentialBlue)

                val badgeCenter = Offset(w * .70f, h * .52f)
                drawCircle(
                    color = Color.White,
                    radius = w * .145f,
                    center = badgeCenter,
                )
                drawCircle(
                    color = CredentialBlue,
                    radius = w * .085f,
                    center = badgeCenter,
                )
                drawCircle(
                    color = Color.White,
                    radius = w * .025f,
                    center = badgeCenter,
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .70f, h * .60f),
                    end = Offset(w * .70f, h * .78f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .70f, h * .70f),
                    end = Offset(w * .78f, h * .70f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
'''

key = '''            CredentialActionGlyph.KEY -> {
                // Match iOS SF Symbol `key.fill`: solid round bow, white bore,
                // vertical shaft and two compact teeth.
                drawCircle(
                    color = CredentialBlue,
                    radius = w * .17f,
                    center = Offset(w * .42f, h * .28f),
                )
                drawCircle(
                    color = Color.White,
                    radius = w * .050f,
                    center = Offset(w * .42f, h * .28f),
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .42f, h * .42f),
                    end = Offset(w * .42f, h * .78f),
                    strokeWidth = 5.0.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .42f, h * .59f),
                    end = Offset(w * .55f, h * .67f),
                    strokeWidth = 5.0.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .42f, h * .72f),
                    end = Offset(w * .52f, h * .79f),
                    strokeWidth = 5.0.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
'''

transfer = '''            CredentialActionGlyph.TRANSFER -> {
                // Match iOS SF Symbol `square.and.arrow.up.on.square`.
                val symbolStroke = Stroke(width = 2.15.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .33f, h * .40f),
                    size = Size(w * .49f, h * .48f),
                    cornerRadius = CornerRadius(3.5.dp.toPx()),
                    style = symbolStroke,
                )
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .18f, h * .30f),
                    size = Size(w * .50f, h * .48f),
                    cornerRadius = CornerRadius(3.5.dp.toPx()),
                    style = symbolStroke,
                )
                drawLine(
                    color = CredentialBlue,
                    start = Offset(w * .43f, h * .58f),
                    end = Offset(w * .43f, h * .11f),
                    strokeWidth = 2.15.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val arrow = Path().apply {
                    moveTo(w * .30f, h * .25f)
                    lineTo(w * .43f, h * .11f)
                    lineTo(w * .56f, h * .25f)
                }
                drawPath(arrow, CredentialBlue, style = symbolStroke)
            }
'''

text = replace_case(
    text,
    'LOGIN',
    '            CredentialActionGlyph.KEY -> {\n',
    login,
)
text = replace_case(
    text,
    'KEY',
    '            CredentialActionGlyph.TRANSFER -> {\n',
    key,
)
text = replace_case(
    text,
    'TRANSFER',
    '        }\n    }\n}\n\n@Composable\nprivate fun CredentialImportMobilePage(',
    transfer,
)

path.write_text(text)
