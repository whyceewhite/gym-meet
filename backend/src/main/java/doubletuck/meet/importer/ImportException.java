package doubletuck.meet.importer;

/**
 * A problem with the import files that stops the manifest from being imported.
 * Thrown inside the manifest's transaction, so nothing from it is saved.
 */
public class ImportException extends RuntimeException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
