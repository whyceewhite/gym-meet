package doubletuck.meet.domain;

import lombok.Getter;

/**
 * QUAL = Qualification. EVENT = an Apparatus Final (also called an "Event
 * Final"). AA = All-Around Final. TEAM = Team Final.
 *
 * <p>An Apparatus Final is genuinely its own event -- its own start list, its
 * own scores, its own medal ceremony -- even though several are typically
 * held on the same calendar date and, with this enum, share one {@code
 * EVENT} round. That per-apparatus distinction lives on {@link
 * Session#getApparatus()} instead of on a separate enum constant per
 * apparatus (the previous design here), matching how {@link Score} already
 * carries its own apparatus rather than duplicating it onto the round.
 */
@Getter
public enum Round {

    QUAL("Qualification"),
    EVENT("Apparatus Final"),
    AA("All-Around Final"),
    TEAM("Team Final");

    private final String longName;

    Round(String longName) {
        this.longName = longName;
    }

    public static Round find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (Round round : Round.values()) {
                if (round.name().equalsIgnoreCase(text)
                        || round.longName.equalsIgnoreCase(text)) {
                    return round;
                }
            }
        }
        return null;
    }
}
