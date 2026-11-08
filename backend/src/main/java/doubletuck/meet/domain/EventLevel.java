package doubletuck.meet.domain;

import lombok.Getter;

@Getter
public enum EventLevel {

    OLYMPIC_GAMES("Olympic Games"),
    WORLD_CHAMPIONSHIPS("World Championships"),
    YOUTH_OLYMPIC_GAMES("Youth Olympic Games"),
    CONTINENTAL_GAMES("Continental Games"),
    CONTINENTAL_CHAMPIONSHIPS("Continental Championships"),
    WORLD_CUP("World Cup"),
    WORLD_CHALLENGE_CUP("World Challenge Cup"),
    INTERNATIONAL_TOURNAMENTS("International Tournament"),
    NATIONAL_CHAMPIONSHIPS("National Championships"),
    DOMESTIC_COMPETITION("Domestic Competition"),
    UNKNOWN("Unknown");

    private final String longName;

    EventLevel(String longName) {
        this.longName = longName;
    }

    public static EventLevel find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (EventLevel eventLevel : EventLevel.values()) {
                if (eventLevel.name().equalsIgnoreCase(text)
                        || eventLevel.longName.equalsIgnoreCase(text)) {
                    return eventLevel;
                }
            }
        }
        return null;
    }
}
