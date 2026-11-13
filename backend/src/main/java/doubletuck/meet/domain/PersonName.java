package doubletuck.meet.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Puts a person's name into the one form it's stored in, whichever source it
 * came from. FIG results print surnames in capitals (keeping lowercase
 * particles: "van POL", "McDONALD") while national results print names as
 * written ("McCauley"), so every run of two or more capitals is turned into a
 * capital followed by lowercase and everything else is kept as printed:
 * "BLAKELY" becomes "Blakely", "McDONALD" becomes "McDonald", "van POL" becomes
 * "van Pol". Uppercasing it again gives back the FIG style for display.
 */
public final class PersonName {

    private static final Pattern CAPITALS = Pattern.compile("\\p{Lu}{2,}");

    private PersonName() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        return CAPITALS.matcher(name.trim()).replaceAll(run ->
                run.group().charAt(0) + run.group().substring(1).toLowerCase(Locale.ROOT));
    }
}
