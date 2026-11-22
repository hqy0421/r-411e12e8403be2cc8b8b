package technology.tabula.form;

/**
 * Raised when AcroForm extraction cannot be performed: invalid extraction
 * configuration, a full field path that resolves to more than one field, a
 * field whose actual type does not match the configured output type, or a
 * field type that is not supported for extraction.
 */
public class FormExtractionException extends Exception {

    private static final long serialVersionUID = 1L;

    public FormExtractionException(String message) {
        super(message);
    }

    public FormExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
