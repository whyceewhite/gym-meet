package doubletuck.meet.importer;

import doubletuck.meet.domain.AllAroundResult;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.ApparatusResult;
import doubletuck.meet.domain.BonusDetail;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Judge;
import doubletuck.meet.domain.JudgeFunction;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.QualificationResult;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Score;
import doubletuck.meet.domain.ScoreStatus;
import doubletuck.meet.domain.Session;
import doubletuck.meet.domain.TeamResult;
import doubletuck.meet.repository.AllAroundResultRepository;
import doubletuck.meet.repository.ApparatusResultRepository;
import doubletuck.meet.repository.EventRepository;
import doubletuck.meet.repository.JudgeRepository;
import doubletuck.meet.repository.ParticipantRepository;
import doubletuck.meet.repository.ScoreRepository;
import doubletuck.meet.repository.SessionRepository;
import doubletuck.meet.repository.TeamResultRepository;
import doubletuck.meet.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Imports fixtures cut down from the real results files (a few athletes each,
 * otherwise unchanged) and checks what was stored against the published results.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({ EventImporter.class, ParticipantImporter.class, ScoreImporter.class, SummaryImporter.class,
        JudgeImporter.class, ResultReconciler.class, AthleteMatcher.class, SessionService.class })
class EventImporterTest {

    private static final String WORLDS_WAG = "worlds/2026-WORLD-CHAMPIONSHIPS-WAG-SR-IMPORT.json";
    private static final String WORLDS_MAG = "worlds/2026-WORLD-CHAMPIONSHIPS-MAG-SR-IMPORT.json";
    private static final String COTTBUS_WAG = "cottbus/2026-APPARATUS-WORLD-CUP-COTTBUS-WAG-SR-IMPORT.json";
    private static final String US_MAG = "us/2026-US-CHAMPIONSHIPS-MAG-SR-IMPORT.json";
    private static final String EUROPEANS_MAG = "europeans/2026-EUROPEAN-CHAMPIONSHIPS-MAG-SR-IMPORT.json";

    @Autowired
    private EventImporter eventImporter;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ScoreRepository scoreRepository;

    @Autowired
    private TeamResultRepository teamResultRepository;

    @Autowired
    private AllAroundResultRepository allAroundResultRepository;

    @Autowired
    private ApparatusResultRepository apparatusResultRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Test
    void testImport_BothDisciplinesShareOneEvent() {
        importFixture(WORLDS_WAG);
        importFixture(WORLDS_MAG);

        List<Event> events = eventRepository.findAll();
        assertEquals(1, events.size());
        Event worlds = events.get(0);
        assertEquals("54th Artistic Gymnastics World Championships", worlds.getTitle());
        assertEquals("17247", worlds.getFigEventNumber());
        assertEquals(LocalDate.of(2026, 10, 17), worlds.getStartDate());
        assertEquals("NED", worlds.getCountryCode());
        assertEquals("Spieth", worlds.getApparatusSuppliers());
        assertEquals(EventLevel.WORLD_CHAMPIONSHIPS, worlds.getLevel());
        assertTrue(sessionRepository.findByEvent(worlds).stream().anyMatch(s -> s.getDiscipline() == Discipline.WAG));
        assertTrue(sessionRepository.findByEvent(worlds).stream().anyMatch(s -> s.getDiscipline() == Discipline.MAG));
    }

    @Test
    void testImport_StoresEveryScoreRowWithoutWarnings() {
        ImportReport report = importFixture(WORLDS_WAG);

        assertEquals(rowCount(WORLDS_WAG, ImportManifest::scores), scoreRepository.count());
        assertEquals(rowCount(WORLDS_WAG, ImportManifest::participants), participantRepository.count());
        assertEquals(List.of(), report.getWarnings());
    }

