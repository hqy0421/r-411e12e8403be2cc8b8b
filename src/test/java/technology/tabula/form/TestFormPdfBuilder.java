package technology.tabula.form;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.cos.COSName;

/**
 * Builds small AcroForm PDFs for the form extraction tests. Kept under the
 * test sources; it is not part of the shipped library.
 */
final class TestFormPdfBuilder {

    private PDDocument document;
    private PDAcroForm form;
    private PDPage currentPage;

    private TestFormPdfBuilder() {
    }

    static TestFormPdfBuilder create(int pageCount) {
        TestFormPdfBuilder builder = new TestFormPdfBuilder();
        builder.document = new PDDocument();
        builder.form = new PDAcroForm(builder.document);
        PDResources resources = new PDResources();
        resources.put(COSName.getPDFName("Helv"),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        builder.form.setDefaultResources(resources);
        builder.form.setDefaultAppearance("/Helv 0 Tf 0 g");
        builder.document.getDocumentCatalog().setAcroForm(builder.form);
        for (int i = 0; i < pageCount; i++) {
            builder.document.addPage(new PDPage(PDRectangle.LETTER));
        }
        return builder;
    }

    TestFormPdfBuilder page(int oneBased) {
        this.currentPage = document.getPage(oneBased - 1);
        return this;
    }

    PDNonTerminalField addGroup(String partialName) {
        PDNonTerminalField group = new PDNonTerminalField(form);
        group.setPartialName(partialName);
        form.getFields().add(group);
        return group;
    }

    PDTextField addTextField(String partialName, String value, int... pageNumbers) throws IOException {
        PDTextField field = new PDTextField(form);
        field.setPartialName(partialName);
        if (value != null) {
            field.setValue(value);
        }
        attachWidgets(field, pageNumbers);
        form.getFields().add(field);
        return field;
    }

    /**
     * Add a text field whose widget is reachable through the page's
     * {@code Annots} array but has no {@code /P} back-reference, mimicking a
     * malformed file. The extractor must still locate the page.
     */
    PDTextField addOrphanWidgetTextField(String partialName, String value, int pageNumber) throws IOException {
        PDTextField field = new PDTextField(form);
        field.setPartialName(partialName);
        if (value != null) {
            field.setValue(value);
        }
        PDAnnotationWidget widget = new PDAnnotationWidget();
        widget.setRectangle(new PDRectangle(50, 50, 100, 20));
        widget.setParent(field);
        field.setWidgets(Arrays.asList(widget));
        form.getFields().add(field);

        PDPage page = document.getPage(pageNumber - 1);
        List<PDAnnotation> annotations = new ArrayList<>(page.getAnnotations());
        annotations.add(widget);
        page.setAnnotations(annotations);
        return field;
    }

    PDTextField addChildTextField(PDNonTerminalField parent, String partialName, String value, int... pageNumbers)
            throws IOException {
        PDTextField field = new PDTextField(form);
        field.setPartialName(partialName);
        if (value != null) {
            field.setValue(value);
        }
        attachWidgets(field, pageNumbers);
        // setChildren (not getChildren().add) writes the /Kids array back to
        // the COS dictionary, which PDFBox needs to recognize the node as
        // non-terminal when the file is reloaded.
        java.util.List<org.apache.pdfbox.pdmodel.interactive.form.PDField> children =
                new java.util.ArrayList<>(parent.getChildren());
        children.add(field);
        parent.setChildren(children);
        return field;
    }

    PDCheckBox addCheckBox(String partialName, boolean checked, int... pageNumbers) throws IOException {
        PDCheckBox field = new PDCheckBox(form);
        field.setPartialName(partialName);
        attachWidgets(field, pageNumbers);
        form.getFields().add(field);
        if (checked) {
            field.check();
        }
        return field;
    }

    PDListBox addListBox(String partialName, List<String> options, List<String> selected, int... pageNumbers)
            throws IOException {
        PDListBox field = new PDListBox(form);
        field.setPartialName(partialName);
        field.setOptions(options);
        field.setMultiSelect(true);
        if (selected != null) {
            field.setValue(selected);
        }
        attachWidgets(field, pageNumbers);
        form.getFields().add(field);
        return field;
    }

    PDComboBox addComboBox(String partialName, List<String> options, String selected, int... pageNumbers)
            throws IOException {
        PDComboBox field = new PDComboBox(form);
        field.setPartialName(partialName);
        field.setOptions(options);
        if (selected != null) {
            field.setValue(selected);
        }
        attachWidgets(field, pageNumbers);
        form.getFields().add(field);
        return field;
    }

    PDRadioButton addRadioButton(String partialName, int... pageNumbers) {
        PDRadioButton field = new PDRadioButton(form);
        field.setPartialName(partialName);
        attachWidgets(field, pageNumbers);
        form.getFields().add(field);
        return field;
    }

    private void attachWidgets(PDTerminalField field, int... pageNumbers) {
        PDAnnotationWidget[] widgets = new PDAnnotationWidget[pageNumbers.length];
        for (int i = 0; i < pageNumbers.length; i++) {
            PDAnnotationWidget widget = new PDAnnotationWidget();
            widget.setRectangle(new PDRectangle(50, 50 + i * 30, 100, 20));
            widget.setPage(document.getPage(pageNumbers[i] - 1));
            widget.setParent(field);
            widgets[i] = widget;
        }
        field.setWidgets(Arrays.asList(widgets));
    }

    File save(File folder, String name) throws IOException {
        File file = new File(folder, name);
        document.save(file);
        document.close();
        return file;
    }
}
