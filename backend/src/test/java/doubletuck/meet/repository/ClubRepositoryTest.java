package doubletuck.meet.repository;

import doubletuck.meet.domain.Club;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ClubRepositoryTest {

    @Autowired
    private ClubRepository clubRepository;

    private Club testClub;

    @BeforeEach
    void setUp() {
        testClub = clubRepository.save(new Club("WOGA", "USA"));
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Club> found = clubRepository.findById(testClub.getId());

        assertTrue(found.isPresent());
        assertEquals("WOGA", found.get().getName());
        assertEquals("USA", found.get().getCountry());
    }

    @Test
    void testFindByNameIgnoreCaseAndCountry_Found() {
        Optional<Club> found = clubRepository.findByNameIgnoreCaseAndCountry("woga", "USA");

        assertTrue(found.isPresent());
        assertEquals(testClub.getId(), found.get().getId());
    }

    @Test
    void testFindByNameIgnoreCaseAndCountry_OtherCountry_NotFound() {
        assertTrue(clubRepository.findByNameIgnoreCaseAndCountry("WOGA", "CAN").isEmpty());
    }

    @Test
    void testSave_SameNameInAnotherCountry() {
        Club other = clubRepository.saveAndFlush(new Club("WOGA", "CAN"));

        assertNotEquals(testClub.getId(), other.getId());
    }

    @Test
    void testCreate_FailsBecauseDuplicateNameAndCountry() {
        assertThrows(DataIntegrityViolationException.class,
                () -> clubRepository.saveAndFlush(new Club("WOGA", "USA")));
    }

    @Test
    void testCreate_FailsBecauseCountryMissing() {
        assertThrows(ConstraintViolationException.class, () -> clubRepository.saveAndFlush(new Club("WOGA", null)));
    }
}