    @Test
    void testImport_CreatesOneSessionPerRoundAndPerApparatusFinal() {
        importFixture(WORLDS_WAG);

        List<Session> sessions = sessionRepository.findAll();
        assertEquals(LocalDate.of(2026, 10, 18), session(sessions, Round.QUAL, null).getDate());
        assertEquals(LocalDate.of(2026, 10, 21), session(sessions, Round.TEAM, null).getDate());
        assertEquals(LocalDate.of(2026, 10, 23), session(sessions, Round.AA, null).getDate());
        assertEquals(LocalDate.of(2026, 10, 25), session(sessions, Round.EVENT, Apparatus.BB).getDate());
        assertTrue(sessions.stream().allMatch(s -> s.getPart() == null));
    }

    @Test
    void testImport_ScoreFieldsAsPublished() {
        importFixture(WORLDS_WAG);

        Score zhang = score("Zhang", "Qingying", Round.QUAL, Apparatus.BB);
        assertEquals(6.8, zhang.getRoutine().getDifficulty());
        assertEquals(8.3, zhang.getRoutine().getExecution());
        assertEquals(15.1, zhang.getRoutine().getScore());
        assertEquals(15.1, zhang.getTotalScore());
        assertEquals(ScoreStatus.COMPLETED, zhang.getRoutine().getStatus());
        assertFalse(zhang.getRoutine().getDifficultyInquiry());
        assertEquals(1, zhang.getRank());
        assertEquals(1, zhang.getQualificationRank());
        assertEquals(QualificationResult.Q, zhang.getQualificationResult());
        assertNull(zhang.getSecondRoutine());

        assertEquals(QualificationResult.NR, score("Ke", "Qinqin", Round.QUAL, Apparatus.BB).getQualificationResult());
    }

    @Test
    void testImport_TwoVaultEntry() {
        importFixture(WORLDS_MAG);

        Score jarman = score("Jarman", "Jake", Round.QUAL, Apparatus.VT);
        assertEquals(5.6, jarman.getRoutine().getDifficulty());
        assertEquals(14.9, jarman.getRoutine().getScore());
        assertEquals(5.2, jarman.getSecondRoutine().getDifficulty());
        assertEquals(14.366, jarman.getSecondRoutine().getScore());
        assertEquals(ScoreStatus.COMPLETED, jarman.getSecondRoutine().getStatus());
        assertEquals(14.633, jarman.getTotalScore());
        assertEquals(1, jarman.getRank());
        assertEquals(1, jarman.getQualificationRank());
        assertEquals(QualificationResult.Q, jarman.getQualificationResult());
    }

    @Test
    void testImport_DidNotStart() {
        importFixture(WORLDS_MAG);

        Score tulloch = score("Tulloch", "Courtney", Round.QUAL, Apparatus.PH);
        assertEquals(ScoreStatus.DNS, tulloch.getRoutine().getStatus());
        assertNull(tulloch.getRoutine().getScore());
        assertNull(tulloch.getTotalScore());
        assertNull(tulloch.getRank());
    }

    @Test
    void testImport_MarksScoresDroppedFromTeamTotal() {
        importFixture(WORLDS_WAG);

        assertTrue(score("Zhang", "Qingying", Round.QUAL, Apparatus.BB).getCountsTowardTeamResult());
        assertTrue(score("Qiu", "Qiyuan", Round.QUAL, Apparatus.BB).getCountsTowardTeamResult());
        assertTrue(score("Ke", "Qinqin", Round.QUAL, Apparatus.BB).getCountsTowardTeamResult());
        assertFalse(score("Zhang", "Yihan", Round.QUAL, Apparatus.BB).getCountsTowardTeamResult());
    }

    @Test
    void testImport_IndividualEnteredAlongsideTeam_DoesNotCountTowardTeam() {
        ImportReport report = importFixture(EUROPEANS_MAG);

        // MURABITO competed as an individual: the best floor score, but not the team's
        assertFalse(score("Murabito", "Luca", Round.QUAL, Apparatus.FX).getCountsTowardTeamResult());
        assertTrue(score("Langenegger", "Florian", Round.QUAL, Apparatus.FX).getCountsTowardTeamResult());
        assertEquals(List.of(), report.getWarnings());
    }

