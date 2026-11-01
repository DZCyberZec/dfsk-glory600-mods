package it.cyberzec.home;

public class LogicTest {
    public static void main(String[] a) {
        java.util.Locale.setDefault(java.util.Locale.ITALY);
        assert Logic.meteoText(0).equals("Sereno");
        assert Logic.meteoText(63).equals("Pioggia");
        assert Logic.meteoText(96).equals("Temporale");
        assert Logic.pages(0, 18) == 1 && Logic.pages(18, 18) == 1 && Logic.pages(19, 18) == 2;
        assert Logic.snapPage(0, 400, 1800, 0, 3) == 1;
        assert Logic.snapPage(0, 100, 1800, 0, 3) == 0;
        assert Logic.snapPage(1, 1700, 1800, 0, 3) == 1;
        assert Logic.snapPage(2, 3500, 1800, 5000, 3) == 2;
        assert Logic.snapPage(1, 1800, 1800, -5000, 3) == 0;
        assert Logic.meteoIcon(0).equals("sole") && Logic.meteoIcon(3).equals("nubi");
        assert Logic.meteoIcon(61).equals("pioggia") && Logic.meteoIcon(73).equals("neve") && Logic.meteoIcon(95).equals("temporale");
        assert Logic.internetShort(true, true, true).equals("Hotspot") && Logic.internetShort(false, false, false).equals("Offline");
        assert Logic.climaLevelText(28f).equals("4") && Logic.climaLevelText(28.5f).equals("4,5");
        assert Logic.climaLevelText(32f).equals("8") && Logic.climaLevelText(17f).equals("-8");
        assert Logic.climaLevelText(24f).equals("-1") && Logic.climaLevelText(24.5f).equals("-0,5") && Logic.climaLevelText(23.5f).equals("-1,5");
        assert Logic.modeName(3).equals("Piedi + vetro");
        java.util.Locale.setDefault(java.util.Locale.US);
        assert Logic.meteoText(0).equals("Clear") && Logic.meteoText(85).equals("Snow showers") && Logic.meteoText(81).equals("Showers");
        assert Logic.climaLevelText(28.5f).equals("4.5") && Logic.modeName(0).equals("Face");
        System.out.println("LogicTest OK");
    }
}
