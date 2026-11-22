package technology.tabula.form;

import java.util.Collections;
import java.util.List;

/**
 * The extracted value of one mapped AcroForm field together with the
 * (deduplicated, sorted) 1-based page numbers of the pages on which the field
 * has a widget among the selected pages.
 *
 * The value is a {@code String}, a {@code Boolean}, a {@code List<String>} or
 * {@code null}, depending on the configured output type:
 * <ul>
 *     <li>text: {@code String}, an empty string for an empty text field</li>
 *     <li>checkbox: {@code Boolean}, {@code false} when not checked</li>
 *     <li>choice: {@code List<String>} of export values in storage order,
 *     an empty list when nothing is selected</li>
 *     <li>{@code null} when the field has no widget on any selected page</li>
 * </ul>
 */
public class FormFieldValue {

    private final Object value;
    private final List<Integer> pages;

    public FormFieldValue(Object value, List<Integer> pages) {
        this.value = value;
        this.pages = Collections.unmodifiableList(new java.util.ArrayList<>(pages));
    }

    public Object getValue() {
        return value;
    }

    public List<Integer> getPages() {
        return pages;
    }
}
