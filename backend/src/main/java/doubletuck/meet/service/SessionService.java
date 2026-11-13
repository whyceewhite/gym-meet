package doubletuck.meet.service;

import doubletuck.meet.domain.Session;
import doubletuck.meet.exception.ConflictException;
import doubletuck.meet.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    @Autowired
    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Creates a session, rejecting a duplicate (same event, round, discipline,
     * age category, apparatus, and part) with a {@link ConflictException}.
     * The database's {@code uk_session} constraint rejects one too; this check
     * reports it as a conflict naming the session rather than a SQL error.
     */
    @Transactional
    public Session createSession(Session session) {
        checkForDuplicate(session);
        return sessionRepository.save(session);
    }

    private void checkForDuplicate(Session session) {
        // A null apparatus or part matches only a session where it is also null
        Optional<Session> duplicate = sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndApparatusAndPart(
                session.getEvent(), session.getRound(), session.getDiscipline(), session.getAgeCategory(),
                session.getApparatus(), session.getPart());

        if (duplicate.isPresent()) {
            throw new ConflictException("A " + session.getRound() + " session for event "
                    + session.getEvent().getId() + " (" + session.getDiscipline() + " " + session.getAgeCategory()
                    + (session.getApparatus() != null ? " " + session.getApparatus() : "")
                    + (session.getPart() != null ? " part " + session.getPart() : "") + ") already exists");
        }
    }
}
