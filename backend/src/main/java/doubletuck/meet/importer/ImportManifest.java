package doubletuck.meet.importer;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * An {@code <PREFIX>-IMPORT.json} manifest: the event's metadata plus the files
 * holding one discipline and age category's results.
 */
public record ImportManifest(
        ManifestEvent event,
        List<String> participants,
        List<String> judges,
        List<String> scores,
        List<String> summaries) {

    public record ManifestEvent(
            String title,
            String disciplines,
            String ageCategory,
            String level,
            LocalDate startDate,
            LocalDate endDate,
            String city,
            String country,
            String figEventNumber,
            String venue,
            String apparatusSuppliers) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static ImportManifest read(Path file) {
        try {
            ImportManifest manifest = MAPPER.readValue(file.toFile(), ImportManifest.class);
            if (manifest.event() == null) {
                throw new ImportException(file + ": manifest has no event");
            }
            return manifest;
        } catch (JacksonException e) {
            throw new ImportException("Unable to read " + file + ": " + e.getOriginalMessage(), e);
        }
    }

    public List<String> participants() {
        return participants == null ? List.of() : participants;
    }

    public List<String> judges() {
        return judges == null ? List.of() : judges;
    }

    public List<String> scores() {
        return scores == null ? List.of() : scores;
    }

    public List<String> summaries() {
        return summaries == null ? List.of() : summaries;
    }
}
