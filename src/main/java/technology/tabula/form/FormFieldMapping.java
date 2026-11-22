package technology.tabula.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One entry of a form extraction mapping: the full path of an AcroForm field,
 * the output column its value is written to, and the output type used to read
 * the field.
 */
public class FormFieldMapping {

    private final List<String> path;
    private final String outputColumn;
    private final FormFieldType type;

    public FormFieldMapping(List<String> path, String outputColumn, FormFieldType type) {
        this.path = new ArrayList<>(path);
        this.outputColumn = outputColumn;
        this.type = type;
    }

    /**
     * @return immutable full field path, from the form root to the field
     */
    public List<String> getPath() {
        return Collections.unmodifiableList(path);
    }

    /**
     * @return dotted rendering of the full field path
     */
    public String getFullPath() {
        return String.join(".", path);
    }

    public String getOutputColumn() {
        return outputColumn;
    }

    public FormFieldType getType() {
        return type;
    }
}
