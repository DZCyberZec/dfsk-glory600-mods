# DFSK Glory 600 – modifiche della community (unità DFSK_F508_G, Android 9)

> **Non ufficiale, non legato a DFSK, Seres o al produttore dell'unità. Lo usi a tuo rischio.**
> Niente viene scritto in `/system`: le app si installano in `/data` e si tolgono con una normale disinstallazione.
> Prima del tagliando torna all'originale (vedi [docs/INSTALL.md](docs/INSTALL.md)).
> Niente video alla guida; nulla tocca telecamere, sensori o sistemi di sicurezza.

▶ **[Video demo](docs/demo.mp4)** (emulatore: lì il clima è simulato, in auto comanda quello vero)

![Pagina "Tutte le app" in auto: le app di serie hanno le icone Aurora](docs/car_all_apps.png)

## Problemi risolti

**CarPlay (SpeedPlay)**
- **La musica si ferma ogni volta che metti la retromarcia** → CyberZec Set: *Attenuata* o *Più bassa*, la musica continua più bassa.
- **I tasti volume al volante non cambiano il volume della chiamata** → CyberZec Set li collega alla chiamata CarPlay.
- **Qualità delle chiamate e dei vocali via CarPlay** (telefono, WhatsApp, Siri): chi ascolta ti sente distorto → **risolto**. Il guadagno microfono di serie di SpeedPlay è ×5; a ×1 si sente pulito. CyberZec Set → *Microfono CarPlay* livello 1/2/3 (consigliato 1), oppure `persist.suding.mic.gainlevel.default` a 1 (serve accesso di sistema, es. `adb shell setprop`, vedi note tecniche). Provato in una chiamata reale: si sente quasi come chiamare dal telefono.
- **Il Wi-Fi dell'auto cade appena accendi** → con il Bluetooth dell'iPhone acceso, CarPlay via Bluetooth occupa l'hotspot `ap0`. Usa il cavo o spegni il Bluetooth.

**Clima**
- **"Viso" non manda aria in faccia, il tasto MODE sembra non fare nulla** → è acceso lo sbrinamento parabrezza, che tiene l'aria sul vetro. La Home te lo segnala.
- **Premo + e non cambia** → ogni tocco vale mezzo livello; la Home mostra "4,5".
- **Il lunotto non si accende** → funziona solo a motore acceso.

**Lingua e Home**
- **App metà in inglese/cinese, telecamera bloccata in inglese** → traduzioni italiane per 16 app.
- **Dopo l'accensione la Home ha ancora i testi vecchi** → la Home di serie parte prima delle traduzioni; riavviarla risolve.
- **Testi piccoli, app inutili nell'elenco** → Home CyberZec: caratteri grandi, app nascondibili con pressione lunga.

## Cosa c'è
- **`home/` – Home CyberZec**: Home grande e leggibile alla guida, clima completo (⏻, livello a mezzi scatti, ventola, 4 modalità aria, A/C, ricircolo, parabrezza, lunotto), meteo con città, cielo che segue alba/tramonto e meteo, tema notte, previsioni a ore, scheda media (titolo, copertina, ⏮ ⏯ ⏭), ricerca città con tastiera grande, "Dove ho parcheggiato", stato CarPlay/Wi-Fi/GPS/temperatura esterna. **Si configura dall'app**: tieni premuta un'app per metterla nella barra o nasconderla.
- **`helper/` – CyberZec Set** (prima DFSK Helper): la musica CarPlay non va più in pausa in retromarcia (Pausa / Attenuata / Più bassa); volume chiamata dai tasti al volante. Volume in retromarcia a scelta (100/75/50/25 %). Pannello web con QR/PIN in rete locale: impostazioni, città meteo e nome della Home, telecomando, **scrivi in auto dal telefono**. Alcune funzioni servono solo nella versione di sistema e qui sono spente.
- **`keyboard/` – Tastiera CyberZec**: tastiera grande per l'auto (lettere, numeri, accenti), riceve il testo scritto sul telefono.
- **`iphone/` – CyberZec Remote**: app iPhone (SwiftUI) per il pannello web di CyberZec Set. Apri in Xcode, metti il tuo team, avvia.

Home, Set e Tastiera si parlano con un permesso di firma: **compilale tutte con la stessa chiave**.
- **`translations/`**: traduzione italiana di 16 app di serie (overlay RRO).
- **`icons/`**: set di icone "Aurora".

**Lingua:** le app sono in inglese e passano da sole all'italiano se l'unità è in italiano.

Le build di questo repo usano una chiave tua: si compilano, ma clima della Home e overlay vanno riprovati in auto con questa firma. Segnalalo nelle Issues.

**Provato su:** DFSK Glory 600 (acquistata a luglio 2025, Italia), modello Android `DFSK_F508_G`, piattaforma Yato1 AC8257, firmware `OS_Yato1_DFSK-F508-G_01_V2.44.00_241107`, MCU `MCU_Yato1_DFSK-F508-G_01_V1.18.00_241107`. Altre unità Yato1 (es. DFSK E5) non provate: se la provi apri una Issue con le tue versioni.

## Cosa installo?
APK pronti da scaricare: **[Releases](https://github.com/DZCyberZec/dfsk-glory600-mods/releases)**.
Ogni app esiste in una sola versione: è in inglese e diventa italiana da sola se l'unità è impostata in italiano (Impostazioni → Lingua).
- **Unità in italiano:** installa anche le traduzioni (`*_it_overlay.apk`) per avere in italiano le app di serie.
- **Unità in inglese o altra lingua:** salta le traduzioni, servono solo per l'italiano.
- **Scegli i pezzi:** solo retromarcia e volume chiamata → CyberZec Set; Home grande con clima e meteo → Home CyberZec; tutto → tutti e tre.
- **Indeciso?** Parti dal solo CyberZec Set: è quello che cambia meno e `restore_stock.sh` lo toglie in un attimo.

Dettagli: [docs/INSTALL.md – Step 0](docs/INSTALL.md#step-0--choose-what-you-want).

## Ripristino all'originale
Prima del tagliando o di un intervento in garanzia: `./restore_stock.sh` (prima in prova, poi con `--yes`) toglie Home CyberZec, CyberZec Set e le traduzioni e rimette la Home di serie.
Su `/system` non viene scritto nulla, quindi non resta alcuna traccia.
Dopo, riavvia l'infotainment (o spegni e riaccendi l'auto) perché la Home originale ricarichi i testi.
Se avevi cambiato `persist.suding.mic.gainlevel.default`, rimettilo a `5`.
Guida completa e comandi manuali: [docs/INSTALL.md](docs/INSTALL.md#restore-to-stock).

Compilazione e note tecniche: vedi [README.md](README.md) e [docs/](docs). Licenza MIT.
