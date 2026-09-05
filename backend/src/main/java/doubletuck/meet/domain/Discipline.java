package doubletuck.meet.domain;

public enum Discipline {

    MAG,
    WAG;

    public static Discipline find(String text) {

        if (text != null && !text.isEmpty()) {
            text = text.trim();
            for (Discipline discipline : Discipline.values()) {
                if (discipline.name().equalsIgnoreCase(text)) {
                    return discipline;
                }
            }
        }
        return null;
    }
}
