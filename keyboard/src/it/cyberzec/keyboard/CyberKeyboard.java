package it.cyberzec.keyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Big car keyboard, same look as the Home CyberZec city keyboard. Never logs typed text. */
public class CyberKeyboard extends InputMethodService {
    static final int BG = 0xFF080E10, BG2 = 0xFF101A1D, KEY = 0xFF182329, KEY_DOWN = 0xFF2E3A3D, LINE = 0xFF2E3A3D,
            TXT = 0xFFF1F4EE, MUT = 0xFFA3B1AF, LIME = 0xFFC5F56C, LIME_DOWN = 0xFF9FCB4E, DARK = 0xFF080E10;
    static final int KEY_H = 74, GAP = 8;

    private final KeyLogic logic = new KeyLogic();
    private final Handler h = new Handler();
    private final List<TextView> letters = new ArrayList<>();
    private LinearLayout root;
    private TextView shiftKey;
    private int page;
    private float k;

    private final Runnable repeatDel = new Runnable() {
        @Override public void run() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            h.postDelayed(this, 70);
        }
    };

    /** "Scrivi in auto": CyberZec Set (same signing key) types a text into the focused field. Never logs it. */
    private final BroadcastReceiver typeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String text = i.getStringExtra("text");
            InputConnection ic = getCurrentInputConnection();
            boolean ok = ic != null && text != null && text.length() <= 200;
            if (ok) {
                ic.commitText(text, 1);
                if (i.getBooleanExtra("enter", false)) enter(ic);
            }
            if (isOrderedBroadcast()) setResultCode(ok ? 1 : 0);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        registerReceiver(typeReceiver, new IntentFilter("it.cyberzec.keyboard.TYPE"), "it.cyberzec.permission.SETTINGS", null);
    }

    @Override public void onDestroy() {
        unregisterReceiver(typeReceiver);
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return false;   // landscape would otherwise hide the app behind a full-screen text box
    }

    @Override public View onCreateInputView() {
        k = getResources().getDisplayMetrics().density / 1.5f;   // sizes are 240 dpi pixels, like the Home
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(GAP), px(12), px(GAP), px(12));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG2, BG}));
        return root;
    }

    @Override public void onStartInputView(EditorInfo ei, boolean restarting) {
        super.onStartInputView(ei, restarting);
        logic.shift = KeyLogic.OFF;
        page = KeyLogic.startPage(ei.inputType);
        build();
        autoCaps();
    }

    @Override public void onFinishInputView(boolean finishing) {
        h.removeCallbacks(repeatDel);
        super.onFinishInputView(finishing);
    }

    private void build() {
        if (root == null) return;
        h.removeCallbacks(repeatDel);
        root.removeAllViews();
        letters.clear();
        shiftKey = null;
        String[][] rows = KeyLogic.rows(page);
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = new LinearLayout(this);
            float used = 0;
            for (String key : rows[r]) used += KeyLogic.weight(key);
            if (used < 10) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, (10 - used) / 2));
            for (String key : rows[r]) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(KEY_H), KeyLogic.weight(key));
                lp.setMargins(px(GAP / 2), 0, px(GAP / 2), 0);
                row.addView(key(key), lp);
            }
            if (used < 10) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, (10 - used) / 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            if (r > 0) lp.topMargin = px(GAP);
            root.addView(row, lp);
        }
    }

    private TextView key(final String key) {
        final TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        boolean action = key.equals(KeyLogic.ACTION);
        String shown = action ? KeyLogic.actionLabel(editorAction()) : key.equals(KeyLogic.SPACE) ? KeyLogic.t("spazio", "space") : key;
        t.setText(shown);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(shown.length() > 1 ? 28 : 34));
        t.setTextColor(action ? DARK : KeyLogic.target(key) >= 0 || key.equals(KeyLogic.SPACE) ? MUT : TXT);
        t.setBackground(action ? keyBg(LIME, LIME_DOWN) : keyBg(KEY, KEY_DOWN));
        if (key.equals(KeyLogic.SHIFT)) shiftKey = t;
        else if (key.length() == 1 && Character.isLetter(key.charAt(0))) letters.add(t);
        if (key.equals(KeyLogic.DEL)) {
            t.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        v.setPressed(true);
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                        h.postDelayed(repeatDel, 400);
                    } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        v.setPressed(false);
                        h.removeCallbacks(repeatDel);
                        autoCaps();
                    }
                    return true;
                }
            });
            return t;
        }
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                press(key);
            }
        });
        return t;
    }

    private void press(String key) {
        int target = KeyLogic.target(key);
        if (target >= 0) {
            page = target;
            build();
            autoCaps();
            return;
        }
        if (key.equals(KeyLogic.SHIFT)) {
            logic.tapShift(SystemClock.uptimeMillis());
            refreshShift();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (key.equals(KeyLogic.ACTION)) {
            enter(ic);
        } else {
            ic.commitText(logic.label(key), 1);
        }
        autoCaps();
    }

    private void enter(InputConnection ic) {
        int a = editorAction();
        if (a != 0) ic.performEditorAction(a);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private int editorAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei == null ? 0 : KeyLogic.editorAction(ei.imeOptions, ei.inputType);
    }

    /** Uppercase at the start of the field / after ". " when the editor asks for it (CAP_SENTENCES/WORDS). */
    private void autoCaps() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        logic.autoCaps(ei != null && ic != null && ei.inputType != 0 && ic.getCursorCapsMode(ei.inputType) != 0);
        refreshShift();
    }

    private void refreshShift() {
        for (TextView t : letters) t.setText(logic.label(t.getText().toString().toLowerCase(java.util.Locale.ROOT)));
        if (shiftKey == null) return;
        boolean on = logic.shift != KeyLogic.OFF;
        shiftKey.setText(logic.shift == KeyLogic.LOCK ? "⇪" : KeyLogic.SHIFT);
        shiftKey.setTextColor(on ? DARK : TXT);
        shiftKey.setBackground(on ? keyBg(LIME, LIME_DOWN) : keyBg(KEY, KEY_DOWN));
    }

    private StateListDrawable keyBg(int color, int pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed));
        s.addState(new int[0], round(color));
        return s;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(18));
        g.setStroke(Math.max(1, px(2)), color == KEY || color == KEY_DOWN ? LINE : color);
        return g;
    }

    private int px(float v) {
        return Math.round(v * k);
    }
}
