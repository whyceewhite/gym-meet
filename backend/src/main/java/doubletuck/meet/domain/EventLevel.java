package doubletuck.meet.domain;

public enum EventLevel {

    OLYMPIC_GAMES,
    WORLD_CHAMPIONSHIPS,
    YOUTH_OLYMPIC_GAMES,
    CONTINENTAL_GAMES,
    CONTINENTAL_CHAMPIONSHIPS,
    WORLD_CUP,
    WORLD_CHALLENGE_CUP,
    INTERNATIONAL_TOURNAMENTS,
    NATIONAL_CHAMPIONSHIPS,
    DOMESTIC_COMPETITION,
    UNKNOWN;

    public static EventLevel find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (EventLevel eventLevel : EventLevel.values()) {
                if (eventLevel.name().equalsIgnoreCase(text)) {
                    return eventLevel;
                }
            }
        }
        return null;
    }
}
