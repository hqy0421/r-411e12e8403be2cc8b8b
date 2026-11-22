package technology.tabula.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;

/**
 * Extracts values from the AcroForm of a PDF, restricted to fields that have
 * at least one widget annotation on one of the selected pages.
 *
 * Fields are identified by their full path (the chain of partial names from
 * the form root). Same-named fields under different parents are therefore
 * handled as distinct fields. A field backed by several widget annotations
 * yields a single value, accompanied by the deduplicated list of selected
 * pages carrying one of its widgets.
 */
public class AcroFormExtractor {

    private final PDDocument document;
    private final TreeSet<Integer> selectedPages;
    private final Map<PDAnnotation, Integer> annotationPageIndex;

    /**
     * @param document the loaded PDF
     * @param selectedPages 1-based selected page numbers; {@code null} means
     *                      every page of the document
     */
    public AcroFormExtractor(PDDocument document, List<Integer> selectedPages) {
        this.document = document;
        this.selectedPages = selectedPages == null ? null : new TreeSet<>(selectedPages);
        this.annotationPageIndex = buildAnnotationPageIndex(document);
    }

    /**
     * Extract the mapped fields.
     *
     * @param mappings field path to output column/type mappings
     * @return one entry per mapping, keyed by output column and in mapping
     *         order; values are {@code null} when the field has no widget on
     *         any selected page
     * @throws FormExtractionException on path conflicts, type mismatches or
     *         unsupported field types
     */
    public Map<String, FormFieldValue> extract(List<FormFieldMapping> mappings) throws FormExtractionException {
        Map<List<String>, List<PDField>> fieldsByPath = collectFields();

        ensureNoPathConflicts(fieldsByPath);

        Map<String, FormFieldValue> results = new LinkedHashMap<>();
        for (FormFieldMapping mapping : mappings) {
            results.put(mapping.getOutputColumn(), extractMapping(mapping, fieldsByPath));
        }
        return results;
    }

    private Map<List<String>, List<PDField>> collectFields() {
        Map<List<String>, List<PDField>> fieldsByPath = new HashMap<>();
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            return fieldsByPath;
        }
        // Index every node, including non-terminal grouping nodes, so that a
        // mapping aimed at a group fails with an explicit type error instead
        // of being treated as a missing field.
        for (PDField field : acroForm.getFieldTree()) {
            List<String> path = fullPath(field);
            fieldsByPath.computeIfAbsent(path, key -> new ArrayList<>()).add(field);
        }
        return fieldsByPath;
    }

    private void ensureNoPathConflicts(Map<List<String>, List<PDField>> fieldsByPath)
            throws FormExtractionException {
        for (Map.Entry<List<String>, List<PDField>> entry : fieldsByPath.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new FormExtractionException(String.format(
                        "Full field path \"%s\" resolves to %d fields; field paths must be unique.",
                        String.join(".", entry.getKey()), entry.getValue().size()));
            }
        }
    }

    private FormFieldValue extractMapping(
            FormFieldMapping mapping,
            Map<List<String>, List<PDField>> fieldsByPath) throws FormExtractionException {

        List<PDField> matches = fieldsByPath.get(mapping.getPath());
        if (matches == null) {
            // No such field in the document: nothing can appear on a selected page.
            return new FormFieldValue(null, new ArrayList<Integer>());
        }

        PDField field = matches.get(0);

        // Validate the requested type first, so a mapping onto an unsupported
        // field fails explicitly even when none of its widgets is on a
        // selected page (non-terminal grouping nodes have no widgets at all).
        if (!mapping.getType().supports(field)) {
            throw new FormExtractionException(String.format(
                    "Field \"%s\" is of type %s and cannot be read as %s.",
                    mapping.getFullPath(), actualTypeName(field), mapping.getType().configName()));
        }

        PDTerminalField terminalField = (PDTerminalField) field;
        List<Integer> pages = widgetPagesOnSelectedPages(terminalField);
        if (pages.isEmpty()) {
            // The field exists but none of its widgets is on a selected page.
            return new FormFieldValue(null, new ArrayList<Integer>());
        }

        Object value = readValue(mapping.getType(), terminalField);
        return new FormFieldValue(value, pages);
    }

    private Object readValue(FormFieldType type, PDTerminalField field) throws FormExtractionException {
        switch (type) {
            case TEXT:
                String text = ((PDTextField) field).getValue();
                return text == null ? "" : text;
            case CHECKBOX:
                return ((PDCheckBox) field).isChecked();
            case CHOICE:
                List<String> selected = ((PDChoice) field).getValue();
                return selected == null ? new ArrayList<String>() : new ArrayList<>(selected);
            default:
                throw new FormExtractionException(
                        "Output type " + type.configName() + " is not supported.");
        }
    }

    /**
     * Resolve the 1-based selected pages on which the field has a widget.
     * The widget's own page reference is used first; the page annotation
     * index built from the document is the fallback for malformed files.
     * Widgets that cannot be placed on any page are ignored.
     */
    private List<Integer> widgetPagesOnSelectedPages(PDTerminalField field) {
        TreeSet<Integer> pages = new TreeSet<>();
        for (PDAnnotationWidget widget : field.getWidgets()) {
            int pageNumber = resolvePageNumber(widget);
            if (pageNumber <= 0) {
                continue;
            }
            if (selectedPages == null || selectedPages.contains(pageNumber)) {
                pages.add(pageNumber);
            }
        }
        return new ArrayList<>(pages);
    }

    private int resolvePageNumber(PDAnnotationWidget widget) {
        PDPage page = widget.getPage();
        if (page != null) {
            int pageNumber = document.getPages().indexOf(page) + 1;
            if (pageNumber > 0) {
                return pageNumber;
            }
        }
        Integer indexed = annotationPageIndex.get(widget);
        return indexed == null ? -1 : indexed;
    }

    private static List<String> fullPath(PDField field) {
        List<String> reversed = new ArrayList<>();
        for (PDField current = field; current != null; current = current.getParent()) {
            String partialName = current.getPartialName();
            if (partialName != null) {
                reversed.add(partialName);
            }
        }
        List<String> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    private static String actualTypeName(PDField field) {
        if (field instanceof PDTextField) {
            return "text";
        }
        if (field instanceof PDCheckBox) {
            return "checkbox";
        }
        if (field instanceof PDChoice) {
            return "choice";
        }
        if (field instanceof PDNonTerminalField) {
            return "a non-terminal grouping field with no value";
        }
        String fieldType = field.getFieldType();
        if (fieldType != null) {
            return "an unsupported field of PDF type " + fieldType;
        }
        return "an unsupported " + field.getClass().getSimpleName();
    }

    private static Map<PDAnnotation, Integer> buildAnnotationPageIndex(PDDocument document) {
        Map<PDAnnotation, Integer> index = new HashMap<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage page = document.getPage(i);
            try {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    index.put(annotation, i + 1);
                }
            } catch (IOException e) {
                // A page whose annotations cannot be read contributes nothing.
            }
        }
        return index;
    }
}
