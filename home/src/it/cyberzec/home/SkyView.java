package it.cyberzec.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Sky behind clock and weather: colours from time of day + weather, with a slow, faint animation
 *  (sun glow, drifting clouds, falling drops, sunset glow, twinkling stars). Stops when the Home is not visible. */
final class SkyView extends View {
    private static final int[][][] SKIES = {   // [phase][kind] top, middle, bottom; kind 0 clear, 1 cloudy, 2 rain
        {{0xFF1E3F66, 0xFF3D6C99, 0xFFC88A5E}, {0xFF2A3646, 0xFF465468, 0xFF7A6A62}, {0xFF151E2A, 0xFF1E2C3C, 0xFF273A4E}},
        {{0xFF0E3F6E, 0xFF1F67A0, 0xFF4B92C4}, {0xFF2A3A4A, 0xFF3E5163, 0xFF5B6C7C}, {0xFF151E2A, 0xFF1E2C3C, 0xFF273A4E}},
        {{0xFF1A1B38, 0xFF3A2440, 0xFF6A3328}, {0xFF22233A, 0xFF3A3046, 0xFF5A3A38}, {0xFF151E2A, 0xFF1E2C3C, 0xFF273A4E}},
        {{0xFF05080F, 0xFF0A1022, 0xFF121A30}, {0xFF0A0E18, 0xFF121828, 0xFF1A2234}, {0xFF0B1018, 0xFF111A26, 0xFF18243A}}};
    private static final long FRAME_MS = 50;   // 20 fps is plenty for this slow motion

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), fx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float radius;
    private int phase = -1, kind = -1;
    private boolean running;
    private final long start = SystemClock.elapsedRealtime();

    SkyView(Context c, float radius) {
        super(c);
        this.radius = radius;
    }

    void set(int phase, int kind) {
        if (phase == this.phase && kind == this.kind) return;
        this.phase = phase;
        this.kind = kind;
        fill.setShader(null);   // rebuilt for the new colours
        invalidate();
    }

    private final Runnable frame = new Runnable() { public void run() { invalidate(); } };

    /** False when the Home is not visible: the last frame stays still. Called every 2 s: an invalidate dropped
     *  while the window was hidden (e.g. CarPlay on top) would otherwise stop the animation for good. */
    void setRunning(boolean on) {
        running = on;
        removeCallbacks(frame);
        if (on) postOnAnimation(frame);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        fill.setShader(null);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (phase < 0 || w == 0) return;
        if (fill.getShader() == null)
            fill.setShader(new LinearGradient(0, 0, 0, h, SKIES[phase][kind], new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float t = (SystemClock.elapsedRealtime() - start) / 1000f;
        c.save();
        c.clipPath(clip);
        c.drawRect(0, 0, w, h, fill);
        if (kind == 0 && phase == 0) glow(c, w * 0.72f, h * 0.05f, w * 0.30f, 0xFFFFD25A, 0.30f + 0.15f * wave(t, 24));
        if (phase == 2) glow(c, w * 0.6f, h * (0.84f + 0.04f * wave(t, 40)), w * 0.45f, 0xFFFF8C46, 0.42f);
        if (phase == 3 && kind != 2) stars(c, w, h, t);
        if (kind == 1 || (kind == 0 && phase < 2)) clouds(c, w, h, t, kind == 1 ? 3 : 1);
        if (kind == 2) drops(c, w, h, t);
        c.restore();
        if (running) {
            removeCallbacks(frame);   // one pending frame at most
            postDelayed(frame, FRAME_MS);
        }
    }

    /** 0..1..0 over the period, smooth. */
    private static float wave(float t, float period) {
        return 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * t / period);
    }

    private void glow(Canvas c, float x, float y, float r, int color, float alpha) {
        int a = Math.round(alpha * 255) << 24 | (color & 0xFFFFFF);
        fx.setShader(new RadialGradient(x, y, r, a, color & 0xFFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, fx);
        fx.setShader(null);
    }

    private void clouds(Canvas c, int w, int h, float t, int n) {
        fx.setColor(kind == 1 ? 0x1CFFFFFF : 0x14FFFFFF);
        float span = w + 520;
        for (int i = 0; i < n; i++) {
            float speed = 18 + i * 5, y = h * (0.12f + 0.16f * i), cw = 180 + 60 * (i % 2);
            float x = (t * speed + i * span / n) % span - 260;
            c.drawRoundRect(x, y, x + cw, y + 56, 28, 28, fx);
            c.drawCircle(x + cw * 0.38f, y + 6, 44, fx);
        }
    }

    private void drops(Canvas c, int w, int h, float t) {   // "Pioggia A": short drops at different speeds
        fx.setStrokeWidth(3);
        fx.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 26; i++) {
            float speed = 260 + (i % 5) * 45, x = (i * 29 + 11) % w, len = 22;
            float y = ((t + i * 0.53f) * speed) % (h + 60) - 40;
            fx.setColor((i % 3 == 0 ? 0x8C : i % 3 == 1 ? 0x66 : 0x47) << 24 | 0xAAD2FA);
            c.drawLine(x, y, x, y + len, fx);
        }
    }

    private void stars(Canvas c, int w, int h, float t) {
        for (int i = 0; i < 28; i++) {   // twinkle every ~5 s and drift slowly right (6 px/s): visible, not distracting
            float a = 0.15f + 0.85f * wave(t + i * 0.37f * 4, 5);
            fx.setColor(Math.round(a * 230) << 24 | 0xFFFFFF);
            float x = ((37 * i * 7) + t * 6) % w;
            c.drawCircle(x, (53 * i * 3) % (h * 0.55f) + 10, i % 3 == 0 ? 3.4f : 2.4f, fx);
        }
    }
}
