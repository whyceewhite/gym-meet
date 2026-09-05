package doubletuck.meet.domain;

public enum ScoreStatus {

    COMPLETED,
    DNS,
    INVALID_VAULT;

    public static ScoreStatus find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (ScoreStatus scoreStatus : ScoreStatus.values()) {
                if (scoreStatus.name().equalsIgnoreCase(text)) {
                    return scoreStatus;
                }
            }
        }
        return null;
    }
}
