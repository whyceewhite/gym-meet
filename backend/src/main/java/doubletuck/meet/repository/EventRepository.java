package doubletuck.meet.repository;

import doubletuck.meet.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByFigEventNumber(String figEventNumber);

    Optional<Event> findByTitleIgnoreCaseAndStartDate(String title, LocalDate startDate);
}
