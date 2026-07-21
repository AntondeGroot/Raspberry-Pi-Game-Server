package ADG.services.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NginxAccessLogReaderTest {

    // ── scanner probes on a 4xx are hidden ───────────────────────────────────

    @Test
    void hidesAwsCredentialsProbe() {
        // The exact case that prompted this: GET /.aws/credentials -> 404.
        assertTrue(NginxAccessLogReader.isScannerNoise("/.aws/credentials", 404));
    }

    @Test
    void hidesCommonSecretAndCmsProbes() {
        assertTrue(NginxAccessLogReader.isScannerNoise("/.env", 404));
        assertTrue(NginxAccessLogReader.isScannerNoise("/.git/config", 404));
        assertTrue(NginxAccessLogReader.isScannerNoise("/wp-login.php", 404));
        assertTrue(NginxAccessLogReader.isScannerNoise("/some/thing.php", 403));
        assertTrue(NginxAccessLogReader.isScannerNoise("/PHPMyAdmin/", 404)); // case-insensitive
    }

    // ── never hide a real error ──────────────────────────────────────────────

    @Test
    void keepsFiveXxEvenOnProbePath() {
        // A 5xx on a probe path is a genuine server error — must stay visible.
        assertFalse(NginxAccessLogReader.isScannerNoise("/.env", 500));
    }

    @Test
    void keepsLegitAppFourXx() {
        // Real app 4xx (a bad game move, an unknown room) must never be hidden.
        assertFalse(NginxAccessLogReader.isScannerNoise("/qwixx/move", 400));
        assertFalse(NginxAccessLogReader.isScannerNoise("/keezen/", 404));
        assertFalse(NginxAccessLogReader.isScannerNoise("/rooms/abc/players/x", 404));
    }
}