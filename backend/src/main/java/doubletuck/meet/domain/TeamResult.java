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
@Table(name = "team_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_result", columnNames = { "session_id", "noc" })
})
public class TeamResult extends BaseEntity {

    @NotNull(message = "Session is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_result_session"))
    private Session session;

    @NotBlank(message = "NOC is required")
    // 4 characters for FIG's numbered neutral-athlete codes (AIN1, AIN2)
    @Size(min = 3, max = 4, message = "NOC must be a 3- or 4-character code")
    @Column(name = "noc", nullable = false, length = 4)
    private String noc;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "qualification_result_code", length = 10)
    @Enumerated(EnumType.STRING)
    private QualificationResult qualificationResult;
}
