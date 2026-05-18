package ci.itechciv.sigdep.sync.config;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sigdep.sync")
public record SyncProperties(
        String siteCode,
        String centralApiUrl,
        int batchSize,
        int syncIntervalMinutes,
        LocalDateTime watermarkInitial,
        Map<String, String> identifierMapping,
        Keycloak keycloak,
        Backfill backfill,
        // How many times we re-push a record the hub rejected before parking
        // it in DEAD_LETTER. Tuned so transient FK ordering issues
        // (UNKNOWN_PATIENT during initial backfill) resolve themselves while
        // genuinely bad data stops looping. Default 10.
        int maxRejectAttempts
) {
    public record Keycloak(String issuerUrl, String clientId, String clientSecret) {}

    public record Backfill(boolean enabled, int maxRequestsPerMinute, String cron) {}
}
