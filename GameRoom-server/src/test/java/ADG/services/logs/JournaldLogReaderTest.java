package ADG.services.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JournaldLogReaderTest {

    // ── unit-name allowlist ──────────────────────────────────────────────────

    @Test
    void acceptsRealUnitNames() {
        assertTrue(JournaldLogReader.isSafeUnit("gameroom.service"));
        assertTrue(JournaldLogReader.isSafeUnit("keezen.service"));
        assertTrue(JournaldLogReader.isSafeUnit("my-app@1.service"));
    }

    @Test
    void rejectsShellMetacharsAndFlagsInUnit() {
        assertFalse(JournaldLogReader.isSafeUnit("keezen.service; rm -rf /"));
        assertFalse(JournaldLogReader.isSafeUnit("$(whoami)"));
        assertFalse(JournaldLogReader.isSafeUnit("a`id`"));
        assertFalse(JournaldLogReader.isSafeUnit("a b"));          // space
        assertFalse(JournaldLogReader.isSafeUnit("--output=cat"));  // flag-shaped
        assertFalse(JournaldLogReader.isSafeUnit("-u"));            // leading dash
        assertFalse(JournaldLogReader.isSafeUnit(""));
        assertFalse(JournaldLogReader.isSafeUnit(null));
    }

    // ── --since allowlist ────────────────────────────────────────────────────

    @Test
    void acceptsMappedSinceArguments() {
        assertTrue(JournaldLogReader.isSafeSince("15 min ago"));
        assertTrue(JournaldLogReader.isSafeSince("1 hour ago"));
        assertTrue(JournaldLogReader.isSafeSince("7 days ago"));
    }

    @Test
    void rejectsMetacharsAndFlagsInSince() {
        assertFalse(JournaldLogReader.isSafeSince("1 hour ago; rm -rf /"));
        assertFalse(JournaldLogReader.isSafeSince("$(reboot)"));
        assertFalse(JournaldLogReader.isSafeSince("--output=cat"));
        assertFalse(JournaldLogReader.isSafeSince(null));
    }
}