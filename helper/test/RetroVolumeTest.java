import it.dfsk.helper.RetroVolume;

public class RetroVolumeTest {
    public static void main(String[] a) {
        assert RetroVolume.onStart(100, 13) == -1;
        assert RetroVolume.onStart(75, 13) == 9;
        assert RetroVolume.onStart(50, 13) == 6;
        assert RetroVolume.onStart(25, 13) == 3;
        assert RetroVolume.onStart(25, 2) == 1;   // still audible
        assert RetroVolume.onStart(50, 0) == 0;
        assert RetroVolume.onStart(0, 13) == -1;  // bad value: leave the volume alone
        assert RetroVolume.onStart(RetroVolume.AUTO, 13) == -1;
        assert RetroVolume.policyFor(RetroVolume.AUTO) == 20 && RetroVolume.policyFor(100) == 100 && RetroVolume.policyFor(25) == 100;
        System.out.println("RetroVolumeTest OK");
    }
}
