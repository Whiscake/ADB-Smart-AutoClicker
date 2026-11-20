<p align="center">
  <img src="https://github.com/Nain57/SmartAutoClicker/blob/master/smartautoclicker/src/main/ic_smart_auto_clicker-playstore.png?raw=true" height="64">
  <h3 align="center">Smart AutoClicker</h3>
  <p align="center">An open-source auto clicker on images for Android<p>
</p>

<br>
 
## Safety and performance

The clicker is light: detection pauses when the screen has nothing new, so it uses little processor and battery.
It only reads the screen to find your images, and only contacts the computer you set as bridge and, for the
library and the version check, GitHub. The code is open: you can read it, ask an AI assistant to review it, scan the
APK on [VirusTotal](https://www.virustotal.com), or build it yourself. If you have any doubt, ask the authors, Whiscake
and barone138, directly on Discord: both are known in the YKWPP community.

**Keep the bridge token to yourself.** The token lets whoever has it send commands to the bridge on your computer:
with the bundled `adb_bridge.py` that means adb commands on your device. Don't post it, and hide it before sharing a
screenshot (the settings show it as dots until you tap the eye). If you shared it, change it: with ADB-YKWP-Dual,
delete the `token` in its `settings.json` and restart it; with the bundled bridge, start it with a new `--token`.

## Introduction

Smart AutoClicker is an Android application allowing to automate repetitive task by clicking automatically for you on the screen. Unlike the regular auto clicker application, the clicks aren't based on timers to execute the clicks. Instead, it allows you to capture an image from a part of your screen and execute the click once this image is detected again.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.buzbuz.smartautoclicker/)

<br>

<p align="center">
  <img src="https://i.postimg.cc/65JBX8D9/Phone-Screenshot-1.png" width="200">
  <img src="https://i.postimg.cc/6Q3X0nGh/Phone-Screenshot-2.png" width="200">
  <img src="https://i.postimg.cc/1zjZYXG1/Phone-Screenshot-3.png" width="200">
  <img src="https://i.postimg.cc/qvp0N9JS/Phone-Screenshot-4.png" width="200">
</p>
<br>
<p align="center">
  <img src="https://i.postimg.cc/8zwGMts5/Phone-Screenshot-5.png" width="200">
  <img src="https://i.postimg.cc/ZnrtRL1J/Phone-Screenshot-6.png" width="200">
  <img src="https://i.postimg.cc/1Xx1sd7W/Phone-Screenshot-7.png" width="200">
  <img src="https://i.postimg.cc/nz9t8x2j/Phone-Screenshot-8.png" width="200">
</p>
<br>

## Features

- Organize clicks by scenario
- Execute clicks or swipes
- Add an image condition from the screen
- Modify the tolerance for the condition detection
- Combine multiple conditions
- Configure the delay before the next click
- Modify the prority order of a click
- Send adb commands to your computer when a condition is detected (see below)
- Download ready-made configs from an online library
- Play together with [ADB-YKWP-Dual](https://github.com/Whiscake/ADB-YKWP-Dual), which runs two emulators in turns

**Download:** [smartautoclicker.apk](https://github.com/barone138/ykwp-clicker-library/releases/latest/download/smartautoclicker.apk) (latest version). If another build of the clicker is already installed and
Android refuses the update, export your configs, uninstall it, install this one and import them again.

## What this fork adds (1.6.1)

**New**
- **Library** (button next to the settings): ready-made configs listed in
  [ykwp-clicker-library](https://github.com/barone138/ykwp-clicker-library), with an info page (author, resolution,
  events, tags, description), colored tags, a search and filters by resolution, author and tags. A downloaded config
  gets the resolution written in the library. A red **!** on the button means the library changed since you last
  opened it. The library is updated online, with no new APK.
- **News and tutorial** (the **i** button): what changed in this version, and a tour of the buttons of the config list.
  A red **!** on it means a new version of the clicker is out, with a Download button.
- **Settings** (gear): the bridge address, port and token used by every ADB action whose fields are left empty, a
  switch to turn ADB actions off, the library address and a switch for the notices above. The token is hidden behind
  dots; the eye shows it.
- **Config list**: each config shows the resolution it was made for (set it from the pencil, **Resolution**); several
  configs can be exported or deleted at once.
- **Floating menu**: play and stop act on the first touch.
- **For ADB-YKWP-Dual**: the program on the computer can export and import configs, send the bridge settings, open a
  config by its name and press play on it, also after an emulator restart.

**Fixes**
- When the emulator stops sending screen images, for example after the computer screen went to sleep, the clicker
  starts the screen capture again by itself instead of standing still.
- Tapping a config while selecting configs to export or delete selects it instead of starting it.
- The floating menu opens in the top right corner until you move it, where most configs expect it.
- Two condition images with the same hash no longer replace each other when saving or importing.
- A config imported from a backup no longer gets a guessed resolution.

**Stability**
- An action, an image or a condition that fails is logged and skipped: the scenario keeps running instead of stopping
  or closing the app.
- A whole-screen condition bigger than the screen and a damaged condition image no longer close the app.
- Detection waits a little when there is no new screen image or after an error, instead of using the processor for
  nothing.

## ADB command action (this fork)

This fork adds an **ADB command** action. When an event's conditions are detected, the app sends an adb command to a
small bridge running on the computer that hosts your emulator (BlueStacks, etc.), which runs it with the local `adb`.
The app lives inside the emulator, so it can't run the computer's `adb` itself.

**1. Start the bridge on your computer** (Python 3.8+, no dependencies):

```bash
python3 tools/adb-bridge/adb_bridge.py --token <secret> --serial emulator-5554
```

**2. Add an ADB command action** to an event and fill in:

| Field | Value |
|---|---|
| Bridge address | The computer as seen from the emulator: `10.0.2.2` for the Android Studio emulator, usually the computer's LAN IP for BlueStacks |
| Bridge port | `8765` (the bridge's `--port`) |
| ADB command | The adb arguments without the leading `adb`, e.g. `shell input keyevent 4` |
| Bridge token | The `--token` you started the bridge with |

**Protocol:** the app sends `POST http://<address>:<port>/adb` with the header `X-Adb-Bridge-Token` and the form field
`command`. A failed request is logged and never interrupts the scenario. You can write your own bridge, for example
one that only accepts the few commands you need.

With ADB-YKWP-Dual you don't need the bridge above: the program links the clicker to the computer through
`adb reverse` and sends it the port and token, so the action fields stay empty.

**Security:** anyone who has the token can control the device, and the traffic is plain HTTP, so only run the bridge on
a network you trust. The bundled bridge never uses a shell and refuses adb sub-commands that act on the computer
(`connect`, `forward`, `kill-server`, ...). The token is never written to scenario backups, so enter it again after
importing one.
