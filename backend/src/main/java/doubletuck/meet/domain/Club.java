package doubletuck.meet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A gym or club an athlete trains with. Most international competitions do not
 * publish this information and identify athletes by country instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "club", uniqueConstraints = {
        @UniqueConstraint(name = "uk_club", columnNames = { "name", "country_code" })
})
public class Club extends BaseEntity {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must have 100 or fewer characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Country is required")
    @Size(min = 3, max = 3, message = "Country must be a 3-letter code")
    @Column(name = "country_code", nullable = false, length = 3)
    private String country;
}
