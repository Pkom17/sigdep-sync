package ci.itechciv.sigdep.sync.buffer;

import ci.itechciv.sigdep.contracts.EntityType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public OutboxRepository(@Qualifier("bufferJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(EntityType entityType, UUID sourceUuid, LocalDateTime watermark, String payloadJson) {
        jdbc.update(
                """
                INSERT INTO outbox (entity_type, source_uuid, watermark, payload_json, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """,
                entityType.name(),
                sourceUuid.toString(),
                java.sql.Timestamp.valueOf(watermark),
                payloadJson);
    }

    public List<OutboxEntry> findPending(EntityType entityType, int limit) {
        return jdbc.query(
                """
                SELECT id, entity_type, source_uuid, watermark, payload_json, attempts
                FROM outbox
                WHERE status = 'PENDING' AND entity_type = ?
                ORDER BY id
                LIMIT ?
                """,
                (rs, i) -> new OutboxEntry(
                        rs.getLong("id"),
                        EntityType.valueOf(rs.getString("entity_type")),
                        UUID.fromString(rs.getString("source_uuid")),
                        rs.getTimestamp("watermark").toLocalDateTime(),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")),
                entityType.name(),
                limit);
    }

    public void markSent(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.update(
                "UPDATE outbox SET status='SENT', sent_at=datetime('now') WHERE id IN (" + placeholders + ")",
                ids.toArray());
    }

    public void markFailed(List<Long> ids, String error) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = error;
        for (int i = 0; i < ids.size(); i++) {
            params[i + 1] = ids.get(i);
        }
        jdbc.update(
                "UPDATE outbox SET attempts = attempts + 1, last_error = ?, status='PENDING' "
                        + "WHERE id IN (" + placeholders + ")",
                params);
    }

    public record OutboxEntry(
            long id,
            EntityType entityType,
            UUID sourceUuid,
            LocalDateTime watermark,
            String payloadJson,
            int attempts
    ) {}
}
