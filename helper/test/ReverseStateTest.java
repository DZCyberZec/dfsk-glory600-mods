import it.dfsk.helper.ReverseState;

import static it.dfsk.helper.ReverseState.*;

/** Run with: java -ea (see build.sh). */
public class ReverseStateTest {
    public static void main(String[] a) {
        boolean ea = false; assert ea = true; if (!ea) throw new AssertionError("run with java -ea");
        long D = STOP_DEBOUNCE_MS;

        // Pausa / Attenuata: never request focus
        ReverseState s = new ReverseState();
        assert !s.onStart(0, PAUSE, false) && !s.isDucked() && s.isReversing();
        s.onStop(10);
        assert !s.isReversing() && !s.shouldRelease(10 + D, PAUSE, false);
        assert !s.onStart(100, ATTENUATE, false) && !s.isDucked();

        // Più bassa: duck on START, release only after the debounced STOP
        s = new ReverseState();
        assert s.onStart(1000, LOWER, false) && s.isDucked();
        assert !s.onStart(1100, LOWER, false);                      // AVM's second START: no second request
        assert !s.shouldRelease(5000, LOWER, false);                // still reversing
        s.onStop(6000);
        assert !s.shouldRelease(6000 + D - 1, LOWER, false);        // debounce
        assert s.shouldRelease(6000 + D, LOWER, false) && !s.isDucked();
        assert !s.shouldRelease(6000 + D + 1, LOWER, false);        // released once

        // R-D-R flap: STOP then START within the debounce keeps the duck, no re-request
        s = new ReverseState();
        assert s.onStart(0, LOWER, false);
        s.onStop(100);
        assert !s.onStart(300, LOWER, false) && s.isDucked();
        assert !s.shouldRelease(100 + D, LOWER, false);             // STOP superseded by START

        // never during a call; a call starting mid-reverse releases at once
        s = new ReverseState();
        assert !s.onStart(0, LOWER, true) && !s.isDucked();
        s = new ReverseState();
        assert s.onStart(0, LOWER, false);
        assert s.shouldRelease(10, LOWER, true);

        // mode changed away from "Più bassa" while ducked
        s = new ReverseState();
        assert s.onStart(0, LOWER, false);
        assert s.shouldRelease(10, ATTENUATE, false);

        // STOP lost: 3-minute safety timeout; clock going backwards also releases
        s = new ReverseState();
        assert s.onStart(1000, LOWER, false);
        assert !s.shouldRelease(1000 + TIMEOUT_MS - 1, LOWER, false);
        assert s.shouldRelease(1000 + TIMEOUT_MS, LOWER, false);
        s = new ReverseState();
        assert s.onStart(5000, LOWER, false);
        assert s.shouldRelease(4000, LOWER, false);

        // a new START after timeout ducks again (fresh 3 minutes)
        s = new ReverseState();
        assert s.onStart(0, LOWER, false);
        assert s.shouldRelease(TIMEOUT_MS, LOWER, false);
        assert s.onStart(TIMEOUT_MS + 1, LOWER, false);
        System.out.println("ReverseStateTest OK");
    }
}
