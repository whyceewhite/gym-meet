package doubletuck.meet.repository;

import doubletuck.meet.domain.Judge;
import doubletuck.meet.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JudgeRepository extends JpaRepository<Judge, Long> {

    List<Judge> findBySession(Session session);

    List<Judge> findBySessionId(Long sessionId);

    void deleteBySession(Session session);
}
