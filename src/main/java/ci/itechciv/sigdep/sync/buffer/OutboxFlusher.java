package ci.itechciv.sigdep.sync.buffer;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.SyncBatchRequest;
import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import ci.itechciv.sigdep.sync.buffer.OutboxRepository.OutboxEntry;
import ci.itechciv.sigdep.sync.config.SyncProperties;
import ci.itechciv.sigdep.sync.pusher.CentralApiClient;
import ci.itechciv.sigdep.sync.state.SyncStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drains the SQLite outbox in pages and pushes each page as a SyncBatchRequest
 * to the central API. After a successful ACK we mark rows SENT and advance
 * the watermark for the entity to the highest watermark seen in the batch
 * (so a crash mid-cycle never re-extracts already-acked rows).
 */
@Component
public class OutboxFlusher {

    private static final Logger log = LoggerFactory.getLogger(OutboxFlusher.class);

    private final OutboxRepository outbox;
    private final CentralApiClient api;
    private final ObjectMapper mapper;
    private final SyncStateRepository syncState;
    private final SyncProperties props;

    public OutboxFlusher(OutboxRepository outbox,
                         CentralApiClient api,
                         ObjectMapper mapper,
                         SyncStateRepository syncState,
                         SyncProperties props) {
        this.outbox = outbox;
        this.api = api;
        this.mapper = mapper;
        this.syncState = syncState;
        this.props = props;
    }

    /**
     * Drains all pending rows for the given entity, pushing them in pages of
     * {@link SyncProperties#batchSize()}. Returns the total number of rows
     * successfully ACK'd.
     */
    public FlushResult flush(EntityType entityType) {
        int total = 0;
        int batches = 0;
        while (true) {
            List<OutboxEntry> page = outbox.findPending(entityType, props.batchSize());
            if (page.isEmpty()) break;

            try {
                pushPage(entityType, page);
                total += page.size();
                batches++;
            } catch (IOException e) {
                List<Long> ids = page.stream().map(OutboxEntry::id).toList();
                outbox.markFailed(ids, e.getMessage());
                log.warn("Push failed for {} rows ({}), they remain PENDING and will retry: {}",
                        page.size(), entityType, e.getMessage());
                return new FlushResult(total, batches, true);
            }
        }
        return new FlushResult(total, batches, false);
    }

    private void pushPage(EntityType entityType, List<OutboxEntry> page) throws IOException {
        Class<?> dtoClass = PayloadTypes.classFor(entityType);
        List<Object> records = new ArrayList<>(page.size());
        LocalDateTime maxWatermark = page.get(0).watermark();
        for (OutboxEntry e : page) {
            records.add(mapper.readValue(e.payloadJson(), dtoClass));
            if (e.watermark().isAfter(maxWatermark)) {
                maxWatermark = e.watermark();
            }
        }

        SyncBatchRequest<Object> batch = new SyncBatchRequest<>(
                props.siteCode(),
                UUID.randomUUID(),
                entityType,
                records);

        SyncBatchResponse resp = api.push(entityType, batch);
        log.info("ACK {} entityType={} accepted={} rejected={}",
                resp.batchId(), entityType, resp.accepted(), resp.rejected());

        List<Long> ids = page.stream().map(OutboxEntry::id).toList();
        outbox.markSent(ids);
        syncState.updateWatermark(entityType, maxWatermark, resp.accepted(), "OK");
    }

    public record FlushResult(int rowsAcked, int batches, boolean stoppedEarly) {}
}
