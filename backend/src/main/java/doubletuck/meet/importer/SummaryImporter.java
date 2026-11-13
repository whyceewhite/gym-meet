package doubletuck.meet.importer;

import doubletuck.meet.domain.AllAroundResult;
import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.ApparatusResult;
import doubletuck.meet.domain.QualificationResult;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import doubletuck.meet.domain.TeamResult;
import doubletuck.meet.repository.AllAroundResultRepository;
import doubletuck.meet.repository.ApparatusResultRepository;
import doubletuck.meet.repository.TeamResultRepository;
import org.springframework.stereotype.Component;

/**
 * Imports {@code SUMMARY-*.csv} files: team standings (rows keyed by NOC),
 * all-around standings (rows keyed by bib), and a split round's combined
 * apparatus standings (rows keyed by bib and apparatus).
 */
@Component
class SummaryImporter {

    private final TeamResultRepository teamResultRepository;
    private final AllAroundResultRepository allAroundResultRepository;
    private final ApparatusResultRepository apparatusResultRepository;

    SummaryImporter(TeamResultRepository teamResultRepository, AllAroundResultRepository allAroundResultRepository,
            ApparatusResultRepository apparatusResultRepository) {
        this.teamResultRepository = teamResultRepository;
        this.allAroundResultRepository = allAroundResultRepository;
        this.apparatusResultRepository = apparatusResultRepository;
    }

    void importFile(ImportContext context, String fileName) {
        for (CsvRow row : CsvRow.read(context.file(fileName))) {
            Round round = row.requiredCode("Round", Round::find);
            Session session = context.sessionForResults(round, null, row.integer("Part"), row);
            if (row.hasColumn("Apparatus")) {
                importApparatusResult(context, row, session);
            } else if (row.hasColumn("Bib")) {
                importAllAroundResult(context, row, session);
            } else {
                importTeamResult(context, row, session);
            }
        }
    }

    private void importTeamResult(ImportContext context, CsvRow row, Session session) {
        TeamResult result = new TeamResult();
        result.setSession(session);
        result.setNoc(row.requiredText("NOC Code"));
        result.setTotalScore(row.decimal("Total"));
        result.setRank(row.integer("Rank"));
        result.setQualificationResult(row.code("Qualification", QualificationResult::find));
        teamResultRepository.save(result);
        context.getReport().count("team results");

        // Not stored: kept to work out which scores counted toward the team total
        for (Apparatus apparatus : Apparatus.values()) {
            Double total = row.decimal(apparatus.name() + " Total");
            if (total != null) {
                context.getPublishedTeamTotals().add(new ImportContext.PublishedTeamTotal(session,
                        result.getNoc(), apparatus, total, row.location()));
            }
        }
    }

    private void importAllAroundResult(ImportContext context, CsvRow row, Session session) {
        AllAroundResult result = new AllAroundResult();
        result.setParticipant(context.participant(row));
        result.setSession(session);
        result.setTotalScore(row.decimal("Total"));
        result.setRank(row.integer("Rank"));
        result.setQualificationResult(row.code("Qualification", QualificationResult::find));
        context.getAllAroundResults().add(allAroundResultRepository.save(result));
        context.getReport().count("all-around results");
    }

    private void importApparatusResult(ImportContext context, CsvRow row, Session session) {
        ApparatusResult result = new ApparatusResult();
        result.setParticipant(context.participant(row));
        result.setSession(session);
        result.setApparatus(row.requiredCode("Apparatus", Apparatus::find));
        result.setTotalScore(row.decimal("Total"));
        result.setRank(row.integer("Rank"));
        context.getApparatusResults().add(apparatusResultRepository.save(result));
        context.getReport().count("apparatus results");
    }
}
