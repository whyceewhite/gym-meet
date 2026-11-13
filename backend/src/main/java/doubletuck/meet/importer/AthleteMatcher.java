package doubletuck.meet.importer;

import doubletuck.meet.domain.Athlete;
import doubletuck.meet.domain.AthleteNameVariant;
import doubletuck.meet.domain.Discipline;
import doubletuck.meet.repository.AthleteNameVariantRepository;
import doubletuck.meet.repository.AthleteRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the existing {@link Athlete} an entry-list row refers to, or creates one.
 *
 * <p>A FIG license identifies an athlete outright. Without one, an athlete with
 * the same name and discipline (or a known name variant of one) is the same
 * person, unless their license or birth details contradict the row's, which
 * fails the import rather than merging two people. A matched athlete gains any
 * details the row adds; existing values are never overwritten.
 */
@Component
class AthleteMatcher {

    record Candidate(Discipline discipline, String surname, String givenName, String licenseNumber,
            LocalDate birthDate, Short birthYear) {
    }

    record Match(Athlete athlete, boolean created) {
    }

    private final AthleteRepository athleteRepository;
    private final AthleteNameVariantRepository nameVariantRepository;

    AthleteMatcher(AthleteRepository athleteRepository, AthleteNameVariantRepository nameVariantRepository) {
        this.athleteRepository = athleteRepository;
        this.nameVariantRepository = nameVariantRepository;
    }

    /**
     * @param location where the row came from, for error messages
     */
    Match match(Candidate candidate, String location) {
        if (candidate.licenseNumber() != null) {
            Athlete licensed = athleteRepository.findByLicenseNumber(candidate.licenseNumber()).orElse(null);
            if (licensed != null) {
                if (licensed.getDiscipline() != candidate.discipline()) {
                    throw error(location, "FIG license " + candidate.licenseNumber() + " belongs to "
                            + licensed.getDiscipline() + " athlete " + name(licensed));
                }
                checkBirthDetails(licensed, candidate, location);
                recordNameVariant(licensed, candidate);
                return new Match(fillIn(licensed, candidate), false);
            }
        }

        Athlete named = findByName(candidate, location);
        if (named != null) {
            if (named.getLicenseNumber() != null && candidate.licenseNumber() != null
                    && !named.getLicenseNumber().equals(candidate.licenseNumber())) {
                throw error(location, name(named) + " is already recorded with FIG license " + named.getLicenseNumber()
                        + ", not " + candidate.licenseNumber() + "; if these are different athletes, the "
                        + "athlete key (name + discipline) can't hold both");
            }
            checkBirthDetails(named, candidate, location);
            return new Match(fillIn(named, candidate), false);
        }

        Athlete athlete = new Athlete();
        athlete.setDiscipline(candidate.discipline());
        athlete.setSurname(candidate.surname());
        athlete.setGivenName(candidate.givenName());
        fillIn(athlete, candidate);
        return new Match(athleteRepository.save(athlete), true);
    }

    private Athlete findByName(Candidate candidate, String location) {
        Set<Athlete> found = new LinkedHashSet<>(
                athleteRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndDiscipline(candidate.surname(),
                        candidate.givenName(), candidate.discipline()));
        nameVariantRepository.findBySurnameIgnoreCaseAndGivenNameIgnoreCaseAndAthleteDiscipline(
                candidate.surname(), candidate.givenName(), candidate.discipline())
                .forEach(variant -> found.add(variant.getAthlete()));
        if (found.size() > 1) {
            throw error(location, "name " + candidate.surname() + " " + candidate.givenName() + " matches "
                    + found.size() + " athletes");
        }
        return found.isEmpty() ? null : found.iterator().next();
    }

    private void checkBirthDetails(Athlete athlete, Candidate candidate, String location) {
        if (athlete.getBirthDate() != null && candidate.birthDate() != null
                && !athlete.getBirthDate().equals(candidate.birthDate())) {
            throw error(location, name(athlete) + " is already recorded as born " + athlete.getBirthDate() + ", not "
                    + candidate.birthDate());
        }
        if (athlete.getBirthYear() != null && candidate.birthYear() != null
                && !athlete.getBirthYear().equals(candidate.birthYear())) {
            throw error(location, name(athlete) + " is already recorded as born in " + athlete.getBirthYear() + ", not "
                    + candidate.birthYear());
        }
    }

    // A license match under a different spelling (e.g. a transliteration) is kept as a name variant
    private void recordNameVariant(Athlete athlete, Candidate candidate) {
        boolean sameName = athlete.getSurname().equalsIgnoreCase(candidate.surname())
                && athlete.getGivenName().equalsIgnoreCase(candidate.givenName());
        if (!sameName && !nameVariantRepository.existsByAthleteAndSurnameIgnoreCaseAndGivenNameIgnoreCase(athlete,
                candidate.surname(), candidate.givenName())) {
            AthleteNameVariant variant = new AthleteNameVariant();
            variant.setAthlete(athlete);
            variant.setSurname(candidate.surname());
            variant.setGivenName(candidate.givenName());
            nameVariantRepository.save(variant);
        }
    }

    private Athlete fillIn(Athlete athlete, Candidate candidate) {
        if (athlete.getLicenseNumber() == null) {
            athlete.setLicenseNumber(candidate.licenseNumber());
        }
        if (athlete.getBirthDate() == null) {
            athlete.setBirthDate(candidate.birthDate());
        }
        if (athlete.getBirthYear() == null) {
            athlete.setBirthYear(candidate.birthYear());
        }
        return athlete;
    }

    private static ImportException error(String location, String message) {
        return new ImportException(location + ": " + message);
    }

    private static String name(Athlete athlete) {
        return athlete.getSurname() + " " + athlete.getGivenName() + " (id " + Objects.toString(athlete.getId()) + ")";
    }
}
