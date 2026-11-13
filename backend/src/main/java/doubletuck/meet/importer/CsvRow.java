package doubletuck.meet.importer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;

/**
 * One row of an import CSV, read by column name. Cells are trimmed and an
 * empty cell reads as null, following the import files' "empty means empty"
 * convention.
 */
public class CsvRow {

    private final String fileName;
    private final CSVRecord record;

    private CsvRow(String fileName, CSVRecord record) {
        this.fileName = fileName;
        this.record = record;
    }

    public static List<CsvRow> read(Path file) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(Files.readString(file, StandardCharsets.UTF_8), format)) {
            String fileName = file.getFileName().toString();
            return parser.getRecords().stream().map(record -> new CsvRow(fileName, record)).toList();
        } catch (IOException | IllegalArgumentException e) {
            throw new ImportException("Unable to read " + file + ": " + e.getMessage(), e);
        }
    }

    public boolean hasColumn(String column) {
        return record.isMapped(column);
    }

    public String text(String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column).trim();
        return value.isEmpty() ? null : value;
    }

    public String requiredText(String column) {
        String value = text(column);
        if (value == null) {
            throw error("'" + column + "' is required");
        }
        return value;
    }

    public Double decimal(String column) {
        return parse(column, Double::valueOf);
    }

    public Integer integer(String column) {
        return parse(column, Integer::valueOf);
    }

    public LocalDate date(String column) {
        return parse(column, LocalDate::parse);
    }

    /**
     * Reads a code column through an enum's {@code find} method, failing on a
     * value the enum doesn't recognise rather than silently storing null.
     */
    public <T> T code(String column, Function<String, T> find) {
        String value = text(column);
        if (value == null) {
            return null;
        }
        T found = find.apply(value);
        if (found == null) {
            throw error("unrecognised " + column + " '" + value + "'");
        }
        return found;
    }

    public <T> T requiredCode(String column, Function<String, T> find) {
        requiredText(column);
        return code(column, find);
    }

    public ImportException error(String message) {
        return new ImportException(location() + ": " + message);
    }

    public String location() {
        // Line 1 is the header, so the first data record is line 2
        return fileName + " line " + (record.getRecordNumber() + 1);
    }

    private <T> T parse(String column, Function<String, T> parser) {
        String value = text(column);
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw error("'" + column + "' value '" + value + "' is not valid");
        }
    }
}
