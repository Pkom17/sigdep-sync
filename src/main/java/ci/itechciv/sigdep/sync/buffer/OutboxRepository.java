package ci.itechciv.sigdep.sync.buffer;

import ci.itechciv.sigdep.contracts.EntityType;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public OutboxRepository(@Qualifier("bufferJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(EntityType entityType, String payloadJson) {
        jdbc.update(
                "INSERT INTO outbox (entity_type, payload_json, status) VALUES (?, ?, 'PENDING')",
                entityType.name(),
                payloadJson
        );
    }

    public List<OutboxEntry> findPending(EntityType entityType, int limit) {
        return jdbc.query(
                """
                SELECT id, entity_type, payload_json, attempts
                FROM outbox
                WHERE status = 'PENDING' AND entity_type = ?
                ORDER BY id
                LIMIT ?
                """,
                (rs, i) -> new OutboxEntry(
                        rs.getLong("id"),
                        EntityType.valueOf(rs.getString("entity_type")),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")
                ),
                entityType.name(),
                limit
        );
    }

    public void markSent(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.update(
                "UPDATE outbox SET status='SENT', sent_at=datetime('now') WHERE id IN (" + placeholders + ")",
                ids.toArray()
        );
    }

    public void markFailed(long id, String error) {
        jdbc.update(
                "UPDATE outbox SET attempts = attempts + 1, last_error = ?, status='FAILED' WHERE id = ?",
                error,
                id
        );
    }

    public record OutboxEntry(long id, EntityType entityType, String payloadJson, int attempts) {}
}
