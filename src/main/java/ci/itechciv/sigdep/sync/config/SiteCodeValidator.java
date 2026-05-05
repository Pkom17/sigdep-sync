package ci.itechciv.sigdep.sync.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the agent if site-code is missing or set to the default
 * placeholder. An agent without a real site code would push orphan batches
 * the central server has no way to attribute, so failing loudly at boot is
 * the safest option.
 */
@Component
public class SiteCodeValidator {

    private static final Logger log = LoggerFactory.getLogger(SiteCodeValidator.class);

    private final SyncProperties props;

    public SiteCodeValidator(SyncProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void validate() {
        String code = props.siteCode();
        if (code == null || code.isBlank() || "UNKNOWN".equals(code)) {
            throw new IllegalStateException(
                    "sigdep.sync.site-code is not configured. Set the SIGDEP_SITE_CODE "
                            + "environment variable to the DHIS2 'Identifiant Unique "
                            + "Etablissement' for this site (e.g. 02884).");
        }
        log.info("Agent configured for site '{}'", code);
    }
}
