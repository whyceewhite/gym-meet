package doubletuck.meet.domain;

import lombok.Getter;

/**
 * The apparatuses that are associated with mens and womens artistic gymnastics.
 */
@Getter
public enum Apparatus {

    VT("Vault"),
    UB("Uneven Bars"),
    BB("Balance Beam"),
    FX("Floor Exercise"),
    PH("Pommel Horse"),
    SR("Still Rings"),
    PB("Parallel Bars"),
    HB("Horizontal Bar");

    private final String longName;

    Apparatus(String longName) {
        this.longName = longName;
    }

    public static Apparatus find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (Apparatus apparatus : Apparatus.values()) {
                if (apparatus.name().equalsIgnoreCase(text)
                        || apparatus.longName.equalsIgnoreCase(text)) {
                    return apparatus;
                }
            }
        }
        return null;
    }
}
