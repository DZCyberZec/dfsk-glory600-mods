import it.dfsk.helper.CallState;

/** Run with: java -ea (see build.sh). */
public class CallStateTest {
    public static void main(String[] a) {
        boolean ea = false; assert ea = true; if (!ea) throw new AssertionError("run with java -ea");
        CallState s = new CallState();
        assert !s.isActive(0);
        assert !s.onStatus("CONNECTED", 0) && !s.isActive(0);
        assert s.onStatus("PHONE_CALL_ON", 1000) && s.isActive(2000);
        assert !s.onStatus("PHONE_CALL_ON", 3000) && s.isActive(3000);          // repeated ON: still active
        assert s.onStatus("PHONE_CALL_OFF", 4000) && !s.isActive(4000);
        assert !s.onStatus("PHONE_CALL_OFF", 5000);                               // OFF without ON: no change
        s.onStatus("PHONE_CALL_ON", 10_000);
        assert s.onStatus("DISCONNECT", 11_000) && !s.isActive(11_000);           // CarPlay lost mid-call
        s.onStatus("PHONE_CALL_ON", 20_000);
        assert s.isActive(20_000 + CallState.TIMEOUT_MS);
        assert !s.isActive(20_001 + CallState.TIMEOUT_MS);                        // 2 h safety reset
        assert !s.isActive(20_000);                                               // stays reset
        s.onStatus("PHONE_CALL_ON", 50_000);
        assert !s.isActive(40_000);                                               // clock went backwards -> reset
        s.onStatus("PHONE_CALL_ON", 1);
        assert !s.onStatus(null, 2) && s.isActive(2);                             // missing status ignored
        s.reset();
        assert !s.isActive(3);
        System.out.println("CallStateTest OK");
    }
}
