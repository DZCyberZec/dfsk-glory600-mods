# Installazione e ripristino

🇬🇧 [English version](INSTALL.md)

## Passo 0 – scegli cosa vuoi

Ogni pezzo è indipendente: installa solo quello che ti serve.

**1. Lingua.** Ogni app esiste in una sola versione, non una italiana e una inglese.
Le app seguono la lingua dell'unità:

| Lingua dell'unità (Impostazioni → Lingua) | Home CyberZec e CyberZec Set sono in | Installo le traduzioni? |
|---|---|---|
| Italiano | Italiano | **Sì**: traducono le app di serie BW (dashcam, impostazioni, clima…) |
| Inglese o altra lingua | Inglese | **No**: aggiungono solo l'italiano. Salta il punto 4 |

Vuoi un'altra lingua? Copia `translations/<App>/values-it` in `values-<tua lingua>` e traduci; per le due app, aggiungi i testi nelle chiamate `Logic.t()` / `Lang.t()`. Le pull request sono benvenute.

**2. Pezzi.**

| Voglio… | Installa |
|---|---|
| La musica che non si ferma in retromarcia, il volume della chiamata dal volante | solo `CyberZec-Set-<versione>.apk` |
| Una Home più grande con clima e meteo | `HomeCyberZec-<versione>.apk` (+ punto 3) |
| Una tastiera grande per l'auto | `TastieraCyberZec-<versione>.apk`, poi attivala nelle impostazioni di Android |
| Le app di serie in italiano | i file `*_it_overlay.apk` (punto 4, serve adb) |
| Tutto | tutti i precedenti |

Indeciso? Parti dal solo **CyberZec Set**: è quello che cambia meno e `restore_stock.sh` lo toglie in pochi secondi.

## Prima di iniziare
- **Versione di prova:** le versioni pubbliche sono firmate con una chiave diversa da quella dell'auto dell'autore e non sono ancora state provate in auto. Il clima della Home e le traduzioni sono ancora da collaudare: segnala cosa funziona nelle [Issues](https://github.com/DZCyberZec/dfsk-glory600-mods/issues).
- **Non presente nelle versioni pubbliche:** livello del microfono CarPlay, titolo del brano CarPlay e pannello web per il telefono (quindi anche l'app iPhone e la scrittura dal telefono) richiedono una versione di sistema. Senza la chiave di sistema la scheda media della Home può restare vuota.
- **adb:** le app si installano senza computer. Per attivare le traduzioni, impostare la Home con un comando e usare il ripristino automatico serve adb collegato all'unità.

## Installare le app (senza computer)
0. Scarica gli APK dall'[ultima release](https://github.com/DZCyberZec/dfsk-glory600-mods/releases) (oppure compilali, vedi il README).
1. Installa **E5 APK Manager** sull'unità: segui [la sua guida su XDA](https://xdaforums.com/t/guide-dfsk-e5-fengon-600-evolute-i-space-install-any-apk-e5-apk-manager.4796818/) (di Chrisstian).
2. Apri il suo **Web Transfer**, apri l'indirizzo indicato dal telefono o dal PC sullo stesso Wi-Fi, carica l'APK e premi **Installa** sull'auto.
3. Home CyberZec: concedi la posizione quando viene chiesta, poi premi Home e sceglila.
   Con adb puoi impostarla direttamente:
   ```
   adb shell cmd package set-home-activity it.cyberzec.home/.HomeActivity
   ```
4. Traduzioni (serve adb): scarica `Italian-translation-overlays.zip` dalla [release v1.0.0-beta](https://github.com/DZCyberZec/dfsk-glory600-mods/releases/tag/v1.0.0-beta), estrailo, installa ogni `*_it_overlay.apk` e attivalo:
   ```
   adb shell cmd overlay enable it.dfsk.overlay.<nome>
   ```

## Aggiornamenti
`adb install -r <apk>` conserva le impostazioni e la scelta della Home (serve la stessa chiave di firma).

## Tornare all'originale
Da usare prima del tagliando o di un intervento in garanzia, o quando vuoi togliere le modifiche.

**Cosa toglie:** Home CyberZec (`it.cyberzec.home`), CyberZec Set (`it.dfsk.helper`), Tastiera CyberZec (`it.cyberzec.keyboard`) e tutte le traduzioni (`it.dfsk.overlay.*`), e rimette come predefinita la Home di serie (`com.android.launcher3/.Launcher`). Le app che hai installato tu (per esempio E5 Launcher Plus) restano: se vuoi, toglile a mano.

**Nessuna traccia in /system:** queste modifiche non scrivono mai in `/system`, quindi dopo la rimozione l'unità torna come uscita di fabbrica.

Automatico (serve adb sul computer, auto accesa, adb collegato):
```
./restore_stock.sh            # prova: elenca solo cosa farebbe
./restore_stock.sh --yes      # lo fa davvero
./restore_stock.sh -s 192.168.x.x:5555 --yes   # sceglie il dispositivo (oppure variabile ADB_SERIAL)
```
Lo script finisce con OK o con l'elenco di ciò che è ancora installato. Poi **riavvia l'infotainment** (o spegni e riaccendi l'auto), così la Home di serie ricarica i testi originali.

A mano, se lo script non basta:
```
adb shell cmd package set-home-activity com.android.launcher3/.Launcher
adb uninstall it.cyberzec.home
adb uninstall it.dfsk.helper
adb uninstall it.cyberzec.keyboard
adb shell cmd overlay list | grep it.dfsk.overlay   # poi per ciascuno:
adb uninstall it.dfsk.overlay.<nome>
```

Se avevi cambiato la proprietà del guadagno del microfono `persist.suding.mic.gainlevel.default`, rimettila a `5`.

**Per reinstallare dopo:** ripeti i passi di "Installare le app" (APK, poi Home CyberZec come predefinita, poi attivazione delle traduzioni).
