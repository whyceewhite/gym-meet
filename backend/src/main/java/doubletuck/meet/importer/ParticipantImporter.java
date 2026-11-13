package doubletuck.meet.importer;

import doubletuck.meet.domain.Club;
import doubletuck.meet.domain.Participant;
import doubletuck.meet.repository.ClubRepository;
import doubletuck.meet.repository.ParticipantRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Imports {@code PARTICIPANTS.csv}: the athletes, their clubs, and their entries into the event. */
@Component
class ParticipantImporter {

    private final AthleteMatcher athleteMatcher;
    private final ClubRepository clubRepository;
    private final ParticipantRepository participantRepository;

    ParticipantImporter(AthleteMatcher athleteMatcher, ClubRepository clubRepository,
            ParticipantRepository participantRepository) {
        this.athleteMatcher = athleteMatcher;
        this.clubRepository = clubRepository;
        this.participantRepository = participantRepository;
    }

    void importFile(ImportContext context, String fileName) {
        // Some entry lists print a country's NOC only on its first row
        String noc = null;
        for (CsvRow row : CsvRow.read(context.file(fileName))) {
            noc = row.text("NOC Code") != null ? row.text("NOC Code") : noc;
            if (noc == null) {
                throw row.error("no NOC Code on this or any earlier row");
            }
            importRow(context, row, noc);
        }
    }

    private void importRow(ImportContext context, CsvRow row, String noc) {
        String bib = row.requiredText("Bib Number");
        if (context.getParticipantsByBib().containsKey(bib)) {
            throw row.error("bib " + bib + " appears more than once");
        }

        LocalDate birthDate = row.date("Birth Date");
        Integer birthYear = birthDate != null ? Integer.valueOf(birthDate.getYear()) : row.integer("Birth Year");
        AthleteMatcher.Candidate candidate = new AthleteMatcher.Candidate(context.getDiscipline(),
                row.requiredText("Surname"), row.requiredText("Given Name"), row.text("FIG License"), birthDate,
                birthYear != null ? birthYear.shortValue() : null);
        AthleteMatcher.Match match = athleteMatcher.match(candidate, row.location());
        if (match.created()) {
            context.getReport().newAthlete(candidate.surname() + " " + candidate.givenName() + " (" + noc + ")");
            context.getReport().count("athletes created");
        } else {
            context.getReport().count("athletes matched");
        }

        if (participantRepository.findByEventAndAthlete(context.getEvent(), match.athlete()).isPresent()) {
            throw row.error(candidate.surname() + " " + candidate.givenName() + " is already entered in this event");
        }
        Participant participant = new Participant();
        participant.setEvent(context.getEvent());
        participant.setAthlete(match.athlete());
        participant.setBibNumber(bib);
        participant.setNoc(noc);
        participant.setClub(club(row, noc));
        participant = participantRepository.save(participant);
        context.getParticipantsByBib().put(bib, participant);
        context.getReport().count("participants");
    }

    private Club club(CsvRow row, String noc) {
        String name = row.text("Club");
        if (name == null) {
            return null;
        }
        return clubRepository.findByNameIgnoreCaseAndCountry(name, noc)
                .orElseGet(() -> clubRepository.save(new Club(name, noc)));
    }
}
