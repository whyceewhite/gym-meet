package doubletuck.meet.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportRunnerTest {

    @TempDir
    private Path dir;

    private final EventImporter eventImporter = mock(EventImporter.class);
    private final ImportRunner runner = new ImportRunner(eventImporter);

    @Test
    void testRun_ImportsEveryManifestUnderTheDirectory() throws IOException {
        Path worlds = manifest("2026-WORLDS/2026-WORLDS-MAG-SR-IMPORT.json");
        Path europeans = manifest("2026-EUROS/2026-EUROS-WAG-SR-IMPORT.json");
        Files.writeString(dir.resolve("2026-EUROS/2026-EUROS-WAG-SR-SCORES-QUAL-FX.csv"), "");
        when(eventImporter.importManifest(any(), eq(false))).thenReturn(new ImportReport("report"));

        runner.run(new DefaultApplicationArguments("--import.path=" + dir));

        verify(eventImporter).importManifest(worlds, false);
        verify(eventImporter).importManifest(europeans, false);
    }

    @Test
    void testRun_OneManifestFails_OthersStillImportAndRunFails() throws IOException {
        Path failing = manifest("A/A-MAG-SR-IMPORT.json");
        Path passing = manifest("B/B-MAG-SR-IMPORT.json");
        when(eventImporter.importManifest(failing, true)).thenThrow(new ImportException("bad row"));
        when(eventImporter.importManifest(passing, true)).thenReturn(new ImportReport("report"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments("--import.path=" + dir, "--import.replace")));

        verify(eventImporter).importManifest(passing, true);
        assertTrue(e.getMessage().contains("A-MAG-SR-IMPORT.json"), e.getMessage());
    }

    @Test
    void testRun_WithoutPath_Fails() {
        assertThrows(IllegalArgumentException.class, () -> runner.run(new DefaultApplicationArguments()));
    }

    @Test
    void testRun_NoManifestsFound_Fails() {
        assertThrows(IllegalArgumentException.class,
                () -> runner.run(new DefaultApplicationArguments("--import.path=" + dir)));
    }

    private Path manifest(String relativePath) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
        return file;
    }
}
