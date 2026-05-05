package ci.itechciv.sigdep.sync.buffer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Applies db/sqlite/buffer-schema.sql to the local SQLite buffer at startup.
 * Idempotent — the DDL uses CREATE TABLE IF NOT EXISTS.
 */
@Component
public class BufferSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BufferSchemaInitializer.class);

    private final JdbcTemplate buffer;

    public BufferSchemaInitializer(@Qualifier("bufferJdbcTemplate") JdbcTemplate buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run(ApplicationArguments args) {
        String ddl;
        try {
            ddl = StreamUtils.copyToString(
                    new ClassPathResource("db/sqlite/buffer-schema.sql").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read buffer-schema.sql", e);
        }
        for (String stmt : ddl.split(";")) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            buffer.execute(trimmed);
        }
        log.info("SQLite buffer schema ensured");
    }
}
