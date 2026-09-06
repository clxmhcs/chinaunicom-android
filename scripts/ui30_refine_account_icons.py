from pathlib import Path
p = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsAccountScreen.kt')
s = p.read_text()
old = '''            CredentialActionGlyph.LOGIN -> {
                drawCircle(CredentialBlue, radius = w * .14f, center = Offset(w * .38f, h * .33f))
                drawArc(
                    color = CredentialBlue,
                    startAngle = 198f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = Offset(w * .17f, h * .38f),
                    size = Size(w * .42f, h * .34f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(CredentialBlue, radius = w * .08f, center = Offset(w * .68f, h * .47f), style = stroke)
                drawLine(CredentialBlue, Offset(w * .68f, h * .55f), Offset(w * .68f, h * .76f), strokeWidth, StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .68f, h * .68f), Offset(w * .76f, h * .68f), strokeWidth, StrokeCap.Round)
            }
            CredentialActionGlyph.KEY -> {
                drawCircle(CredentialBlue, radius = w * .12f, center = Offset(w * .42f, h * .30f), style = Stroke(width = 4.dp.toPx()))
                drawLine(CredentialBlue, Offset(w * .42f, h * .42f), Offset(w * .42f, h * .78f), 5.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .42f, h * .61f), Offset(w * .56f, h * .70f), 5.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .42f, h * .72f), Offset(w * .53f, h * .81f), 5.dp.toPx(), StrokeCap.Round)
            }
            CredentialActionGlyph.TRANSFER -> {
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .18f, h * .37f),
                    size = Size(w * .56f, h * .45f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .30f, h * .47f),
                    size = Size(w * .52f, h * .40f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawLine(CredentialBlue, Offset(w * .50f, h * .12f), Offset(w * .50f, h * .57f), strokeWidth, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(w * .36f, h * .27f)
                    lineTo(w * .50f, h * .12f)
                    lineTo(w * .64f, h * .27f)
                }
                drawPath(arrow, CredentialBlue, style = stroke)
            }
'''
new = '''            CredentialActionGlyph.LOGIN -> {
                // Match iOS person.badge.key: solid head/shoulders with a compact key badge.
                drawCircle(CredentialBlue, radius = w * .105f, center = Offset(w * .37f, h * .30f))
                val torso = Path().apply {
                    moveTo(w * .18f, h * .67f)
                    cubicTo(w * .20f, h * .49f, w * .30f, h * .43f, w * .40f, h * .43f)
                    cubicTo(w * .50f, h * .43f, w * .56f, h * .51f, w * .57f, h * .68f)
                    close()
                }
                drawPath(torso, CredentialBlue)
                drawCircle(CredentialBlue, radius = w * .085f, center = Offset(w * .67f, h * .46f))
                drawCircle(Color.White, radius = w * .028f, center = Offset(w * .67f, h * .46f))
                drawLine(CredentialBlue, Offset(w * .67f, h * .54f), Offset(w * .67f, h * .74f), 3.2.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .67f, h * .65f), Offset(w * .75f, h * .65f), 3.2.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .67f, h * .72f), Offset(w * .72f, h * .77f), 3.2.dp.toPx(), StrokeCap.Round)
            }
            CredentialActionGlyph.KEY -> {
                // Match iOS key.fill silhouette: filled bow, thick shaft and two teeth.
                drawCircle(CredentialBlue, radius = w * .125f, center = Offset(w * .40f, h * .28f))
                drawCircle(Color.White, radius = w * .045f, center = Offset(w * .40f, h * .28f))
                drawLine(CredentialBlue, Offset(w * .40f, h * .39f), Offset(w * .40f, h * .78f), 6.dp.toPx(), StrokeCap.Round)
                drawLine(CredentialBlue, Offset(w * .40f, h * .62f), Offset(w * .54f, h * .68f), 6.dp.toPx(), StrokeCap.Butt)
                drawLine(CredentialBlue, Offset(w * .40f, h * .73f), Offset(w * .50f, h * .80f), 6.dp.toPx(), StrokeCap.Butt)
            }
            CredentialActionGlyph.TRANSFER -> {
                // Match iOS square.and.arrow.up.on.square: two stacked rounded squares plus tall export arrow.
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .18f, h * .34f),
                    size = Size(w * .50f, h * .46f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawRoundRect(
                    color = CredentialBlue,
                    topLeft = Offset(w * .30f, h * .43f),
                    size = Size(w * .50f, h * .44f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(CredentialBlue, Offset(w * .49f, h * .13f), Offset(w * .49f, h * .59f), 2.8.dp.toPx(), StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(w * .35f, h * .28f)
                    lineTo(w * .49f, h * .13f)
                    lineTo(w * .63f, h * .28f)
                }
                drawPath(arrow, CredentialBlue, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
            }
'''
if old not in s:
    raise SystemExit('target block not found')
p.write_text(s.replace(old,new))
