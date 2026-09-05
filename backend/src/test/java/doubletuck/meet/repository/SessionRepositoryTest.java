package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
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
class SessionRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testEvent = savedEvent("2026 European Championships");

        testSession = new Session();
        testSession.setEvent(testEvent);
        testSession.setDate(LocalDate.of(2026, 4, 21));
        testSession.setRound(Round.QUALIFICATION);
        testSession.setDiscipline(Discipline.WAG);
        testSession.setAgeCategory(AgeCategory.SR);
        testSession = sessionRepository.save(testSession);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Session> found = sessionRepository.findById(testSession.getId());

        assertTrue(found.isPresent());
        assertEquals(testEvent.getId(), found.get().getEvent().getId());
        assertEquals(LocalDate.of(2026, 4, 21), found.get().getDate());
        assertEquals(Round.QUALIFICATION, found.get().getRound());
        assertEquals(Discipline.WAG, found.get().getDiscipline());
        assertEquals(AgeCategory.SR, found.get().getAgeCategory());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_Found() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategory(
                testEvent, Round.QUALIFICATION, Discipline.WAG, AgeCategory.SR);

        assertTrue(found.isPresent());
        assertEquals(testSession.getId(), found.get().getId());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongRound_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategory(
                testEvent, Round.AA_FINAL, Discipline.WAG, AgeCategory.SR);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongDiscipline_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategory(
                testEvent, Round.QUALIFICATION, Discipline.MAG, AgeCategory.SR);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongAgeCategory_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategory(
                testEvent, Round.QUALIFICATION, Discipline.WAG, AgeCategory.JR);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEvent_ReturnsAllSessionsForEvent() {
        Session teamFinal = new Session();
        teamFinal.setEvent(testEvent);
        teamFinal.setDate(LocalDate.of(2026, 4, 22));
        teamFinal.setRound(Round.TEAM_FINAL);
        teamFinal.setDiscipline(Discipline.WAG);
        teamFinal.setAgeCategory(AgeCategory.SR);
        sessionRepository.save(teamFinal);

        List<Session> found = sessionRepository.findByEvent(testEvent);

        assertEquals(2, found.size());
    }

    @Test
    void testFindByEventId_MatchesFindByEvent() {
        List<Session> found = sessionRepository.findByEventId(testEvent.getId());

        assertEquals(1, found.size());
        assertEquals(testSession.getId(), found.get(0).getId());
    }

    @Test
    void testFindByEvent_DoesNotReturnOtherEventsSessions() {
        Event otherEvent = savedEvent("2026 Mediterranean Games");
        Session otherSession = new Session();
        otherSession.setEvent(otherEvent);
        otherSession.setDate(LocalDate.of(2026, 6, 1));
        otherSession.setRound(Round.QUALIFICATION);
        otherSession.setDiscipline(Discipline.WAG);
        otherSession.setAgeCategory(AgeCategory.SR);
        sessionRepository.save(otherSession);

        List<Session> found = sessionRepository.findByEvent(testEvent);

        assertEquals(1, found.size());
        assertEquals(testSession.getId(), found.get(0).getId());
    }

    @Test
    void testCreate_FailsBecauseDuplicateEventRoundDisciplineAgeCategory() {
        Session duplicate = new Session();
        duplicate.setEvent(testEvent);
        duplicate.setDate(LocalDate.of(2026, 4, 21));
        duplicate.setRound(Round.QUALIFICATION);
        duplicate.setDiscipline(Discipline.WAG);
        duplicate.setAgeCategory(AgeCategory.SR);

        assertThrows(DataIntegrityViolationException.class, () -> sessionRepository.saveAndFlush(duplicate));
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
}
