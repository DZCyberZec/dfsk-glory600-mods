package it.dfsk.helper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/** Settings screen in the Home CyberZec style: a horizontal row of cards with big segmented pill choices. */
public class MainActivity extends Activity {
    static final int BG = 0xFF080E10, LIME = 0xFFC5F56C, TEAL = 0xFF2FD6A6, TXT = 0xFFF1F4EE,
            MUT = 0xFFA3B1AF, LINE = 0xFF2A3639, DARK = 0xFF080E10;

    private float k;
    private TextView status, pin;
    private LinearLayout webBox;
    private ImageView qr;
    private String qrIp;
    private final Handler h = new Handler();
    private final Runnable qrTick = new Runnable() { public void run() { refreshQr(false); h.postDelayed(this, 5000); } };
    private HorizontalScrollView scroll;
    private static volatile MainActivity live; // on screen, for changes made from the phone

    /** A choice was changed from the web panel / iPhone app: redraw the cards if Set is on screen. */
    static void changed() {
        final MainActivity a = live;
        if (a != null) a.runOnUiThread(new Runnable() { public void run() { if (live == a) { a.build(); a.refresh(); } } });
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); // the card row needs the full 720 px height
        HelperService.start(this);
        k = getResources().getDisplayMetrics().density / 1.5f; // design pixels are 240 dpi pixels, like Home CyberZec
    }

    /** The whole screen; rebuilt on resume and on phone changes, so every choice shows its current value. */
    private void build() {
        final int x = scroll != null ? scroll.getScrollX() : 0;
        qrIp = null; // new QR view: render it again
        LinearLayout root = vbox();
        root.setBackground(background());
        root.setPadding(0, px(22), 0, px(26));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(28), 0, px(28), 0);
        TextView home = pill("‹ Home", false, new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
        });
        top.addView(home, new LinearLayout.LayoutParams(px(200), px(92)));
        TextView name = text("CyberZec Set", 44, TXT, true);
        name.setPadding(px(30), 0, px(30), 0);
        top.addView(name);
        status = text("", 26, MUT, false);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setLineSpacing(px(6), 1);
        top.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        LinearLayout row = hbox();
        row.setPadding(px(28), px(22), px(8), 0); // inside the scroll view, so cards are not clipped at the edges

        LinearLayout c = card(Lang.t("Musica in retromarcia", "Music in reverse"),
                Lang.t("Cosa fa la musica CarPlay quando inserisci la retro. Telecamera, sensori e chiamate non cambiano.",
                        "What CarPlay music does when you select reverse. Camera, sensors and calls are not affected."));
        c.addView(seg(HelperService.modeNames(), Prefs.reverseMode(this), new Pick() {
            public void on(int i) { HelperService.setReverseMode(MainActivity.this, i); refresh(); }
        }));
        row.addView(c, cardLp(660));

        row.addView(toggleCard(Lang.t("Volume chiamata dal volante", "Call volume from steering wheel"),
                Lang.t("In chiamata CarPlay i tasti volume del volante regolano la voce di chi chiama.",
                        "During a CarPlay call the steering-wheel volume keys change the call volume."), Prefs.CALL_VOLUME, true), cardLp(540));

        LinearLayout rv = card(Lang.t("Volume in retromarcia", "Volume in reverse"),
                Lang.t("Auto: l'auto abbassa da sola. 100%: la musica non si abbassa. Finita la retro torna il volume di prima.",
                        "Auto: the car lowers it itself. 100%: music is not lowered. Afterwards the previous level comes back."));
        int sel = 0;
        for (int i = 0; i < RetroVolume.PERCENTS.length; i++) if (RetroVolume.PERCENTS[i] == Prefs.retroPercent(this)) sel = i;
        rv.addView(seg(new String[]{"Auto", "100%", "75%", "50%", "25%"}, sel, new Pick() {
            public void on(int i) { HelperService.setRetroPercent(MainActivity.this, RetroVolume.PERCENTS[i]); refresh(); }
        }));
        row.addView(rv, cardLp(760));

        if (Sys.isSystem()) {
            row.addView(toggleCard(Lang.t("Brano CarPlay nella Home", "CarPlay track on Home"),
                    Lang.t("Con CarPlay collegato la Home mostra titolo e artista del brano.",
                            "While CarPlay is connected the Home shows title and artist."), Prefs.CARPLAY_TRACK, true), cardLp(540));

            c = card(Lang.t("Microfono CarPlay", "CarPlay microphone"),
                    Lang.t("Sensibilità del microfono per chiamate e Siri. Consigliato: 1.",
                            "Microphone gain for CarPlay calls and Siri. Recommended: 1."));
            int mic = Sys.micLevel();
            c.addView(seg(new String[]{"1", "2", "3"}, mic >= 1 && mic <= 3 ? mic - 1 : -1, new Pick() {
                public void on(int i) { Sys.setMic(i + 1); refresh(); }
            }));
            row.addView(c, cardLp(500));

            c = card(Lang.t("Voce di benvenuto", "Welcome voice"),
                    Lang.t("La frase \"Hi, I'm Glory\" all'accensione. Vale dalla prossima accensione.",
                            "The \"Hi, I'm Glory\" phrase at start-up. Applies from the next start."));
            c.addView(seg(offOn(), Sys.welcomeVoice(this) ? 1 : 0, new Pick() {
                public void on(int i) { Sys.setWelcomeVoice(MainActivity.this, i == 1); refresh(); }
            }));
            row.addView(c, cardLp(500));

            c = card(Lang.t("Accesso Mac (ADB Wi-Fi)", "Computer access (ADB Wi-Fi)"),
                    Lang.t("Collega il Mac all'auto via Wi-Fi, porta 5555. Si spegne da solo al riavvio dell'auto.",
                            "Connect the computer over Wi-Fi, port 5555. Turns itself off when the car restarts."));
            c.addView(seg(offOn(), Sys.adbPort() > 0 ? 1 : 0, new Pick() {
                public void on(int i) { Sys.setAdbWifi(MainActivity.this, i == 1); refresh(); }
            }));
            row.addView(c, cardLp(540));

            row.addView(hotspotCard(), cardLp(560));
            row.addView(webCard(), cardLp(1000));
        }

        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.addView(row);
        root.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        scroll = s;
        s.post(new Runnable() { public void run() { scroll.scrollTo(x, 0); } });
    }

    @Override protected void onResume() {
        super.onResume();
        live = this;
        build();
        refresh();
        if (webBox != null) qrTick.run();
    }

    @Override protected void onPause() {
        live = null;
        h.removeCallbacks(qrTick);
        super.onPause();
    }

    private static String[] offOn() { return new String[]{Lang.t("Spento", "Off"), Lang.t("Acceso", "On")}; }

    /** "Hotspot automatico": "Spento" or one of the saved Wi-Fi networks, in a vertical list that scrolls if long. */
    private LinearLayout hotspotCard() {
        LinearLayout c = card(Lang.t("Hotspot automatico", "Automatic hotspot"),
                Lang.t("L'auto si collega da sola a questa rete Wi-Fi salvata.", "The car joins this saved Wi-Fi network by itself."));
        List<String> ssids = HelperService.savedSsids(this);
        final String[] labels = new String[ssids.size() + 1];
        labels[0] = Lang.t("Spento", "Off");
        String cur = Prefs.hotspotSsid(this);
        int sel = 0;
        for (int i = 0; i < ssids.size(); i++) {
            labels[i + 1] = ssids.get(i);
            if (ssids.get(i).equals(cur)) sel = i + 1;
        }
        unweightNote(c);
        final LinearLayout list = vbox();
        final Pick pick = new Pick() {
            public void on(int i) {
                Prefs.setHotspotSsid(MainActivity.this, i == 0 ? null : labels[i]); // also restarts the service
                refresh();
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView p = pill(labels[i], i == sel, new View.OnClickListener() {
                public void onClick(View v) { select(list, idx); pick.on(idx); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(92));
            if (i > 0) lp.topMargin = px(12);
            list.addView(p, lp);
        }
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        c.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return c;
    }

    /** "Pannello web (iPhone)": on/off, then QR on the right and address/PIN + New QR / New PIN on the left. */
    private LinearLayout webCard() {
        LinearLayout c = card(Lang.t("Pannello web iPhone", "iPhone web panel"),
                Lang.t("Inquadra il QR con la fotocamera dell'iPhone (stessa Wi-Fi). Vale 5 minuti, una volta sola.",
                        "Scan the QR with the iPhone camera (same Wi-Fi). Valid 5 minutes, once."));
        unweightNote(c);
        c.addView(seg(offOn(), Prefs.web(this) ? 1 : 0, new Pick() {
            public void on(int i) {
                Prefs.get(MainActivity.this).edit().putBoolean(Prefs.WEB, i == 1).apply();
                HelperService.start(MainActivity.this); // start/stop the web panel
                refresh();
            }
        }));
        webBox = hbox();
        webBox.setPadding(0, px(14), 0, 0);
        LinearLayout left = vbox();
        pin = text("", 28, TXT, false);
        pin.setLineSpacing(px(8), 1);
        left.addView(pin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout btns = hbox();
        btns.addView(pill(Lang.t("Nuovo QR", "New QR"), false, new View.OnClickListener() {
            public void onClick(View v) { refreshQr(true); }
        }), weighted(0));
        btns.addView(pill(Lang.t("Nuovo PIN", "New PIN"), false, new View.OnClickListener() {
            public void onClick(View v) { Prefs.newPin(MainActivity.this); refreshQr(true); refresh(); }
        }), weighted(14));
        left.addView(btns);
        webBox.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackground(round(Color.WHITE, 16, 0));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(px(230), ViewGroup.LayoutParams.MATCH_PARENT);
        qlp.leftMargin = px(24);
        webBox.addView(qr, qlp);
        c.addView(webBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return c;
    }

    /** New one-time QR when asked, or when the IP changed / the token expired or was used. */
    private void refreshQr(boolean force) {
        if (webBox == null) return;
        boolean on = Prefs.web(this);
        webBox.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
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

    /** Status line: version + variant, reverse safety, CarPlay call. */
    private void refresh() {
        Map<String, String> m = HelperService.status(this);
        status.setText("v" + m.get(Lang.t("Versione", "Version"))
                + "\n" + Lang.t("Retro: ", "Reverse: ") + m.get(Lang.t("Retromarcia", "Reverse"))
                + "  ·  " + Lang.t("Chiamata CarPlay: ", "CarPlay call: ") + m.get(Lang.t("Chiamata CarPlay attiva", "CarPlay call active")));
        refreshQr(false);
    }

    interface Pick { void on(int index); }

    /** Segmented choice: one pill per option, the active one lime. */
    private LinearLayout seg(String[] labels, int checked, final Pick pick) {
        final LinearLayout g = hbox();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            g.addView(pill(labels[i], i == checked, new View.OnClickListener() {
                public void onClick(View v) { select(g, idx); pick.on(idx); }
            }), weighted(i == 0 ? 0 : 12));
        }
        return g;
    }

    private void select(LinearLayout g, int idx) {
        for (int i = 0; i < g.getChildCount(); i++) stylePill((TextView) g.getChildAt(i), i == idx);
    }

    private LinearLayout toggleCard(String title, String note, final String key, boolean def) {
        LinearLayout c = card(title, note);
        c.addView(seg(offOn(), Prefs.get(this).getBoolean(key, def) ? 1 : 0, new Pick() {
            public void on(int i) {
                Prefs.get(MainActivity.this).edit().putBoolean(key, i == 1).apply();
                HelperService.start(MainActivity.this); // update session / web panel
                refresh();
            }
        }));
        return c;
    }

    // ---- style helpers (same look as Home CyberZec) ----

    int px(float v) { return Math.round(v * k); }

    private LinearLayout.LayoutParams weighted(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(96), 1);
        lp.leftMargin = px(leftMargin);
        return lp;
    }

    private LinearLayout.LayoutParams cardLp(int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.rightMargin = px(20);
        return lp;
    }

    /** Card with title and plain explanation; the control added after them sits at the bottom. */
    private LinearLayout card(String title, String note) {
        LinearLayout c = vbox();
        c.setPadding(px(32), px(30), px(32), px(30));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF141E21, 0xFF0E1618});
        g.setCornerRadius(px(30));
        g.setStroke(Math.max(1, px(2)), LINE);
        c.setBackground(g);
        c.addView(text(title, 38, TXT, true));
        TextView n = text(note, 26, MUT, false);
        n.setLineSpacing(px(8), 1);
        n.setPadding(0, px(14), 0, px(20));
        c.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return c;
    }

    /** For cards whose own content fills the space: the note keeps its natural height. */
    private void unweightNote(LinearLayout card) {
        card.getChildAt(1).setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView pill(String s, boolean on, View.OnClickListener click) {
        TextView t = text(s, 30, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(16), 0, px(16), 0);
        stylePill(t, on);
        t.setOnClickListener(click);
        return t;
    }

    private void stylePill(TextView t, boolean on) {
        t.setTextColor(on ? DARK : TXT);
        t.setBackground(on ? round(LIME, 46, 0) : round(0x0FFFFFFF, 46, 0x40FFFFFF));
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        if (stroke != 0) g.setStroke(Math.max(1, px(2)), stroke);
        return g;
    }

    private Drawable background() {
        GradientDrawable a = new GradientDrawable();
        a.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        a.setColors(new int[]{0x1AC5F56C, 0x00C5F56C});
        a.setGradientCenter(0.91f, 0.11f);
        a.setGradientRadius(px(600));
        GradientDrawable b = new GradientDrawable();
        b.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        b.setColors(new int[]{0x142FD6A6, 0x002FD6A6});
        b.setGradientCenter(0.15f, 0.97f);
        b.setGradientRadius(px(700));
        GradientDrawable base = new GradientDrawable();
        base.setColor(BG);
        return new LayerDrawable(new Drawable[]{base, a, b});
    }
}
