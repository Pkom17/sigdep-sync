package ci.itechciv.sigdep.sync.pusher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration
public class NoopTokenProvider {

    /**
     * Default token provider when no auth scheme is configured (or
     * sigdep.sync.auth.mode=none). Returns null so CentralApiClient skips
     * the Authorization header entirely.
     */
    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    @ConditionalOnProperty(name = "sigdep.sync.auth.mode", havingValue = "none", matchIfMissing = true)
    public TokenProvider noopTokenProvider() {
        return () -> null;
    }
}
