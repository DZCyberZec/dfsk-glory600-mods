package it.dfsk.helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny LAN-only web control panel (system variant). Plain java.net, one request per connection, one thread.
 * No Android imports so request parsing / auth can be tested on the JVM.
 */
public final class WebPanel {
    public interface Backend {
        /** Status lines, label -> value (shown escaped). */
        Map<String, String> status();
        /** Already whitelisted action/value (see ACTIONS). @return true on success. */
        boolean act(String action, String value);
    }

    /** Whitelist: action -> {value, label, value, label...}. Nothing else is accepted. */
    static final String[][] ACTIONS = {
        {"mic", "1", "1", "2", "2", "3", "3"},
        {"reverse", "0", "Pausa", "1", "Attenuata", "2", "Più bassa"},
        {"callvol", "on", "Attivo", "off", "Spento"},
        {"home", "restart", "Riavvia Home"},
        {"adb", "on", "Attivo", "off", "Spento"},
        {"voice", "on", "Attiva", "off", "Spenta"},
    };
    static final String[] TITLES = {"Microfono CarPlay", "Musica in retromarcia", "Volume chiamata dal volante",
            "Home", "Accesso Mac (ADB Wi-Fi)", "Voce di benvenuto"};
    static final int MAX_HEADER = 8192, MAX_BODY = 2048;

    private final Backend backend;
    private final WebAuth auth;
    private ServerSocket server;
    private volatile String flash = "";

    public WebPanel(Backend b, WebAuth a) { backend = b; auth = a; }

    public synchronized void start(int port) throws IOException {
        if (server != null) return;
        final ServerSocket ss = new ServerSocket(port); // all interfaces; non-LAN peers are dropped in loop()
        server = ss;
        Thread t = new Thread(new Runnable() { public void run() { loop(ss); } }, "DFSKWeb");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        server = null;
    }

    /** Monotonic ms clock shared with the app (the car's wall clock can jump at boot/GPS sync). */
    public static long now() { return System.nanoTime() / 1_000_000; }

    public synchronized boolean isRunning() { return server != null; }

