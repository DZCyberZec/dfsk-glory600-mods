package it.dfsk.helper;

/** CarPlay call tracking from SpeedPlay "status" broadcasts. Pure Java (no Android) so it can be unit-tested on the JVM. */
public final class CallState {
    public static final long TIMEOUT_MS = 2L * 60 * 60 * 1000; // safety reset if PHONE_CALL_OFF is ever lost
    public static final CallState INSTANCE = new CallState();

    private long since = -1; // elapsedRealtime of PHONE_CALL_ON, -1 = no call

    /** @return true if the in-call state changed. */
    public synchronized boolean onStatus(String status, long now) {
        boolean was = isActive(now);
        if ("PHONE_CALL_ON".equals(status)) since = now;
        else if ("PHONE_CALL_OFF".equals(status) || "DISCONNECT".equals(status) || "EXIT".equals(status)) since = -1;
        return was != isActive(now);
    }

    public synchronized boolean isActive(long now) {
        if (since >= 0 && (now - since > TIMEOUT_MS || now < since)) since = -1;
        return since >= 0;
    }

    public synchronized void reset() { since = -1; }
}
