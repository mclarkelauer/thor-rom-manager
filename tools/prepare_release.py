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
(folder/'release-notes.txt').write_text(f'Thor ROM Manager {version} (Android versionCode {code})\n\nRedesigned dark interface with rounded cards, lime accents, circular transfer progress, received-size and speed metrics, and Receive / Verify / Ready stages. Persistent Transfer, Library and Options navigation. Adaptive landscape layout, searchable recycled library rows, and retained keep-awake controls.\n\nAdd the repository URL to Obtainium and select the universal APK. Android 8.0 or newer.\n\nSelect the SD card ROMs folder in the app before receiving games. Your existing ROM files are not overwritten.\n')
print(folder)
