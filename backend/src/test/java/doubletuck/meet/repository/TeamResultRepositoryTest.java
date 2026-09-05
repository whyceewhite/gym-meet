package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.QualificationResult;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import doubletuck.meet.domain.TeamResult;
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
class TeamResultRepositoryTest {

    @Autowired
    private TeamResultRepository teamResultRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private Session testSession;
    private TeamResult testTeamResult;

    @BeforeEach
    void setUp() {
        Event event = new Event();
        event.setTitle("2026 European Championships");
        event.setStartDate(LocalDate.of(2026, 4, 20));
        event.setEndDate(LocalDate.of(2026, 4, 26));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setDisciplines(Set.of(Discipline.WAG));
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        event = eventRepository.save(event);

        testSession = new Session();
        testSession.setEvent(event);
        testSession.setDate(LocalDate.of(2026, 4, 22));
        testSession.setRound(Round.TEAM_FINAL);
        testSession.setDiscipline(Discipline.WAG);
        testSession.setAgeCategory(AgeCategory.SR);
        testSession = sessionRepository.save(testSession);

        testTeamResult = new TeamResult();
        testTeamResult.setSession(testSession);
        testTeamResult.setNoc("USA");
        testTeamResult.setTotalScore(168.5);
        testTeamResult.setRank(1);
        testTeamResult = teamResultRepository.save(testTeamResult);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<TeamResult> found = teamResultRepository.findById(testTeamResult.getId());

        assertTrue(found.isPresent());
        assertEquals(testSession.getId(), found.get().getSession().getId());
        assertEquals("USA", found.get().getNoc());
        assertEquals(168.5, found.get().getTotalScore());
        assertEquals(1, found.get().getRank());
    }

    @Test
    void testFindBySessionAndNoc_Found() {
        Optional<TeamResult> found = teamResultRepository.findBySessionAndNoc(testSession, "USA");

        assertTrue(found.isPresent());
        assertEquals(testTeamResult.getId(), found.get().getId());
    }

    @Test
    void testFindBySessionAndNoc_WrongNoc_NotFound() {
        Optional<TeamResult> found = teamResultRepository.findBySessionAndNoc(testSession, "GBR");

        assertTrue(found.isEmpty());
    }

    @Test
    void testFindBySession_ReturnsAllTeamsInSession() {
        TeamResult secondTeam = new TeamResult();
        secondTeam.setSession(testSession);
        secondTeam.setNoc("GBR");
        secondTeam.setTotalScore(165.0);
        secondTeam.setRank(2);
        teamResultRepository.save(secondTeam);

        List<TeamResult> found = teamResultRepository.findBySession(testSession);

        assertEquals(2, found.size());
    }

    @Test
    void testFindBySessionId_MatchesFindBySession() {
        List<TeamResult> found = teamResultRepository.findBySessionId(testSession.getId());

        assertEquals(1, found.size());
        assertEquals(testTeamResult.getId(), found.get(0).getId());
    }

    @Test
    void testSave_WithQualificationResult() {
        TeamResult qualified = new TeamResult();
        qualified.setSession(testSession);
        qualified.setNoc("FRA");
        qualified.setQualificationResult(QualificationResult.Q);
        qualified = teamResultRepository.save(qualified);

        TeamResult found = teamResultRepository.findById(qualified.getId()).orElseThrow();

        assertEquals(QualificationResult.Q, found.getQualificationResult());
        assertNull(found.getTotalScore());
    }

    @Test
    void testCreate_FailsBecauseDuplicateSessionAndNoc() {
        TeamResult duplicate = new TeamResult();
        duplicate.setSession(testSession);
        duplicate.setNoc("USA");

        assertThrows(DataIntegrityViolationException.class, () -> teamResultRepository.saveAndFlush(duplicate));
    }
}
