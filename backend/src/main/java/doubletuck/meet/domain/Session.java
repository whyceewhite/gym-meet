package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "session", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session", columnNames = { "event_id", "round_code", "discipline_code", "age_category_code" })
})
public class Session extends BaseEntity {

    @NotNull(message = "Event is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_event"))
    private Event event;

    @NotNull(message = "Date is required")
    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @NotNull(message = "Round is required")
    @Column(name = "round_code", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Round round;

    @NotNull(message = "Discipline is required")
    @Column(name = "discipline_code", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Discipline discipline;

    @NotNull(message = "Age category is required")
    @Column(name = "age_category_code", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private AgeCategory ageCategory;
}
