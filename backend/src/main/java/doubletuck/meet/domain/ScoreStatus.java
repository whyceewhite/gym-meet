package doubletuck.meet.domain;

import lombok.Getter;

@Getter
public enum ScoreStatus {

    COMPLETED("Completed"),
    DNS("Did Not Start"),
    INVALID_VAULT("Invalid Vault"),
    UNKNOWN("Unknown");

    private final String longName;

    ScoreStatus(String longName) {
        this.longName = longName;
    }

    public static ScoreStatus find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (ScoreStatus scoreStatus : ScoreStatus.values()) {
                if (scoreStatus.name().equalsIgnoreCase(text)
                        || scoreStatus.longName.equalsIgnoreCase(text)) {
                    return scoreStatus;
                }
            }
        }
        return null;
    }
}
