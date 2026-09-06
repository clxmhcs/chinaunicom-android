from pathlib import Path

path = Path('app/src/main/java/com/clxmhcs/chinaunicom/ui/SettingsAccountScreen.kt')
text = path.read_text()
old = '        CredentialPage.LOGIN -> SMSCredentialLoginPage(\n'
new = '        CredentialPage.LOGIN -> SMSCredentialLoginIosPage(\n'
count = text.count(old)
if count != 1:
    raise SystemExit(f'expected exactly one login route anchor, found {count}')
path.write_text(text.replace(old, new, 1))
