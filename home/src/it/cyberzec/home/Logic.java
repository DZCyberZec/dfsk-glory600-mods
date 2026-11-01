package it.cyberzec.home;

/** Pure logic, no Android imports: checked on the JVM by test/LogicTest.java. */
final class Logic {
    private Logic() {}

    /** Italian text when the unit is set to Italian, English otherwise. */
    static String t(String it, String en) {
        return "it".equals(java.util.Locale.getDefault().getLanguage()) ? it : en;
    }

    /** Level shown with half steps: each key press moves the unit 0.5 (28.0 = 4, 28.5 = "4,5"; level 0 does not exist). */
    static String climaLevelText(float vhalTemp) {
        float lv = vhalTemp >= 25f ? vhalTemp - 24f : vhalTemp - 25f;
        int whole = (int) lv;
        return lv == whole ? String.valueOf(whole) : (lv < 0 && whole == 0 ? "-0" : String.valueOf(whole)) + t(",5", ".5");
    }

    /** WMO weather code (Open-Meteo) to short text. */
    static String meteoText(int code) {
        if (code == 0) return t("Sereno", "Clear");
        if (code <= 2) return t("Poco nuvoloso", "Partly cloudy");
        if (code == 3) return t("Nuvoloso", "Cloudy");
        if (code == 45 || code == 48) return t("Nebbia", "Fog");
        if (code >= 51 && code <= 57) return t("Pioviggine", "Drizzle");
        if (code >= 61 && code <= 67) return t("Pioggia", "Rain");
        if (code >= 71 && code <= 77) return t("Neve", "Snow");
        if (code >= 80 && code <= 82) return t("Rovesci", "Showers");
        if (code == 85 || code == 86) return t("Rovesci di neve", "Snow showers");
        if (code >= 95) return t("Temporale", "Thunderstorm");
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

    static String modeName(int m) {
        return new String[]{t("Viso", "Face"), t("Viso + piedi", "Face + feet"), t("Piedi", "Feet"), t("Piedi + vetro", "Feet + glass")}[m];
    }

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
