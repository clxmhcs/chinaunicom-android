from pathlib import Path
p = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsAccountScreen.kt')
s = p.read_text()
old = '''                    CredentialActionIcon(glyph)
                    Spacer(Modifier.width(11.dp))
'''
new = '''                    when (glyph) {
                        CredentialActionGlyph.LOGIN -> RefinedCredentialActionIcon(
                            glyph = RefinedCredentialActionGlyph.LOGIN,
                            modifier = Modifier.size(30.dp),
                            tint = CredentialBlue,
                        )
                        CredentialActionGlyph.KEY -> RefinedCredentialActionIcon(
                            glyph = RefinedCredentialActionGlyph.KEY,
                            modifier = Modifier.size(30.dp),
                            tint = CredentialBlue,
                        )
                        CredentialActionGlyph.TRANSFER -> RefinedCredentialActionIcon(
                            glyph = RefinedCredentialActionGlyph.TRANSFER,
                            modifier = Modifier.size(30.dp),
                            tint = CredentialBlue,
                        )
                        else -> CredentialActionIcon(glyph)
                    }
                    Spacer(Modifier.width(11.dp))
'''
if old not in s:
    raise SystemExit('anchor not found')
p.write_text(s.replace(old,new,1))
