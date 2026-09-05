package doubletuck.meet.repository;

import doubletuck.meet.domain.AgeCategory;
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

    Optional<Session> findByEventAndRoundAndDisciplineAndAgeCategory(Event event, Round round, Discipline discipline,
            AgeCategory ageCategory);

    List<Session> findByEvent(Event event);

    List<Session> findByEventId(Long eventId);
}
