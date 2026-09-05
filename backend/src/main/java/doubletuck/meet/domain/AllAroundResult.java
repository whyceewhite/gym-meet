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


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "all_around_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_all_around_result", columnNames = { "participant_id", "session_id" })
})
public class AllAroundResult extends BaseEntity {

    @NotNull(message = "Participant is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "participant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_all_around_result_participant"))
    private Participant participant;

    @NotNull(message = "Session is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_all_around_result_session"))
    private Session session;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "qualification_result_code", length = 10)
    @Enumerated(EnumType.STRING)
    private QualificationResult qualificationResult;
}
