package doubletuck.meet.domain;

import lombok.Getter;

@Getter
public enum JudgeFunction {

    PRESIDENT("President"),
    VICE_PRESIDENT("Vice President"),
    MEMBER("Member"),
    D1("Difficulty Judge 1"),
    D2("Difficulty Judge 2"),
    E1("Execution Judge 1"),
    E2("Execution Judge 2"),
    E3("Execution Judge 3"),
    E4("Execution Judge 4"),
    E5("Execution Judge 5"),
    E6("Execution Judge 6"),
    E7("Execution Judge 7"),
    L("Line Judge"),
    L1("Line Judge 1"),
    L2("Line Judge 2"),
    T("Time Judge"),
    T1("Time Judge 1"),
    T2("Time Judge 2"),
    SV("Supervisor"),
    R("Reserve Judge"),
    R1("Reserve Judge 1"),
    R2("Reserve Judge 2"),
    R3("Reserve Judge 3"),
    R4("Reserve Judge 4"),
    R5("Reserve Judge 5"),
    R6("Reserve Judge 6");

    private final String longName;

    JudgeFunction(String longName) {
        this.longName = longName;
    }

    /**
     * Finds the enum whose name or long name matches the given text,
     * irrespective of casing. When matching the name, spaces and hyphens
     * are interpreted as underscores, so "Vice-President" matches
     * {@code VICE_PRESIDENT}.
     */
    public static JudgeFunction find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            String code = text.replaceAll("[\\s-]+", "_");
            for (JudgeFunction judgeFunction : JudgeFunction.values()) {
                if (judgeFunction.name().equalsIgnoreCase(code)
                        || judgeFunction.longName.equalsIgnoreCase(text)) {
                    return judgeFunction;
                }
            }
        }
        return null;
    }
}
