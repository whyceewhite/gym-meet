package doubletuck.meet.repository;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.Event;
import doubletuck.meet.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByEventAndAthlete(Event event, Athlete athlete);

    List<Participant> findByEvent(Event event);

    List<Participant> findByEventId(Long eventId);

    List<Participant> findByAthlete(Athlete athlete);
}
