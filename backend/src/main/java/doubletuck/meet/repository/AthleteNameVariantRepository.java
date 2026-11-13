package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.AthleteNameVariant;
import doubletuck.meet.domain.Discipline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AthleteNameVariantRepository extends JpaRepository<AthleteNameVariant, Long> {

    List<AthleteNameVariant> findByAthlete(Athlete athlete);

    List<AthleteNameVariant> findByAthleteId(Long athleteId);

    List<AthleteNameVariant> findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndAthleteDiscipline(String surname,
            String givenName, Discipline discipline);

    boolean existsByAthleteAndSurnameIgnoreCaseAndGivenNameIgnoreCase(Athlete athlete, String surname,
            String givenName);
}
