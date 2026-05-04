package ci.itechciv.sigdep.sync.extractor;

import ci.itechciv.sigdep.contracts.EntityType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CanonicalRecord(
        EntityType entityType,
        UUID sourceUuid,
        LocalDateTime watermark,
        Object payload
) {}
