package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.AllAroundResult;
import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
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
class AllAroundResultRepositoryTest {

    @Autowired
    private AllAroundResultRepository allAroundResultRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private Session testSession;
    private Participant testParticipant;
    private AllAroundResult testResult;

    @BeforeEach
    void setUp() {
        Event event = new Event();
        event.setTitle("2026 European Championships");
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        event = eventRepository.save(event);

        testSession = new Session();
        testSession.setEvent(event);
        testSession.setDate(LocalDate.of(2026, 4, 23));
        testSession.setRound(Round.AA);
        testSession.setDiscipline(Discipline.WAG);
        testSession.setAgeCategory(AgeCategory.SR);
        testSession = sessionRepository.save(testSession);

        Athlete athlete = new Athlete();
        athlete.setDiscipline(Discipline.WAG);
        athlete.setSurname("Biles");
        athlete.setGivenName("Simone");
        athlete = athleteRepository.save(athlete);

        testParticipant = new Participant();
        testParticipant.setEvent(event);
        testParticipant.setAthlete(athlete);
        testParticipant.setNoc("USA");
        testParticipant = participantRepository.save(testParticipant);

        testResult = new AllAroundResult();
        testResult.setParticipant(testParticipant);
        testResult.setSession(testSession);
        testResult.setTotalScore(57.199);
        testResult.setRank(1);
        testResult = allAroundResultRepository.save(testResult);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<AllAroundResult> found = allAroundResultRepository.findById(testResult.getId());

        assertTrue(found.isPresent());
        assertEquals(testParticipant.getId(), found.get().getParticipant().getId());
        assertEquals(testSession.getId(), found.get().getSession().getId());
        assertEquals(57.199, found.get().getTotalScore());
        assertEquals(1, found.get().getRank());
    }

    @Test
    void testFindByParticipantAndSession_Found() {
        Optional<AllAroundResult> found = allAroundResultRepository.findByParticipantAndSession(
                testParticipant, testSession);

        assertTrue(found.isPresent());
        assertEquals(testResult.getId(), found.get().getId());
    }

    @Test
    void testFindByParticipantAndSession_WrongSession_NotFound() {
        Session otherSession = new Session();
        otherSession.setEvent(testSession.getEvent());
        otherSession.setDate(LocalDate.of(2026, 4, 21));
        otherSession.setRound(Round.QUAL);
        otherSession.setDiscipline(Discipline.WAG);
        otherSession.setAgeCategory(AgeCategory.SR);
        otherSession = sessionRepository.save(otherSession);

        Optional<AllAroundResult> found = allAroundResultRepository.findByParticipantAndSession(
                testParticipant, otherSession);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindBySession_ReturnsAllAthletesInSession() {
        Athlete secondAthlete = new Athlete();
        secondAthlete.setDiscipline(Discipline.WAG);
        secondAthlete.setSurname("Lee");
        secondAthlete.setGivenName("Sunisa");
        secondAthlete = athleteRepository.save(secondAthlete);

        Participant secondParticipant = new Participant();
        secondParticipant.setEvent(testSession.getEvent());
        secondParticipant.setAthlete(secondAthlete);
        secondParticipant.setNoc("USA");
        secondParticipant = participantRepository.save(secondParticipant);

        AllAroundResult secondResult = new AllAroundResult();
        secondResult.setParticipant(secondParticipant);
        secondResult.setSession(testSession);
        secondResult.setTotalScore(56.4);
        secondResult.setRank(2);
        allAroundResultRepository.save(secondResult);

        List<AllAroundResult> found = allAroundResultRepository.findBySession(testSession);

        assertEquals(2, found.size());
    }

    @Test
    void testFindByParticipant_ReturnsResultsAcrossSessions() {
        Session qualification = new Session();
        qualification.setEvent(testSession.getEvent());
        qualification.setDate(LocalDate.of(2026, 4, 21));
        qualification.setRound(Round.QUAL);
        qualification.setDiscipline(Discipline.WAG);
        qualification.setAgeCategory(AgeCategory.SR);
        qualification = sessionRepository.save(qualification);

        AllAroundResult qualResult = new AllAroundResult();
        qualResult.setParticipant(testParticipant);
        qualResult.setSession(qualification);
        qualResult.setTotalScore(56.8);
        qualResult.setRank(3);
        allAroundResultRepository.save(qualResult);

        List<AllAroundResult> found = allAroundResultRepository.findByParticipant(testParticipant);

        assertEquals(2, found.size());
    }

    @Test
    void testCreate_FailsBecauseDuplicateParticipantAndSession() {
        AllAroundResult duplicate = new AllAroundResult();
        duplicate.setParticipant(testParticipant);
        duplicate.setSession(testSession);

        assertThrows(DataIntegrityViolationException.class, () -> allAroundResultRepository.saveAndFlush(duplicate));
    }
}
