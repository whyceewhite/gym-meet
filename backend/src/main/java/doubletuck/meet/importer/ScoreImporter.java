package doubletuck.meet.importer;

import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.BonusDetail;
import doubletuck.meet.domain.QualificationResult;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Routine;
import doubletuck.meet.domain.Score;
import doubletuck.meet.domain.ScoreStatus;
import doubletuck.meet.domain.Session;
import doubletuck.meet.repository.ScoreRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Imports {@code SCORES-<ROUND>-<APP>.csv} files, creating each session the
 * first time one of its scores appears.
 */
@Component
class ScoreImporter {

    // US Championships itemize the bonus FIG prints as one B value
    private static final List<Map.Entry<String, String>> BONUS_BREAKDOWN = List.of(
            Map.entry("D+", "Difficulty bonus"),
            Map.entry("E+", "Execution bonus"),
            Map.entry("S+", "Stick bonus"));

    private final ScoreRepository scoreRepository;

    ScoreImporter(ScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    void importFile(ImportContext context, String fileName) {
        for (CsvRow row : CsvRow.read(context.file(fileName))) {
            Round round = row.requiredCode("Round", Round::find);
            Apparatus apparatus = row.requiredCode("Apparatus", Apparatus::find);
            Integer part = row.integer("Part");
            if (row.date("Session Start Date") == null) {
                throw row.error("'Session Start Date' is required");
            }
            Session session = context.sessionForScores(round, apparatus, part, row.date("Session Start Date"), row);

            Score score = new Score();
            score.setParticipant(context.participant(row));
            score.setSession(session);
            score.setApparatus(apparatus);
            // The two-attempt vault shape suffixes each attempt's columns with 1 or 2
            if (row.hasColumn("Status1")) {
                score.setRoutine(routine(row, "1"));
                if (row.text("Status2") != null) {
                    score.setSecondRoutine(routine(row, "2"));
                }
                score.setCombinedBonus(row.decimal("Combined Bonus"));
                score.setTotalScore(row.decimal("Total"));
            } else {
                score.setRoutine(routine(row, ""));
                score.setTotalScore(score.getRoutine().getScore());
            }
            score.setRank(row.integer("Rank"));
            score.setQualificationRank(row.integer("Qualification Rank"));
            score.setQualificationResult(row.code("Qualification", QualificationResult::find));

            context.getScores().add(scoreRepository.save(score));
            context.getReport().count("scores");
        }
    }

    private Routine routine(CsvRow row, String attempt) {
        Routine routine = new Routine(row.requiredCode("Status" + attempt, ScoreStatus::find));
        String difficulty = row.text("D" + attempt);
        if (difficulty != null && difficulty.startsWith("!")) {
            routine.setDifficultyInquiry(true);
            difficulty = difficulty.substring(1);
        }
        try {
            routine.setDifficulty(difficulty != null ? Double.valueOf(difficulty) : null);
        } catch (NumberFormatException e) {
            throw row.error("'D" + attempt + "' value '" + difficulty + "' is not valid");
        }
        routine.setExecution(row.decimal("E" + attempt));
        routine.setBonus(row.decimal("B" + attempt));
        routine.setPenalty(row.decimal("Penalty" + attempt));
        routine.setSjPenalty(row.decimal("SJ Penalty" + attempt));
        routine.setScore(row.decimal("Total" + attempt));
        for (Map.Entry<String, String> breakdown : BONUS_BREAKDOWN) {
            Double bonus = row.decimal(breakdown.getKey() + attempt);
            if (bonus != null) {
                routine.addBonusDetail(new BonusDetail(bonus, breakdown.getValue()));
            }
        }
        return routine;
    }
}
