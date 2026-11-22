package technology.tabula.form;

import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/**
 * The output types that can be requested for an AcroForm field in an extraction
 * mapping. Each type declares which PDFBox field classes it is able to read.
 */
public enum FormFieldType {

    /** Free text field: read as a JSON string. */
    TEXT("text") {
        @Override
        public boolean supports(PDField field) {
            return field instanceof PDTextField;
        }
    },

    /** Check box: read as a JSON boolean. */
    CHECKBOX("checkbox") {
        @Override
        public boolean supports(PDField field) {
            return field instanceof PDCheckBox;
        }
    },

    /** Combo box or list box: read as a JSON array of export values. */
    CHOICE("choice") {
        @Override
        public boolean supports(PDField field) {
            return field instanceof PDChoice;
        }
    };

    private final String configName;

    FormFieldType(String configName) {
        this.configName = configName;
    }

    /**
     * @return the name used for this type in the mapping configuration.
     */
    public String configName() {
        return configName;
    }

    /**
     * @param field a terminal field found in the document
     * @return whether this output type can read the given field
     */
    public abstract boolean supports(PDField field);

    /**
     * Resolve the type name used in the mapping configuration.
     *
     * @param name type name as written by the user
     * @return the matching type
     * @throws FormExtractionException if the name does not name a supported type
     */
    public static FormFieldType fromConfigName(String name) throws FormExtractionException {
        if (name != null) {
            for (FormFieldType type : values()) {
                if (type.configName.equalsIgnoreCase(name.trim())) {
                    return type;
                }
            }
        }
        throw new FormExtractionException(String.format(
                "Unsupported form field type '%s'. Supported types: text, checkbox, choice.", name));
    }
}
