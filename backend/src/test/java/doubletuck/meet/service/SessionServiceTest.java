package doubletuck.meet.service;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import doubletuck.meet.exception.ConflictException;
import doubletuck.meet.repository.EventRepository;
import doubletuck.meet.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class SessionServiceTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private SessionService sessionService;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository);

        testEvent = new Event();
        testEvent.setTitle("2026 European Championships");
        testEvent.setStartDate(LocalDate.of(2026, 4, 20));
        testEvent.setEndDate(LocalDate.of(2026, 4, 26));
        testEvent.setCity("Stuttgart");
        testEvent.setCountryCode("DEU");
        testEvent.setDisciplines(Set.of(Discipline.MAG));
        testEvent.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        testEvent = eventRepository.save(testEvent);
    }

    @Test
    void testCreateSession_Succeeds() {
        Session session = sessionOf(Round.QUAL, null);

        Session saved = sessionService.createSession(session);

        assertNotNull(saved.getId());
    }

    @Test
    void testCreateSession_DuplicateQualSession_ThrowsConflictException() {
        sessionService.createSession(sessionOf(Round.QUAL, null));

        assertThrows(ConflictException.class, () -> sessionService.createSession(sessionOf(Round.QUAL, null)));
    }

    @Test
    void testCreateSession_DuplicateEventSessionSameApparatus_ThrowsConflictException() {
        sessionService.createSession(sessionOf(Round.EVENT, Apparatus.FX));

        assertThrows(ConflictException.class,
                () -> sessionService.createSession(sessionOf(Round.EVENT, Apparatus.FX)));
    }

    @Test
    void testCreateSession_EventSessionsDifferentApparatus_BothSucceed() {
        Session fx = sessionService.createSession(sessionOf(Round.EVENT, Apparatus.FX));
        Session ph = sessionService.createSession(sessionOf(Round.EVENT, Apparatus.PH));

        assertNotNull(fx.getId());
        assertNotNull(ph.getId());
        assertNotEquals(fx.getId(), ph.getId());
    }

    private Session sessionOf(Round round, Apparatus apparatus) {
        Session session = new Session();
        session.setEvent(testEvent);
        session.setDate(LocalDate.of(2026, 4, 21));
        session.setRound(round);
        session.setDiscipline(Discipline.MAG);
        session.setAgeCategory(AgeCategory.SR);
        session.setApparatus(apparatus);
        return session;
    }
}
