package doubletuck.meet.repository;

import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.ApparatusResult;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApparatusResultRepository extends JpaRepository<ApparatusResult, Long> {

    Optional<ApparatusResult> findByParticipantAndSessionAndApparatus(Participant participant, Session session,
            Apparatus apparatus);

    List<ApparatusResult> findBySessionAndApparatus(Session session, Apparatus apparatus);

    List<ApparatusResult> findByParticipant(Participant participant);

    boolean existsByParticipant(Participant participant);

    void deleteBySession(Session session);
}