    @Test
    void testImport_NamesStoredInWrittenForm() {
        importFixture(WORLDS_WAG);

        assertTrue(participantRepository.findAll().stream().map(p -> p.getAthlete().getSurname())
                .anyMatch("Hamames-Moallic"::equals));
        assertTrue(judgeRepository.findAll().stream().noneMatch(judge -> judge.getSurname().matches(".*\\p{Lu}{2}.*")));
    }

    @Test
    void testImport_TeamAndAllAroundResults() {
        importFixture(WORLDS_WAG);
        List<Session> sessions = sessionRepository.findAll();

        TeamResult china = teamResultRepository.findBySessionAndNoc(session(sessions, Round.QUAL, null), "CHN")
                .orElseThrow();
        assertEquals(1, china.getRank());
        assertEquals(164.996, china.getTotalScore());
        assertEquals(QualificationResult.Q, china.getQualificationResult());

        AllAroundResult ke = allAroundResultRepository.findBySession(session(sessions, Round.AA, null)).stream()
                .filter(result -> result.getParticipant().getAthlete().getSurname().equals("Ke"))
                .findFirst().orElseThrow();
        assertEquals(3, ke.getRank());
        assertEquals(55.032, ke.getTotalScore());
    }

    @Test
    void testImport_FinalsSuperiorJuryServesEveryApparatusFinal() {
        importFixture(WORLDS_WAG);

        List<Session> finals = sessionRepository.findAll().stream().filter(s -> s.getRound() == Round.EVENT).toList();
        assertFalse(finals.isEmpty());
        for (Session apparatusFinal : finals) {
            List<Judge> judges = judgeRepository.findBySession(apparatusFinal);
            assertTrue(judges.stream().anyMatch(judge -> judge.getFunction() == JudgeFunction.VICE_PRESIDENT
                    && judge.getApparatus() == null), "no Vice-President on " + apparatusFinal.getApparatus());
        }
    }

    @Test
    void testImport_ApparatusFormatQualificationHasOneSessionPerApparatus() {
        importFixture(COTTBUS_WAG);

        List<Session> qualifications = sessionRepository.findAll().stream()
                .filter(s -> s.getRound() == Round.QUAL).toList();
        assertEquals(4, qualifications.size());
        assertTrue(qualifications.stream().allMatch(s -> s.getApparatus() != null));
        assertEquals(LocalDate.of(2026, 2, 19), session(qualifications, Round.QUAL, Apparatus.UB).getDate());
        assertEquals(LocalDate.of(2026, 2, 20), session(qualifications, Round.QUAL, Apparatus.BB).getDate());
    }

