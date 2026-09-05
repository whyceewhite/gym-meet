package doubletuck.meet.repository;

import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setFigEventNumber("FIG-2026-EURO");
        testEvent.setTitle("2026 European Championships");
        testEvent.setStartDate(LocalDate.of(2026, 4, 20));
        testEvent.setEndDate(LocalDate.of(2026, 4, 26));
        testEvent.setCity("Stuttgart");
        testEvent.setCountryCode("DEU");
        testEvent.setVenue("Schleyerhalle");
        testEvent.setDisciplines(Set.of(Discipline.MAG, Discipline.WAG));
        testEvent.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        testEvent = eventRepository.save(testEvent);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Event> found = eventRepository.findById(testEvent.getId());

        assertTrue(found.isPresent());
        assertEquals("FIG-2026-EURO", found.get().getFigEventNumber());
        assertEquals("2026 European Championships", found.get().getTitle());
        assertEquals(LocalDate.of(2026, 4, 20), found.get().getStartDate());
        assertEquals(LocalDate.of(2026, 4, 26), found.get().getEndDate());
        assertEquals("Stuttgart", found.get().getCity());
        assertEquals("DEU", found.get().getCountryCode());
        assertEquals("Schleyerhalle", found.get().getVenue());
        assertEquals(Set.of(Discipline.MAG, Discipline.WAG), found.get().getDisciplines());
        assertEquals(EventLevel.CONTINENTAL_CHAMPIONSHIPS, found.get().getLevel());
    }

    @Test
    void testFindByFigEventNumber_Found() {
        Optional<Event> found = eventRepository.findByFigEventNumber("FIG-2026-EURO");

        assertTrue(found.isPresent());
        assertEquals(testEvent.getId(), found.get().getId());
    }

    @Test
    void testFindByFigEventNumber_WrongNumber_NotFound() {
        Optional<Event> found = eventRepository.findByFigEventNumber("NO-SUCH-EVENT");

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByTitleAndStartDate_CaseInsensitiveMatch() {
        Optional<Event> found = eventRepository.findByTitleIgnoreCaseAndStartDate(
                "2026 EUROPEAN CHAMPIONSHIPS", LocalDate.of(2026, 4, 20));

        assertTrue(found.isPresent());
        assertEquals(testEvent.getId(), found.get().getId());
    }

    @Test
    void testFindByTitleAndStartDate_WrongDate_NotFound() {
        Optional<Event> found = eventRepository.findByTitleIgnoreCaseAndStartDate(
                testEvent.getTitle(), LocalDate.of(2026, 5, 1));

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByTitleAndStartDate_WrongTitle_NotFound() {
        Optional<Event> found = eventRepository.findByTitleIgnoreCaseAndStartDate(
                "Wrong Title", testEvent.getStartDate());

        assertTrue(found.isEmpty());
    }
}
