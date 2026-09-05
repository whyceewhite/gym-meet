package doubletuck.meet.domain;

public enum AgeCategory {

    SR,
    JR,
    JR16,
    JR17;

    public static AgeCategory find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (AgeCategory ageCategory : AgeCategory.values()) {
                if (ageCategory.name().equalsIgnoreCase(text)) {
                    return ageCategory;
                }
            }
        }
        return null;
    }
}
