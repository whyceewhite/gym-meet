package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Judge;
import doubletuck.meet.domain.JudgeFunction;
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
class JudgeRepositoryTest {

    @Autowired
    private JudgeRepository judgeRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private Session testSession;
    private Judge testJudge;

    @BeforeEach
    void setUp() {
        Event event = new Event();
        event.setTitle("2026 European Championships");
        event.setStartDate(LocalDate.of(2026, 8, 13));
        event.setEndDate(LocalDate.of(2026, 8, 18));
        event.setCity("Stuttgart");
        event.setCountryCode("DEU");
        event.setLevel(EventLevel.CONTINENTAL_CHAMPIONSHIPS);
        event = eventRepository.save(event);

        testSession = new Session();
        testSession.setEvent(event);
        testSession.setDate(LocalDate.of(2026, 8, 13));
        testSession.setRound(Round.QUAL);
        testSession.setDiscipline(Discipline.WAG);
        testSession.setAgeCategory(AgeCategory.SR);
        testSession = sessionRepository.save(testSession);

        testJudge = new Judge();
        testJudge.setSession(testSession);
        testJudge.setApparatus(Apparatus.VT);
        testJudge.setFunction(JudgeFunction.D1);
        testJudge.setGivenName("Anca");
        testJudge.setSurname("MIHAILESCU GRIGORAS");
        testJudge.setNocCode("ROU");
        testJudge = judgeRepository.save(testJudge);
    }

    @Test
    void testSave_PersistsAllFields() {
        Optional<Judge> found = judgeRepository.findById(testJudge.getId());

        assertTrue(found.isPresent());
        assertEquals(testSession.getId(), found.get().getSession().getId());
        assertEquals(Apparatus.VT, found.get().getApparatus());
        assertEquals(JudgeFunction.D1, found.get().getFunction());
        assertEquals("Anca", found.get().getGivenName());
        assertEquals("MIHAILESCU GRIGORAS", found.get().getSurname());
        assertEquals("ROU", found.get().getNocCode());
    }

    @Test
    void testSave_NocCodeNotThreeLetters_Succeeds() {
        Judge presidentJudge = new Judge();
        presidentJudge.setSession(testSession);
        presidentJudge.setFunction(JudgeFunction.PRESIDENT);
        presidentJudge.setGivenName("Mairead Geraldine");
        presidentJudge.setSurname("KAVANAGH");
        presidentJudge.setNocCode("EG");
        presidentJudge = judgeRepository.save(presidentJudge);

        Optional<Judge> found = judgeRepository.findById(presidentJudge.getId());

        assertTrue(found.isPresent());
        assertEquals("EG", found.get().getNocCode());
        assertNull(found.get().getApparatus());
    }

    @Test
    void testFindBySession_ReturnsAllJudgesForSession() {
        Judge secondJudge = new Judge();
        secondJudge.setSession(testSession);
        secondJudge.setApparatus(Apparatus.UB);
        secondJudge.setFunction(JudgeFunction.D1);
        secondJudge.setGivenName("Christine");
        secondJudge.setSurname("FRAUENKNECHT");
        secondJudge.setNocCode("LIE");
        judgeRepository.save(secondJudge);

        List<Judge> found = judgeRepository.findBySession(testSession);

        assertEquals(2, found.size());
    }

    @Test
    void testFindBySessionId_MatchesFindBySession() {
        List<Judge> found = judgeRepository.findBySessionId(testSession.getId());

        assertEquals(1, found.size());
        assertEquals(testJudge.getId(), found.get(0).getId());
    }

    @Test
    void testFindBySession_MultipleMembersShareFunctionAndNullApparatus() {
        Judge member1 = new Judge();
        member1.setSession(testSession);
        member1.setFunction(JudgeFunction.MEMBER);
        member1.setGivenName("Maria");
        member1.setSurname("COCUZZA");
        member1.setNocCode("EG");
        judgeRepository.save(member1);

        Judge member2 = new Judge();
        member2.setSession(testSession);
        member2.setFunction(JudgeFunction.MEMBER);
        member2.setGivenName("Fabienne");
        member2.setSurname("RINGLER");
        member2.setNocCode("EG");
        judgeRepository.save(member2);

        List<Judge> found = judgeRepository.findBySession(testSession);

        assertEquals(3, found.size());
    }

    @Test
    void testCreate_FailsBecauseDuplicateSessionApparatusFunctionAndName() {
        Judge duplicate = new Judge();
        duplicate.setSession(testSession);
        duplicate.setApparatus(Apparatus.VT);
        duplicate.setFunction(JudgeFunction.D1);
        duplicate.setGivenName("Anca");
        duplicate.setSurname("MIHAILESCU GRIGORAS");
        duplicate.setNocCode("ROU");

        assertThrows(DataIntegrityViolationException.class, () -> judgeRepository.saveAndFlush(duplicate));
    }
}
