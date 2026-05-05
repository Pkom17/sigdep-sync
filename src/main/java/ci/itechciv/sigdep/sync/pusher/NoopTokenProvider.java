package ci.itechciv.sigdep.sync.pusher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default TokenProvider used when no auth scheme is configured (or when
 * sigdep.sync.auth.mode=none, the default). Returns null so CentralApiClient
 * skips the Authorization header entirely. Real Keycloak provider will plug
 * in by registering its own TokenProvider bean.
 */
@Configuration
public class NoopTokenProvider {

    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    @ConditionalOnProperty(name = "sigdep.sync.auth.mode", havingValue = "none", matchIfMissing = true)
    public TokenProvider tokenProvider() {
        return () -> null;
    }
}
