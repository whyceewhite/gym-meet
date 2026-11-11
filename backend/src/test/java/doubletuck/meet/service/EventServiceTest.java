package doubletuck.meet.service;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import doubletuck.meet.dto.EventRequest;
import doubletuck.meet.dto.EventResponse;
import doubletuck.meet.exception.ConflictException;
import doubletuck.meet.repository.AthleteRepository;
import doubletuck.meet.repository.EventRepository;
import doubletuck.meet.repository.ParticipantRepository;
import doubletuck.meet.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private EventService eventService;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, sessionRepository, participantRepository);

        testEvent = new Event();
        testEvent.setFigEventNumber("FIG-2026-EURO");
        testEvent.setTitle("2026 European Championships");
        testEvent.setStartDate(LocalDate.of(2026, 4, 20));
        testEvent.setEndDate(LocalDate.of(2026, 4, 26));
        testEvent.setCity("Stuttgart");
        testEvent.setCountryCode("GER");
        testEvent.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        testEvent = eventRepository.save(testEvent);
    }

    @Test
    void testFindById_Found() {
        Optional<EventResponse> found = eventService.findById(testEvent.getId());

        assertTrue(found.isPresent());
        assertEquals(testEvent.getId(), found.get().id());
        assertEquals("2026 European Championships", found.get().title());
    }

    @Test
    void testFindById_NotFound() {
        Optional<EventResponse> found = eventService.findById(-1L);

        assertTrue(found.isEmpty());
    }

    @Test
    void testGetPaginatedEntities_ReturnsAllEvents() {
        assertEquals(1, eventService.getPaginatedEntities(PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void testCreate_Succeeds() {
        EventRequest request = requestFor("FIG-2026-WORLDS", "2026 World Championships",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        EventResponse created = eventService.create(request);

        assertNotNull(created.id());
        assertEquals("2026 World Championships", created.title());
        assertTrue(eventRepository.findById(created.id()).isPresent());
    }

    @Test
    void testCreate_FailsOnDuplicateFigEventNumber() {
        EventRequest request = requestFor("FIG-2026-EURO", "A Different Title",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5));

        assertThrows(ConflictException.class, () -> eventService.create(request));
    }

    @Test
    void testCreate_FailsOnDuplicateTitleAndStartDate_WhenFigEventNumberAbsent() {
        EventRequest request = new EventRequest(null, "2026 European Championships", testEvent.getStartDate(),
                LocalDate.of(2026, 4, 27), "Different City", "FRA", null, null,
                EventLevel.DOMESTIC_COMPETITION);

        assertThrows(ConflictException.class, () -> eventService.create(request));
    }

    @Test
    void testCreate_SucceedsWithoutFigEventNumber_WhenTitleAndStartDateAreUnique() {
        EventRequest request = new EventRequest(null, "2026 US Championships", LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 18), "Fort Worth", "USA", null, null,
                EventLevel.NATIONAL_CHAMPIONSHIPS);

        EventResponse created = eventService.create(request);

        assertNull(created.figEventNumber());
        assertNotNull(created.id());
    }

    @Test
    void testUpdate_Succeeds() {
        EventRequest request = requestFor("FIG-2026-EURO", "2026 European Championships (Updated)",
                testEvent.getStartDate(), testEvent.getEndDate());

        Optional<EventResponse> updated = eventService.update(testEvent.getId(), request);

        assertTrue(updated.isPresent());
        assertEquals("2026 European Championships (Updated)", updated.get().title());
    }

    @Test
    void testUpdate_AllowsSameEventToKeepItsOwnFigEventNumber() {
        EventRequest request = requestFor("FIG-2026-EURO", "2026 European Championships", testEvent.getStartDate(),
                testEvent.getEndDate());

        Optional<EventResponse> updated = eventService.update(testEvent.getId(), request);

        assertTrue(updated.isPresent());
    }

    @Test
    void testUpdate_NotFound() {
        EventRequest request = requestFor("FIG-2026-WORLDS", "2026 World Championships",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));

        Optional<EventResponse> updated = eventService.update(-1L, request);

        assertTrue(updated.isEmpty());
    }

    @Test
    void testUpdate_FailsOnDuplicateFigEventNumberOfAnotherEvent() {
        Event otherEvent = new Event();
        otherEvent.setFigEventNumber("FIG-2026-WORLDS");
        otherEvent.setTitle("2026 World Championships");
        otherEvent.setStartDate(LocalDate.of(2026, 10, 1));
        otherEvent.setEndDate(LocalDate.of(2026, 10, 10));
        otherEvent.setCity("Jakarta");
        otherEvent.setCountryCode("IDN");
        otherEvent.setLevel(EventLevel.WORLD_CHAMPIONSHIPS);
        eventRepository.save(otherEvent);

        EventRequest request = requestFor("FIG-2026-WORLDS", "2026 European Championships",
                testEvent.getStartDate(), testEvent.getEndDate());

        assertThrows(ConflictException.class, () -> eventService.update(testEvent.getId(), request));
    }

    @Test
    void testDeleteById_SucceedsWhenNoDependents() {
        boolean deleted = eventService.deleteById(testEvent.getId());

        assertTrue(deleted);
        assertTrue(eventRepository.findById(testEvent.getId()).isEmpty());
    }

    @Test
    void testDeleteById_NotFound() {
        boolean deleted = eventService.deleteById(-1L);

        assertFalse(deleted);
    }

    @Test
    void testDeleteById_FailsWhenSessionsExist() {
        Session session = new Session();
        session.setEvent(testEvent);
        session.setDate(testEvent.getStartDate());
        session.setRound(Round.QUAL);
        session.setDiscipline(Discipline.WAG);
        session.setAgeCategory(AgeCategory.SR);
        sessionRepository.save(session);

        assertThrows(ConflictException.class, () -> eventService.deleteById(testEvent.getId()));
        assertTrue(eventRepository.findById(testEvent.getId()).isPresent());
    }

    @Test
    void testDeleteById_FailsWhenParticipantsExist() {
        Athlete athlete = new Athlete();
        athlete.setDiscipline(Discipline.WAG);
        athlete.setSurname("Biles");
        athlete.setGivenName("Simone");
        athlete = athleteRepository.save(athlete);

        Participant participant = new Participant();
        participant.setEvent(testEvent);
        participant.setAthlete(athlete);
        participant.setNoc("USA");
        participantRepository.save(participant);

        assertThrows(ConflictException.class, () -> eventService.deleteById(testEvent.getId()));
        assertTrue(eventRepository.findById(testEvent.getId()).isPresent());
    }

    private EventRequest requestFor(String figEventNumber, String title, LocalDate startDate, LocalDate endDate) {
        return new EventRequest(figEventNumber, title, startDate, endDate, "Stuttgart", "GER", null, null,
                EventLevel.CONTINENTAL_CHAMPIONSHIPS);
    }
}
