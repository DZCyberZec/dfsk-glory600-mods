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
        assert Logic.placeLabel("Fano, Pesaro e Urbino, Marche, 61032, Italia").equals("Fano, Pesaro e Urbino, Marche");
        assert Logic.placeLabel("Roma").equals("Roma");
        assert Logic.greeting(8).equals("Buongiorno") && Logic.greeting(15).equals("Buon pomeriggio") && Logic.greeting(22).equals("Buonasera") && Logic.greeting(2).equals("Buonasera");
        assert Logic.skyPhase(7) == 0 && Logic.skyPhase(12) == 1 && Logic.skyPhase(18) == 2 && Logic.skyPhase(23) == 3 && Logic.skyPhase(3) == 3;
        double[] sun = Logic.sunTimes(306, 41.9, 12.5, 1);   // Rome 2 Nov: 06:44 / 17:03 CET
        assert Math.abs(sun[0] - (6 * 60 + 44)) < 10 && Math.abs(sun[1] - (17 * 60 + 3)) < 10;
        sun = Logic.sunTimes(172, 41.9, 12.5, 2);   // 21 Jun: 05:35 / 20:48 CEST
        assert Math.abs(sun[0] - (5 * 60 + 35)) < 10 && Math.abs(sun[1] - (20 * 60 + 48)) < 10;
        assert Logic.sunTimes(172, 80, 0, 0) == null && Logic.sunTimes(355, 80, 0, 0) == null;   // polar day, polar night
        sun = new double[]{410, 1012};
        assert Logic.sunPhase(400, sun) == 0 && Logic.sunPhase(529, sun) == 0 && Logic.sunPhase(531, sun) == 1;
        assert Logic.sunPhase(960, sun) == 2 && Logic.sunPhase(1041, sun) == 2 && Logic.sunPhase(1043, sun) == 3 && Logic.sunPhase(300, sun) == 3;
        assert Logic.sunPhase(7 * 60 + 5, null) == 0 && Logic.sunPhase(23 * 60, null) == 3;
        assert Logic.meteoColor("pioggia") == 0xFF4DA3FF && Logic.meteoColor("nubi") == 0xFFB8C4C8;
        assert Logic.firstWet(new double[]{0, 0.05, 0.3, 1}) == 2 && Logic.firstWet(new double[]{0, 0}) == -1;
        assert Logic.rainText(0, 1000) == null;
        assert Logic.rainText(1_000_000 + 20 * 60_000, 1_000_000).equals("Pioggia tra 20 min");
        assert Logic.rainText(1_000_000 + 2 * 60_000, 1_000_000).equals("Pioggia tra 5 min");
        assert Logic.rainText(1_000_000 - 10 * 60_000, 1_000_000).equals("Pioggia adesso");
        assert Logic.rainText(1_000_000 + 200 * 60_000, 1_000_000) == null;
        assert Logic.clock(102_000).equals("1:42") && Logic.clock(3_725_000).equals("1:02:05");
        assert "dQw4w9WgXcQ".equals(Logic.youtubeId("{\"items\": [{\"id\": {\"kind\": \"youtube#video\", \"videoId\": \"dQw4w9WgXcQ\"}}, {\"id\": {\"videoId\": \"aaaaaaaaaaa\"}}]}"));
        assert Logic.youtubeId("<html>no results</html>") == null;
        assert "https://is1.mzstatic.com/a/600x600bb.jpg".equals(Logic.itunesArt("{\"results\":[{\"artworkUrl100\":\"https://is1.mzstatic.com/a/100x100bb.jpg\"}]}"));
        assert Logic.itunesArt("{\"resultCount\":0,\"results\":[]}") == null;
        assert Logic.skyKind("sole_nubi") == 0 && Logic.skyKind("nebbia") == 1 && Logic.skyKind("temporale") == 2;
        assert Logic.meteoIconAt(0, 23 * 60, null).equals("luna") && Logic.meteoIconAt(0, 12 * 60, null).equals("sole") && Logic.meteoIconAt(61, 23 * 60, null).equals("pioggia");
        double[] nov = {403, 1018};   // sunrise 06:43, sunset 16:58
        assert Logic.meteoIconAt(0, 17 * 60 + 30, nov).equals("luna") && Logic.meteoIconAt(0, 16 * 60, nov).equals("sole");
        assert Logic.validName("") && Logic.validName("Marco") && Logic.validName("Anna-Maria D'Ascoli".substring(0, 19));
        assert !Logic.validName(null) && !Logic.validName("123456789012345678901") && !Logic.validName("a\nb");
        double[] c = Logic.cityValue("Roma|41.9028|12.4964");
        assert c != null && c[0] == 41.9028 && c[1] == 12.4964 && Logic.cityValue("Polo|-90|180") != null;
        assert Logic.cityValue(null) == null && Logic.cityValue("|43|13") == null && Logic.cityValue("A;B|43|13") == null;
        assert Logic.cityValue("Roma|91|12") == null && Logic.cityValue("Roma|41|-181") == null && Logic.cityValue("Roma|NaN|12") == null;
        assert Logic.cityValue("Roma|41|12|x") == null && Logic.cityValue("Roma|41") == null && Logic.cityValue("Roma|a|12") == null;
        assert Logic.cityValue("12345678901234567890123456789012345678901|1|1") == null;
        assert Logic.callText(2).equals("Chiamata in arrivo") && Logic.callText(4).equals("In chiamata") && Logic.callText(1).equals("Chiamata in uscita");
        assert Logic.callText(0) == null && Logic.callText(6) == null && Logic.callText(-1) == null && Logic.callText(5) != null;
        java.util.Locale.setDefault(java.util.Locale.US);
        assert Logic.meteoText(0).equals("Clear") && Logic.meteoText(85).equals("Snow showers") && Logic.meteoText(81).equals("Showers");
        assert Logic.climaLevelText(28.5f).equals("4.5") && Logic.modeName(0).equals("Face");
        System.out.println("LogicTest OK");
    }
}
