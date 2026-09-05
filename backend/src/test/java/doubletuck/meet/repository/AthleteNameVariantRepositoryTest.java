package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.AthleteNameVariant;
import doubletuck.meet.domain.Discipline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AthleteNameVariantRepositoryTest {

    @Autowired
    private AthleteNameVariantRepository athleteNameVariantRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private Athlete testAthlete;

    @BeforeEach
    void setUp() {
        testAthlete = new Athlete();
        testAthlete.setDiscipline(Discipline.WAG);
        testAthlete.setSurname("Biles");
        testAthlete.setGivenName("Simone");
        testAthlete = athleteRepository.save(testAthlete);
    }

    @Test
    void testSave_PersistsAllFields() {
        AthleteNameVariant variant = new AthleteNameVariant();
        variant.setAthlete(testAthlete);
        variant.setSurname("Biles-Owens");
        variant.setGivenName("Simone");
        variant = athleteNameVariantRepository.save(variant);

        Optional<AthleteNameVariant> found = athleteNameVariantRepository.findById(variant.getId());

        assertTrue(found.isPresent());
        assertEquals(testAthlete.getId(), found.get().getAthlete().getId());
        assertEquals("Biles-Owens", found.get().getSurname());
        assertEquals("Simone", found.get().getGivenName());
    }

    @Test
    void testFindByAthlete_ReturnsAllVariants() {
        saveVariant(testAthlete, "Biles-Owens", "Simone");
        saveVariant(testAthlete, "Byles", "Simone");

        List<AthleteNameVariant> found = athleteNameVariantRepository.findByAthlete(testAthlete);

        assertEquals(2, found.size());
    }

    @Test
    void testFindByAthlete_NoVariants_ReturnsEmpty() {
        List<AthleteNameVariant> found = athleteNameVariantRepository.findByAthlete(testAthlete);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByAthleteId_MatchesFindByAthlete() {
        saveVariant(testAthlete, "Biles-Owens", "Simone");

        List<AthleteNameVariant> found = athleteNameVariantRepository.findByAthleteId(testAthlete.getId());

        assertEquals(1, found.size());
        assertEquals("Biles-Owens", found.get(0).getSurname());
    }

    @Test
    void testFindByAthlete_DoesNotReturnOtherAthletesVariants() {
        Athlete otherAthlete = new Athlete();
        otherAthlete.setDiscipline(Discipline.MAG);
        otherAthlete.setSurname("Nedoroscik");
        otherAthlete.setGivenName("Stephen");
        otherAthlete = athleteRepository.save(otherAthlete);

        saveVariant(testAthlete, "Biles-Owens", "Simone");
        saveVariant(otherAthlete, "Nedoroscik", "Steve");

        List<AthleteNameVariant> found = athleteNameVariantRepository.findByAthlete(testAthlete);

        assertEquals(1, found.size());
        assertEquals("Biles-Owens", found.get(0).getSurname());
    }

    private AthleteNameVariant saveVariant(Athlete athlete, String surname, String givenName) {
        AthleteNameVariant variant = new AthleteNameVariant();
        variant.setAthlete(athlete);
        variant.setSurname(surname);
        variant.setGivenName(givenName);
        return athleteNameVariantRepository.save(variant);
    }
}
