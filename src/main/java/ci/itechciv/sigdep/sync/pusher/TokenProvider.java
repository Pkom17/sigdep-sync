package ci.itechciv.sigdep.sync.pusher;

/**
 * Supplies the bearer token to attach to outbound sync requests.
 * Real implementations will hit Keycloak (client_credentials grant) and cache
 * the token until close to expiry. The Noop impl is used for local dev where
 * the hub runs with profile=dev and accepts unauthenticated requests.
 */
public interface TokenProvider {
    /** Returns a token string, or null if no Authorization header should be sent. */
    String getToken();
}
