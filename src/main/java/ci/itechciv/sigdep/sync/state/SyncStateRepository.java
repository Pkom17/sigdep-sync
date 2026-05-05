package ci.itechciv.sigdep.sync.state;

import ci.itechciv.sigdep.contracts.EntityType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncStateRepository {

    private final JdbcTemplate jdbc;

    public SyncStateRepository(@Qualifier("bufferJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LocalDateTime> getWatermark(EntityType entityType) {
        return jdbc.query(
                "SELECT last_watermark FROM sync_state WHERE entity_type = ?",
                rs -> rs.next()
                        ? Optional.ofNullable(rs.getTimestamp("last_watermark")).map(java.sql.Timestamp::toLocalDateTime)
                        : Optional.empty(),
                entityType.name()
        );
    }

    public void updateWatermark(EntityType entityType, LocalDateTime watermark, int recordsSent, String status) {
        jdbc.update(
                """
                INSERT INTO sync_state (entity_type, last_watermark, last_run_at, last_status, records_sent)
                VALUES (?, ?, datetime('now'), ?, ?)
                ON CONFLICT(entity_type) DO UPDATE SET
                  last_watermark = excluded.last_watermark,
                  last_run_at    = excluded.last_run_at,
                  last_status    = excluded.last_status,
                  records_sent   = sync_state.records_sent + excluded.records_sent
                """,
                entityType.name(),
                java.sql.Timestamp.valueOf(watermark),
                status,
                recordsSent
        );
    }
}
