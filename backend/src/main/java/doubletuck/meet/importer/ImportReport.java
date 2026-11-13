package doubletuck.meet.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one manifest's import did: how many of each record it created, the
 * athletes it had to create rather than match, and anything that imported but
 * looked wrong.
 */
public class ImportReport {

    private final String manifest;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<String> newAthletes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ImportReport(String manifest) {
        this.manifest = manifest;
    }

    public void count(String what) {
        count(what, 1);
    }

    public void count(String what, int howMany) {
        counts.merge(what, howMany, Integer::sum);
    }

    public void newAthlete(String description) {
        newAthletes.add(description);
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    public String getManifest() {
        return manifest;
    }

    public int getCount(String what) {
        return counts.getOrDefault(what, 0);
    }

    public Map<String, Integer> getCounts() {
        return Collections.unmodifiableMap(counts);
    }

    public List<String> getNewAthletes() {
        return Collections.unmodifiableList(newAthletes);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public String summary() {
        StringBuilder text = new StringBuilder(manifest).append('\n');
        counts.forEach((what, count) -> text.append("  ").append(what).append(": ").append(count).append('\n'));
        text.append("  new athletes: ").append(newAthletes.size()).append('\n');
        text.append("  warnings: ").append(warnings.size()).append('\n');
        warnings.forEach(warning -> text.append("    - ").append(warning).append('\n'));
        return text.toString();
    }
}
