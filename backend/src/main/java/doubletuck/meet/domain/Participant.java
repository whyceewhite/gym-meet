package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "participant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_participant", columnNames = { "event_id", "athlete_id" })
})
public class Participant extends BaseEntity {

    @NotNull(message = "Event is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_event"))
    private Event event;

    @NotNull(message = "Athlete is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "athlete_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_athlete"))
    private Athlete athlete;

    @Size(max = 10, message = "Bib number must have 10 or fewer characters")
    @Column(name = "bib_number", length = 10)
    private String bibNumber;

    @NotBlank(message = "NOC is required")
    // 4 characters for FIG's numbered neutral-athlete codes (AIN1, AIN2)
    @Size(min = 3, max = 4, message = "NOC must be a 3- or 4-character code")
    @Column(name = "noc", nullable = false, length = 4)
    private String noc;
}
