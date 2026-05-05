package ci.itechciv.sigdep.sync.extractor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads OpenMRS 'obs' rows for a given set of encounters and pivots them into
 * a per-encounter map of concept UUID -> typed value.
 *
 * Each obs row carries a value in one of several columns (numeric, datetime,
 * text, coded). For value_coded we resolve the concept_name (locale_preferred,
 * not voided) so the central server stores a human-readable string rather
 * than a meaningless integer that's not stable across instances.
 */
@Component
public class ObsPivot {

    private final JdbcTemplate localDb;

    public ObsPivot(@Qualifier("localJdbcTemplate") JdbcTemplate localDb) {
        this.localDb = localDb;
    }

    /**
     * For the given encounters, return: encounterId -> (conceptUuid -> ObsValue).
     * Empty map if encounters is empty.
     */
    public Map<Long, Map<String, ObsValue>> pivot(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", encounterIds.stream().map(i -> "?").toList());
        Object[] params = encounterIds.toArray();

        Map<Long, Map<String, ObsValue>> result = new HashMap<>();
        localDb.query(
                """
                SELECT o.encounter_id        AS encounter_id,
                       c.uuid                AS concept_uuid,
                       o.value_numeric       AS value_numeric,
                       o.value_datetime      AS value_datetime,
                       o.value_text          AS value_text,
                       o.value_coded         AS value_coded,
                       cn.name               AS coded_name
                FROM obs o
                JOIN concept c ON c.concept_id = o.concept_id
                LEFT JOIN concept_name cn
                       ON cn.concept_id = o.value_coded
                      AND cn.locale_preferred = 1
                      AND cn.voided = 0
                WHERE o.voided = 0 AND o.encounter_id IN (%s)
                """.formatted(placeholders),
                rs -> {
                    long eid = rs.getLong("encounter_id");
                    String conceptUuid = rs.getString("concept_uuid");

                    // Read value_coded first so its wasNull() is captured before
                    // any subsequent getXxx() overwrites it.
                    long coded = rs.getLong("value_coded");
                    boolean codedNull = rs.wasNull();

                    BigDecimal num = rs.getBigDecimal("value_numeric");
                    Timestamp dt = rs.getTimestamp("value_datetime");
                    String text = rs.getString("value_text");
                    String codedName = rs.getString("coded_name");

                    ObsValue v;
                    if (num != null) {
                        v = ObsValue.numeric(num);
                    } else if (dt != null) {
                        v = ObsValue.datetime(dt);
                    } else if (!codedNull) {
                        v = ObsValue.coded(coded, codedName);
                    } else if (text != null) {
                        v = ObsValue.text(text);
                    } else {
                        return;
                    }
                    result.computeIfAbsent(eid, k -> new HashMap<>()).put(conceptUuid, v);
                },
                params);
        return result;
    }

    /** Convenience extractor wrappers. */
    public static java.time.LocalDate asDate(ObsValue v) {
        if (v == null) return null;
        if (v.datetime != null) return v.datetime.toLocalDateTime().toLocalDate();
        if (v.text != null) {
            try { return java.time.LocalDate.parse(v.text); } catch (RuntimeException ignored) { return null; }
        }
        return null;
    }

    public static BigDecimal asDecimal(ObsValue v) {
        return v == null ? null : v.numeric;
    }

    public static Short asShort(ObsValue v) {
        if (v == null) return null;
        if (v.numeric != null) {
            try { return v.numeric.shortValueExact(); }
            catch (ArithmeticException e) { return null; }
        }
        // Some sites store vital signs (BP, etc.) in value_text rather than
        // value_numeric. Try to parse if it looks like a number.
        if (v.text != null && !v.text.isBlank()) {
            try { return Short.parseShort(v.text.trim()); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public static Integer asInt(ObsValue v) {
        if (v == null) return null;
        if (v.numeric != null) {
            try { return v.numeric.intValueExact(); }
            catch (ArithmeticException e) { return null; }
        }
        if (v.text != null && !v.text.isBlank()) {
            try { return Integer.parseInt(v.text.trim()); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public static String asString(ObsValue v) {
        if (v == null) return null;
        if (v.codedName != null) return v.codedName;
        if (v.text != null) return v.text;
        if (v.numeric != null) return v.numeric.toPlainString();
        if (v.datetime != null) return v.datetime.toString();
        return null;
    }

    /** A typed obs value. Exactly one of the fields is non-null. */
    public static final class ObsValue {
        public final BigDecimal numeric;
        public final Timestamp datetime;
        public final String text;
        public final Long codedId;
        public final String codedName;

        private ObsValue(BigDecimal n, Timestamp d, String t, Long ci, String cn) {
            this.numeric = n; this.datetime = d; this.text = t; this.codedId = ci; this.codedName = cn;
        }
        static ObsValue numeric(BigDecimal n)              { return new ObsValue(n, null, null, null, null); }
        static ObsValue datetime(Timestamp d)              { return new ObsValue(null, d, null, null, null); }
        static ObsValue text(String s)                     { return new ObsValue(null, null, s, null, null); }
        static ObsValue coded(long id, String name)        { return new ObsValue(null, null, null, id, name); }
    }
}
