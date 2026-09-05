package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Discipline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AthleteRepositoryTest {

    @Autowired
    private AthleteRepository athleteRepository;

    private Athlete testAthlete;

    @BeforeEach
    void setUp() {
        testAthlete = new Athlete();
        testAthlete.setLicenseNumber("FIG-12345");
        testAthlete.setDiscipline(Discipline.WAG);
        testAthlete.setSurname("Biles");
        testAthlete.setGivenName("Simone");
        testAthlete.setCountry("USA");
        testAthlete.setBirthDate(LocalDate.of(1997, 3, 14));
        testAthlete = athleteRepository.save(testAthlete);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Athlete> found = athleteRepository.findById(testAthlete.getId());

        assertTrue(found.isPresent());
        assertEquals("FIG-12345", found.get().getLicenseNumber());
        assertEquals(Discipline.WAG, found.get().getDiscipline());
        assertEquals("Biles", found.get().getSurname());
        assertEquals("Simone", found.get().getGivenName());
        assertEquals("USA", found.get().getCountry());
        assertEquals(LocalDate.of(1997, 3, 14), found.get().getBirthDate());
        assertNotNull(found.get().getCreationTimestamp());
        assertNotNull(found.get().getLastUpdateTimestamp());
    }

    @Test
    void testFindByLicenseNumber_Found() {
        Optional<Athlete> found = athleteRepository.findByLicenseNumber("FIG-12345");

        assertTrue(found.isPresent());
        assertEquals(testAthlete.getId(), found.get().getId());
    }

    @Test
    void testFindByLicenseNumber_NotFound() {
        Optional<Athlete> found = athleteRepository.findByLicenseNumber("NO-SUCH-LICENSE");

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindBySurnameGivenNameAndDiscipline_ExactMatch() {
        List<Athlete> found = athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(
                "Biles", "Simone", Discipline.WAG);

        assertEquals(1, found.size());
        assertEquals(testAthlete.getId(), found.get(0).getId());
    }

    @Test
    void testFindBySurnameGivenNameAndDiscipline_CaseInsensitive() {
        List<Athlete> found = athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(
                "biles", "SIMONE", Discipline.WAG);

        assertEquals(1, found.size());
        assertEquals(testAthlete.getId(), found.get(0).getId());
    }

    @Test
    void testFindBySurnameGivenNameAndDiscipline_WrongDiscipline_ReturnsEmpty() {
        List<Athlete> found = athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(
                "Biles", "Simone", Discipline.MAG);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindBySurnameGivenNameAndDiscipline_MultipleAthletesSameName() {
        Athlete otherDiscipline = new Athlete();
        otherDiscipline.setDiscipline(Discipline.MAG);
        otherDiscipline.setSurname("Biles");
        otherDiscipline.setGivenName("Simone");
        athleteRepository.save(otherDiscipline);

        List<Athlete> wagMatches = athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(
                "Biles", "Simone", Discipline.WAG);
        List<Athlete> magMatches = athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(
                "Biles", "Simone", Discipline.MAG);

        assertEquals(1, wagMatches.size());
        assertEquals(1, magMatches.size());
        assertNotEquals(wagMatches.get(0).getId(), magMatches.get(0).getId());
    }
}
