package ci.itechciv.sigdep.sync.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    @Scheduled(fixedDelayString = "${sigdep.sync.interval-ms:900000}")
    public void runCycle() {
        log.info("Sync cycle skeleton — wire extractors, outbox flush, push here");
    }
}
