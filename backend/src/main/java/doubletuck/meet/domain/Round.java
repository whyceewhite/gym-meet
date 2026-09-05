package doubletuck.meet.domain;

/**
 * Each apparatus final is genuinely its own round -- its own start list, its own
 * scores, its own medal ceremony -- even though several are typically held on
 * the same calendar date.
 */
public enum Round {

    QUALIFICATION,
    TEAM_FINAL,
    AA_FINAL,
    VT_FINAL,
    UB_FINAL,
    BB_FINAL,
    FX_FINAL,
    PH_FINAL,
    SR_FINAL,
    PB_FINAL,
    HB_FINAL;

    public static Round find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (Round round : Round.values()) {
                if (round.name().equalsIgnoreCase(text)) {
                    return round;
                }
            }
        }
        return null;
    }
}
