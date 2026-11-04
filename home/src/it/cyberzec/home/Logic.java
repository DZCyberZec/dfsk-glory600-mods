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

    /** "Fano, Pesaro e Urbino, Marche, 61032, Italia" -> "Fano, Pesaro e Urbino, Marche". */
    static String placeLabel(String displayName) {
        String[] p = displayName.split(",\\s*");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.length && i < 3; i++) b.append(i > 0 ? ", " : "").append(p[i].trim());
        return b.toString();
    }

    /** Name sent by CyberZec Set: 0-20 characters, no control characters (empty = no name). */
    static boolean validName(String v) {
        return v != null && v.trim().length() <= 20 && !v.matches("(?s).*\\p{Cntrl}.*");
    }

    /** "Nome|lat|lon" sent by CyberZec Set: {lat, lon} or null (name 1-40 chars without | ; or control chars, lat -90..90, lon -180..180). */
    static double[] cityValue(String v) {
        String[] f = v == null ? new String[0] : v.split("\\|", -1);
        if (f.length != 3 || f[0].trim().isEmpty() || f[0].length() > 40 || f[0].indexOf(';') >= 0 || f[0].matches("(?s).*\\p{Cntrl}.*")) return null;
        try {
            double lat = Double.parseDouble(f[1]), lon = Double.parseDouble(f[2]);
            return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 ? new double[]{lat, lon} : null;   // NaN fails too
        } catch (NumberFormatException e) {
            return null;
        }
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

    /** Greeting by hour of day; name may be empty. */
    static String greeting(int hour) {
        if (hour >= 5 && hour < 12) return t("Buongiorno", "Good morning");
        if (hour >= 12 && hour < 18) return t("Buon pomeriggio", "Good afternoon");
        return t("Buonasera", "Good evening");
    }

    /** Sky colours of the clock card: 0 dawn, 1 day, 2 dusk, 3 night by fixed hours: fallback of sunPhase when no position is known. */
    static int skyPhase(int hour) {
        if (hour >= 6 && hour < 9) return 0;
        if (hour >= 9 && hour < 17) return 1;
        if (hour >= 17 && hour < 20) return 2;
        return 3;
    }

    /** Sunrise and sunset in minutes after local midnight (NOAA approximation, about ±2 min), or null on polar day/night. */
    static double[] sunTimes(int dayOfYear, double lat, double lon, double tzHours) {
        double g = 2 * Math.PI / 365 * (dayOfYear - 1);
        double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double la = Math.toRadians(lat);
        double cos = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        if (cos < -1 || cos > 1) return null;   // NaN at the poles fails too
        double ha = Math.toDegrees(Math.acos(cos)), noon = 720 - 4 * lon - eq + tzHours * 60;
        return new double[]{noon - 4 * ha, noon + 4 * ha};
    }

    /** skyPhase from the sun: dawn sunrise-30 min..+2 h, dusk sunset-1 h..+30 min; fixed hours when sun is null. */
    static int sunPhase(double minute, double[] sun) {
        if (sun == null) return skyPhase((int) (minute / 60));
        if (minute >= sun[0] - 30 && minute < sun[0] + 120) return 0;
        if (minute >= sun[1] - 60 && minute < sun[1] + 30) return 2;
        return minute > sun[0] && minute < sun[1] ? 1 : 3;
    }

    /** Sky background family: 0 clear, 1 cloudy, 2 rain. */
    static int skyKind(String icon) {
        if (icon.equals("pioggia") || icon.equals("temporale")) return 2;
        return icon.equals("sole") || icon.equals("sole_nubi") ? 0 : 1;
    }

    /** Weather icon at a minute of the day: a clear sky between sunset and sunrise shows the moon (fixed hours if sun is null). */
    static String meteoIconAt(int code, double minute, double[] sun) {
        String i = meteoIcon(code);
        boolean night = sun == null ? skyPhase((int) (minute / 60)) == 3 : minute < sun[0] || minute > sun[1];
        return i.equals("sole") && night ? "luna" : i;
    }

    /** Tint of a weather icon family: sun yellow, clouds grey, rain blue... */
    static int meteoColor(String icon) {
        switch (icon) {
            case "sole": case "sole_nubi": return 0xFFFFC531;
            case "pioggia": return 0xFF4DA3FF;
            case "temporale": return 0xFFFFD43B;
            case "neve": return 0xFFE6F4FF;
            case "luna": return 0xFFE8E2C4;
            default: return 0xFFB8C4C8;   // nubi, nebbia
        }
    }

    /** Index of the first 15-minute slot with rain (>= 0.1 mm), or -1. */
    static int firstWet(double[] mm) {
        for (int i = 0; i < mm.length; i++) if (mm[i] >= 0.1) return i;
        return -1;
    }

    /** Alert text for rain starting at rainAt (epoch ms, 0 = none); null when nothing to say. */
    static String rainText(long rainAt, long now) {
        if (rainAt == 0) return null;
        long min = (rainAt - now) / 60_000;
        if (min <= 0 && min > -60) return t("Pioggia adesso", "Raining now");
        if (min > 0 && min <= 120) return t("Pioggia tra ", "Rain in ") + Math.max(5, Math.round(min / 5.0) * 5) + " min";
        return null;
    }

    /** CarPlay call card line for an iAP2 call status (SpeedPlay broadcast CarPlayPhoneStatus); null = no call to show. */
    static String callText(int status) {
        switch (status) {
            case 1: case 3: return t("Chiamata in uscita", "Calling");
            case 2: return t("Chiamata in arrivo", "Incoming call");
            case 4: return t("In chiamata", "In call");
            case 5: return t("Chiamata in attesa", "Call on hold");
            default: return null;   // 0 disconnected, 6 disconnecting
        }
    }

    /** "m:ss" or "h:mm:ss" for a media position. */
    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** First video id in a YouTube Data API search answer, or null. */
    static String youtubeId(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"videoId\"\\s*:\\s*\"([\\w-]{11})\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 600 px cover from an iTunes Search API answer, or null. */
    static String itunesArt(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"artworkUrl100\":\"(https?://[^\"]+)\"").matcher(json);
        return m.find() ? m.group(1).replace("100x100bb", "600x600bb") : null;
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
