package doubletuck.meet.repository;

import doubletuck.meet.domain.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    Optional<Club> findByNameIgnoreCaseAndCountry(String name, String country);
}
