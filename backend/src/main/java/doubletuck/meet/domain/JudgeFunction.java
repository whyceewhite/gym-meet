package doubletuck.meet.domain;

public enum JudgeFunction {

    PRESIDENT,
    VICE_PRESIDENT,
    MEMBER,
    D1,
    D2,
    E1,
    E2,
    E3,
    E4,
    E5,
    E6,
    E7,
    L,
    L1,
    L2,
    T,
    T1,
    T2,
    SV,
    R1,
    R2,
    R3,
    R4,
    R5,
    R6;

    public static JudgeFunction find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim().replaceAll("[\\s-]+", "_");
            for (JudgeFunction judgeFunction : JudgeFunction.values()) {
                if (judgeFunction.name().equalsIgnoreCase(text)) {
                    return judgeFunction;
                }
            }
        }
        return null;
    }
}
