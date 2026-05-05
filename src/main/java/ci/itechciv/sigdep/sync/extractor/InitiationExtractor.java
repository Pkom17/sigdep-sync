package ci.itechciv.sigdep.sync.extractor;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.dto.TreatmentInitiationDto;
import ci.itechciv.sigdep.sync.extractor.ObsPivot.ObsValue;
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
 * Extracts encounters of type "PEC - Ouverture de dossier" (id=1) from
 * OpenMRS. One DTO per enrollment. Currently only arv_init_date has a
 * confirmed concept UUID; hiv_test_date / hiv_type / entry_point will be
 * promoted from extra_data once their UUIDs are confirmed for SIGDEP-3.
 */
@Component
public class InitiationExtractor implements DataExtractor {

    private static final Logger log = LoggerFactory.getLogger(InitiationExtractor.class);

    // Encounter type UUID for "PEC - Ouverture de dossier"
    private static final String OPENING_FILE_UUID = "8d5b27bc-c2cc-11de-8d13-0010c6dffd0f";

    // "Antiretroviral treatment start date" (CIEL standard)
    private static final String ARV_START_DATE_UUID = "159599AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final java.util.Set<String> MAPPED = java.util.Set.of(ARV_START_DATE_UUID);

    private final JdbcTemplate localDb;
    private final ObsPivot obsPivot;

    public InitiationExtractor(@Qualifier("localJdbcTemplate") JdbcTemplate localDb,
                               ObsPivot obsPivot) {
        this.localDb = localDb;
        this.obsPivot = obsPivot;
    }

    @Override public EntityType getEntityType()  { return EntityType.TREATMENT_INITIATIONS; }
    @Override public String getSourceTable()     { return "encounter"; }
    @Override public String getWatermarkColumn() { return "date_changed"; }
    @Override public boolean isEnabled()         { return true; }

    @Override
    public List<CanonicalRecord> extract(LocalDateTime since, int batchSize) {
        List<EncounterRow> rows = localDb.query(
                """
                SELECT e.encounter_id              AS encounter_id,
                       e.uuid                      AS encounter_uuid,
                       per.uuid                    AS patient_uuid,
                       e.encounter_datetime        AS encounter_datetime,
                       e.voided                    AS voided,
                       COALESCE(e.date_changed, e.date_created) AS effective_changed
                FROM encounter e
                JOIN encounter_type et ON et.encounter_type_id = e.encounter_type
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
                        rs.getTimestamp("effective_changed").toLocalDateTime()),
                OPENING_FILE_UUID,
                Timestamp.valueOf(since),
                batchSize);

        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> ids = rows.stream().map(r -> r.encounterId).toList();
        Map<Long, Map<String, ObsValue>> byEncounter = obsPivot.pivot(ids);

        List<CanonicalRecord> out = new ArrayList<>(rows.size());
        for (EncounterRow r : rows) {
            Map<String, ObsValue> obs = byEncounter.getOrDefault(r.encounterId, Map.of());

            LocalDate arvInit = ObsPivot.asDate(obs.get(ARV_START_DATE_UUID));

            Map<String, Object> extra = new LinkedHashMap<>();
            for (Map.Entry<String, ObsValue> e : obs.entrySet()) {
                if (MAPPED.contains(e.getKey())) continue;
                extra.put(e.getKey(), ObsPivot.asString(e.getValue()));
            }

            TreatmentInitiationDto dto = new TreatmentInitiationDto(
                    r.encounterUuid,
                    r.patientUuid,
                    r.encounterDatetime == null ? null : r.encounterDatetime.toLocalDateTime().toLocalDate(),
                    arvInit,
                    null,                       // hivTestDate (TBD)
                    null,                       // hivType    (TBD)
                    null,                       // entryPoint (TBD)
                    extra.isEmpty() ? null : extra,
                    r.voided);

            out.add(new CanonicalRecord(EntityType.TREATMENT_INITIATIONS, r.encounterUuid, r.changed, dto));
        }
        log.debug("Extracted {} initiation(s) since {}", out.size(), since);
        return out;
    }

    private record EncounterRow(
            long encounterId,
            UUID encounterUuid,
            UUID patientUuid,
            Timestamp encounterDatetime,
            boolean voided,
            LocalDateTime changed
    ) {}
}