    private void loop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try (Socket s = ss.accept()) {
                if (!WebAuth.isLan(s.getInetAddress().getAddress())) continue; // close without answering
                s.setSoTimeout(5000);
                handle(s.getInputStream(), s.getOutputStream(), now());
            } catch (Throwable ignored) { /* bad client or socket closed: keep serving */ }
        }
    }

    static final class Request {
        String method, path, query = "", body = "";
        final Map<String, String> headers = new HashMap<>();
    }

    /** @return null if malformed or too large. */
    static Request parse(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int c, tail = 0; // last 4 bytes, to find the blank line
        while (tail != 0x0d0a0d0a) {
            if ((c = in.read()) < 0 || head.size() >= MAX_HEADER) return null;
            head.write(c);
            tail = (tail << 8) | c;
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] rl = lines[0].split(" ");
        if (rl.length != 3 || !rl[2].startsWith("HTTP/1.")) return null;
        Request r = new Request();
        r.method = rl[0];
        int q = rl[1].indexOf('?');
        r.path = q >= 0 ? rl[1].substring(0, q) : rl[1];
        if (q >= 0) r.query = rl[1].substring(q + 1);
        for (int i = 1; i < lines.length; i++) {
            int k = lines[i].indexOf(':');
            if (k > 0) r.headers.put(lines[i].substring(0, k).trim().toLowerCase(), lines[i].substring(k + 1).trim());
        }
        String cl = r.headers.get("content-length");
        if (cl != null) {
            int n;
            try { n = Integer.parseInt(cl); } catch (NumberFormatException e) { return null; }
            if (n < 0 || n > MAX_BODY) return null;
            byte[] b = new byte[n];
            for (int off = 0; off < n; ) {
                int got = in.read(b, off, n - off);
                if (got < 0) return null;
                off += got;
            }
            r.body = new String(b, StandardCharsets.UTF_8);
        }
        return r;
    }

    static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int e = kv.indexOf('=');
            if (e <= 0) continue;
            try {
                m.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
            } catch (Exception ignored) { }
        }
        return m;
    }

    static String cookie(String header, String name) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }

    static boolean allowed(String action, String value) {
        for (String[] a : ACTIONS) {
            if (!a[0].equals(action)) continue;
            for (int i = 1; i < a.length; i += 2) if (a[i].equals(value)) return true;
        }
        return false;
    }

    /** Handles one request. Public for the JVM self-check. */
    public void handle(InputStream in, OutputStream out, long now) throws IOException {
        Request r = parse(in);
        if (r == null) { send(out, "400 Bad Request", null, "Richiesta non valida"); return; }
        String token = cookie(r.headers.get("cookie"), "sid");
        String csrf = auth.csrf(token, now);
        if ("GET".equals(r.method) && "/".equals(r.path)) {
            String pair = form(r.query).get("pair");
            if (pair != null) { // QR code from the app: one-time token, then a clean URL
                String t = auth.pair(pair, now);
                flash = t == null ? "Codice QR scaduto o già usato: genera un nuovo QR nell'app, oppure usa il PIN." : "";
                redirect(out, t == null ? null : "sid=" + t + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400");
                return;
            }
            send(out, "200 OK", null, csrf == null ? loginPage(now) : panelPage(csrf));
            return;
        }
        if (!"POST".equals(r.method)) { send(out, "405 Method Not Allowed", null, "Metodo non ammesso"); return; }
        Map<String, String> f = form(r.body);
        if ("/login".equals(r.path)) {
            String t = auth.login(f.get("pin"), now);
            flash = t == null ? (auth.isLocked(now) ? "Troppi tentativi: riprova tra 5 minuti." : "PIN errato.") : "";
            redirect(out, t == null ? null : "sid=" + t + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400");
        } else if ("/logout".equals(r.path) && auth.checkCsrf(token, f.get("csrf"), now)) {
            auth.logout(token);
            redirect(out, "sid=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        } else if ("/a".equals(r.path)) {
            if (!auth.checkCsrf(token, f.get("csrf"), now)) { send(out, "403 Forbidden", null, "Sessione scaduta: ricarica la pagina."); return; }
            String a = f.get("a"), v = f.get("v");
            if (!allowed(a, v)) { send(out, "400 Bad Request", null, "Comando non valido"); return; }
            flash = backend.act(a, v) ? "Fatto: " + a + " = " + v : "Non riuscito: " + a + " = " + v;
            redirect(out, null);
        } else {
            send(out, "404 Not Found", null, "Non trovato");
        }
    }

    private void redirect(OutputStream out, String setCookie) throws IOException {
        String h = "HTTP/1.1 303 See Other\r\nLocation: /\r\nContent-Length: 0\r\nConnection: close\r\n"
                + (setCookie != null ? "Set-Cookie: " + setCookie + "\r\n" : "") + "\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void send(OutputStream out, String status, String setCookie, String html) throws IOException {
        byte[] b = (html.startsWith("<!doctype") ? html : page("<p>" + esc(html) + "</p><p><a href=\"/\">Indietro</a></p>")).getBytes(StandardCharsets.UTF_8);
        String h = "HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nX-Frame-Options: DENY\r\nX-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\n"
                + "Referrer-Policy: no-referrer\r\nConnection: close\r\n"
                + (setCookie != null ? "Set-Cookie: " + setCookie + "\r\n" : "") + "\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private String takeFlash() {
        String f = flash;
        flash = "";
        return f.isEmpty() ? "" : "<p class=f>" + esc(f) + "</p>";
    }

    private String loginPage(long now) {
        return page(takeFlash() + (auth.isLocked(now) ? "<p>Accesso bloccato per qualche minuto.</p>" : "")
                + "<form method=post action=/login><label>PIN (lo trovi nell'app DFSK Helper sull'auto)<br>"
                + "<input name=pin inputmode=numeric pattern=\"[0-9]{6}\" maxlength=6 autocomplete=off required></label>"
                + "<button>Entra</button></form>");
    }

    private String panelPage(String csrf) {
        StringBuilder sb = new StringBuilder(takeFlash()).append("<h2>Stato</h2><table>");
        for (Map.Entry<String, String> e : backend.status().entrySet())
            sb.append("<tr><th>").append(esc(e.getKey())).append("</th><td>").append(esc(e.getValue())).append("</td></tr>");
        sb.append("</table>");
        String hidden = "<input type=hidden name=csrf value=\"" + esc(csrf) + "\">";
        for (int i = 0; i < ACTIONS.length; i++) {
            String[] a = ACTIONS[i];
            sb.append("<h2>").append(esc(TITLES[i])).append("</h2><form method=post action=/a>").append(hidden)
              .append("<input type=hidden name=a value=").append(a[0]).append(">");
            for (int k = 1; k < a.length; k += 2)
                sb.append("<button name=v value=\"").append(esc(a[k])).append("\">").append(esc(a[k + 1])).append("</button>");
            sb.append("</form>");
        }
        sb.append("<form method=post action=/logout>").append(hidden).append("<button class=o>Esci</button></form>");
        return page(sb.toString());
    }

    private static String page(String body) {
        return "<!doctype html><html lang=it><head><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>DFSK Helper</title><style>"
                + "body{font:17px -apple-system,system-ui,sans-serif;margin:0;padding:16px;background:#f4f4f6;color:#111}"
                + "h1{font-size:22px}h2{font-size:17px;margin:22px 0 8px}table{width:100%;border-collapse:collapse;background:#fff;border-radius:10px}"
                + "th,td{text-align:left;padding:8px;border-bottom:1px solid #eee;vertical-align:top}th{font-weight:600;width:45%}"
                + "button{font-size:17px;padding:12px 16px;margin:4px 6px 4px 0;border:0;border-radius:10px;background:#0a60ff;color:#fff}"
                + "button.o{background:#777}input{font-size:22px;padding:10px;width:9em;margin:8px 0;letter-spacing:4px}"
                + ".f{background:#fff3c4;padding:10px;border-radius:8px}"
                + "@media(prefers-color-scheme:dark){body{background:#111;color:#eee}table{background:#1d1d1f}th,td{border-color:#333}.f{background:#4a3f12}}"
                + "</style></head><body><h1>DFSK Helper</h1>" + body + "</body></html>";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
