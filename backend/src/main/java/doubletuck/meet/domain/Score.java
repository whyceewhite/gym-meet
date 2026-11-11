package doubletuck.meet.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
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
@Table(name = "score", uniqueConstraints = {
        @UniqueConstraint(name = "uk_score", columnNames = { "participant_id", "session_id", "apparatus_code" })
})
public class Score extends BaseEntity {

    @NotNull(message = "Participant is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "participant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_score_participant"))
    private Participant participant;

    @NotNull(message = "Session is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_score_session"))
    private Session session;

    @NotNull(message = "Apparatus is required")
    @Column(name = "apparatus_code", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    private Apparatus apparatus;

    @Valid
    @NotNull(message = "Routine is required")
    @Embedded
    @AttributeOverride(name = "difficultyInquiry", column = @Column(name = "difficulty_inquiry", nullable = false))
    @AttributeOverride(name = "status", column = @Column(name = "status_code", nullable = false, length = 20))
    private Routine routine;

    @Valid
    @Embedded
    @AttributeOverride(name = "difficulty", column = @Column(name = "second_difficulty"))
    @AttributeOverride(name = "difficultyInquiry", column = @Column(name = "second_difficulty_inquiry"))
    @AttributeOverride(name = "execution", column = @Column(name = "second_execution"))
    @AttributeOverride(name = "bonus", column = @Column(name = "second_bonus"))
    @AttributeOverride(name = "bonusDetail", column = @Column(name = "second_bonus_detail", columnDefinition = "json"))
    @AttributeOverride(name = "penalty", column = @Column(name = "second_penalty"))
    @AttributeOverride(name = "sjPenalty", column = @Column(name = "second_sj_penalty"))
    @AttributeOverride(name = "score", column = @Column(name = "second_score"))
    @AttributeOverride(name = "status", column = @Column(name = "second_status_code", length = 20))
    private Routine secondRoutine;

    @Column(name = "combined_bonus")
    private Double combinedBonus;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "qualification_rank")
    private Integer qualificationRank;

    @Column(name = "qualification_result_code", length = 10)
    @Enumerated(EnumType.STRING)
    private QualificationResult qualificationResult;

    @NotNull(message = "Counts toward team result is required")
    @Column(name = "counts_toward_team_result", nullable = false)
    private Boolean countsTowardTeamResult = true;
}
