"""Build a small, signed native APK with the official standalone Android tools."""
import os
import sys
import secrets
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('THOR_ANDROID_SDK',str(ROOT.parent/'.android-tools')))
BUILD=ROOT/'build'
TOOLS=SDK/'build-tools/35.0.0'
if not TOOLS.exists():TOOLS=SDK/'build-tools/android-15'
ANDROID=SDK/'platforms/android-35/android.jar'
if not ANDROID.exists():ANDROID=SDK/'platform/android-35/android.jar'

def run(*args):
    subprocess.run([str(a) for a in args],check=True,cwd=ROOT)

BUILD.mkdir(exist_ok=True)
for name in ('classes','dex'):
    shutil.rmtree(BUILD/name,ignore_errors=True)
    (BUILD/name).mkdir()
run(TOOLS/'aapt2','compile','--dir',ROOT/'res','-o',BUILD/'resources.zip')
manifest=ROOT/'AndroidManifest.xml'
if '--debug' in sys.argv:
    manifest=BUILD/'test-manifest.xml'
    manifest.write_text((ROOT/'AndroidManifest.xml').read_text().replace('<application ', '<application android:debuggable="true" '))
run(TOOLS/'aapt2','link','-o',BUILD/'resources.apk','--manifest',manifest,
    '-I',ANDROID,BUILD/'resources.zip','--min-sdk-version','26','--target-sdk-version','35')
run('javac','--release','8','-classpath',ANDROID,'-d',BUILD/'classes',*sorted((ROOT/'src').rglob('*.java')))
run(TOOLS/'d8','--lib',ANDROID,'--min-api','26','--output',BUILD/'dex',*sorted((BUILD/'classes').rglob('*.class')))
unsigned=BUILD/'unsigned.apk'
unsigned.write_bytes((BUILD/'resources.apk').read_bytes())
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
    for dex in (BUILD/'dex').glob('*.dex'):z.write(dex,dex.name)
run(TOOLS/'zipalign','-f','4',unsigned,BUILD/'aligned.apk')
signing=ROOT/'.signing';signing.mkdir(exist_ok=True,mode=0o700)
password=signing/'password';keystore=signing/'reader.jks'
if not password.exists():
    password.write_text(secrets.token_urlsafe(32));password.chmod(0o600)
if not keystore.exists():
    run('keytool','-genkeypair','-keystore',keystore,'-storepass:file',password,
        '-keypass:file',password,'-alias','reader','-keyalg','RSA','-keysize','2048',
        '-validity','10000','-dname','CN=Thor ROM Manager local build','-noprompt')
    keystore.chmod(0o600)
apk=BUILD/('Thor-ROM-Manager-test.apk' if '--debug' in sys.argv else 'Thor-ROM-Manager.apk')
run(TOOLS/'apksigner','sign','--ks',keystore,'--ks-pass','file:'+str(password),
    '--out',apk,BUILD/'aligned.apk')
run(TOOLS/'apksigner','verify','--verbose',apk)
print(apk)
