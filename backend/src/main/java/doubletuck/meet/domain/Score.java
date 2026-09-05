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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "difficulty")
    private Double difficulty;

    @Column(name = "execution")
    private Double execution;

    @Column(name = "penalty")
    private Double penalty;

    @Column(name = "bonus")
    private Double bonus;

    @Column(name = "bonus_detail", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BonusDetail> bonusDetail;

    @Column(name = "score")
    private Double score;

    @NotNull(message = "Status is required")
    @Column(name = "status_code", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ScoreStatus status;

    @Column(name = "second_difficulty")
    private Double secondDifficulty;

    @Column(name = "second_execution")
    private Double secondExecution;

    @Column(name = "second_penalty")
    private Double secondPenalty;

    @Column(name = "second_bonus")
    private Double secondBonus;

    @Column(name = "second_bonus_detail", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BonusDetail> secondBonusDetail;

    @Column(name = "second_score")
    private Double secondScore;

    @Column(name = "second_status_code", length = 20)
    @Enumerated(EnumType.STRING)
    private ScoreStatus secondStatus;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "qualification_result_code", length = 10)
    @Enumerated(EnumType.STRING)
    private QualificationResult qualificationResult;

    @NotNull(message = "Counts toward team result is required")
    @Column(name = "counts_toward_team_result", nullable = false)
    private Boolean countsTowardTeamResult = true;

    public void addBonusDetail(BonusDetail detail) {
        if (detail == null)
            return;

        if (bonusDetail == null) {
            bonusDetail = new ArrayList<>();
        }
        bonusDetail.add(detail);
    }

    public void addSecondBonusDetail(BonusDetail detail) {
        if (detail == null)
            return;

        if (secondBonusDetail == null) {
            secondBonusDetail = new ArrayList<>();
        }
        secondBonusDetail.add(detail);
    }
}
