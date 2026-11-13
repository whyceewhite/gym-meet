package doubletuck.meet.importer;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.AthleteNameVariant;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.repository.AthleteNameVariantRepository;
import doubletuck.meet.repository.AthleteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(AthleteMatcher.class)
class AthleteMatcherTest {

    @Autowired
    private AthleteMatcher athleteMatcher;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private AthleteNameVariantRepository nameVariantRepository;

    private Athlete hoopes;

    @BeforeEach
    void setUp() {
        hoopes = new Athlete();
        hoopes.setDiscipline(Discipline.MAG);
        hoopes.setSurname("HOOPES");
        hoopes.setGivenName("Patrick");
        hoopes.setLicenseNumber("40123");
        hoopes.setBirthYear((short) 2002);
        hoopes = athleteRepository.save(hoopes);
    }

    @Test
    void testMatch_NoMatch_CreatesAthlete() {
        AthleteMatcher.Match match = athleteMatcher.match(
                candidate("NELSON", "Kameron", "40456", LocalDate.of(2001, 8, 2), (short) 2001), "test");

        assertTrue(match.created());
        assertNotNull(match.athlete().getId());
        assertEquals("40456", match.athlete().getLicenseNumber());
        assertEquals(LocalDate.of(2001, 8, 2), match.athlete().getBirthDate());
    }

    @Test
    void testMatch_ByLicense_FillsInMissingBirthDate() {
        AthleteMatcher.Match match = athleteMatcher.match(
                candidate("HOOPES", "Patrick", "40123", LocalDate.of(2002, 5, 15), (short) 2002), "test");

        assertFalse(match.created());
        assertEquals(hoopes.getId(), match.athlete().getId());
        assertEquals(LocalDate.of(2002, 5, 15), match.athlete().getBirthDate());
    }

    @Test
    void testMatch_ByLicenseUnderAnotherSpelling_RecordsNameVariant() {
        athleteMatcher.match(candidate("HOOPS", "Patrick", "40123", null, null), "test");
        athleteMatcher.match(candidate("HOOPS", "Patrick", "40123", null, null), "test");

        List<AthleteNameVariant> variants = nameVariantRepository.findByAthlete(hoopes);
        assertEquals(1, variants.size());
        assertEquals("Hoops", variants.get(0).getSurname());
    }

    @Test
    void testMatch_ByNameIgnoringCase_WhenNoLicense() {
        AthleteMatcher.Match match = athleteMatcher.match(candidate("Hoopes", "Patrick", null, null, null), "test");

        assertFalse(match.created());
        assertEquals(hoopes.getId(), match.athlete().getId());
        assertEquals("Hoopes", match.athlete().getSurname());
    }

    @Test
    void testMatch_ByName_FillsInLicense() {
        hoopes.setLicenseNumber(null);

        AthleteMatcher.Match match = athleteMatcher.match(candidate("HOOPES", "Patrick", "40123", null, null), "test");

        assertEquals(hoopes.getId(), match.athlete().getId());
        assertEquals("40123", match.athlete().getLicenseNumber());
    }

    @Test
    void testMatch_ByNameVariant() {
        AthleteNameVariant variant = new AthleteNameVariant();
        variant.setAthlete(hoopes);
        variant.setSurname("HOOPS");
        variant.setGivenName("Pat");
        nameVariantRepository.save(variant);

        AthleteMatcher.Match match = athleteMatcher.match(candidate("Hoops", "Pat", null, null, null), "test");

        assertEquals(hoopes.getId(), match.athlete().getId());
    }

    @Test
    void testMatch_SameNameOtherDiscipline_CreatesAthlete() {
        AthleteMatcher.Match match = athleteMatcher.match(new AthleteMatcher.Candidate(Discipline.WAG, "HOOPES",
                "Patrick", null, null, null), "test");

        assertTrue(match.created());
    }

    @Test
    void testMatch_LicenseOfOtherDiscipline_Fails() {
        AthleteMatcher.Candidate wag = new AthleteMatcher.Candidate(Discipline.WAG, "SMITH", "Anna", "40123", null,
                null);

        ImportException e = assertThrows(ImportException.class, () -> athleteMatcher.match(wag, "file line 3"));
        assertTrue(e.getMessage().startsWith("file line 3: FIG license 40123 belongs to MAG"), e.getMessage());
    }

    @Test
    void testMatch_NameMatchWithDifferentBirthYear_Fails() {
        ImportException e = assertThrows(ImportException.class,
                () -> athleteMatcher.match(candidate("HOOPES", "Patrick", null, null, (short) 1999), "test"));
        assertTrue(e.getMessage().contains("born in 2002, not 1999"), e.getMessage());
    }

    @Test
    void testMatch_NameMatchWithDifferentLicense_Fails() {
        ImportException e = assertThrows(ImportException.class,
                () -> athleteMatcher.match(candidate("HOOPES", "Patrick", "99999", null, null), "test"));
        assertTrue(e.getMessage().contains("already recorded with FIG license 40123"), e.getMessage());
    }

    private static AthleteMatcher.Candidate candidate(String surname, String givenName, String license,
            LocalDate birthDate, Short birthYear) {
        return new AthleteMatcher.Candidate(Discipline.MAG, surname, givenName, license, birthDate, birthYear);
    }
}
