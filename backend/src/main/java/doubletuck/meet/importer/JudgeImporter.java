package doubletuck.meet.importer;

import doubletuck.meet.domain.Apparatus;
import doubletuck.meet.domain.Judge;
import doubletuck.meet.domain.JudgeFunction;
import doubletuck.meet.domain.Round;
import doubletuck.meet.domain.Session;
import doubletuck.meet.repository.JudgeRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Imports {@code JUDGES.csv}, attaching each judge to the session(s) their round and apparatus identify. */
@Component
class JudgeImporter {

    private final JudgeRepository judgeRepository;

    JudgeImporter(JudgeRepository judgeRepository) {
        this.judgeRepository = judgeRepository;
    }

    void importFile(ImportContext context, String fileName) {
        Set<String> sessionsWithoutScores = new LinkedHashSet<>();
        for (CsvRow row : CsvRow.read(context.file(fileName))) {
            Round round = row.requiredCode("Round", Round::find);
            Apparatus apparatus = row.code("Apparatus", Apparatus::find);
            List<Session> sessions = context.sessionsForJudge(round, apparatus);
            if (sessions.isEmpty()) {
                sessionsWithoutScores.add(round + (apparatus != null ? " " + apparatus : ""));
                continue;
            }

            JudgeFunction function = row.requiredCode("Function", JudgeFunction::find);
            String surname = row.requiredText("Surname");
            String givenName = row.requiredText("Given Name");
            for (Session session : sessions) {
                Judge judge = new Judge();
                judge.setSession(session);
                judge.setApparatus(apparatus);
                judge.setFunction(function);
                judge.setSurname(surname);
                judge.setGivenName(givenName);
                judge.setNocCode(row.text("NOC Code"));
                judgeRepository.save(judge);
                context.getReport().count("judges");
            }
        }
        sessionsWithoutScores.forEach(session -> context.getReport().warn(fileName + ": judges for " + session
                + " were skipped because no scores were imported for that session"));
    }
}
