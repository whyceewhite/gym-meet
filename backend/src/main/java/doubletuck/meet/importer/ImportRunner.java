package doubletuck.meet.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Imports results from the command line under the {@code import} profile:
 * {@code --import.path=<manifest, or a directory searched for *-IMPORT.json>}
 * (repeatable) and optionally {@code --import.replace}. Each manifest is
 * imported on its own, so one failing doesn't stop the rest; the run fails if
 * any did.
 */
@Component
@Profile("import")
class ImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private final EventImporter eventImporter;

    ImportRunner(EventImporter eventImporter) {
        this.eventImporter = eventImporter;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> paths = args.getOptionValues("import.path");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("--import.path is required");
        }
        boolean replace = args.containsOption("import.replace");

        List<Path> manifests = paths.stream().flatMap(path -> manifests(Path.of(path))).toList();
        if (manifests.isEmpty()) {
            throw new IllegalArgumentException("No *-IMPORT.json manifests found under " + paths);
        }

        List<String> failures = new ArrayList<>();
        for (Path manifest : manifests) {
            try {
                log.info("Imported {}", eventImporter.importManifest(manifest, replace).summary());
            } catch (RuntimeException e) {
                log.error("Failed to import {}: {}", manifest.getFileName(), e.getMessage());
                failures.add(manifest.getFileName().toString());
            }
        }
        log.info("Imported {} of {} manifests", manifests.size() - failures.size(), manifests.size());
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Failed to import " + failures);
        }
    }

    private static Stream<Path> manifests(Path path) {
        if (Files.isRegularFile(path)) {
            return Stream.of(path);
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(file -> file.getFileName().toString().endsWith("-IMPORT.json")).sorted().toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to search " + path, e);
        }
    }
}
