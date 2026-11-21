# Thor ROM Manager

A native Android companion for a ROM downloader. Select the SD card’s `ROMs`
folder once, then install prepared games over local Wi-Fi without removing the
card. The app also imports files received through LocalSend and lists the actual
contents of the selected folder.

## Install with Obtainium

Add this repository’s GitHub URL to Obtainium after the first release is published.
Choose the universal `Thor-ROM-Manager-<version>.apk` release asset. Android 8.0+
is supported; there are no architecture-specific APK variants.

The application ID is `net.thor.roms`. All updates must retain the same signing
certificate and increase `versionCode`. Obtainium handles app updates; ROM
transfers and firmware are independent of Obtainium.

## Pair with the desktop downloader

1. Install the APK on the Thor and choose the SD card’s **ROMs** folder.
2. Tap **Start Wi-Fi receiver**. Keep the Thor and Mac on the same local network.
3. Tap **Copy desktop pairing details**, then paste that JSON into the downloader’s
   **Transfer destination** section and select **Pair and use Wi-Fi**.
4. Use **Send cached ROMs to Thor**, or **Download + send to Thor**.

The desktop extracts archives locally and sends one prepared file at a time.
Console folders, Switch `updates`, and Switch `dlc` paths are preserved relative
to the selected ROMs folder. The receiver verifies SHA-256 and reads back its
staged copy before committing the final filename. Identical files are skipped;
different files are never silently overwritten. Local caches are retained.

Transfers use HTTPS with a certificate fingerprint pinned during pairing and a
random authentication token. Keep pairing details private. The service runs only
after you start it and can be stopped from the app. If Android stops it, reopen
and restart the receiver. Interrupted files restart from the beginning; completed
files are detected and skipped on retry. No account or cloud server is required.
The desktop inventory reflects current files when the receiver is reachable; it
does not pretend an offline card has been scanned.

## Import LocalSend deliveries

Select a console/destination folder in the app and tap **Import received files**.
Choose files from LocalSend’s receiving folder. Ordinary ZIP files are extracted;
arcade sets selected for `mame` stay zipped. Other raw files are copied as selected.
Multipart ZIP, RAR, and 7z should be extracted by the desktop downloader first.
For locally imported Switch files, explicitly choose `switch/updates` or
`switch/dlc` as appropriate. Successful installs keep the original source files.
The companion installs game files into folders; emulator-specific registration
of updates/DLC may still be needed in the emulator.

## Build

Requires a JDK, Python 3, and Android SDK platform 35/build tools 35.0.0.
Set `THOR_ANDROID_SDK` to your SDK directory, then run:

```sh
python3 tools/build.py
```

The existing Thor workspace’s `.android-tools` layout is also supported.
Output: `build/Thor-ROM-Manager.apk`. The local `.signing` directory is created
once; back it up privately and retain it for every future release. Never commit
or upload it. The test build (`--debug`) is for emulator validation only and must
not be attached to releases.

Run `python3 tools/prepare_release.py` to create a versioned APK, SHA-256 file,
and release notes in `release/<version>/`. This does not publish anything.

## Protocol

Authenticated HTTPS, port 53318. All routes require `Authorization: Bearer TOKEN`.
The desktop verifies the server certificate’s SHA-256 fingerprint before sending
its token. Relative paths must stay within the user-selected ROMs tree.

- `GET /inventory`: current relative file paths and sizes.
- `GET /check?path=...&size=...&sha256=...`: checks a potential duplicate.
- `POST /upload?path=...&sha256=...`: raw file bytes with Content-Length;
  returns path, size, SHA-256, and `verified: true` only after successful readback.

The receiver has no remote delete, arbitrary shell, or arbitrary filesystem API.

## Validation for 1.0.0

Tested in an Android 11/API 30 emulator with an isolated Storage Access Framework
folder: persistent folder permission, HTTPS transfer, readback checksum,
duplicate-safe retry, current inventory, invalid-path rejection, bad-checksum
rejection, interrupted-file cleanup, and local ZIP import. The desktop queue-to-
Android integration and browser regression checks also passed. A physical AYN
Thor and Android 15/16 have not yet been tested.

### Keeping transfers awake

Version 1.0.1 enables **Keep screen awake while receiver is on** by default. Leave Thor ROM Manager visible while transferring; the screen stays awake between files and during checksum verification. Turn the option off or stop the receiver to restore normal screen timeout. The preference survives app restarts. Android releases screen protection when the app is in the background; manual power-button sleep or closing the lid is not overridden. CPU wake protection also covers authenticated inventory and checksum requests.

### Connection and transfer dashboard

The main screen shows setup instructions until the computer contacts the receiver, then its address and transfer activity. The connection indicator reflects active authenticated requests or contact within the last 45 seconds, not a permanent socket connection. Transfers show filename, received/total MiB, average receiving speed, SD verification, completion, or interruption. Open **Options** for receiver controls, pairing, folder access, keep-awake, and LocalSend imports.

### Interface (1.0.2)

Use the bottom **Transfer**, **Library**, and **Options** tabs. Wide screens show connection and transfer cards side by side; narrow screens stack them. The circular indicator measures bytes received; the separate verification phase confirms the SD copy before showing completion. Library rows are recycled while scrolling, with search across full file paths. Keep-awake behavior is unchanged and works across all three tabs while the app remains visible.
