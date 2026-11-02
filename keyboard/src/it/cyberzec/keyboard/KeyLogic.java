package it.cyberzec.keyboard;

/** Pure logic, no Android imports: checked on the JVM by test/KeyLogicTest.java. */
final class KeyLogic {
    static final int LETTERS = 0, SYM1 = 1, SYM2 = 2, ACCENTS = 3;
    static final int OFF = 0, ONCE = 1, LOCK = 2;
    static final String SHIFT = "⇧", DEL = "⌫", SPACE = " ", ACTION = "\n";
    static final long DOUBLE_TAP_MS = 400;

    int shift = OFF;
    private long lastShiftTap = Long.MIN_VALUE / 2;

    /** Italian text when the unit is set to Italian, English otherwise. */
    static String t(String it, String en) {
        return "it".equals(java.util.Locale.getDefault().getLanguage()) ? it : en;
    }

    /** Tap = next letter uppercase (or off again), quick second tap = caps lock, tap on lock = off. */
    void tapShift(long now) {
        shift = shift == ONCE && now - lastShiftTap < DOUBLE_TAP_MS ? LOCK : shift == OFF ? ONCE : OFF;
        lastShiftTap = now;
    }

    /** After a key or on a new field: caps lock stays, otherwise follow the editor's auto-caps. */
    void autoCaps(boolean capsWanted) {
        if (shift != LOCK) shift = capsWanted ? ONCE : OFF;
    }

    String label(String key) {
        if (shift == OFF || key.length() != 1 || !Character.isLetter(key.charAt(0))) return key;
        return String.valueOf(Character.toUpperCase(key.charAt(0)));   // ß stays ß
    }

    /** Four rows; multi-char keys are page switches, SHIFT/DEL/SPACE/ACTION are the specials. */
    static String[][] rows(int page) {
        String[] bottom = {page == LETTERS ? "?123" : "ABC", page == ACCENTS ? "?123" : "àè", ",", SPACE, ".", ACTION};
        switch (page) {
            case SYM1: return new String[][]{
                    split("1234567890"), split("@#€&_-+()/"),
                    {"=\\<", "*", "\"", "'", ":", ";", "!", "?", "%", DEL}, bottom};
            case SYM2: return new String[][]{
                    split("[]{}<>$£^~"), split("\\|`°§¿¡«»"),
                    {"?123", "¥", "×", "÷", "±", "•", "…", "=", DEL}, bottom};
            case ACCENTS: return new String[][]{
                    split("àèéìòùçñü'"), split("áíóúöäßêâ"),
                    {SHIFT, "ô", "î", "û", "ë", "ï", "ã", "õ", DEL}, bottom};
            default: return new String[][]{
                    split("qwertyuiop"), split("asdfghjkl"),
                    {SHIFT, "z", "x", "c", "v", "b", "n", "m", DEL}, bottom};
        }
    }

    private static String[] split(String s) {
        String[] r = new String[s.length()];
        for (int i = 0; i < r.length; i++) r[i] = String.valueOf(s.charAt(i));
        return r;
    }

    /** Page a key switches to, or -1 when it types/acts. */
    static int target(String key) {
        if (key.equals("?123")) return SYM1;
        if (key.equals("ABC")) return LETTERS;
        if (key.equals("àè")) return ACCENTS;
        if (key.equals("=\\<")) return SYM2;
        return -1;
    }

    static float weight(String key) {
        return key.equals(SPACE) ? 3.5f : key.length() > 1 || key.equals(SHIFT) || key.equals(DEL) || key.equals(ACTION) ? 1.5f : 1f;
    }

    /** Numbers, phone, date/time open on the number page. */
    static int startPage(int inputType) {
        int cls = inputType & 0xF;   // EditorInfo TYPE_MASK_CLASS
        return cls == 2 || cls == 3 || cls == 4 ? SYM1 : LETTERS;
    }

    /** EditorInfo action id (2..7) to perform, or 0 = send a plain Enter key. */
    static int editorAction(int imeOptions, int inputType) {
        boolean multiLine = (inputType & 0xF) == 1 && (inputType & 0x20000) != 0;   // TYPE_TEXT_FLAG_MULTI_LINE
        if (multiLine || (imeOptions & 0x40000000) != 0) return 0;                    // IME_FLAG_NO_ENTER_ACTION
        int a = imeOptions & 0xFF;                                                    // IME_MASK_ACTION
        return a >= 2 && a <= 7 ? a : 0;
    }

    static String actionLabel(int action) {
        switch (action) {
            case 2: return t("Vai", "Go");
            case 3: return t("Cerca", "Search");
            case 4: return t("Invia", "Send");
            case 5: return t("Avanti", "Next");
            case 6: return t("Fatto", "Done");
            case 7: return t("Indietro", "Back");
            default: return t("Invio", "Enter");
        }
    }
}
