package it.dfsk.helper;

/**
 * Reverse-gear state for the "Più bassa" option: when to hold / abandon the ducking audio focus.
 * Pure Java (no Android) so it can be unit-tested on the JVM. Times are elapsedRealtime ms.
 */
public final class ReverseState {
    public static final int PAUSE = 0, ATTENUATE = 1, LOWER = 2;
    public static final long STOP_DEBOUNCE_MS = 500;      // reverse and AVM each send START/STOP; absorbs R-D-R flaps
    public static final long TIMEOUT_MS = 3L * 60 * 1000; // never hold the focus longer than this, even if STOP is lost
    public static final ReverseState INSTANCE = new ReverseState();

    private boolean reversing, ducked;
    private long duckAt = -1, stopAt = -1;
    public volatile String lastEvent = "nessuno";

    /** Real BACKCAR_START seen. @return true if the ducking focus must be requested now. */
    public synchronized boolean onStart(long now, int mode, boolean inCall) {
        reversing = true;
        stopAt = -1;
        if (ducked || mode != LOWER || inCall) return false;
        ducked = true;
        duckAt = now;
        return true;
    }

    public synchronized void onStop(long now) {
        reversing = false;
        stopAt = now;
    }

    /** @return true if the focus must be abandoned now: STOP (debounced), call, mode changed, timeout, clock jump. */
    public synchronized boolean shouldRelease(long now, int mode, boolean inCall) {
        if (!ducked) return false;
        boolean stopped = !reversing && stopAt >= 0 && now - stopAt >= STOP_DEBOUNCE_MS;
        if (inCall || mode != LOWER || now < duckAt || now - duckAt >= TIMEOUT_MS || stopped) {
            ducked = false;
            return true;
        }
        return false;
    }

    public synchronized boolean isReversing() { return reversing; }
    public synchronized boolean isDucked() { return ducked; }
}
