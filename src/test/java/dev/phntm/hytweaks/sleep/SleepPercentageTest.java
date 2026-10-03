package dev.phntm.hytweaks.sleep;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepPercentageTest {
    @Test
    void skipsOnceEnoughButNotAllAreAsleep() {
        assertTrue(SleepPercentage.enough(1, 2, 2, 0.5));
        assertTrue(SleepPercentage.enough(2, 3, 3, 0.5));
        assertFalse(SleepPercentage.enough(1, 3, 3, 0.5));
        assertFalse(SleepPercentage.enough(0, 4, 4, 0.0));
    }

    @Test
    void leavesUnanimousSleepToVanilla() {
        assertFalse(SleepPercentage.enough(1, 1, 1, 0.5));
        assertFalse(SleepPercentage.enough(4, 4, 4, 0.5));
    }

    @Test
    void ignoresAfkPlayers() {
        // 2 of 4 active players asleep, plus 6 AFK: vanilla would wait for all 10.
        assertTrue(SleepPercentage.enough(2, 4, 10, 0.5));
        assertFalse(SleepPercentage.enough(1, 4, 10, 0.5));
        // Everyone active is asleep but an AFK player isn't, so vanilla never would.
        assertTrue(SleepPercentage.enough(3, 3, 4, 1.0));
    }

    @Test
    void wakesAtTheNextWakeUpHour() {
        Instant evening = Instant.parse("2026-01-01T22:00:00Z");
        Instant earlyMorning = Instant.parse("2026-01-02T02:00:00Z");
        assertEquals(Instant.parse("2026-01-02T05:30:00Z"), SleepPercentage.nextWakeUp(evening, 5.5f));
        assertEquals(Instant.parse("2026-01-02T05:30:00Z"), SleepPercentage.nextWakeUp(earlyMorning, 5.5f));
    }
}
