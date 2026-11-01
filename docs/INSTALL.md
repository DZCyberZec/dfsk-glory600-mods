# Install and restore

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

## Restore to stock (before dealership service)
```
adb shell cmd package set-home-activity com.android.launcher3/.Launcher
adb uninstall it.cyberzec.home
adb uninstall it.dfsk.helper
adb shell cmd overlay list | grep it.dfsk.overlay   # then for each one:
adb uninstall it.dfsk.overlay.<name>
```
Nothing else on the unit was changed by these apps.
