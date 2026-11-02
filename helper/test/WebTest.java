import it.dfsk.helper.QrEncoder;
import it.dfsk.helper.WebAuth;
import it.dfsk.helper.WebPanel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Run with: java -ea (see build.sh). Auth, parsing and the request handler, without sockets. */
public class WebTest {
    static final List<String> acts = new ArrayList<>();
    static WebPanel panel;

    static String req(String raw, long now) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        panel.handle(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), out, now);
        return out.toString("UTF-8");
    }

    static String post(String path, String cookie, String body, long now) throws Exception {
        return req("POST " + path + " HTTP/1.1\r\nHost: x\r\n" + (cookie != null ? "Cookie: " + cookie + "\r\n" : "")
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                + "\r\n\r\n" + body, now);
    }

    static String find(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    public static void main(String[] a) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.ITALY);
        boolean ea = false; assert ea = true; if (!ea) throw new AssertionError("run with java -ea");

        // LAN filter
        assert WebAuth.isLan(new byte[]{10, 1, 2, 3}) && WebAuth.isLan(new byte[]{127, 0, 0, 1});
        assert WebAuth.isLan(new byte[]{(byte) 172, 16, 0, 1}) && WebAuth.isLan(new byte[]{(byte) 172, 31, 9, 9});
        assert !WebAuth.isLan(new byte[]{(byte) 172, 32, 0, 1}) && !WebAuth.isLan(new byte[]{(byte) 172, 15, 0, 1});
        assert WebAuth.isLan(new byte[]{(byte) 192, (byte) 168, 1, 64}) && !WebAuth.isLan(new byte[]{(byte) 192, (byte) 169, 1, 1});
        assert !WebAuth.isLan(new byte[]{8, 8, 8, 8}) && !WebAuth.isLan(new byte[16]) && !WebAuth.isLan(null);

        // PIN, lockout, sessions, idle expiry
        WebAuth au = new WebAuth();
        assert au.login("123456", 0) == null;                       // no PIN set yet
        au.setPin("123456");
        for (int i = 0; i < 4; i++) assert au.login("000000", 0) == null;
        String t = au.login(" 123456 ", 0);
        assert t != null && t.length() == 32;                      // 4 failures, then right PIN: ok and counter reset
        for (int i = 0; i < 5; i++) assert au.login("999999", 10) == null;
        assert au.isLocked(10) && au.login("123456", 10) == null;  // locked even with the right PIN
        assert !au.isLocked(10 + WebAuth.LOCK_MS) && au.login("123456", 10 + WebAuth.LOCK_MS) != null;
        String c = au.csrf(t, 1000);
        assert c != null && au.checkCsrf(t, c, 2000) && !au.checkCsrf(t, c + "x", 2000) && !au.checkCsrf(t, null, 2000);
        assert !au.checkCsrf("nope", c, 2000) && au.csrf(null, 0) == null;
        assert au.csrf(t, 2000 + WebAuth.IDLE_MS) != null;         // touched -> still alive
        assert au.csrf(t, 2001 + 2 * WebAuth.IDLE_MS) == null;     // idle > 30 min -> gone
        String t2 = au.login("123456", 0);
        au.setPin("654321");
        assert au.csrf(t2, 0) == null;                             // new PIN logs everybody out
        assert au.login("123456", 0) == null && au.login("654321", 0) != null;
        // QR pairing token: 128 bit, single use, 5 minutes, replaced by a new one, cleared by a new PIN
        WebAuth pa = new WebAuth(); pa.setPin("135790");
        assert !pa.pairingValid(0) && pa.pair("x", 0) == null;
        String p1 = pa.newPairing(1000);
        assert p1.matches("[0-9a-f]{32}") && pa.pairingValid(1000);
        assert pa.pair(p1 + "0", 1001) == null && pa.pair(null, 1001) == null && pa.pairingValid(1001); // wrong token keeps it
        String ps = pa.pair(p1, 1002);
        assert ps != null && pa.csrf(ps, 1003) != null;            // creates a normal session
        assert pa.pair(p1, 1004) == null && !pa.pairingValid(1004); // single use
        String p2 = pa.newPairing(2000);
        assert !pa.pairingValid(2000 + WebAuth.PAIR_MS) && pa.pair(p2, 2000 + WebAuth.PAIR_MS) == null; // expired
        String p3 = pa.newPairing(3000), p4 = pa.newPairing(3001);
        assert pa.pair(p3, 3002) == null && pa.pair(p4, 3002) != null; // new QR invalidates the old one
        String p5 = pa.newPairing(4000);
        assert pa.pair(p5, 3999) == null;                           // clock before issue -> invalid
        String p6 = pa.newPairing(5000); pa.setPin("000111");
        assert pa.pair(p6, 5001) == null;                           // new PIN clears pairing too

        WebAuth cap = new WebAuth(); cap.setPin("111111");
        String first = cap.login("111111", 0);
        for (int i = 0; i < WebAuth.MAX_SESSIONS; i++) cap.login("111111", 0);
        assert cap.csrf(first, 0) == null;                         // oldest session evicted
        assert WebAuth.newPin(new java.security.SecureRandom()).matches("[0-9]{6}");

        // handler end to end
        WebAuth wa = new WebAuth();
        wa.setPin("246810");
        panel = new WebPanel(new WebPanel.Backend() {
            public Map<String, String> status() { Map<String, String> m = new LinkedHashMap<>(); m.put("Versione", "<1.2>"); m.put("Parcheggio", "45.46421,9.19003,1793632200000"); return m; }
            public boolean act(String x, String v) { acts.add(x + "=" + v); return true; }
            public String[][] dynamic() { return new String[][]{{"city", "Città meteo", "gps", "GPS", "Milano|45.5|9.2", "Milano"},
                    {"hotspot", "Hotspot automatico", "off", "Spento", "iPhone <Anna>", "iPhone <Anna>"}}; }
            public String current(String x) { return "name".equals(x) ? "Anna \"<b>\"" : ""; }
        }, wa);
        String r = req("GET / HTTP/1.1\r\nHost: x\r\n\r\n", 0);
        assert r.startsWith("HTTP/1.1 200") && r.contains("name=pin") && !r.contains("Versione");
        assert r.contains("Content-Security-Policy: default-src 'none'");
        assert req("GARBAGE\r\n\r\n", 0).startsWith("HTTP/1.1 400");
        assert req("GET / HTTP/1.1\r\nContent-Length: 99999\r\n\r\n", 0).startsWith("HTTP/1.1 400"); // body too big
        assert req("GET /x HTTP/1.1\r\n\r\n", 0).startsWith("HTTP/1.1 405");
        assert req("PUT / HTTP/1.1\r\n\r\n", 0).startsWith("HTTP/1.1 405");
        assert post("/a", null, "a=home&v=restart", 0).startsWith("HTTP/1.1 403") && acts.isEmpty(); // no session

        r = post("/login", null, "pin=000000", 0);
        assert r.startsWith("HTTP/1.1 303") && !r.contains("Set-Cookie");
        r = post("/login", null, "pin=246810", 0);
        String sid = find(r, "Set-Cookie: sid=([0-9a-f]{32});");
        assert sid != null && r.contains("HttpOnly") && r.contains("SameSite=Strict");
        String ck = "foo=1; sid=" + sid;
        r = req("GET / HTTP/1.1\r\nCookie: " + ck + "\r\n\r\n", 10);
        assert r.contains("&lt;1.2&gt;") && !r.contains("<1.2>");   // status escaped
        assert r.contains("<tr><th>Parcheggio</th><td>45.46421,9.19003,1793632200000</td></tr>"); // exact row the iPhone parses
        String csrf = find(r, "name=csrf value=\"([0-9a-f]{32})\"");
        assert csrf != null;

        assert post("/a", ck, "a=home&v=restart", 20).startsWith("HTTP/1.1 403");                 // missing CSRF
        assert post("/a", ck, "csrf=" + csrf + "x&a=home&v=restart", 20).startsWith("HTTP/1.1 403");
        assert post("/a", ck, "csrf=" + csrf + "&a=sh&v=reboot", 20).startsWith("HTTP/1.1 400");   // not whitelisted
        assert post("/a", ck, "csrf=" + csrf + "&a=mic&v=5", 20).startsWith("HTTP/1.1 400");
        assert post("/a", ck, "csrf=" + csrf + "&a=cptrack&v=maybe", 20).startsWith("HTTP/1.1 400");
        assert acts.isEmpty();
        assert post("/a", ck, "csrf=" + csrf + "&a=mic&v=2", 30).startsWith("HTTP/1.1 303");
        assert post("/a", ck, "csrf=" + csrf + "&a=reverse&v=2&a=x", 30).startsWith("HTTP/1.1 400"); // duplicate key: last wins -> rejected
        assert post("/a", ck, "csrf=" + csrf + "&a=reverse&v=2", 30).startsWith("HTTP/1.1 303");
        assert acts.toString().equals("[mic=2, reverse=2]") : acts;
        // dynamic buttons (same HTML as the fixed ones) and text forms (class=t, exact structure for the iPhone parser)
        assert r.contains("<h2>Città meteo</h2><form method=post action=/a><input type=hidden name=csrf value=\"" + csrf
                + "\"><input type=hidden name=a value=city><button name=v value=\"gps\">GPS</button><button name=v value=\"Milano|45.5|9.2\">Milano</button></form>");
        assert r.contains("<button name=v value=\"iPhone &lt;Anna&gt;\">iPhone &lt;Anna&gt;</button>") && !r.contains("<Anna>");
        assert r.contains("<h2>Nome nel saluto</h2><form method=post action=/a class=t><input type=hidden name=csrf value=\"" + csrf
                + "\"><input type=hidden name=a value=name><input name=v maxlength=20 value=\"Anna &quot;&lt;b&gt;&quot;\"><button>Salva</button></form>");
        assert r.contains("<h2>Cerca città</h2><form method=post action=/a class=t>") && r.contains("name=a value=cityset><input name=v maxlength=64 value=\"\">");
        assert r.contains("<h2>Cielo animato</h2><form method=post action=/a>");
        assert r.contains("<h2>Scrivi in auto</h2><form method=post action=/a class=t><input type=hidden name=csrf value=\"" + csrf
                + "\"><input type=hidden name=a value=type><input name=v maxlength=200 value=\"\"><button>Salva</button></form>");
        acts.clear();
        String[] bad = {"a=city&v=Roma|41.9|12.5", "a=hotspot&v=Altro", "a=name&v=123456789012345678901", "a=name&v=%3Cb%3E",
                "a=name&v=a%0Ab", "a=cityset&v=Roma", "a=cityset&v=|41|12", "a=cityset&v=Ro;ma|41|12", "a=cityset&v=Roma|91|12",
                "a=cityset&v=Roma|41|181", "a=cityset&v=Roma|NaN|12", "a=cityset&v=Roma|1e1|12", "a=cityset&v=Roma|41|12|1", "a=sky&v=maybe"};
        for (String b : bad) assert post("/a", ck, "csrf=" + csrf + "&" + b, 31).startsWith("HTTP/1.1 400") : b;
        assert acts.isEmpty();
        String[] good = {"a=city&v=gps", "a=city&v=Milano|45.5|9.2", "a=hotspot&v=iPhone+%3CAnna%3E", "a=hotspot&v=off", "a=name&v=",
                "a=name&v=Anna-Maria+D'Ascoli", "a=name&v=Jos%C3%A9+2", "a=cityset&v=Sant'Elpidio+a+Mare|43.2306|-13.6869", "a=sky&v=off", "a=hours&v=on"};
        for (String g : good) assert post("/a", ck, "csrf=" + csrf + "&" + g, 32).startsWith("HTTP/1.1 303") : g;
        assert acts.size() == good.length && acts.get(2).equals("hotspot=iPhone <Anna>") && acts.get(6).equals("name=José 2") : acts;
        assert post("/a", ck, "csrf=" + csrf + "&a=cityset&v=Polo|-90|180", 33).startsWith("HTTP/1.1 303");
        assert post("/a", ck, "csrf=" + csrf + "&a=cityset&v=12345678901234567890123456789012345678901|1|1", 33).startsWith("HTTP/1.1 400");
        // "Scrivi in auto": 1-200 chars, no control chars; the flash never echoes the text
        String max = new String(new char[200]).replace('\0', 'x');
        String cjk = new String(new char[200]).replace("\0", "%E4%BD%A0");   // 200 x 3-byte char fits MAX_BODY
        String[] badType = {"a=type&v=", "a=type&v=" + max + "y", "a=type&v=ciao%0A", "a=type&v=a%09b", "a=type&v=%00", "a=type&v=x%7F", "a=type&v=%1F"};
        acts.clear();
        for (String b : badType) assert post("/a", ck, "csrf=" + csrf + "&" + b, 34).startsWith("HTTP/1.1 400") : b;
        assert acts.isEmpty();
        for (String g : new String[]{"a=type&v=ciao", "a=type&v=via+Roma+1%2C+Milano", "a=type&v=" + max, "a=type&v=" + cjk, "a=type&v=%3Cb%3E+%F0%9F%9A%97"})
            assert post("/a", ck, "csrf=" + csrf + "&" + g, 34).startsWith("HTTP/1.1 303") : g;
        assert acts.size() == 5 && acts.get(1).equals("type=via Roma 1, Milano") && acts.get(4).equals("type=<b> \uD83D\uDE97") : acts;
        r = req("GET / HTTP/1.1\r\nCookie: " + ck + "\r\n\r\n", 35);
        assert r.contains("<p class=f>Inviato</p>") && !r.contains("<b> ") : "flash";
        assert post("/a", ck, "csrf=" + csrf + "&a=adb&v=on", 40 + WebAuth.IDLE_MS + 31).startsWith("HTTP/1.1 403"); // idle expiry

        // QR link through the handler: sets the cookie once, then clean redirect; reuse fails
        String pt = wa.newPairing(100);
        r = req("GET /?pair=" + pt + " HTTP/1.1\r\n\r\n", 101);
        assert r.startsWith("HTTP/1.1 303") && r.contains("Location: /\r\n") && find(r, "Set-Cookie: sid=([0-9a-f]{32});") != null;
        r = req("GET /?pair=" + pt + " HTTP/1.1\r\n\r\n", 102);
        assert r.startsWith("HTTP/1.1 303") && !r.contains("Set-Cookie");
        assert req("GET / HTTP/1.1\r\n\r\n", 103).contains("già usato");  // flash shown on the login page
        java.util.Locale.setDefault(java.util.Locale.US);
        assert req("GET / HTTP/1.1\r\n\r\n", 0).contains("lang=en") && req("GET / HTTP/1.1\r\n\r\n", 0).contains("Log in");

        // QR encoder: known format word (M, mask 0 = 101010000010010), version by length, finder corners
        assert QrEncoder.formatBits(0) == 0b101010000010010;
        assert QrEncoder.encode("A").size == 21 && QrEncoder.encode("http://192.168.1.10:8091/?pair=" + pt).size == 37;
        QrEncoder q = QrEncoder.encode("http://172.20.10.2:8091/");
        for (int[] k : new int[][]{{0, 0}, {q.size - 7, 0}, {0, q.size - 7}})
            for (int d = 0; d < 7; d++) assert q.get(k[0] + d, k[1]) && q.get(k[0], k[1] + d) && q.get(k[0] + 6, k[1] + d);
        assert !q.get(7, 0) && !q.get(0, 7);                         // separators light
        try { QrEncoder.encode(new String(new char[300])); assert false; } catch (IllegalArgumentException expected) { }

        System.out.println("WebTest OK");
    }
}
