package it.dfsk.helper;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** PIN login, sessions, CSRF and LAN check for the web panel. Pure Java (JVM self-check). Times in ms. */
public final class WebAuth {
    public static final long IDLE_MS = 30L * 60 * 1000, LOCK_MS = 5L * 60 * 1000, PAIR_MS = 5L * 60 * 1000;
    public static final int MAX_TRIES = 5, MAX_SESSIONS = 4;
    public static final WebAuth INSTANCE = new WebAuth();

    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, String[]> sessions = new LinkedHashMap<>(); // token -> {csrf, lastUsedMs}
    private String pin;
    private int fails;
    private long lockUntil;
    private String pairToken; // one-time QR code token, PAIR_MS validity
    private long pairAt;

    public static String newPin(SecureRandom r) { return String.format("%06d", r.nextInt(1_000_000)); }

    /** New PIN: logs everybody out, clears the QR token and the lockout. */
    public synchronized void setPin(String p) {
        if (p != null && p.equals(pin)) return; // same PIN (service restart): keep sessions and the current QR
        pin = p;
        pairToken = null;
        sessions.clear();
        fails = 0;
        lockUntil = 0;
    }

    public synchronized boolean isLocked(long now) { return now < lockUntil; }

    /** @return session token, or null (wrong PIN, malformed, or locked out). */
    public synchronized String login(String tryPin, long now) {
        if (isLocked(now) || pin == null) return null;
        if (tryPin == null || !eq(pin, tryPin.trim())) {
            if (++fails >= MAX_TRIES) { fails = 0; lockUntil = now + LOCK_MS; }
            return null;
        }
        fails = 0;
        return newSession(now);
    }

    /** New one-time pairing token (128 bit, URL-safe hex); replaces any previous one. */
    public synchronized String newPairing(long now) {
        pairToken = hex(16);
        pairAt = now;
        return pairToken;
    }

    /** @return true while the current pairing token is unused and younger than PAIR_MS. */
    public synchronized boolean pairingValid(long now) {
        return pairToken != null && now >= pairAt && now - pairAt < PAIR_MS;
    }

    /** QR code login: valid, unexpired, single use. @return session token or null. */
    public synchronized String pair(String token, long now) {
        if (token == null || !pairingValid(now) || !eq(pairToken, token)) return null;
        pairToken = null;
        return newSession(now);
    }

    private String newSession(long now) {
        if (sessions.size() >= MAX_SESSIONS) { Iterator<String> it = sessions.keySet().iterator(); it.next(); it.remove(); }
        String token = hex(16);
        sessions.put(token, new String[]{hex(16), Long.toString(now)});
        return token;
    }

    /** @return the session's CSRF token (and refreshes the idle timer), or null if the session is invalid/expired. */
    public synchronized String csrf(String token, long now) {
        String[] s = token == null ? null : sessions.get(token);
        if (s == null) return null;
        long last = Long.parseLong(s[1]);
        if (now - last > IDLE_MS || now < last) { sessions.remove(token); return null; }
        s[1] = Long.toString(now);
        return s[0];
    }

    public synchronized boolean checkCsrf(String token, String csrf, long now) {
        String expected = csrf(token, now);
        return expected != null && csrf != null && eq(expected, csrf);
    }

    public synchronized void logout(String token) { if (token != null) sessions.remove(token); }

    /** Only private/loopback IPv4: 10/8, 172.16/12, 192.168/16, 127/8. */
    public static boolean isLan(byte[] a) {
        if (a == null || a.length != 4) return false;
        int b0 = a[0] & 0xff, b1 = a[1] & 0xff;
        return b0 == 10 || b0 == 127 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168);
    }

    static boolean eq(String a, String b) { // constant time
        if (a.length() != b.length()) return false;
        int d = 0;
        for (int i = 0; i < a.length(); i++) d |= a.charAt(i) ^ b.charAt(i);
        return d == 0;
    }

    private String hex(int bytes) {
        byte[] b = new byte[bytes];
        rnd.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
