package ci.itechciv.sigdep.sync.extractor;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.dto.TreatmentInitiationDto;
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
 * Extracts encounters of type "PEC - Ouverture de dossier" (id=1) from OpenMRS.
 * One DTO per enrolment. Mapped concepts come from the SIGDEP-3 "Fiche initiale
 * adulte" HTML form (see infra/scripts/scan_htmlforms.py).
 *
 * The DTO also carries four profile fields (marital_status, birth_place,
 * education_level, religion) that the form fills in but ultimately belong to
 * core.patients — the hub propagates them on upsert (see InitiationWriter).
 */
@Component
public class InitiationExtractor implements DataExtractor {

    private static final Logger log = LoggerFactory.getLogger(InitiationExtractor.class);

    // Encounter type UUID for "PEC - Ouverture de dossier"
    private static final String OPENING_FILE_UUID = "8d5b27bc-c2cc-11de-8d13-0010c6dffd0f";

    // --- Mapped concepts ------------------------------------------------------

    // Dates
    private static final String ARV_START_DATE_UUID  = "159599AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // DATE DE DEBUT ARV
    private static final String HIV_TEST_DATE_UUID   = "160554AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Date diagnostique VIH

    // HIV identity
    private static final String HIV_TYPE_UUID        = "163623AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Resultat Type VIH
    private static final String ENTRY_POINT_UUID     = "164523AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Point d'entrée

    // Clinical snapshot at enrolment
    private static final String WHO_STAGE_INIT_UUID  = "164487AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Stade clinique OMS
    private static final String CDC_STAGE_INIT_UUID  = "1209AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Stadification CDC
    private static final String ARV_REGIMEN_INIT_UUID = "162240AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Regime ARV
    private static final String WEIGHT_INIT_UUID     = "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";   // POIDS (KG)
    private static final String CD4_INIT_UUID        = "5497AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";   // Numération CD4
    private static final String CD4_PCT_INIT_UUID    = "730AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";    // CD4%
    private static final String KARNOFSKY_UUID       = "5283AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";   // Score de Karnofsky

    // Patient pathway
    private static final String REFERRED_UUID        = "1648AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // PATIENT REFERE
    private static final String REFERRED_ORIGIN_UUID = "164562AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Référé origine
    private static final String TREATMENT_MOTIVE_UUID = "162225AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Motif mise sous TARV

    // Partner / counselling
    private static final String PARTNER_HIV_STATUS_UUID = "1436AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Statut sero partenaire

    // Antecedents
    private static final String TB_HISTORY_UUID      = "1389AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Antécédent Tuberculose
    private static final String ARV_HISTORY_UUID     = "164540AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Antécédent ARV
    private static final String TRANSFUSION_HISTORY_UUID = "1871AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Antécédent transfusion

    // PTME history
    private static final String PTME_HISTORY_UUID    = "163450AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Antécédent PTME
    private static final String PTME_REGIMEN_HIST_UUID = "1400AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Régime antécédent PTME
    private static final String PTME_HISTORY_DATE_UUID = "164588AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Date PTME

    // --- Profile concepts (propagated to core.patients) ---------------------
    private static final String MARITAL_STATUS_UUID  = "1054AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Etat civil
    private static final String BIRTH_PLACE_UUID     = "164444AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Lieu de naissance
    private static final String EDUCATION_LEVEL_UUID = "1712AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Niveau d'éducation
    private static final String RELIGION_UUID        = "162894AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // Religion

