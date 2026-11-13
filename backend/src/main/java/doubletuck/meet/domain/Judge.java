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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
/**
 * A judge is not necessarily unique within a session by {@code apparatus}
 * and {@code function} alone -- e.g. a {@code QUAL} session's Superior Jury
 * typically has several {@code MEMBER} judges sharing that same function
 * with no apparatus. The unique key adds the judge's name to distinguish
 * those rather than assuming a business rule (one judge per apparatus/
 * function slot) that doesn't hold for every function.
 */
@Entity
@Table(name = "judge", uniqueConstraints = {
        // V1 declares this NULLS NOT DISTINCT, which JPA can't express, so that null apparatus
        // values count as equal and a duplicate is rejected
        @UniqueConstraint(name = "uk_judge", columnNames = { "session_id", "apparatus_code", "function_code", "surname", "given_name" })
})
public class Judge extends BaseEntity {

    @NotNull(message = "Session is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_judge_session"))
    private Session session;

    @Column(name = "apparatus_code", length = 5)
    @Enumerated(EnumType.STRING)
    private Apparatus apparatus;

    @NotNull(message = "Function is required")
    @Column(name = "function_code", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private JudgeFunction function;

    @NotBlank(message = "Given name is required")
    @Size(max = 60, message = "Given name must have 60 or fewer characters")
    @Column(name = "given_name", nullable = false, length = 60)
    private String givenName;

    @NotBlank(message = "Surname is required")
    @Size(max = 60, message = "Surname must have 60 or fewer characters")
    @Column(name = "surname", nullable = false, length = 60)
    private String surname;

    // Null for officials not credited to a federation, e.g. a Superior Jury member
    @Size(max = 10, message = "NOC code must have 10 or fewer characters")
    @Column(name = "noc_code", length = 10)
    private String nocCode;

    // Names are stored in one form whatever capitalisation the source printed; see PersonName
    public void setSurname(String surname) {
        this.surname = PersonName.normalize(surname);
    }

    public void setGivenName(String givenName) {
        this.givenName = PersonName.normalize(givenName);
    }
}
