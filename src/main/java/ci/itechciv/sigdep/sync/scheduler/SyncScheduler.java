package ci.itechciv.sigdep.sync.scheduler;

import ci.itechciv.sigdep.sync.buffer.OutboxEnqueuer;
import ci.itechciv.sigdep.sync.buffer.OutboxFlusher;
import ci.itechciv.sigdep.sync.config.SyncProperties;
import ci.itechciv.sigdep.sync.extractor.CanonicalRecord;
import ci.itechciv.sigdep.sync.extractor.DataExtractor;
import ci.itechciv.sigdep.sync.state.SyncStateRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final List<DataExtractor> extractors;
    private final OutboxEnqueuer enqueuer;
    private final OutboxFlusher flusher;
    private final SyncStateRepository syncState;
    private final SyncProperties props;

    public SyncScheduler(List<DataExtractor> extractors,
                         OutboxEnqueuer enqueuer,
                         OutboxFlusher flusher,
                         SyncStateRepository syncState,
                         SyncProperties props) {
        this.extractors = extractors;
        this.enqueuer = enqueuer;
        this.flusher = flusher;
        this.syncState = syncState;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${sigdep.sync.interval-ms:900000}")
    public void runCycle() {
        log.info("Sync cycle started. {} extractor(s) registered.", extractors.size());
        for (DataExtractor x : extractors) {
            if (!x.isEnabled()) {
                log.debug("Extractor {} disabled, skipping", x.getEntityType());
                continue;
            }
            try {
                runOne(x);
            } catch (RuntimeException e) {
                log.error("Extractor {} failed, continuing with next: {}",
                        x.getEntityType(), e.getMessage(), e);
            }
        }
        log.info("Sync cycle completed.");
    }

    private void runOne(DataExtractor x) {
        // 1. Watermark
        LocalDateTime since = syncState.getWatermark(x.getEntityType())
                .orElse(props.watermarkInitial());

        // 2. Extract a page from the source DB
        List<CanonicalRecord> records = x.extract(since, props.batchSize());
        if (records.isEmpty()) {
            log.debug("Nothing new for {} since {}", x.getEntityType(), since);
            return;
        }

        // 3. Enqueue into outbox
        int enqueued = enqueuer.enqueue(records);
        log.info("Enqueued {} {} record(s) (since {})", enqueued, x.getEntityType(), since);

        // 4. Drain the outbox for this entity. Flusher advances the watermark
        //    on every successful batch ACK, so a crash mid-cycle never wastes
        //    work already accepted by the central server.
        var result = flusher.flush(x.getEntityType());
        log.info("Flushed {} batch(es), {} rows ACK'd for {}{}",
                result.batches(), result.rowsAcked(), x.getEntityType(),
                result.stoppedEarly() ? " (stopped early after a push failure)" : "");
    }
}
