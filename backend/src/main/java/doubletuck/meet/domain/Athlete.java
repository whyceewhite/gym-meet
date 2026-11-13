package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "athlete", uniqueConstraints = {
        @UniqueConstraint(name = "uk_athlete", columnNames = { "surname", "given_name", "discipline_code" })
}, indexes = {
        @Index(name = "ix_athlete_license_number", columnList = "license_number")
})
public class Athlete extends BaseEntity {

    @Size(max = 20, message = "License number must have 20 or fewer characters")
    @Column(name = "license_number", length = 20)
    private String licenseNumber;

    @NotNull(message = "Discipline is required")
    @Column(name = "discipline_code", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Discipline discipline;

    @NotBlank(message = "Surname is required")
    @Size(max = 60, message = "Surname must have 60 or fewer characters")
    @Column(name = "surname", nullable = false, length = 60)
    private String surname;

    @NotBlank(message = "Given name is required")
    @Size(max = 60, message = "Given name must have 60 or fewer characters")
    @Column(name = "given_name", nullable = false, length = 60)
    private String givenName;

    @Size(max = 50, message = "Birthplace must have 50 or fewer characters")
    @Column(name = "birthplace", length = 50)
    private String birthplace;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    // Names are stored in one form whatever capitalisation the source printed; see PersonName
    public void setSurname(String surname) {
        this.surname = PersonName.normalize(surname);
    }

    public void setGivenName(String givenName) {
        this.givenName = PersonName.normalize(givenName);
    }
}
