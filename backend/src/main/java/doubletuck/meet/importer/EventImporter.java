package doubletuck.meet.importer;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.EventLevel;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.Session;
import doubletuck.meet.repository.AllAroundResultRepository;
import doubletuck.meet.repository.ApparatusResultRepository;
import doubletuck.meet.repository.EventRepository;
import doubletuck.meet.repository.JudgeRepository;
import doubletuck.meet.repository.ParticipantRepository;
import doubletuck.meet.repository.ScoreRepository;
import doubletuck.meet.repository.SessionRepository;
import doubletuck.meet.repository.TeamResultRepository;
import doubletuck.meet.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

/**
 * Imports one {@code IMPORT.json} manifest -- one discipline and age category
 * of a competition -- in a single transaction, so a problem anywhere in its
 * files leaves nothing behind.
 */
@Service
public class EventImporter {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ScoreRepository scoreRepository;
    private final JudgeRepository judgeRepository;
    private final TeamResultRepository teamResultRepository;
    private final AllAroundResultRepository allAroundResultRepository;
    private final ApparatusResultRepository apparatusResultRepository;
    private final SessionService sessionService;
    private final ParticipantImporter participantImporter;
    private final ScoreImporter scoreImporter;
    private final SummaryImporter summaryImporter;
    private final JudgeImporter judgeImporter;
    private final ResultReconciler resultReconciler;

    EventImporter(EventRepository eventRepository, SessionRepository sessionRepository,
            ParticipantRepository participantRepository, ScoreRepository scoreRepository,
            JudgeRepository judgeRepository, TeamResultRepository teamResultRepository,
            AllAroundResultRepository allAroundResultRepository,
            ApparatusResultRepository apparatusResultRepository, SessionService sessionService,
            ParticipantImporter participantImporter, ScoreImporter scoreImporter, SummaryImporter summaryImporter,
            JudgeImporter judgeImporter, ResultReconciler resultReconciler) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.scoreRepository = scoreRepository;
        this.judgeRepository = judgeRepository;
        this.teamResultRepository = teamResultRepository;
        this.allAroundResultRepository = allAroundResultRepository;
        this.apparatusResultRepository = apparatusResultRepository;
        this.sessionService = sessionService;
        this.participantImporter = participantImporter;
        this.scoreImporter = scoreImporter;
        this.summaryImporter = summaryImporter;
        this.judgeImporter = judgeImporter;
        this.resultReconciler = resultReconciler;
    }

    /**
     * @param replace when the event already has sessions for this discipline
     *                and age category, delete them (and their results) and
     *                import again; otherwise that is an error
     */
    @Transactional
    public ImportReport importManifest(Path manifestFile, boolean replace) {
        ImportManifest manifest = ImportManifest.read(manifestFile);
        ImportReport report = new ImportReport(manifestFile.getFileName().toString());
        Discipline discipline = required(Discipline.find(manifest.event().disciplines()), "disciplines",
                manifest.event().disciplines(), manifestFile);
        AgeCategory ageCategory = required(AgeCategory.find(manifest.event().ageCategory()), "ageCategory",
                manifest.event().ageCategory(), manifestFile);
        Event event = resolveEvent(manifest.event(), manifestFile, report);

        List<Session> existing = sessionRepository.findByEventAndDisciplineAndAgeCategory(event, discipline,
                ageCategory);
        if (!existing.isEmpty()) {
            if (!replace) {
                throw new ImportException(manifestFile.getFileName() + ": " + event.getTitle() + " " + discipline
                        + " " + ageCategory + " is already imported; import with replace to load it again");
            }
            deleteSlice(event, discipline, existing, report);
        }

        ImportContext context = new ImportContext(manifestFile.getParent(), manifest, event, discipline,
                ageCategory, report, sessionService);
        manifest.participants().forEach(file -> participantImporter.importFile(context, file));
        manifest.scores().forEach(file -> scoreImporter.importFile(context, file));
        manifest.summaries().forEach(file -> summaryImporter.importFile(context, file));
        manifest.judges().forEach(file -> judgeImporter.importFile(context, file));
        resultReconciler.reconcile(context);
        return report;
    }

    private Event resolveEvent(ImportManifest.ManifestEvent source, Path manifestFile, ImportReport report) {
        String figEventNumber = source.figEventNumber() == null || source.figEventNumber().isBlank() ? null
                : source.figEventNumber().trim();
        Event event = (figEventNumber != null
                ? eventRepository.findByFigEventNumber(figEventNumber)
                : eventRepository.findByTitleIgnoreCaseAndStartDate(source.title(), source.startDate()))
                .orElse(null);
        if (event != null) {
            if (!event.getTitle().equalsIgnoreCase(source.title())
                    || !event.getStartDate().equals(source.startDate())) {
                report.warn("existing event " + event.getId() + " (" + event.getTitle() + ", " + event.getStartDate()
                        + ") matched by FIG event number has a different title or start date than the manifest");
            }
            return event;
        }

        event = new Event();
        event.setFigEventNumber(figEventNumber);
        event.setTitle(source.title());
        event.setStartDate(source.startDate());
        event.setEndDate(source.endDate());
        event.setCity(source.city());
        event.setCountryCode(source.country());
        event.setVenue(source.venue());
        event.setApparatusSuppliers(source.apparatusSuppliers());
        event.setLevel(required(EventLevel.find(source.level()), "level", source.level(), manifestFile));
        report.count("events created");
        return eventRepository.save(event);
    }

    /**
     * Deletes the slice's sessions and everything on them, then the event's
     * participants in this discipline left with no results at all -- which
     * includes this slice's entries, since participants aren't tied to an age
     * category.
     */
    private void deleteSlice(Event event, Discipline discipline, List<Session> sessions, ImportReport report) {
        for (Session session : sessions) {
            scoreRepository.deleteBySession(session);
            judgeRepository.deleteBySession(session);
            teamResultRepository.deleteBySession(session);
            allAroundResultRepository.deleteBySession(session);
            apparatusResultRepository.deleteBySession(session);
        }
        sessionRepository.deleteAll(sessions);
        sessionRepository.flush();
        report.count("sessions replaced", sessions.size());

        for (Participant participant : participantRepository.findByEvent(event)) {
            if (participant.getAthlete().getDiscipline() == discipline
                    && !scoreRepository.existsByParticipant(participant)
                    && !allAroundResultRepository.existsByParticipant(participant)
                    && !apparatusResultRepository.existsByParticipant(participant)) {
                participantRepository.delete(participant);
                report.count("participants replaced");
            }
        }
        participantRepository.flush();
    }

    private static <T> T required(T value, String field, String text, Path manifestFile) {
        if (value == null) {
            throw new ImportException(manifestFile.getFileName() + ": event " + field + " '" + text
                    + "' is missing or not recognised");
        }
        return value;
    }
}
