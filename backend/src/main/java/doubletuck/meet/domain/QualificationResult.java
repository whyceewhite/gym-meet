package doubletuck.meet.domain;

public enum QualificationResult {

    Q,
    R1,
    R2,
    R3,
    NR;

    public static QualificationResult find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (QualificationResult qualificationResult : QualificationResult.values()) {
                if (qualificationResult.name().equalsIgnoreCase(text)) {
                    return qualificationResult;
                }
            }
        }
        return null;
    }
}
