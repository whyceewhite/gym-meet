package doubletuck.meet.dto;

import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record EventResponse(
        Long id,
        String figEventNumber,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String city,
        String countryCode,
        String venue,
        Set<Discipline> disciplines,
        EventLevel level,
        Instant creationTimestamp,
        Instant lastUpdateTimestamp) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getFigEventNumber(),
                event.getTitle(),
                event.getStartDate(),
                event.getEndDate(),
                event.getCity(),
                event.getCountryCode(),
                event.getVenue(),
                event.getDisciplines(),
                event.getLevel(),
                event.getCreationTimestamp(),
                event.getLastUpdateTimestamp());
    }
}
