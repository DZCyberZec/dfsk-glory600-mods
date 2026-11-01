# Technical notes (DFSK_F508_G, Android 9)

Findings while building these mods. Values verified on one car; please report differences.

## Climate (bw.car)
- Access through reflection: `bw.car.Car.createCar(Context, ServiceConnection)` → `connect()` → `getCarManager("property")`.
  Manifest: `<uses-library android:name="bw.car.proxy" android:required="false"/>`.
- Commands go through `HVAC_KEYEVENT_INPUT` (557909278, area 0): `Integer[]{key,1}`, then `{key,0}` after ~300 ms.

| key | function |
|---|---|
| 0 | A/C (also switches climate on) |
| 3 / 4 | fan + / − |
| 7 / 8 | level + / − (each press = 0.5) |
| 9 | air mode: send `{9, 2m+1}` then `{9, 2m}` |
| 10 | rear defrost (engine must be running) |
| 11 | off |
| 13 | windscreen defrost (forces air to glass, fan jumps to 5) |
| 14 | recirculation |

| state | property id | area |
|---|---|---|
| power | 354419984 | 5 |
| A/C | 354419973 | 5 |
| fan 0..8 | 356517120 | 5 |
| mode 0 face / 1 face+feet / 2 feet / 3 feet+glass | 356517121 | 5 |
| recirculation | 354419976 | 117 |
| defrost front / rear | 320865540 | 1 / 2 |
| set level (float) | 358614275 | 4 |
| outside temperature | 291505923 | 0 |

The climate works in **levels, not °C**. Level = v − 24 if v ≥ 25, else v − 25 (28.0 → 4, 28.5 → 4.5).

## CarPlay (SpeedPlay `com.suding.speedplay`)
- Secure setting `bw.carplay.connect.state`: 0 = disconnected, 2 = CarPlay via cable.
- Reverse: BwCarSource sends the implicit broadcast `com.suding.speedplay.action.BACKCAR_START` and SpeedPlay pauses the phone (`extra_mute` defaults to 1). Sending it earlier with `extra_mute=0` keeps music playing (SpeedPlay ignores the second one while "backing").
- Wi-Fi dropping after boot: if the iPhone's Bluetooth is on, CarPlay over Bluetooth starts the `ap0` hotspot.
- CarPlay microphone gain property: `persist.suding.mic.gainlevel.default` (stock 5, 1 sounds much cleaner).

## Translations
- The stock launcher starts before RRO overlays are applied, so it shows old texts until it restarts.
- `com.bw.adas` forces English when the locale is not Chinese: its overlay also ships `values-en` / `values-en-rUS`.

## Logging
`log.tag` is `M`: liblog drops everything below ERROR. Use `Log.e`, or `adb shell setprop persist.log.tag.<TAG> I`.
