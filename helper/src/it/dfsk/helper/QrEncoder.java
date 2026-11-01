package it.dfsk.helper;

import java.nio.charset.StandardCharsets;

/**
 * Minimal QR Code encoder (ISO/IEC 18004): byte mode, error correction level M, versions 1-10 (up to 213 bytes).
 * Written for this app following the structure of the public-domain algorithm description; pure Java (JVM self-check).
 * shortcut: only ECC M / versions 1-10, enough for the pairing URL (~60 bytes); extend the tables if longer text is needed.
 */
public final class QrEncoder {
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    public final int size;
    private final boolean[][] mod, fn; // [y][x]

    private QrEncoder(int ver) {
        size = ver * 4 + 17;
        mod = new boolean[size][size];
        fn = new boolean[size][size];
    }

    public boolean get(int x, int y) { return mod[y][x]; }

    /** @throws IllegalArgumentException if the text does not fit in version 10-M. */
    public static QrEncoder encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = 1;
        for (; ver <= 10; ver++) if (4 + (ver < 10 ? 8 : 16) + data.length * 8 <= dataCodewords(ver) * 8) break;
        if (ver > 10) throw new IllegalArgumentException("text too long for QR v10-M");
        QrEncoder q = new QrEncoder(ver);
        q.drawFunctionPatterns(ver);
        q.drawCodewords(addEcc(ver, dataBits(ver, data)));
        int best = 0;
        long bestPen = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            q.applyMask(m);
            q.drawFormat(m);
            long p = q.penalty();
            if (p < bestPen) { bestPen = p; best = m; }
            q.applyMask(m); // undo (XOR)
        }
        q.applyMask(best);
        q.drawFormat(best);
        return q;
    }

    static int rawCodewords(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int n = ver / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (ver >= 7) r -= 36;
        }
        return r / 8;
    }

    static int dataCodewords(int ver) { return rawCodewords(ver) - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]; }

    private static byte[] dataBits(int ver, byte[] data) {
        int cap = dataCodewords(ver);
        byte[] out = new byte[cap];
        int[] pos = {0};
        put(out, pos, 0b0100, 4);
        put(out, pos, data.length, ver < 10 ? 8 : 16);
        for (byte b : data) put(out, pos, b & 0xff, 8);
        put(out, pos, 0, Math.min(4, cap * 8 - pos[0]));
        pos[0] = (pos[0] + 7) / 8 * 8;
        for (int pad = 0xEC; pos[0] < cap * 8; pad ^= 0xEC ^ 0x11) put(out, pos, pad, 8);
        return out;
    }

    private static void put(byte[] out, int[] pos, int val, int len) {
        for (int i = len - 1; i >= 0; i--, pos[0]++)
            if (((val >>> i) & 1) != 0) out[pos[0] >>> 3] |= (byte) (0x80 >>> (pos[0] & 7));
    }

    private static byte[] addEcc(int ver, byte[] data) {
        int nb = NUM_BLOCKS[ver], ecLen = ECC_PER_BLOCK[ver], raw = rawCodewords(ver);
        int numShort = nb - raw % nb, shortLen = raw / nb;
        byte[] div = rsDivisor(ecLen);
        byte[][] blocks = new byte[nb][];
        for (int i = 0, k = 0; i < nb; i++) {
            int datLen = shortLen - ecLen + (i < numShort ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, div);
            byte[] b = new byte[shortLen + 1];
            System.arraycopy(dat, 0, b, 0, datLen);
            System.arraycopy(ecc, 0, b, b.length - ecLen, ecLen); // short blocks keep a gap at index datLen
            blocks[i] = b;
        }
        byte[] out = new byte[raw];
        int o = 0;
        for (int i = 0; i < shortLen + 1; i++)
            for (int j = 0; j < nb; j++)
                if (i != shortLen - ecLen || j >= numShort) out[o++] = blocks[j][i];
        return out;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                r[j] = (byte) mul(r[j] & 0xff, root);
                if (j + 1 < degree) r[j] ^= r[j + 1];
            }
            root = mul(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int f = (b ^ r[0]) & 0xff;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) mul(div[i] & 0xff, f);
        }
        return r;
    }

    private static int mul(int x, int y) { // GF(2^8) mod 0x11D
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void set(int x, int y, boolean dark) { mod[y][x] = dark; fn[y][x] = true; }

    private void drawFunctionPatterns(int ver) {
        for (int i = 0; i < size; i++) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0); }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
        if (ver >= 2) {
            int n = ver / 7 + 2, step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
            int[] p = new int[n];
            p[0] = 6;
            for (int i = n - 1, v = ver * 4 + 10; i >= 1; i--, v -= step) p[i] = v;
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                    set(p[i] + dx, p[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
        drawFormat(0); // reserve; rewritten with the chosen mask
        if (ver >= 7) {
            int rem = ver;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = ver << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean b = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3, c = i / 3;
                set(a, c, b); set(c, a, b);
            }
        }
    }

    private void finder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int d = Math.max(Math.abs(dx), Math.abs(dy)), xx = x + dx, yy = y + dy;
            if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, d != 2 && d != 4);
        }
    }

    /** 15-bit format word for ECC M (indicator 00) and the given mask. */
    public static int formatBits(int mask) {
        int data = mask; // (M = 0b00) << 3 | mask
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        return (data << 10 | rem) ^ 0x5412;
    }

    private void drawFormat(int mask) {
        int bits = formatBits(mask);
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6)); set(8, 8, bit(bits, 7)); set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) for (int j = 0; j < 2; j++) {
                int x = right - j, y = ((right + 1) & 2) == 0 ? size - 1 - vert : vert;
                if (!fn[y][x] && i < data.length * 8) { mod[y][x] = bit(data[i >>> 3], 7 - (i & 7)); i++; }
            }
        }
    }

    private void applyMask(int m) {
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            boolean inv;
            switch (m) {
                case 0: inv = (x + y) % 2 == 0; break;
                case 1: inv = y % 2 == 0; break;
                case 2: inv = x % 3 == 0; break;
                case 3: inv = (x + y) % 3 == 0; break;
                case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
            }
            if (inv && !fn[y][x]) mod[y][x] = !mod[y][x];
        }
    }

    /** Rules 1 (runs), 2 (2x2 blocks) and 4 (balance); rule 3 skipped: only affects which valid mask is picked. */
    private long penalty() {
        long p = 0;
        for (int a = 0; a < size; a++) for (int dir = 0; dir < 2; dir++) {
            int run = 1;
            for (int b = 1; b < size; b++) {
                boolean cur = dir == 0 ? mod[a][b] : mod[b][a], prev = dir == 0 ? mod[a][b - 1] : mod[b - 1][a];
                if (cur == prev) { if (++run == 5) p += 3; else if (run > 5) p++; } else run = 1;
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (mod[y][x]) dark++;
            if (x + 1 < size && y + 1 < size && mod[y][x] == mod[y][x + 1] && mod[y][x] == mod[y + 1][x] && mod[y][x] == mod[y + 1][x + 1]) p += 3;
        }
        int total = size * size;
        p += (Math.abs(dark * 20 - total * 10) + total - 1) / total * 10L;
        return p;
    }
}
