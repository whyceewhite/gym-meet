package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.BonusDetail;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.QualificationResult;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Routine;
import doubletuck.meet.domain.Score;
import doubletuck.meet.domain.ScoreStatus;
import doubletuck.meet.domain.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolationException;
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
        testSession.setRound(Round.QUAL);
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
        Routine vault = new Routine(ScoreStatus.COMPLETED);
        vault.setDifficulty(5.0);
        vault.setExecution(9.033);
        vault.setScore(14.033);
        testScore.setRoutine(vault);
        testScore.setTotalScore(14.033);
        testScore = scoreRepository.save(testScore);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Score> found = scoreRepository.findById(testScore.getId());

        assertTrue(found.isPresent());
        assertEquals(testParticipant.getId(), found.get().getParticipant().getId());
        assertEquals(testSession.getId(), found.get().getSession().getId());
        assertEquals(Apparatus.VT, found.get().getApparatus());
        assertEquals(5.0, found.get().getRoutine().getDifficulty());
        assertEquals(9.033, found.get().getRoutine().getExecution());
        assertEquals(14.033, found.get().getRoutine().getScore());
        assertEquals(ScoreStatus.COMPLETED, found.get().getRoutine().getStatus());
        assertEquals(14.033, found.get().getTotalScore());
        assertFalse(found.get().getRoutine().getDifficultyInquiry());
        assertNull(found.get().getSecondRoutine());
        assertTrue(found.get().getCountsTowardTeamResult());
    }

    @Test
    void testSave_PersistsDifficultyInquiry() {
        testScore.getRoutine().setDifficultyInquiry(true);
        scoreRepository.saveAndFlush(testScore);

        Score found = scoreRepository.findById(testScore.getId()).orElseThrow();

        assertTrue(found.getRoutine().getDifficultyInquiry());
    }

    @Test
    void testSave_PersistsBonusDetailAsJson() {
        Score beamScore = new Score();
        beamScore.setParticipant(testParticipant);
        beamScore.setSession(testSession);
        beamScore.setApparatus(Apparatus.BB);
        Routine beam = new Routine(ScoreStatus.COMPLETED);
        beam.addBonusDetail(new BonusDetail(0.1, "Difficulty bonus"));
        beam.addBonusDetail(new BonusDetail(0.05, "Connection bonus"));
        beamScore.setRoutine(beam);
        beamScore = scoreRepository.save(beamScore);

        Score found = scoreRepository.findById(beamScore.getId()).orElseThrow();

        assertEquals(2, found.getRoutine().getBonusDetail().size());
        assertEquals(0.1, found.getRoutine().getBonusDetail().get(0).getBonus());
        assertEquals("Difficulty bonus", found.getRoutine().getBonusDetail().get(0).getDescription());
    }

    @Test
    void testSave_TwoVaultEntryPersistsBothRoutinesAndResult() {
        scoreRepository.delete(testScore);
        scoreRepository.flush();

        Score twoVaults = new Score();
        twoVaults.setParticipant(testParticipant);
        twoVaults.setSession(testSession);
        twoVaults.setApparatus(Apparatus.VT);

        Routine first = new Routine(ScoreStatus.COMPLETED);
        first.setDifficulty(5.6);
        first.setExecution(8.533);
        first.setPenalty(-0.1);
        first.setScore(14.033);
        twoVaults.setRoutine(first);

        Routine second = new Routine(ScoreStatus.COMPLETED);
        second.setDifficulty(5.0);
        second.setExecution(9.133);
        second.setSjPenalty(-0.5);
        second.setScore(13.633);
        second.addBonusDetail(new BonusDetail(0.1, "Landing bonus"));
        twoVaults.setSecondRoutine(second);

        twoVaults.setCombinedBonus(0.2);
        twoVaults.setTotalScore(14.033);
        twoVaults.setRank(14);
        twoVaults.setQualificationRank(3);
        twoVaults.setQualificationResult(QualificationResult.Q);
        twoVaults = scoreRepository.saveAndFlush(twoVaults);

        Score found = scoreRepository.findById(twoVaults.getId()).orElseThrow();

        assertEquals(14.033, found.getRoutine().getScore());
        assertEquals(-0.1, found.getRoutine().getPenalty());
        assertNull(found.getRoutine().getSjPenalty());
        assertNotNull(found.getSecondRoutine());
        assertEquals(5.0, found.getSecondRoutine().getDifficulty());
        assertEquals(-0.5, found.getSecondRoutine().getSjPenalty());
        assertEquals(13.633, found.getSecondRoutine().getScore());
        assertEquals(ScoreStatus.COMPLETED, found.getSecondRoutine().getStatus());
        assertEquals("Landing bonus", found.getSecondRoutine().getBonusDetail().get(0).getDescription());
        assertEquals(0.2, found.getCombinedBonus());
        assertEquals(14, found.getRank());
        assertEquals(3, found.getQualificationRank());
        assertEquals(QualificationResult.Q, found.getQualificationResult());
    }

    @Test
    void testSave_SecondVaultNotStartedLoadsAsDnsRoutine() {
        testScore.setSecondRoutine(new Routine(ScoreStatus.DNS));
        scoreRepository.saveAndFlush(testScore);

        Score found = scoreRepository.findById(testScore.getId()).orElseThrow();

        assertNotNull(found.getSecondRoutine());
        assertEquals(ScoreStatus.DNS, found.getSecondRoutine().getStatus());
        assertNull(found.getSecondRoutine().getScore());
    }

    @Test
    void testCreate_FailsBecauseRoutineMissing() {
        Score noRoutine = new Score();
        noRoutine.setParticipant(testParticipant);
        noRoutine.setSession(testSession);
        noRoutine.setApparatus(Apparatus.BB);

        assertThrows(ConstraintViolationException.class, () -> scoreRepository.saveAndFlush(noRoutine));
    }

    @Test
    void testCreate_FailsBecauseSecondRoutineStatusMissing() {
        Routine second = new Routine();
        second.setScore(13.0);
        testScore.setSecondRoutine(second);

        assertThrows(ConstraintViolationException.class, () -> scoreRepository.saveAndFlush(testScore));
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
        beamScore.setRoutine(new Routine(ScoreStatus.COMPLETED));
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
        aaFinal.setRound(Round.AA);
        aaFinal.setDiscipline(Discipline.WAG);
        aaFinal.setAgeCategory(AgeCategory.SR);
        aaFinal = sessionRepository.save(aaFinal);

        Score aaVaultScore = new Score();
        aaVaultScore.setParticipant(testParticipant);
        aaVaultScore.setSession(aaFinal);
        aaVaultScore.setApparatus(Apparatus.VT);
        aaVaultScore.setRoutine(new Routine(ScoreStatus.COMPLETED));
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
        duplicate.setRoutine(new Routine(ScoreStatus.COMPLETED));

        assertThrows(DataIntegrityViolationException.class, () -> scoreRepository.saveAndFlush(duplicate));
    }

    private Event savedEvent() {
        Event event = new Event();
        event.setTitle("2026 European Championships");
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("GER");
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        return eventRepository.save(event);
    }
}
