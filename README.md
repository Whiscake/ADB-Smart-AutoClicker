<p align="center">
  <img src="https://github.com/Nain57/SmartAutoClicker/blob/master/smartautoclicker/src/main/ic_smart_auto_clicker-playstore.png?raw=true" height="64">
  <h3 align="center">Smart AutoClicker</h3>
  <p align="center">An open-source auto clicker on images for Android<p>
</p>

<br>
 
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

**Security:** anyone who has the token can control the device, and the traffic is plain HTTP, so only run the bridge on
a network you trust. The bundled bridge never uses a shell and refuses adb sub-commands that act on the computer
(`connect`, `forward`, `kill-server`, ...). The token is never written to scenario backups, so enter it again after
importing one.
