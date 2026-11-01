# Install and restore

## Step 0 – choose what you want

Every piece is independent: install only what you need.

**1. Language.** There is one version of each app, not an Italian one and an English one.
The apps follow the language of the head unit:

| Head unit language (Settings → Language) | Home CyberZec and DFSK Helper show | Install the translation overlays? |
|---|---|---|
| Italiano | Italian | **Yes**: they translate the stock BW apps (camera, settings, climate…) |
| English or any other | English | **No**: the overlays only add Italian. Skip step 4 below |

Want another language? Copy `translations/<App>/values-it` to `values-<your language>` and translate; for the two apps, add your text to the `Logic.t()` / `Lang.t()` calls. Pull requests welcome.

**2. Pieces.**

| I want… | Install |
|---|---|
| Music not to stop in reverse, call volume from the steering wheel | `DFSK-Helper.apk` only |
| A bigger home screen with climate control and weather | `HomeCyberZec.apk` (+ step 3) |
| The stock apps in Italian | the `*_it_overlay.apk` files (step 4) |
| Everything | all of the above |

Not sure? Start with **DFSK Helper** alone: it changes the least, and `restore_stock.sh` removes it in seconds.

## Install (no computer needed)
1. Install **E5 APK Manager** on the head unit (see the E5 Launcher community).
2. Open its **Web Transfer**, open the shown address from your phone/PC on the same Wi-Fi, upload the APK, tap **Install** on the car.
3. Home CyberZec: grant location when asked, then press Home and choose it.
   With adb you can set it directly:
   ```
   adb shell cmd package set-home-activity it.cyberzec.home/.HomeActivity
   ```
4. Translations: install each `*_it_overlay.apk`, then enable it:
   ```
   adb shell cmd overlay enable it.dfsk.overlay.<name>
   ```

## Updates
`adb install -r <apk>` keeps settings and the Home choice (same signing key needed).

## Restore to stock
Use it before dealership service or warranty work, or any time you want to hide the mods.

**What it removes:** Home CyberZec (`it.cyberzec.home`), DFSK Helper (`it.dfsk.helper`) and every Italian overlay (`it.dfsk.overlay.*`), and it sets the stock Home (`com.android.launcher3/.Launcher`) back as default. Apps you installed yourself (for example E5 Launcher Plus) are left alone; remove them by hand if you want.

**No trace in /system:** these mods never write to `/system`, so after removal the unit is exactly as the factory left it.

Automatic (needs adb on your computer, car on, adb connected):
```
./restore_stock.sh            # dry run: only lists what it would do
./restore_stock.sh --yes      # really do it
./restore_stock.sh -s 192.168.x.x:5555 --yes   # pick a device (or env ADB_SERIAL)
```
The script ends with OK or the list of what is still installed. Then **restart the infotainment** (or turn the car off and on) so the stock Home reloads its original texts.

Manual fallback:
```
adb shell cmd package set-home-activity com.android.launcher3/.Launcher
adb uninstall it.cyberzec.home
adb uninstall it.dfsk.helper
adb shell cmd overlay list | grep it.dfsk.overlay   # then for each one:
adb uninstall it.dfsk.overlay.<name>
```

If you changed the microphone gain property `persist.suding.mic.gainlevel.default`, set it back to `5`.

**Reinstall afterwards:** repeat the steps in "Install" above (APKs, then set Home CyberZec as default, then enable the overlays).
