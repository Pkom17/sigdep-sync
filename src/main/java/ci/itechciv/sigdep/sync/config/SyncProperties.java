package ci.itechciv.sigdep.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sigdep.sync")
public record SyncProperties(
        String siteCode,
        String centralApiUrl,
        int batchSize,
        int syncIntervalMinutes,
        Keycloak keycloak,
        Backfill backfill
) {
    public record Keycloak(String issuerUrl, String clientId, String clientSecret) {}

    public record Backfill(boolean enabled, int maxRequestsPerMinute, String cron) {}
}
