import it.dfsk.helper.WebAuth;
import it.dfsk.helper.WebPanel;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Mac-only stand-in for the car: the real DFSK Helper web panel code with a fake backend, port 8091. */
public class FakeCar {
    static final Map<String, String> state = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.ITALY);   // the car is Italian
        state.put("mic", "1"); state.put("reverse", "1"); state.put("callvol", "on"); state.put("adb", "on"); state.put("voice", "on");
        WebAuth auth = new WebAuth();
        auth.setPin("123456");
        WebPanel panel = new WebPanel(new WebPanel.Backend() {
            public Map<String, String> status() {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("Versione", "1.3-system (4), variante di sistema (FINTA, Mac)");
                m.put("Retromarcia", "compatibile");
                m.put("Musica in retromarcia", new String[]{"Pausa", "Attenuata", "Più bassa"}[Integer.parseInt(state.get("reverse"))]);
                m.put("Volume chiamata dal volante", "on".equals(state.get("callvol")) ? "attivo" : "spento");
                m.put("Microfono CarPlay", state.get("mic"));
                m.put("ADB Wi-Fi", "on".equals(state.get("adb")) ? "attivo (porta 5555)" : "spento");
                m.put("Voce di benvenuto", "on".equals(state.get("voice")) ? "attiva" : "spenta");
                return m;
            }
            public boolean act(String x, String v) {
                System.out.println("comando " + x + " = " + v);
                if (!x.equals("home")) state.put(x, v);
                return true;
            }
        }, auth);
        System.out.println("Fake car on http://127.0.0.1:8091  PIN 123456");
        System.out.println("QR link: http://127.0.0.1:8091/?pair=" + auth.newPairing(System.currentTimeMillis()));
        try (ServerSocket ss = new ServerSocket(8091)) {
            while (true) try (Socket s = ss.accept()) {
                panel.handle(s.getInputStream(), s.getOutputStream(), System.currentTimeMillis());
            } catch (Exception e) { System.out.println("errore: " + e); }
        }
    }
}
