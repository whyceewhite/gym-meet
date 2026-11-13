package doubletuck.meet.importer;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.AllAroundResult;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.ApparatusResult;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Score;
import doubletuck.meet.domain.Session;
import doubletuck.meet.service.SessionService;
import lombok.Getter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * State shared by the steps importing one manifest: the event and slice
 * (discipline + age category) being imported, and the participants, sessions
 * and results created so far.
 */
@Getter
class ImportContext {

    record PublishedTeamTotal(Session session, String noc, Apparatus apparatus, double total, String location) {
    }

    private record SessionKey(Round round, Apparatus apparatus, Integer part) {
    }

    private final Path directory;
    private final ImportManifest manifest;
    private final Event event;
    private final Discipline discipline;
    private final AgeCategory ageCategory;
    private final ImportReport report;
    private final SessionService sessionService;

    /**
     * True for an apparatus-format meet (a World Cup), where each apparatus's
     * qualification is its own competition. Recognised by the absence of any
     * qualification summary: such meets have no team or all-around ranking.
     */
    private final boolean qualificationByApparatus;

    private final Map<String, Participant> participantsByBib = new HashMap<>();
    private final Map<SessionKey, Session> sessions = new LinkedHashMap<>();
    private final List<Score> scores = new ArrayList<>();
    private final List<AllAroundResult> allAroundResults = new ArrayList<>();
    private final List<ApparatusResult> apparatusResults = new ArrayList<>();
    private final List<PublishedTeamTotal> publishedTeamTotals = new ArrayList<>();

    ImportContext(Path directory, ImportManifest manifest, Event event, Discipline discipline,
            AgeCategory ageCategory, ImportReport report, SessionService sessionService) {
        this.directory = directory;
        this.manifest = manifest;
        this.event = event;
        this.discipline = discipline;
        this.ageCategory = ageCategory;
        this.report = report;
        this.sessionService = sessionService;
        this.qualificationByApparatus = manifest.summaries().stream()
                .noneMatch(file -> file.contains("-SUMMARY-QUAL-"));
    }

    Path file(String name) {
        return directory.resolve(name);
    }

    Participant participant(CsvRow row) {
        String bib = row.requiredText("Bib");
        Participant participant = participantsByBib.get(bib);
        if (participant == null) {
            throw row.error("bib " + bib + " is not in the participants file");
        }
        String noc = row.text("NOC Code");
        if (noc != null && !noc.equals(participant.getNoc())) {
            report.warn(row.location() + ": NOC " + noc + " differs from bib " + bib + "'s entry NOC "
                    + participant.getNoc());
        }
        return participant;
    }

    /**
     * The session a SCORES row was earned in, creating it the first time it's
     * seen. Scores are what establish a session and its date.
     */
    Session sessionForScores(Round round, Apparatus apparatus, Integer part, LocalDate date, CsvRow row) {
        SessionKey key = new SessionKey(round, sessionApparatus(round, apparatus), part);
        Session session = sessions.get(key);
        if (session == null) {
            session = create(key, date);
        } else if (!session.getDate().equals(date)) {
            report.warn(row.location() + ": session date " + date + " differs from " + session.getDate()
                    + " already recorded for the " + describe(key) + " session");
        }
        return session;
    }

    /**
     * The session a SUMMARY row belongs to. It must already exist from the
     * scores, except a split round's whole-round session (part null), which
     * holds only combined standings and is created here, dated on its first
     * part.
     */
    Session sessionForResults(Round round, Apparatus apparatus, Integer part, CsvRow row) {
        SessionKey key = new SessionKey(round, sessionApparatus(round, apparatus), part);
        Session session = sessions.get(key);
        if (session != null) {
            return session;
        }
        if (part == null) {
            LocalDate firstPartDate = sessions.entrySet().stream()
                    .filter(entry -> entry.getKey().round() == key.round()
                            && Objects.equals(entry.getKey().apparatus(), key.apparatus())
                            && entry.getKey().part() != null)
                    .map(entry -> entry.getValue().getDate())
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            if (firstPartDate != null) {
                return create(key, firstPartDate);
            }
        }
        throw row.error("no scores were imported for the " + describe(key) + " session this row belongs to");
    }

    /**
     * The sessions a judge served on: normally one, but a Superior Jury role
     * (no apparatus) in a round held as one session per apparatus -- the
     * apparatus finals -- presides over all of them. Empty when no scores were
     * imported for the session.
     */
    List<Session> sessionsForJudge(Round round, Apparatus apparatus) {
        if (apparatus == null && isPerApparatus(round)) {
            return sessions.entrySet().stream()
                    .filter(entry -> entry.getKey().round() == round && entry.getKey().part() == null)
                    .map(Map.Entry::getValue)
                    .toList();
        }
        Session session = sessions.get(new SessionKey(round, sessionApparatus(round, apparatus), null));
        return session == null ? List.of() : List.of(session);
    }

    private boolean isPerApparatus(Round round) {
        return round == Round.EVENT || (round == Round.QUAL && qualificationByApparatus);
    }

    private Apparatus sessionApparatus(Round round, Apparatus apparatus) {
        return isPerApparatus(round) ? apparatus : null;
    }

    private Session create(SessionKey key, LocalDate date) {
        Session session = new Session();
        session.setEvent(event);
        session.setDate(date);
        session.setRound(key.round());
        session.setDiscipline(discipline);
        session.setAgeCategory(ageCategory);
        session.setApparatus(key.apparatus());
        session.setPart(key.part());
        session = sessionService.createSession(session);
        sessions.put(key, session);
        report.count("sessions");
        return session;
    }

    private String describe(SessionKey key) {
        return key.round() + (key.apparatus() != null ? " " + key.apparatus() : "")
                + (key.part() != null ? " part " + key.part() : "");
    }
}
