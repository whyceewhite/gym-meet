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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "athlete_name_variant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_athlete_name_variant", columnNames = { "athlete_id", "surname", "given_name" })
})
public class AthleteNameVariant extends BaseEntity {

    @NotNull(message = "Athlete is required")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "athlete_id", nullable = false, foreignKey = @ForeignKey(name = "fk_athlete_name_variant_athlete"))
    private Athlete athlete;

    @NotBlank(message = "Surname is required")
    @Size(max = 60, message = "Surname must have 60 or fewer characters")
    @Column(name = "surname", nullable = false, length = 60)
    private String surname;

    @NotBlank(message = "Given name is required")
    @Size(max = 60, message = "Given name must have 60 or fewer characters")
    @Column(name = "given_name", nullable = false, length = 60)
    private String givenName;

    // Names are stored in one form whatever capitalisation the source printed; see PersonName
    public void setSurname(String surname) {
        this.surname = PersonName.normalize(surname);
    }

    public void setGivenName(String givenName) {
        this.givenName = PersonName.normalize(givenName);
    }
}
