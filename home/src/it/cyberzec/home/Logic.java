package it.cyberzec.home;

/** Pure logic, no Android imports: checked on the JVM by test/LogicTest.java. */
final class Logic {
    private Logic() {}

    /** Level shown with half steps: each key press moves the unit 0.5 (28.0 = 4, 28.5 = "4,5"; level 0 does not exist). */
    static String climaLevelText(float vhalTemp) {
        float lv = vhalTemp >= 25f ? vhalTemp - 24f : vhalTemp - 25f;
        int whole = (int) lv;
        return lv == whole ? String.valueOf(whole) : (lv < 0 && whole == 0 ? "-0" : String.valueOf(whole)) + ",5";
    }

    /** WMO weather code (Open-Meteo) to short Italian text. */
    static String meteoText(int code) {
        if (code == 0) return "Sereno";
        if (code <= 2) return "Poco nuvoloso";
        if (code == 3) return "Nuvoloso";
        if (code == 45 || code == 48) return "Nebbia";
        if (code >= 51 && code <= 57) return "Pioviggine";
        if (code >= 61 && code <= 67) return "Pioggia";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Rovesci";
        if (code == 85 || code == 86) return "Rovesci di neve";
        if (code >= 95) return "Temporale";
        return "—";
    }

    /** Weather icon family for a WMO code. */
    static String meteoIcon(int code) {
        if (code == 0) return "sole";
        if (code <= 2) return "sole_nubi";
        if (code == 3) return "nubi";
        if (code == 45 || code == 48) return "nebbia";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "neve";
        if (code >= 95) return "temporale";
        return "pioggia";
    }

    static final String[] MODE_NAMES = {"Viso", "Viso + piedi", "Piedi", "Piedi + vetro"};

    /** Short label for the big in-car chip. */
    static String internetShort(boolean connected, boolean wifi, boolean metered) {
        if (!connected) return "Offline";
        if (wifi) return metered ? "Hotspot" : "Wi-Fi";
        return "Internet";
    }

    static int pages(int apps, int perPage) {
        return Math.max(1, (apps + perPage - 1) / perPage);
    }

    /** Page to settle on after a drag: a 15% drag or a fling moves one page from where the drag started. */
    static int snapPage(int startPage, int scrollX, int pageWidth, int velocityX, int pageCount) {
        int p;
        if (velocityX > 0) p = startPage + 1;
        else if (velocityX < 0) p = startPage - 1;
        else {
            float d = scrollX - startPage * (float) pageWidth;
            p = d > pageWidth * 0.15f ? startPage + 1 : d < -pageWidth * 0.15f ? startPage - 1 : startPage;
        }
        return Math.max(0, Math.min(pageCount - 1, p));
    }
}
