package it.dfsk.helper;

import java.util.Locale;

/** Italian text when the unit is set to Italian, English otherwise. Pure Java: used by the JVM-tested classes too. */
final class Lang {
    private Lang() {}

    static String t(String it, String en) {
        return "it".equals(Locale.getDefault().getLanguage()) ? it : en;
    }
}
