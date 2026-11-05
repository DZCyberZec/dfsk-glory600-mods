# Install and restore

🇮🇹 [Versione italiana](INSTALL_IT.md)

## Step 0 – choose what you want

Every piece is independent: install only what you need.

**1. Language.** There is one version of each app, not an Italian one and an English one.
The apps follow the language of the head unit:

| Head unit language (Settings → Language) | Home CyberZec and CyberZec Set show | Install the translation overlays? |
|---|---|---|
| Italiano | Italian | **Yes**: they translate the stock BW apps (camera, settings, climate…) |
| English or any other | English | **No**: the overlays only add Italian. Skip step 4 below |

Want another language? Copy `translations/<App>/values-it` to `values-<your language>` and translate; for the two apps, add your text to the `Logic.t()` / `Lang.t()` calls. Pull requests welcome.

**2. Pieces.**

| I want… | Install |
|---|---|
| Music not to stop in reverse, call volume from the steering wheel | `CyberZec-Set-<version>.apk` only |
| A bigger home screen with climate control and weather | `HomeCyberZec-<version>.apk` (+ step 3) |
| A big in-car keyboard | `TastieraCyberZec-<version>.apk`, then enable it in Android settings |
| The stock apps in Italian | the `*_it_overlay.apk` files (step 4, needs adb) |
| Everything | all of the above |

Not sure? Start with **CyberZec Set** alone: it changes the least, and `restore_stock.sh` removes it in seconds.

## Before you start
- **Beta:** the public builds are signed with a different key from the author's car and have not been tried on a car yet. Home climate control and the overlays still need testing: please report what works in [Issues](https://github.com/DZCyberZec/dfsk-glory600-mods/issues).
- **Not in the public builds:** CarPlay microphone level, CarPlay track info and the phone web panel (so also the iPhone app and typing from the phone) need a system build. Without the system key the Home media card can stay empty.
- **adb:** the apps install without a computer. Enabling the overlays, setting the Home with a command and the automatic restore need adb connected to the unit.

## Install apps (no computer needed)
0. Download the APKs from the [latest release](https://github.com/DZCyberZec/dfsk-glory600-mods/releases) (or build them, see the README).
1. Install **E5 APK Manager** on the head unit: follow [its guide on XDA](https://xdaforums.com/t/guide-dfsk-e5-fengon-600-evolute-i-space-install-any-apk-e5-apk-manager.4796818/) (by Chrisstian).
2. Open its **Web Transfer**, open the shown address from your phone/PC on the same Wi-Fi, upload the APK, tap **Install** on the car.
3. Home CyberZec: grant location when asked, then press Home and choose it.
   With adb you can set it directly:
   ```
   adb shell cmd package set-home-activity it.cyberzec.home/.HomeActivity
   ```
4. Translations (adb needed): download `Italian-translation-overlays.zip` from the [v1.0.0-beta release](https://github.com/DZCyberZec/dfsk-glory600-mods/releases/tag/v1.0.0-beta), unzip it, install each `*_it_overlay.apk`, then enable it:
   ```
   adb shell cmd overlay enable it.dfsk.overlay.<name>
   ```

## Updates
`adb install -r <apk>` keeps settings and the Home choice (same signing key needed).

## Restore to stock
Use it before dealership service or warranty work, or any time you want to hide the mods.

**What it removes:** Home CyberZec (`it.cyberzec.home`), CyberZec Set (`it.dfsk.helper`), Tastiera CyberZec (`it.cyberzec.keyboard`) and every Italian overlay (`it.dfsk.overlay.*`), and it sets the stock Home (`com.android.launcher3/.Launcher`) back as default. Apps you installed yourself (for example E5 Launcher Plus) are left alone; remove them by hand if you want.

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
adb uninstall it.cyberzec.keyboard
adb shell cmd overlay list | grep it.dfsk.overlay   # then for each one:
adb uninstall it.dfsk.overlay.<name>
```

If you changed the microphone gain property `persist.suding.mic.gainlevel.default`, set it back to `5`.

**Reinstall afterwards:** repeat the steps in "Install" above (APKs, then set Home CyberZec as default, then enable the overlays).
