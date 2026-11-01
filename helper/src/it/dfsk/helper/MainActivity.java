package it.dfsk.helper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Map;

public class MainActivity extends Activity {
    private TextView status, pin;
    private LinearLayout webBox;
    private ImageView qr;
    private String qrIp;
    private final Handler h = new Handler();
    private final Runnable qrTick = new Runnable() { public void run() { refreshQr(false); h.postDelayed(this, 5000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        HelperService.start(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        l.setPadding(p, p, p, p);

        l.addView(title(Lang.t("Musica in retromarcia (CarPlay)", "Music in reverse (CarPlay)")));
        l.addView(choice(HelperService.modeNames(), Prefs.reverseMode(this), new Pick() {
            public void on(int i) { HelperService.setReverseMode(MainActivity.this, i); refresh(); }
        }));
        l.addView(note(Lang.t("Pausa: come in origine. Attenuata: la musica continua, abbassata dall'auto. "
                + "Più bassa: in più l'app abbassa la musica CarPlay finché resti in retromarcia (max 3 minuti). "
                + "Telecamera, sensori e chiamate non cambiano.", "Pause: stock behaviour. Ducked: music keeps playing, lowered by the car. "
                + "Lower: the app also lowers CarPlay music while you stay in reverse (max 3 minutes). "
                + "Camera, sensors and calls are not affected.")));
        l.addView(toggle(Lang.t("Volume chiamata dal volante", "Call volume from steering wheel"), Prefs.CALL_VOLUME, true));
        l.addView(note(Lang.t("Durante una chiamata CarPlay i tasti volume del volante regolano il volume della chiamata.", "During a CarPlay call the steering-wheel volume keys change the call volume.")));

        if (Sys.isSystem()) {
            l.addView(toggle(Lang.t("Home in italiano all'avvio", "Translated Home at start-up"), Prefs.HOME_FIX, true));
            l.addView(note(Lang.t("20 secondi dopo l'accensione riavvia una volta la Home, così prende le traduzioni.", "20 seconds after start-up it restarts the Home once, so it picks up the translations.")));
            l.addView(title(Lang.t("Microfono CarPlay", "CarPlay microphone")));
            int mic = Sys.micLevel();
            l.addView(choice(new String[]{"1", "2", "3"}, mic >= 1 && mic <= 3 ? mic - 1 : -1, new Pick() {
                public void on(int i) { Sys.setMic(i + 1); refresh(); }
            }));
            l.addView(note(Lang.t("Guadagno del microfono per chiamate e vocali CarPlay. Consigliato: 1.", "Microphone gain for CarPlay calls and voice. Recommended: 1.")));
            Switch adb = new Switch(this);
            adb.setText(Lang.t("Accesso Mac (ADB Wi-Fi)", "Computer access (ADB Wi-Fi)"));
            adb.setTextSize(20);
            adb.setChecked(Sys.adbPort() > 0);
            adb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean on) { Sys.setAdbWifi(MainActivity.this, on); refresh(); }
            });
            l.addView(adb);
            l.addView(note(Lang.t("Porta 5555. Si spegne da solo al riavvio dell'auto.", "Port 5555. Turns itself off when the car restarts.")));
            Switch voice = new Switch(this);
            voice.setText(Lang.t("Voce di benvenuto all'avvio", "Welcome voice at start-up"));
            voice.setTextSize(20);
            voice.setChecked(Sys.welcomeVoice(this));
            voice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean on) { Sys.setWelcomeVoice(MainActivity.this, on); refresh(); }
            });
            l.addView(voice);
            l.addView(note(Lang.t("La frase \"Hi, I'm Glory\" all'accensione. Vale dalla prossima accensione.", "The \"Hi, I'm Glory\" phrase at start-up. Applies from the next start.")));
            l.addView(toggle(Lang.t("Pannello web (iPhone)", "Web panel (iPhone)"), Prefs.WEB, false));
            webBox = new LinearLayout(this);
            webBox.setOrientation(LinearLayout.VERTICAL);
            webBox.addView(note(Lang.t("Inquadra il QR con la fotocamera dell'iPhone (stessa rete Wi-Fi). Vale 5 minuti, una volta sola.", "Scan the QR with the iPhone camera (same Wi-Fi). Valid 5 minutes, once.")));
            qr = new ImageView(this);
            webBox.addView(qr);
            pin = note("");
            webBox.addView(pin);
            LinearLayout row = new LinearLayout(this);
            row.addView(button(Lang.t("Nuovo QR", "New QR"), new View.OnClickListener() { public void onClick(View v) { refreshQr(true); } }));
            row.addView(button(Lang.t("Nuovo PIN", "New PIN"), new View.OnClickListener() {
                public void onClick(View v) { Prefs.newPin(MainActivity.this); refreshQr(true); refresh(); }
            }));
            webBox.addView(row);
            l.addView(webBox);
        }
        status = note("");
        l.addView(status);
        ScrollView s = new ScrollView(this);
        s.addView(l);
        setContentView(s);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        if (webBox != null) qrTick.run();
    }

    @Override protected void onPause() {
        h.removeCallbacks(qrTick);
        super.onPause();
    }

    /** New one-time QR when asked, or when the IP changed / the token expired or was used. */
    private void refreshQr(boolean force) {
        if (webBox == null) return;
        boolean on = Prefs.web(this);
        webBox.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        WebAuth.INSTANCE.setPin(Prefs.pin(this)); // no-op if unchanged; makes the PIN live even before the service applies it
        String ip = Sys.lanIp();
        long now = WebPanel.now();
        if (!force && ip != null && ip.equals(qrIp) && WebAuth.INSTANCE.pairingValid(now)) return;
        qrIp = ip;
        String base = ip == null ? null : "http://" + ip + ":" + HelperService.WEB_PORT + "/";
        if (base != null) {
            qr.setImageBitmap(render(QrEncoder.encode(base + "?pair=" + WebAuth.INSTANCE.newPairing(now))));
            qr.setVisibility(View.VISIBLE);
        } else {
            qr.setVisibility(View.GONE);
        }
        pin.setText((base == null ? Lang.t("Nessuna rete Wi-Fi: collega l'auto all'hotspot dell'iPhone.", "No Wi-Fi: connect the car to the iPhone hotspot.") : Lang.t("Indirizzo: ", "Address: ") + base)
                + "\nPIN: " + Prefs.pin(this));
    }

    private static Bitmap render(QrEncoder q) {
        int scale = 8, quiet = 4, n = (q.size + 2 * quiet) * scale;
        int[] px = new int[n * n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int mx = x / scale - quiet, my = y / scale - quiet;
            boolean dark = mx >= 0 && my >= 0 && mx < q.size && my < q.size && q.get(mx, my);
            px[y * n + x] = dark ? Color.BLACK : Color.WHITE;
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder("\n");
        for (Map.Entry<String, String> e : HelperService.status(this).entrySet())
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        status.setText(sb);
        refreshQr(false);
    }

    interface Pick { void on(int index); }

    private RadioGroup choice(String[] labels, int checked, final Pick pick) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(100 + i);
            r.setText(labels[i]);
            r.setTextSize(20);
            g.addView(r);
        }
        if (checked >= 0) g.check(100 + checked);
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int id) { pick.on(id - 100); }
        });
        return g;
    }

    private Switch toggle(String label, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(20);
        sw.setChecked(Prefs.get(this).getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                Prefs.get(MainActivity.this).edit().putBoolean(key, on).apply();
                HelperService.start(MainActivity.this); // update session / web panel
                refresh();
            }
        });
        return sw;
    }

    private TextView title(String t) {
        TextView v = note(t);
        v.setTextSize(20);
        v.setPadding(0, 0, 0, 0);
        return v;
    }

    private TextView note(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(15);
        v.setPadding(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        return v;
    }
}
