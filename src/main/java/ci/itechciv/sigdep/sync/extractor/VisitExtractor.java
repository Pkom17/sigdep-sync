package ci.itechciv.sigdep.sync.extractor;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.dto.VisitDto;
import ci.itechciv.sigdep.sync.extractor.ObsPivot.ObsValue;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Extracts encounters of type "PEC - Suivi patient" (id=2) from OpenMRS.
 * Each encounter becomes one VisitDto. The four officially mapped concepts
 * (weight, height, WHO stage, return visit date) land in dedicated columns;
 * everything else is dumped into extra_data, keyed by concept UUID.
 */
@Component
public class VisitExtractor implements DataExtractor {

    private static final Logger log = LoggerFactory.getLogger(VisitExtractor.class);

    // Encounter type UUID for "PEC - Suivi patient"
    private static final String FOLLOWUP_VISIT_UUID = "8d5b2be0-c2cc-11de-8d13-0010c6dffd0f";

    // Concept UUIDs (CIEL/OpenMRS standard)
    private static final String WEIGHT_KG_UUID         = "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String HEIGHT_CM_UUID         = "5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String WHO_STAGE_UUID         = "5356AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String NEXT_VISIT_DATE_UUID   = "5096AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final java.util.Set<String> MAPPED = java.util.Set.of(
            WEIGHT_KG_UUID, HEIGHT_CM_UUID, WHO_STAGE_UUID, NEXT_VISIT_DATE_UUID);

    private final JdbcTemplate localDb;
    private final ObsPivot obsPivot;

    public VisitExtractor(@Qualifier("localJdbcTemplate") JdbcTemplate localDb,
                          ObsPivot obsPivot) {
        this.localDb = localDb;
        this.obsPivot = obsPivot;
    }

    @Override public EntityType getEntityType()  { return EntityType.VISITS; }
    @Override public String getSourceTable()     { return "encounter"; }
    @Override public String getWatermarkColumn() { return "date_changed"; }
    @Override public boolean isEnabled()         { return true; }

    @Override
    public List<CanonicalRecord> extract(LocalDateTime since, int batchSize) {
        // 1. Pull a page of follow-up encounters.
        List<EncounterRow> rows = localDb.query(
                """
                SELECT e.encounter_id              AS encounter_id,
                       e.uuid                      AS encounter_uuid,
                       per.uuid                    AS patient_uuid,
                       e.encounter_datetime        AS encounter_datetime,
                       e.voided                    AS voided,
                       et.name                     AS encounter_type_name,
                       COALESCE(e.date_changed, e.date_created) AS effective_changed
                FROM encounter e
                JOIN encounter_type et ON et.encounter_type_id = e.encounter_type
                JOIN patient pat       ON pat.patient_id = e.patient_id
                JOIN person  per       ON per.person_id  = e.patient_id
                WHERE et.uuid = ?
                  AND COALESCE(e.date_changed, e.date_created) > ?
                ORDER BY effective_changed
                LIMIT ?
                """,
                (rs, i) -> new EncounterRow(
                        rs.getLong("encounter_id"),
                        UUID.fromString(rs.getString("encounter_uuid")),
                        UUID.fromString(rs.getString("patient_uuid")),
                        rs.getTimestamp("encounter_datetime"),
                        rs.getBoolean("voided"),
                        rs.getString("encounter_type_name"),
                        rs.getTimestamp("effective_changed").toLocalDateTime()),
                FOLLOWUP_VISIT_UUID,
                Timestamp.valueOf(since),
                batchSize);

        if (rows.isEmpty()) {
            return List.of();
        }

        // 2. Pivot all obs for this batch in one round-trip.
        List<Long> encounterIds = rows.stream().map(r -> r.encounterId).toList();
        Map<Long, Map<String, ObsValue>> byEncounter = obsPivot.pivot(encounterIds);

        // 3. Build DTOs.
        List<CanonicalRecord> out = new ArrayList<>(rows.size());
        for (EncounterRow r : rows) {
            Map<String, ObsValue> obs = byEncounter.getOrDefault(r.encounterId, Map.of());

            BigDecimal weight = ObsPivot.asDecimal(obs.get(WEIGHT_KG_UUID));
            BigDecimal height = ObsPivot.asDecimal(obs.get(HEIGHT_CM_UUID));
            Short whoStage = ObsPivot.asShort(obs.get(WHO_STAGE_UUID));
            LocalDate nextVisit = ObsPivot.asDate(obs.get(NEXT_VISIT_DATE_UUID));

            // Everything that wasn't mapped goes to extra_data.
            Map<String, Object> extra = new LinkedHashMap<>();
            for (Map.Entry<String, ObsValue> e : obs.entrySet()) {
                if (MAPPED.contains(e.getKey())) continue;
                extra.put(e.getKey(), ObsPivot.asString(e.getValue()));
            }

            VisitDto dto = new VisitDto(
                    r.encounterUuid,
                    r.patientUuid,
                    r.encounterTypeName,    // sourceForm
                    r.encounterDatetime == null ? null : r.encounterDatetime.toLocalDateTime().toLocalDate(),
                    nextVisit,
                    null,                   // tbScreeningResult
                    null,                   // tbDiagnosed
                    null,                   // tbTreatmentStatus
                    null,                   // tbTreatmentStartDate
                    whoStage,
                    null,                   // cdcStage
                    null,                   // ctxPrescribed
                    null,                   // ctxStartDate
                    null,                   // ivsaSuccessConfirmationDate
                    null,                   // isPregnant
                    null,                   // isBreastfeeding
                    weight,
                    height,
                    extra.isEmpty() ? null : extra,
                    r.voided);

            out.add(new CanonicalRecord(EntityType.VISITS, r.encounterUuid, r.changed, dto));
        }
        log.debug("Extracted {} visit(s) since {}", out.size(), since);
        return out;
    }

    private record EncounterRow(
            long encounterId,
            UUID encounterUuid,
            UUID patientUuid,
            Timestamp encounterDatetime,
            boolean voided,
            String encounterTypeName,
            LocalDateTime changed
    ) {}
}
