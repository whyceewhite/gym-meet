package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
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

/**
 * An athlete's published standing on one apparatus across a round split into
 * several parts (e.g. US Championships' two-day combined apparatus results).
 * It belongs to the whole-round {@link Session} (part null), while the judged
 * routines behind it are {@link Score}s on each part's session.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "apparatus_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_apparatus_result", columnNames = { "participant_id", "session_id", "apparatus_code" })
}, indexes = {
        @Index(name = "ix_apparatus_result_session", columnList = "session_id")
})
public class ApparatusResult extends BaseEntity {

    @NotNull(message = "Participant is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "participant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_apparatus_result_participant"))
    private Participant participant;

    @NotNull(message = "Session is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_apparatus_result_session"))
    private Session session;

    @NotNull(message = "Apparatus is required")
    @Column(name = "apparatus_code", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    private Apparatus apparatus;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "rank")
    private Integer rank;
}
