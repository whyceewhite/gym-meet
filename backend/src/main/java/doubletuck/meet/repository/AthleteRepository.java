package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Discipline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AthleteRepository extends JpaRepository<Athlete, Long> {

    Optional<Athlete> findByLicenseNumber(String licenseNumber);

    List<Athlete> findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(String surname, String givenName,
            Discipline discipline);
}
