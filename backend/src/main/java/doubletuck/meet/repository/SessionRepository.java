package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    /**
     * Looks up a session by its non-apparatus-specific key. Only meaningful
     * for {@code QUAL}/{@code AA}/{@code TEAM} rounds, where {@code
     * apparatus} is null and this combination is unique on its own. For
     * {@code EVENT} rounds, several sessions share this same combination
     * (one per apparatus) -- use {@link
     * #findByEventAndRoundAndDisciplineAndAgeCategoryAndApparatus} instead.
     */
    Optional<Session> findByEventAndRoundAndDisciplineAndAgeCategory(Event event, Round round, Discipline discipline,
            AgeCategory ageCategory);

    Optional<Session> findByEventAndRoundAndDisciplineAndAgeCategoryAndApparatus(Event event, Round round,
            Discipline discipline, AgeCategory ageCategory, Apparatus apparatus);

    List<Session> findByEvent(Event event);

    List<Session> findByEventId(Long eventId);
}
