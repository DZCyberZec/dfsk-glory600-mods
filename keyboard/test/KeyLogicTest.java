package it.cyberzec.keyboard;

public class KeyLogicTest {
    public static void main(String[] a) {
        java.util.Locale.setDefault(java.util.Locale.ITALY);
        KeyLogic k = new KeyLogic();
        assert k.label("a").equals("a");
        k.tapShift(1000);
        assert k.shift == KeyLogic.ONCE && k.label("a").equals("A") && k.label("à").equals("À") && k.label("ß").equals("ß");
        assert k.label("1").equals("1") && k.label("?123").equals("?123");
        k.autoCaps(false);                       // a letter was typed
        assert k.shift == KeyLogic.OFF;
        k.tapShift(5000); k.tapShift(5200);      // double tap
        assert k.shift == KeyLogic.LOCK;
        k.autoCaps(false);
        assert k.shift == KeyLogic.LOCK;         // lock survives typing
        k.tapShift(9000);
        assert k.shift == KeyLogic.OFF;
        k.tapShift(10000); k.tapShift(11000);    // slow second tap = off again
        assert k.shift == KeyLogic.OFF;
        k.autoCaps(true);
        assert k.shift == KeyLogic.ONCE;

        assert KeyLogic.startPage(0x2) == KeyLogic.SYM1 && KeyLogic.startPage(0x3) == KeyLogic.SYM1;
        assert KeyLogic.startPage(0x1 | 0x80) == KeyLogic.LETTERS;   // text password
        assert KeyLogic.startPage(0x12) == KeyLogic.SYM1;            // number password

        assert KeyLogic.editorAction(3, 1) == 3 && KeyLogic.actionLabel(3).equals("Cerca");
        assert KeyLogic.editorAction(2, 0x11) == 2 && KeyLogic.actionLabel(2).equals("Vai");
        assert KeyLogic.editorAction(6, 1 | 0x20000) == 0;           // multiline: Enter
        assert KeyLogic.editorAction(5 | 0x40000000, 1) == 0;        // no-enter-action flag
        assert KeyLogic.editorAction(1, 1) == 0 && KeyLogic.actionLabel(0).equals("Invio");
        assert KeyLogic.actionLabel(5).equals("Avanti") && KeyLogic.actionLabel(6).equals("Fatto");

        for (int p = 0; p < 4; p++) {
            String[][] r = KeyLogic.rows(p);
            assert r.length == 4;
            float w = 0;
            for (String key : r[0]) w += KeyLogic.weight(key);
            assert w == 10f : "row width page " + p;
            w = 0;
            for (String key : r[3]) w += KeyLogic.weight(key);
            assert w == 10f;
        }
        assert KeyLogic.target("?123") == KeyLogic.SYM1 && KeyLogic.target("=\\<") == KeyLogic.SYM2 && KeyLogic.target("a") == -1;
        String all = java.util.Arrays.deepToString(KeyLogic.rows(KeyLogic.SYM1)) + java.util.Arrays.deepToString(KeyLogic.rows(KeyLogic.SYM2));
        for (char c : "./:@_-\\{}[]<>$~^|=".toCharArray()) assert all.indexOf(c) >= 0 : "missing " + c;
        System.out.println("KeyLogicTest OK");
    }
}
