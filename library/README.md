# Config library

The clicker's **Library** (⋮ menu on the scenario list) shows the configs listed in `index.json` here. Tapping
**Download** imports one like a backup. The app reads the index from GitHub every time the Library opens, so adding a
config here needs no new APK.

## Adding a config

1. Export the config from the clicker (⋮ → Export) and put the `.zip` in `configs/`.
2. Run `python make_index.py`. It adds the new file to `index.json` with its name, screen and number of events.
3. Write a `description` and an `author` for it in `index.json`. They are kept the next time the script runs, and so
   is a `name` you change by hand.
4. Commit and push. The Library shows it the next time it is opened.

## index.json

```json
{
  "configs": [
    {
      "name": "Stage farm",
      "description": "Plays the event stages, with the stage clear and login events ready for Dual.",
      "author": "Whiscake",
      "screen": "900x1600",
      "events": 12,
      "file": "configs/stage-farm.zip"
    }
  ]
}
```

`file` is relative to `index.json` and can also be a full URL. Configs made for the screen of the device are listed
first. Another index can be used by changing **Library address** in the clicker's settings (gear icon).
