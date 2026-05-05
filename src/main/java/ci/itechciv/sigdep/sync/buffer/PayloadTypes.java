package ci.itechciv.sigdep.sync.buffer;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.dto.DispensationDto;
import ci.itechciv.sigdep.contracts.dto.PatientDto;
import ci.itechciv.sigdep.contracts.dto.VisitDto;
import java.util.Map;

/**
 * Maps each EntityType to the DTO class used to (de)serialize its payload.
 * Add new entries here as new extractors / writers come online.
 */
public final class PayloadTypes {

    private static final Map<EntityType, Class<?>> TYPES = Map.of(
            EntityType.PATIENTS,      PatientDto.class,
            EntityType.VISITS,        VisitDto.class,
            EntityType.DISPENSATIONS, DispensationDto.class
    );

    public static Class<?> classFor(EntityType type) {
        Class<?> c = TYPES.get(type);
        if (c == null) {
            throw new IllegalStateException("No DTO class registered for " + type);
        }
        return c;
    }

    private PayloadTypes() {}
}
