package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.BonusDetail;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Score;
import doubletuck.meet.domain.ScoreStatus;
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
class ScoreRepositoryTest {

    @Autowired
    private ScoreRepository scoreRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private Event testEvent;
    private Session testSession;
    private Participant testParticipant;
    private Score testScore;

    @BeforeEach
    void setUp() {
        testEvent = savedEvent();

        testSession = new Session();
        testSession.setEvent(testEvent);
        testSession.setDate(LocalDate.of(2026, 4, 21));
        testSession.setRound(Round.QUALIFICATION);
        testSession.setDiscipline(Discipline.WAG);
        testSession.setAgeCategory(AgeCategory.SR);
        testSession = sessionRepository.save(testSession);

        Athlete athlete = new Athlete();
        athlete.setDiscipline(Discipline.WAG);
        athlete.setSurname("Biles");
        athlete.setGivenName("Simone");
        athlete = athleteRepository.save(athlete);

        testParticipant = new Participant();
        testParticipant.setEvent(testEvent);
        testParticipant.setAthlete(athlete);
        testParticipant.setNoc("USA");
        testParticipant = participantRepository.save(testParticipant);

        testScore = new Score();
        testScore.setParticipant(testParticipant);
        testScore.setSession(testSession);
        testScore.setApparatus(Apparatus.VT);
        testScore.setDifficulty(5.0);
        testScore.setExecution(9.033);
        testScore.setScore(14.033);
        testScore.setTotalScore(14.033);
        testScore.setStatus(ScoreStatus.COMPLETED);
        testScore = scoreRepository.save(testScore);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Score> found = scoreRepository.findById(testScore.getId());

        assertTrue(found.isPresent());
        assertEquals(testParticipant.getId(), found.get().getParticipant().getId());
        assertEquals(testSession.getId(), found.get().getSession().getId());
        assertEquals(Apparatus.VT, found.get().getApparatus());
        assertEquals(5.0, found.get().getDifficulty());
        assertEquals(9.033, found.get().getExecution());
        assertEquals(14.033, found.get().getTotalScore());
        assertEquals(ScoreStatus.COMPLETED, found.get().getStatus());
        assertTrue(found.get().getCountsTowardTeamResult());
    }

    @Test
    void testSave_PersistsBonusDetailAsJson() {
        Score beamScore = new Score();
        beamScore.setParticipant(testParticipant);
        beamScore.setSession(testSession);
        beamScore.setApparatus(Apparatus.BB);
        beamScore.setStatus(ScoreStatus.COMPLETED);
        beamScore.addBonusDetail(new BonusDetail(0.1, "Difficulty bonus"));
        beamScore.addBonusDetail(new BonusDetail(0.05, "Connection bonus"));
        beamScore = scoreRepository.save(beamScore);

        Score found = scoreRepository.findById(beamScore.getId()).orElseThrow();

        assertEquals(2, found.getBonusDetail().size());
        assertEquals(0.1, found.getBonusDetail().get(0).getBonus());
        assertEquals("Difficulty bonus", found.getBonusDetail().get(0).getDescription());
    }

    @Test
    void testFindByParticipantSessionAndApparatus_Found() {
        Optional<Score> found = scoreRepository.findByParticipantAndSessionAndApparatus(
                testParticipant, testSession, Apparatus.VT);

        assertTrue(found.isPresent());
        assertEquals(testScore.getId(), found.get().getId());
    }

    @Test
    void testFindByParticipantSessionAndApparatus_WrongApparatus_NotFound() {
        Optional<Score> found = scoreRepository.findByParticipantAndSessionAndApparatus(
                testParticipant, testSession, Apparatus.BB);

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindBySession_ReturnsAllApparatusScoresInSession() {
        Score beamScore = new Score();
        beamScore.setParticipant(testParticipant);
        beamScore.setSession(testSession);
        beamScore.setApparatus(Apparatus.BB);
        beamScore.setStatus(ScoreStatus.COMPLETED);
        scoreRepository.save(beamScore);

        List<Score> found = scoreRepository.findBySession(testSession);

        assertEquals(2, found.size());
    }

    @Test
    void testFindBySessionId_MatchesFindBySession() {
        List<Score> found = scoreRepository.findBySessionId(testSession.getId());

        assertEquals(1, found.size());
        assertEquals(testScore.getId(), found.get(0).getId());
    }

    @Test
    void testFindByParticipant_ReturnsScoresAcrossSessions() {
        Session aaFinal = new Session();
        aaFinal.setEvent(testEvent);
        aaFinal.setDate(LocalDate.of(2026, 4, 23));
        aaFinal.setRound(Round.AA_FINAL);
        aaFinal.setDiscipline(Discipline.WAG);
        aaFinal.setAgeCategory(AgeCategory.SR);
        aaFinal = sessionRepository.save(aaFinal);

        Score aaVaultScore = new Score();
        aaVaultScore.setParticipant(testParticipant);
        aaVaultScore.setSession(aaFinal);
        aaVaultScore.setApparatus(Apparatus.VT);
        aaVaultScore.setStatus(ScoreStatus.COMPLETED);
        scoreRepository.save(aaVaultScore);

        List<Score> found = scoreRepository.findByParticipant(testParticipant);

        assertEquals(2, found.size());
    }

    @Test
    void testCreate_FailsBecauseDuplicateParticipantSessionAndApparatus() {
        Score duplicate = new Score();
        duplicate.setParticipant(testParticipant);
        duplicate.setSession(testSession);
        duplicate.setApparatus(Apparatus.VT);
        duplicate.setStatus(ScoreStatus.COMPLETED);

        assertThrows(DataIntegrityViolationException.class, () -> scoreRepository.saveAndFlush(duplicate));
    }

    private Event savedEvent() {
        Event event = new Event();
        event.setTitle("2026 European Championships");
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setDisciplines(Set.of(Discipline.WAG));
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        return eventRepository.save(event);
    }
}
