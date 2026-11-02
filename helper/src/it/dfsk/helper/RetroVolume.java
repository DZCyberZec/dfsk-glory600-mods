package it.dfsk.helper;

/** "Volume in retromarcia": pure Java so the JVM self-check can test it. Levels are STREAM_MUSIC indexes. */
public final class RetroVolume {
    /** Choices offered in the app and web panel, as % of the normal music level. AUTO = the car's own lowering
     *  (audio policy backcar_music=20, untouched); the others switch that off and apply exactly this %. */
    public static final int AUTO = 0;
    public static final int[] PERCENTS = {AUTO, 100, 75, 50, 25};
    public static final int CAR_DEFAULT = 20; // /system/etc/audio_policy.xml backcar_music

    /** Value for AudioSystem.setAudPolicyStrategy(BACKCAR, MUSIC, x): the car's lowering only in AUTO. */
    public static int policyFor(int percent) { return percent == AUTO ? CAR_DEFAULT : 100; }

    private RetroVolume() {}

    /** Level to set when reverse starts from the normal level; -1 = leave it. Never 0 while music was audible. */
    public static int onStart(int percent, int normal) {
        if (percent >= 100 || percent <= 0) return -1; // AUTO or 100: leave the level alone
        return Math.max(normal > 0 ? 1 : 0, normal * percent / 100);
    }
}
