package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
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
        testSession.setRound(Round.QUAL);
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
        assertEquals(Round.QUAL, found.get().getRound());
        assertEquals(Discipline.WAG, found.get().getDiscipline());
        assertEquals(AgeCategory.SR, found.get().getAgeCategory());
        assertNull(found.get().getPart());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_Found() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.QUAL, Discipline.WAG, AgeCategory.SR, null);

        assertTrue(found.isPresent());
        assertEquals(testSession.getId(), found.get().getId());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongRound_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.AA, Discipline.WAG, AgeCategory.SR, null);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongDiscipline_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.QUAL, Discipline.MAG, AgeCategory.SR, null);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongAgeCategory_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.QUAL, Discipline.WAG, AgeCategory.JR, null);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategory_WrongPart_NotFound() {
        Optional<Session> found = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.QUAL, Discipline.WAG, AgeCategory.SR, 1);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByEventRoundDisciplineAndAgeCategoryAndPart_DistinguishesMultiDayParts() {
        Session aaDay1 = new Session();
        aaDay1.setEvent(testEvent);
        aaDay1.setDate(LocalDate.of(2026, 8, 15));
        aaDay1.setRound(Round.AA);
        aaDay1.setDiscipline(Discipline.WAG);
        aaDay1.setAgeCategory(AgeCategory.SR);
        aaDay1.setPart(1);
        aaDay1 = sessionRepository.save(aaDay1);

        Session aaDay2 = new Session();
        aaDay2.setEvent(testEvent);
        aaDay2.setDate(LocalDate.of(2026, 8, 16));
        aaDay2.setRound(Round.AA);
        aaDay2.setDiscipline(Discipline.WAG);
        aaDay2.setAgeCategory(AgeCategory.SR);
        aaDay2.setPart(2);
        aaDay2 = sessionRepository.save(aaDay2);

        Optional<Session> foundDay1 = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.AA, Discipline.WAG, AgeCategory.SR, 1);
        Optional<Session> foundDay2 = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndPart(
                testEvent, Round.AA, Discipline.WAG, AgeCategory.SR, 2);

        assertTrue(foundDay1.isPresent());
        assertEquals(aaDay1.getId(), foundDay1.get().getId());
        assertTrue(foundDay2.isPresent());
        assertEquals(aaDay2.getId(), foundDay2.get().getId());
        assertNotEquals(foundDay1.get().getId(), foundDay2.get().getId());
    }

    @Test
    void testFindByEvent_ReturnsAllSessionsForEvent() {
        Session teamFinal = new Session();
        teamFinal.setEvent(testEvent);
        teamFinal.setDate(LocalDate.of(2026, 4, 22));
        teamFinal.setRound(Round.TEAM);
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
        otherSession.setRound(Round.QUAL);
        otherSession.setDiscipline(Discipline.WAG);
        otherSession.setAgeCategory(AgeCategory.SR);
        sessionRepository.save(otherSession);

        List<Session> found = sessionRepository.findByEvent(testEvent);

        assertEquals(1, found.size());
        assertEquals(testSession.getId(), found.get(0).getId());
    }

    /**
     * Documents a known limitation rather than a desired behavior: {@code
     * uk_session} includes both {@code apparatus_code} and {@code part}, and
     * standard SQL unique constraints don't treat two nulls as equal. Every
     * real session has at least one of the two null -- {@code apparatus} is
     * set only for {@code EVENT} rounds, {@code part} only for a round split
     * across sessions (§ Round.java, Session.java), and the two are never
     * both set at once in practice -- so this constraint alone does *not*
     * reliably catch a duplicate session of any round type. That protection
     * lives in {@link doubletuck.meet.service.SessionService} instead (see
     * {@code SessionServiceTest} for the cases that actually matter: same
     * apparatus, and same {@code part}). If one of these tests starts
     * failing (i.e. the duplicate insert starts being rejected), the DB
     * behavior has improved and the test/comment should be updated to
     * match, not "fixed" back to throwing.
     */
    @Test
    void testCreate_DuplicateQualSession_NotCaughtByDatabaseConstraintAlone() {
        Session duplicate = new Session();
        duplicate.setEvent(testEvent);
        duplicate.setDate(LocalDate.of(2026, 4, 21));
        duplicate.setRound(Round.QUAL);
        duplicate.setDiscipline(Discipline.WAG);
        duplicate.setAgeCategory(AgeCategory.SR);

        assertDoesNotThrow(() -> sessionRepository.saveAndFlush(duplicate));
    }

    @Test
    void testCreate_DuplicateEventSessionSameApparatus_NotCaughtByDatabaseConstraintAlone() {
        Session fxFinal = new Session();
        fxFinal.setEvent(testEvent);
        fxFinal.setDate(LocalDate.of(2026, 4, 22));
        fxFinal.setRound(Round.EVENT);
        fxFinal.setDiscipline(Discipline.WAG);
        fxFinal.setAgeCategory(AgeCategory.SR);
        fxFinal.setApparatus(Apparatus.FX);
        sessionRepository.saveAndFlush(fxFinal);

        Session duplicate = new Session();
        duplicate.setEvent(testEvent);
        duplicate.setDate(LocalDate.of(2026, 4, 22));
        duplicate.setRound(Round.EVENT);
        duplicate.setDiscipline(Discipline.WAG);
        duplicate.setAgeCategory(AgeCategory.SR);
        duplicate.setApparatus(Apparatus.FX);

        assertDoesNotThrow(() -> sessionRepository.saveAndFlush(duplicate));
    }

    @Test
    void testCreate_DuplicateAaSessionSamePart_NotCaughtByDatabaseConstraintAlone() {
        Session aaDay1 = new Session();
        aaDay1.setEvent(testEvent);
        aaDay1.setDate(LocalDate.of(2026, 8, 15));
        aaDay1.setRound(Round.AA);
        aaDay1.setDiscipline(Discipline.WAG);
        aaDay1.setAgeCategory(AgeCategory.SR);
        aaDay1.setPart(1);
        sessionRepository.saveAndFlush(aaDay1);

        Session duplicate = new Session();
        duplicate.setEvent(testEvent);
        duplicate.setDate(LocalDate.of(2026, 8, 15));
        duplicate.setRound(Round.AA);
        duplicate.setDiscipline(Discipline.WAG);
        duplicate.setAgeCategory(AgeCategory.SR);
        duplicate.setPart(1);

        assertDoesNotThrow(() -> sessionRepository.saveAndFlush(duplicate));
    }

    private Event savedEvent(String title) {
        Event event = new Event();
        event.setTitle(title);
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        return eventRepository.save(event);
    }
}
