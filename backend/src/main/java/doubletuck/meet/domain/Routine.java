package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * One judged performance of a routine on an apparatus. {@link Score} embeds one
 * for every apparatus and a second only for a vault entry with two attempts.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Embeddable
public class Routine {

    @Column(name = "difficulty")
    private Double difficulty;

    @Column(name = "execution")
    private Double execution;

    @Column(name = "bonus")
    private Double bonus;

    @Column(name = "bonus_detail", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BonusDetail> bonusDetail;

    @Column(name = "penalty")
    private Double penalty;

    @Column(name = "sj_penalty")
    private Double sjPenalty;

    @Column(name = "score")
    private Double score;

    @NotNull(message = "Status is required")
    @Column(name = "status_code", length = 20)
    @Enumerated(EnumType.STRING)
    private ScoreStatus status;

    public Routine(ScoreStatus status) {
        this.status = status;
    }

    public void addBonusDetail(BonusDetail detail) {
        if (detail == null)
            return;

        if (bonusDetail == null) {
            bonusDetail = new ArrayList<>();
        }
        bonusDetail.add(detail);
    }
}
