package doubletuck.meet.repository;

import doubletuck.meet.domain.Session;
import doubletuck.meet.domain.TeamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamResultRepository extends JpaRepository<TeamResult, Long> {

    Optional<TeamResult> findBySessionAndNoc(Session session, String noc);

    List<TeamResult> findBySession(Session session);

    List<TeamResult> findBySessionId(Long sessionId);
}
