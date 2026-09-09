package doubletuck.meet.dto;

import doubletuck.meet.domain.EventLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EventRequest(
        @Size(max = 20, message = "FIG event number must have 20 or fewer characters")
        String figEventNumber,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must have 200 or fewer characters")
        String title,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must have 100 or fewer characters")
        String city,

        @NotBlank(message = "Country code is required")
        @Size(min = 3, max = 3, message = "Country code must be a 3-letter code")
        String countryCode,

        @Size(max = 150, message = "Venue must have 150 or fewer characters")
        String venue,

        @NotNull(message = "Level is required")
        EventLevel level) {
}
