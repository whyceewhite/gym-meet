package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "athlete")
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

    @Size(min = 3, max = 3, message = "Country must be a 3-letter code")
    @Column(name = "country_code", length = 3)
    private String country;

    @Column(name = "birth_date")
    private LocalDate birthDate;
}
