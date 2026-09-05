package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ParticipantRepositoryTest {

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private Event testEvent;
    private Athlete testAthlete;
    private Participant testParticipant;

    @BeforeEach
    void setUp() {
        testEvent = savedEvent("2026 European Championships");
        testAthlete = savedAthlete("Biles", "Simone");

        testParticipant = new Participant();
        testParticipant.setEvent(testEvent);
        testParticipant.setAthlete(testAthlete);
        testParticipant.setBibNumber("101");
        testParticipant.setNoc("USA");
        testParticipant = participantRepository.save(testParticipant);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Participant> found = participantRepository.findById(testParticipant.getId());

        assertTrue(found.isPresent());
        assertEquals(testEvent.getId(), found.get().getEvent().getId());
        assertEquals(testAthlete.getId(), found.get().getAthlete().getId());
        assertEquals("101", found.get().getBibNumber());
        assertEquals("USA", found.get().getNoc());
    }

    @Test
    void testFindByEventAndAthlete_Found() {
        Optional<Participant> found = participantRepository.findByEventAndAthlete(testEvent, testAthlete);

        assertTrue(found.isPresent());
        assertEquals(testParticipant.getId(), found.get().getId());
    }

    @Test
    void testFindByEventAndAthlete_WrongAthlete_NotFound() {
        Athlete otherAthlete = savedAthlete("Nedoroscik", "Stephen");

        Optional<Participant> found = participantRepository.findByEventAndAthlete(testEvent, otherAthlete);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventAndAthlete_WrongEvent_NotFound() {
        Event otherEvent = savedEvent("2026 Mediterranean Games");

        Optional<Participant> found = participantRepository.findByEventAndAthlete(otherEvent, testAthlete);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEvent_ReturnsAllParticipantsForEvent() {
        Athlete secondAthlete = savedAthlete("Lee", "Sunisa");
        Participant secondParticipant = new Participant();
        secondParticipant.setEvent(testEvent);
        secondParticipant.setAthlete(secondAthlete);
        secondParticipant.setNoc("USA");
        participantRepository.save(secondParticipant);

        List<Participant> found = participantRepository.findByEvent(testEvent);

        assertEquals(2, found.size());
    }

    @Test
    void testFindByEventId_MatchesFindByEvent() {
        List<Participant> found = participantRepository.findByEventId(testEvent.getId());

        assertEquals(1, found.size());
        assertEquals(testParticipant.getId(), found.get(0).getId());
    }

    @Test
    void testFindByAthlete_ReturnsParticipationAcrossEvents() {
        Event otherEvent = savedEvent("2026 World Championships");
        Participant otherParticipant = new Participant();
        otherParticipant.setEvent(otherEvent);
        otherParticipant.setAthlete(testAthlete);
        otherParticipant.setNoc("USA");
        participantRepository.save(otherParticipant);

        List<Participant> found = participantRepository.findByAthlete(testAthlete);

        assertEquals(2, found.size());
    }

    @Test
    void testCreate_FailsBecauseDuplicateEventAndAthlete() {
        Participant duplicate = new Participant();
        duplicate.setEvent(testEvent);
        duplicate.setAthlete(testAthlete);
        duplicate.setNoc("USA");

        assertThrows(DataIntegrityViolationException.class, () -> participantRepository.saveAndFlush(duplicate));
    }

    private Event savedEvent(String title) {
        Event event = new Event();
        event.setTitle(title);
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setDisciplines(Set.of(Discipline.WAG));
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        return eventRepository.save(event);
    }

    private Athlete savedAthlete(String surname, String givenName) {
        Athlete athlete = new Athlete();
        athlete.setDiscipline(Discipline.WAG);
        athlete.setSurname(surname);
        athlete.setGivenName(givenName);
        return athleteRepository.save(athlete);
    }
}
