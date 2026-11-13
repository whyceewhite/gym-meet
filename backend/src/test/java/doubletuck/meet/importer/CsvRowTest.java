package doubletuck.meet.importer;

import doubletuck.meet.domain.Round;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvRowTest {

    @TempDir
    private Path dir;

    private CsvRow row;

    @BeforeEach
    void setUp() throws IOException {
        Path file = dir.resolve("SCORES.csv");
        Files.writeString(file, """
                Round,Rank,Name,D,Penalty,Date
                QUAL, 3 , SMITH Anna ,5.5,,2026-10-18
                """);
        List<CsvRow> rows = CsvRow.read(file);
        assertEquals(1, rows.size());
        row = rows.get(0);
    }

    @Test
    void testText_TrimsAndReadsEmptyAsNull() {
        assertEquals("SMITH Anna", row.text("Name"));
        assertNull(row.text("Penalty"));
        assertNull(row.text("No Such Column"));
    }

    @Test
    void testTypedValues() {
        assertEquals(3, row.integer("Rank"));
        assertEquals(5.5, row.decimal("D"));
        assertNull(row.decimal("Penalty"));
        assertEquals(LocalDate.of(2026, 10, 18), row.date("Date"));
        assertEquals(Round.QUAL, row.code("Round", Round::find));
    }

    @Test
    void testHasColumn() {
        assertTrue(row.hasColumn("Penalty"));
        assertFalse(row.hasColumn("Status1"));
    }

    @Test
    void testInvalidNumber_ErrorNamesFileAndLine() {
        ImportException e = assertThrows(ImportException.class, () -> row.decimal("Name"));
        assertEquals("SCORES.csv line 2: 'Name' value 'SMITH Anna' is not valid", e.getMessage());
    }

    @Test
    void testUnrecognisedCode_Fails() {
        ImportException e = assertThrows(ImportException.class, () -> row.code("Name", Round::find));
        assertTrue(e.getMessage().contains("unrecognised Name 'SMITH Anna'"), e.getMessage());
    }

    @Test
    void testRequiredText_MissingValue_Fails() {
        ImportException e = assertThrows(ImportException.class, () -> row.requiredText("Penalty"));
        assertEquals("SCORES.csv line 2: 'Penalty' is required", e.getMessage());
    }
}