    @Test
    void testImport_SplitRoundHasPartSessionsAndWholeRoundSession() {
        ImportReport report = importFixture(US_MAG);
        List<Session> sessions = sessionRepository.findAll();

        assertEquals(3, sessions.size());
        Session wholeRound = sessions.stream().filter(s -> s.getPart() == null).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 8, 6), wholeRound.getDate());
        assertTrue(scoreRepository.findBySession(wholeRound).isEmpty());

        AllAroundResult moldauer = allAroundResultRepository.findBySession(wholeRound).stream()
                .filter(result -> result.getParticipant().getAthlete().getSurname().equals("Moldauer"))
                .findFirst().orElseThrow();
        assertEquals(5, moldauer.getRank());
        assertEquals(163.896, moldauer.getTotalScore());

        ApparatusResult hoopes = apparatusResultRepository.findBySessionAndApparatus(wholeRound, Apparatus.PH)
                .stream().filter(result -> result.getParticipant().getAthlete().getSurname().equals("Hoopes"))
                .findFirst().orElseThrow();
        assertEquals(30.816, hoopes.getTotalScore());
        assertEquals(List.of(), report.getWarnings());
    }

    @Test
    void testImport_NationalEntryWithClubAndNoBirthYear() {
        importFixture(US_MAG);

        Participant hoopes = participantRepository.findAll().stream()
                .filter(p -> p.getAthlete().getSurname().equals("Hoopes")).findFirst().orElseThrow();
        assertEquals("Air Force", hoopes.getClub().getName());
        assertEquals("USA", hoopes.getClub().getCountry());
        assertNull(hoopes.getAthlete().getBirthYear());
        assertNull(hoopes.getAthlete().getLicenseNumber());
    }

    @Test
    void testImport_ItemizedBonusBecomesBonusDetail() {
        importFixture(US_MAG);

        Score burkhart = scoreRepository.findAll().stream()
                .filter(s -> s.getParticipant().getAthlete().getSurname().equals("Burkhart")
                        && s.getApparatus() == Apparatus.VT && s.getSession().getPart() == 1)
                .findFirst().orElseThrow();
        assertEquals(0.217, burkhart.getRoutine().getBonus());
        List<BonusDetail> details = burkhart.getRoutine().getBonusDetail();
        assertEquals("Difficulty bonus", details.get(0).getDescription());
        assertEquals(0.217, details.get(0).getBonus());
        assertEquals("Execution bonus", details.get(1).getDescription());
    }

    @Test
    void testImport_AlreadyImportedWithoutReplace_Fails() {
        importFixture(WORLDS_WAG);

        ImportException e = assertThrows(ImportException.class, () -> importFixture(WORLDS_WAG));
        assertTrue(e.getMessage().contains("already imported"), e.getMessage());
    }

    @Test
    void testImport_ReplaceReimportsWithoutDuplicates() {
        importFixture(WORLDS_WAG);
        long scores = scoreRepository.count();
        long participants = participantRepository.count();
        long sessions = sessionRepository.count();

        eventImporter.importManifest(fixture(WORLDS_WAG), true);

        assertEquals(scores, scoreRepository.count());
        assertEquals(participants, participantRepository.count());
        assertEquals(sessions, sessionRepository.count());
    }

    @Test
    void testImport_ReplaceKeepsTheOtherDisciplineSharingTheEvent() {
        importFixture(WORLDS_MAG);
        long magScores = scoreRepository.count();
        importFixture(WORLDS_WAG);

        eventImporter.importManifest(fixture(WORLDS_WAG), true);

        assertEquals(magScores, scoreRepository.findAll().stream()
                .filter(s -> s.getSession().getDiscipline() == Discipline.MAG).count());
    }

    @Test
    void testImport_TeamTotalThatDoesNotAddUp_WarnsAndLeavesScoresCounting(@TempDir Path dir) throws IOException {
        Path manifest = copyFixture(WORLDS_WAG, dir);
        Path teamSummary = dir.resolve("2026-WORLD-CHAMPIONSHIPS-WAG-SR-SUMMARY-QUAL-TEAM.csv");
        Files.writeString(teamSummary, Files.readString(teamSummary).replace(",43.966,", ",43.000,"));

        ImportReport report = eventImporter.importManifest(manifest, false);

        assertEquals(1, report.getWarnings().size());
        assertTrue(report.getWarnings().get(0).contains("CHN team: no group of its athletes adds up"),
                report.getWarnings().get(0));
        assertTrue(report.getWarnings().get(0).contains("BB 43.0"), report.getWarnings().get(0));
        assertTrue(score("Zhang", "Yihan", Round.QUAL, Apparatus.BB).getCountsTowardTeamResult());
    }

    @Test
    void testImport_DifficultyInquiryAndNocPrintedOnlyOnFirstRow(@TempDir Path dir) throws IOException {
        Path manifest = writeMinimalMeet(dir, "!5.5", "101");

        eventImporter.importManifest(manifest, false);

        Score score = scoreRepository.findAll().get(0);
        assertTrue(score.getRoutine().getDifficultyInquiry());
        assertEquals(5.5, score.getRoutine().getDifficulty());
        assertTrue(participantRepository.findAll().stream().allMatch(p -> p.getNoc().equals("NED")));
    }

    @Test
    void testImport_ScoreForUnknownBib_Fails(@TempDir Path dir) throws IOException {
        Path manifest = writeMinimalMeet(dir, "5.5", "999");

        ImportException e = assertThrows(ImportException.class, () -> eventImporter.importManifest(manifest, false));
        assertTrue(e.getMessage().contains("MINI-MAG-SR-SCORES-QUAL-FX.csv line 2: bib 999"), e.getMessage());
    }

    private ImportReport importFixture(String manifest) {
        return eventImporter.importManifest(fixture(manifest), false);
    }

    private Path fixture(String manifest) {
        try {
            return Path.of(getClass().getResource("/import/" + manifest).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path copyFixture(String manifest, Path dir) throws IOException {
        Path source = fixture(manifest);
        try (var files = Files.list(source.getParent())) {
            for (Path file : files.toList()) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
        }
        return dir.resolve(source.getFileName());
    }

    private long rowCount(String manifest, Function<ImportManifest, List<String>> files) {
        Path path = fixture(manifest);
        return files.apply(ImportManifest.read(path)).stream()
                .mapToLong(file -> CsvRow.read(path.getParent().resolve(file)).size()).sum();
    }

    private Score score(String surname, String givenName, Round round, Apparatus apparatus) {
        return scoreRepository.findAll().stream()
                .filter(s -> s.getParticipant().getAthlete().getSurname().equals(surname)
                        && s.getParticipant().getAthlete().getGivenName().equals(givenName)
                        && s.getSession().getRound() == round && s.getApparatus() == apparatus)
                .findFirst().orElseThrow(() -> new AssertionError("no score for " + surname + " " + givenName));
    }

    private static Session session(List<Session> sessions, Round round, Apparatus apparatus) {
        return sessions.stream().filter(s -> s.getRound() == round && s.getApparatus() == apparatus).findFirst()
                .orElseThrow(() -> new AssertionError("no " + round + " " + apparatus + " session"));
    }

    // Two NED athletes, the NOC printed only on the first, and one floor score
    private static Path writeMinimalMeet(Path dir, String difficulty, String scoredBib) throws IOException {
        Files.writeString(dir.resolve("MINI-MAG-SR-IMPORT.json"), """
                {
                    "event": { "title": "Mini Meet", "disciplines": "MAG", "ageCategory": "SR",
                               "level": "INTERNATIONAL_TOURNAMENTS", "startDate": "2026-03-01",
                               "endDate": "2026-03-01", "city": "Utrecht", "country": "NED" },
                    "participants": [ "MINI-MAG-SR-PARTICIPANTS.csv" ],
                    "scores": [ "MINI-MAG-SR-SCORES-QUAL-FX.csv" ]
                }
                """);
        Files.writeString(dir.resolve("MINI-MAG-SR-PARTICIPANTS.csv"), """
                Event Name,Discipline,Age Category,Event Start Date,Order,NOC Code,Bib Number,FIG License,Name,Surname,Given Name,Birth Year
                Mini Meet,MAG,SR,2026-03-01,1,NED,101,,DE VRIES Jan,DE VRIES,Jan,2001
                Mini Meet,MAG,SR,2026-03-01,2,,102,,BAKKER Piet,BAKKER,Piet,2003
                """);
        Files.writeString(dir.resolve("MINI-MAG-SR-SCORES-QUAL-FX.csv"), """
                Event Name,Discipline,Age Category,Session Start Date,Round,Part,Apparatus,Rank,Bib,Name,NOC Code,Status,D,E,B,Penalty,SJ Penalty,Total
                Mini Meet,MAG,SR,2026-03-01,QUAL,,FX,1,%s,DE VRIES Jan,NED,COMPLETED,%s,8.500,,,,14.000
                """.formatted(scoredBib, difficulty));
        return dir.resolve("MINI-MAG-SR-IMPORT.json");
    }
}
