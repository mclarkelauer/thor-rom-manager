"""Prepare Obtainium-compatible release assets without publishing or exposing keys."""
import hashlib
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'AndroidManifest.xml').getroot()
version=manifest.attrib[ns+'versionName'];code=manifest.attrib[ns+'versionCode']
subprocess.run(['python3',str(root/'tools/build.py')],check=True)
folder=root/'release'/version;folder.mkdir(parents=True,exist_ok=True)
apk=folder/f'Thor-ROM-Manager-{version}.apk';shutil.copyfile(root/'build/Thor-ROM-Manager.apk',apk)
(folder/'SHA256SUMS').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
(folder/'release-notes.txt').write_text(f'Thor ROM Manager {version} (Android versionCode {code})\n\nAdds a connection and transfer dashboard with setup instructions, authenticated computer activity, file progress, speed, verification and error states. Settings and imports are grouped under Options. Adds a persistent Keep screen awake while receiver is on option, enabled by default. Leave the app visible during transfers; stop the receiver to restore normal screen sleep. CPU wake protection now also covers inventory scans and existing-file verification. Manual power-button sleep and lid closure are not overridden.\n\nAdd the repository URL to Obtainium and select the universal APK. Android 8.0 or newer.\n\nSelect the SD card ROMs folder in the app before receiving games. Your existing ROM files are not overwritten.\n')
print(folder)
