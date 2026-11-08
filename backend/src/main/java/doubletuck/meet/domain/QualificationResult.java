package doubletuck.meet.domain;

import lombok.Getter;

@Getter
public enum QualificationResult {

    Q("Qualified"),
    R1("First Reserve"),
    R2("Second Reserve"),
    R3("Third Reserve"),
    R4("Fourth Reserve"),
    NR("NOC Reserve");

    private final String longName;

    QualificationResult(String longName) {
        this.longName = longName;
    }

    public static QualificationResult find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (QualificationResult qualificationResult : QualificationResult.values()) {
                if (qualificationResult.name().equalsIgnoreCase(text)
                        || qualificationResult.longName.equalsIgnoreCase(text)) {
                    return qualificationResult;
                }
            }
        }
        return null;
    }
}