    private static final java.util.Set<String> MAPPED = java.util.Set.of(
            ARV_START_DATE_UUID, HIV_TEST_DATE_UUID,
            HIV_TYPE_UUID, ENTRY_POINT_UUID,
            WHO_STAGE_INIT_UUID, CDC_STAGE_INIT_UUID, ARV_REGIMEN_INIT_UUID,
            WEIGHT_INIT_UUID, CD4_INIT_UUID, CD4_PCT_INIT_UUID, KARNOFSKY_UUID,
            REFERRED_UUID, REFERRED_ORIGIN_UUID, TREATMENT_MOTIVE_UUID,
            PARTNER_HIV_STATUS_UUID,
            TB_HISTORY_UUID, ARV_HISTORY_UUID, TRANSFUSION_HISTORY_UUID,
            PTME_HISTORY_UUID, PTME_REGIMEN_HIST_UUID, PTME_HISTORY_DATE_UUID,
            MARITAL_STATUS_UUID, BIRTH_PLACE_UUID, EDUCATION_LEVEL_UUID, RELIGION_UUID);

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

            // Dates
            LocalDate arvInit = ObsPivot.asDate(obs.get(ARV_START_DATE_UUID));
            LocalDate hivTestDate = ObsPivot.asDate(obs.get(HIV_TEST_DATE_UUID));

            // HIV identity
            String hivType = ObsPivot.asString(obs.get(HIV_TYPE_UUID));
            String entryPoint = ObsPivot.asString(obs.get(ENTRY_POINT_UUID));

            // Clinical snapshot
            String whoStage = ObsPivot.asString(obs.get(WHO_STAGE_INIT_UUID));
            String cdcStage = ObsPivot.asString(obs.get(CDC_STAGE_INIT_UUID));
            String arvRegimen = ObsPivot.asString(obs.get(ARV_REGIMEN_INIT_UUID));
            BigDecimal weight = ObsPivot.asDecimal(obs.get(WEIGHT_INIT_UUID));
            Integer cd4 = ObsPivot.asInt(obs.get(CD4_INIT_UUID));
            BigDecimal cd4Pct = ObsPivot.asDecimal(obs.get(CD4_PCT_INIT_UUID));
            Short karnofsky = ObsPivot.asShort(obs.get(KARNOFSKY_UUID));

            // Pathway
            String referred = ObsPivot.asString(obs.get(REFERRED_UUID));
            String referredOrigin = ObsPivot.asString(obs.get(REFERRED_ORIGIN_UUID));
            String treatmentMotive = ObsPivot.asString(obs.get(TREATMENT_MOTIVE_UUID));

            // Partner
            String partnerStatus = ObsPivot.asString(obs.get(PARTNER_HIV_STATUS_UUID));

            // Antecedents
            String tbHistory = ObsPivot.asString(obs.get(TB_HISTORY_UUID));
            String arvHistory = ObsPivot.asString(obs.get(ARV_HISTORY_UUID));
            String transfusionHistory = ObsPivot.asString(obs.get(TRANSFUSION_HISTORY_UUID));

            // PTME
            String ptmeHistory = ObsPivot.asString(obs.get(PTME_HISTORY_UUID));
            String ptmeRegimenHistory = ObsPivot.asString(obs.get(PTME_REGIMEN_HIST_UUID));
            LocalDate ptmeHistoryDate = ObsPivot.asDate(obs.get(PTME_HISTORY_DATE_UUID));

            // Profile fields (will be propagated to core.patients on the hub)
            String maritalStatus = ObsPivot.asString(obs.get(MARITAL_STATUS_UUID));
            String birthPlace = ObsPivot.asString(obs.get(BIRTH_PLACE_UUID));
            String educationLevel = ObsPivot.asString(obs.get(EDUCATION_LEVEL_UUID));
            String religion = ObsPivot.asString(obs.get(RELIGION_UUID));

            // Catch-all
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
                    hivTestDate,
                    hivType,
                    entryPoint,
                    whoStage,
                    cdcStage,
                    arvRegimen,
                    weight,
                    cd4,
                    cd4Pct,
                    karnofsky,
                    referred,
                    referredOrigin,
                    treatmentMotive,
                    partnerStatus,
                    tbHistory,
                    arvHistory,
                    transfusionHistory,
                    ptmeHistory,
                    ptmeRegimenHistory,
                    ptmeHistoryDate,
                    maritalStatus,
                    birthPlace,
                    educationLevel,
                    religion,
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
