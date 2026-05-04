package ci.itechciv.sigdep.sync.extractor;

import ci.itechciv.sigdep.contracts.EntityType;
import java.time.LocalDateTime;
import java.util.List;

public interface DataExtractor {

    EntityType getEntityType();

    String getSourceTable();

    String getWatermarkColumn();

    boolean isEnabled();

    List<CanonicalRecord> extract(LocalDateTime since, int batchSize);
}
