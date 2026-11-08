package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.ApparatusResult;
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
class ApparatusResultRepositoryTest {

    @Autowired
    private ApparatusResultRepository apparatusResultRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    private Session wholeRound;
    private Participant testParticipant;
    private ApparatusResult testResult;

    @BeforeEach
    void setUp() {
        Event event = new Event();
        event.setTitle("2026 Xfinity U.S. Gymnastics Championships");
        event.setStartDate(LocalDate.of(2026, 8, 6));
        event.setEndDate(LocalDate.of(2026, 8, 8));
        event.setCity("Phoenix");
        event.setCountryCode("USA");
        event.setLevel(EventLevel.NATIONAL_CHAMPIONSHIPS);
        event = eventRepository.save(event);

        wholeRound = new Session();
        wholeRound.setEvent(event);
        wholeRound.setDate(LocalDate.of(2026, 8, 6));
        wholeRound.setRound(Round.AA);
        wholeRound.setDiscipline(Discipline.MAG);
        wholeRound.setAgeCategory(AgeCategory.SR);
        wholeRound = sessionRepository.save(wholeRound);

        Athlete athlete = new Athlete();
        athlete.setDiscipline(Discipline.MAG);
        athlete.setSurname("Richard");
        athlete.setGivenName("Fred");
        athlete = athleteRepository.save(athlete);

        testParticipant = new Participant();
        testParticipant.setEvent(event);
        testParticipant.setAthlete(athlete);
        testParticipant.setNoc("USA");
        testParticipant = participantRepository.save(testParticipant);

        testResult = new ApparatusResult();
        testResult.setParticipant(testParticipant);
        testResult.setSession(wholeRound);
        testResult.setApparatus(Apparatus.FX);
        testResult.setTotalScore(28.916);
        testResult.setRank(1);
        testResult = apparatusResultRepository.save(testResult);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<ApparatusResult> found = apparatusResultRepository.findById(testResult.getId());

        assertTrue(found.isPresent());
        assertEquals(testParticipant.getId(), found.get().getParticipant().getId());
        assertEquals(wholeRound.getId(), found.get().getSession().getId());
        assertEquals(Apparatus.FX, found.get().getApparatus());
        assertEquals(28.916, found.get().getTotalScore());
        assertEquals(1, found.get().getRank());
    }

    @Test
    void testFindByParticipantSessionAndApparatus_Found() {
        Optional<ApparatusResult> found = apparatusResultRepository.findByParticipantAndSessionAndApparatus(
                testParticipant, wholeRound, Apparatus.FX);

        assertTrue(found.isPresent());
        assertEquals(testResult.getId(), found.get().getId());
    }

    @Test
    void testFindBySessionAndApparatus_OnlyThatApparatus() {
        ApparatusResult pommel = new ApparatusResult();
        pommel.setParticipant(testParticipant);
        pommel.setSession(wholeRound);
        pommel.setApparatus(Apparatus.PH);
        pommel.setTotalScore(28.068);
        apparatusResultRepository.save(pommel);

        List<ApparatusResult> found = apparatusResultRepository.findBySessionAndApparatus(wholeRound, Apparatus.FX);

        assertEquals(1, found.size());
        assertEquals(Apparatus.FX, found.get(0).getApparatus());
        assertEquals(2, apparatusResultRepository.findByParticipant(testParticipant).size());
    }

    @Test
    void testCreate_FailsBecauseDuplicateParticipantSessionAndApparatus() {
        ApparatusResult duplicate = new ApparatusResult();
        duplicate.setParticipant(testParticipant);
        duplicate.setSession(wholeRound);
        duplicate.setApparatus(Apparatus.FX);

        assertThrows(DataIntegrityViolationException.class, () -> apparatusResultRepository.saveAndFlush(duplicate));
    }
}
