package doubletuck.meet.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "event")
public class Event extends BaseEntity {

    @Size(max = 20, message = "FIG event number must have 20 or fewer characters")
    @Column(name = "fig_event_number", length = 20)
    private String figEventNumber;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must have 200 or fewer characters")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must have 100 or fewer characters")
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @NotBlank(message = "Country code is required")
    @Size(min = 3, max = 3, message = "Country code must be a 3-letter code")
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Size(max = 150, message = "Venue must have 150 or fewer characters")
    @Column(name = "venue", length = 150)
    private String venue;

    @NotEmpty(message = "At least one discipline is required")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_discipline", joinColumns = @JoinColumn(name = "event_id",
            foreignKey = @ForeignKey(name = "fk_event_discipline_event")))
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline_code", nullable = false, length = 3)
    private Set<Discipline> disciplines = new HashSet<>();

    @NotNull(message = "Level is required")
    @Column(name = "level_code", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EventLevel level;
}
