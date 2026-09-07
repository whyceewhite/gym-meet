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
     * age category, and apparatus) with a {@link ConflictException}.
     *
     * <p>This check exists in code, not only as a database constraint,
     * because {@code apparatus} is null for every round except {@code
     * EVENT}, and a plain SQL unique constraint doesn't catch two rows that
     * are both null in that column (standard NULL semantics: null isn't
     * considered equal to null). Session's {@code uk_session} constraint
     * still catches a duplicate {@code EVENT}-round session (apparatus is
     * non-null there) as a redundant safety net, but this check is what
     * actually protects {@code QUAL}/{@code AA}/{@code TEAM} rounds.
     */
    @Transactional
    public Session createSession(Session session) {
        checkForDuplicate(session);
        return sessionRepository.save(session);
    }

    private void checkForDuplicate(Session session) {
        Optional<Session> duplicate = session.getApparatus() != null
                ? sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategoryAndApparatus(session.getEvent(),
                        session.getRound(), session.getDiscipline(), session.getAgeCategory(),
                        session.getApparatus())
                : sessionRepository.findByEventAndRoundAndDisciplineAndAgeCategory(session.getEvent(),
                        session.getRound(), session.getDiscipline(), session.getAgeCategory());

        if (duplicate.isPresent()) {
            throw new ConflictException("A " + session.getRound() + " session for event "
                    + session.getEvent().getId() + " (" + session.getDiscipline() + " " + session.getAgeCategory()
                    + (session.getApparatus() != null ? " " + session.getApparatus() : "") + ") already exists");
        }
    }
}
