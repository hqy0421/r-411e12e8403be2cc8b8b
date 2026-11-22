package technology.tabula.form;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestAcroFormExtractor {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static FormFieldMapping mapping(String path, String column, FormFieldType type) {
        return new FormFieldMapping(Arrays.asList(path.split("\\.")), column, type);
    }

    private PDDocument open(File pdf) throws Exception {
        return Loader.loadPDF(pdf);
    }

    private File buildStandardPdf() throws Exception {
        TestFormPdfBuilder builder = TestFormPdfBuilder.create(3);

        // same-named "name" field under two different parents
        PDNonTerminalField shipper = builder.addGroup("shipper");
        PDNonTerminalField consignee = builder.addGroup("consignee");
        builder.page(1).addChildTextField(shipper, "name", "ACME Ltd", 1);
        builder.page(1).addChildTextField(consignee, "name", "", 1);

        // empty text field and a populated one
        builder.page(1).addTextField("declaration", "Export declaration", 1);
        builder.page(1).addTextField("remarks", "", 1);

        // one field with widgets on page 1 and 2
        builder.addTextField("shared", "shared value", 1, 2);

        // checkbox checked on page 2, unchecked on page 3
        builder.page(2).addCheckBox("insured", true, 2);
        builder.page(3).addCheckBox("optional", false, 3);

        // multi-select list box with non-sorted stored selection
        builder.page(1).addListBox("terms",
                Arrays.asList("EXW", "FOB", "CIF", "DDP"),
                Arrays.asList("CIF", "EXW"), 1);

        // combo box, single selection and an unselected one
        builder.page(1).addComboBox("incoterm", Arrays.asList("FOB", "CIF"), "CIF", 1);
        builder.page(1).addComboBox("unusedChoice", Arrays.asList("A", "B"), null, 1);

        // field living only on page 3
        builder.page(3).addTextField("page3only", "third page", 3);

        // unsupported field type
        builder.page(1).addRadioButton("radio", 1);

        return builder.save(folder.getRoot(), "forms.pdf");
    }

    @Test
    public void extractsTextCheckboxAndChoiceWithPages() throws Exception {
        File pdf = buildStandardPdf();
        List<FormFieldMapping> mappings = Arrays.asList(
                mapping("shipper.name", "shipper_name", FormFieldType.TEXT),
                mapping("consignee.name", "consignee_name", FormFieldType.TEXT),
                mapping("declaration", "declaration", FormFieldType.TEXT),
                mapping("remarks", "remarks", FormFieldType.TEXT),
                mapping("shared", "shared", FormFieldType.TEXT),
                mapping("insured", "insured", FormFieldType.CHECKBOX),
                mapping("optional", "optional", FormFieldType.CHECKBOX),
                mapping("terms", "terms", FormFieldType.CHOICE),
                mapping("incoterm", "incoterm", FormFieldType.CHOICE),
                mapping("unusedChoice", "unusedChoice", FormFieldType.CHOICE));

        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result =
                    new AcroFormExtractor(doc, Arrays.asList(1, 2, 3)).extract(mappings);

            assertEquals("ACME Ltd", result.get("shipper_name").getValue());
            assertEquals(Arrays.asList(1), result.get("shipper_name").getPages());

            // empty text stays an empty string, not null
            assertEquals("", result.get("consignee_name").getValue());
            assertEquals("", result.get("remarks").getValue());

            // field with two widgets keeps one value with deduplicated sorted pages
            FormFieldValue shared = result.get("shared");
            assertEquals("shared value", shared.getValue());
            assertEquals(Arrays.asList(1, 2), shared.getPages());

            assertEquals(Boolean.TRUE, result.get("insured").getValue());
            // unchecked stays false, not null
            assertEquals(Boolean.FALSE, result.get("optional").getValue());

            // choice keeps storage order of export values
            assertEquals(Arrays.asList("CIF", "EXW"), result.get("terms").getValue());
            assertEquals(Arrays.asList("CIF"), result.get("incoterm").getValue());
            // nothing selected stays an empty array, not null
            assertEquals(Arrays.asList(), result.get("unusedChoice").getValue());
        }
    }

    @Test
    public void sameNamedFieldsUnderDifferentParentsAreDistinct() throws Exception {
        File pdf = buildStandardPdf();
        List<FormFieldMapping> mappings = Arrays.asList(
                mapping("shipper.name", "s", FormFieldType.TEXT),
                mapping("consignee.name", "c", FormFieldType.TEXT));
        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result =
                    new AcroFormExtractor(doc, null).extract(mappings);
            assertEquals("ACME Ltd", result.get("s").getValue());
            assertEquals("", result.get("c").getValue());
        }
    }

    @Test
    public void onlyReadsFieldsWithWidgetOnSelectedPages() throws Exception {
        File pdf = buildStandardPdf();
        List<FormFieldMapping> mappings = Arrays.asList(
                mapping("page3only", "page3", FormFieldType.TEXT),
                mapping("shared", "shared", FormFieldType.TEXT),
                mapping("declaration", "declaration", FormFieldType.TEXT));

        // select only page 1: page 3 field and the page-2 widget drop out
        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result =
                    new AcroFormExtractor(doc, Arrays.asList(1)).extract(mappings);

            assertNull(result.get("page3").getValue());
            assertEquals(Arrays.asList(), result.get("page3").getPages());

            // shared has a widget on page 1: kept, but pages list only mentions page 1
            assertEquals("shared value", result.get("shared").getValue());
            assertEquals(Arrays.asList(1), result.get("shared").getPages());

            assertEquals("Export declaration", result.get("declaration").getValue());
        }

        // select only page 3: the page-1-only declaration field becomes null
        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result =
                    new AcroFormExtractor(doc, Arrays.asList(3)).extract(mappings);
            assertNull(result.get("declaration").getValue());
            assertEquals("third page", result.get("page3").getValue());
            assertEquals(Arrays.asList(3), result.get("page3").getPages());
        }
    }

    @Test
    public void missingFieldIsProducedAsNull() throws Exception {
        File pdf = buildStandardPdf();
        List<FormFieldMapping> mappings = Arrays.asList(
                mapping("does.not.exist", "missing", FormFieldType.TEXT));
        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result =
                    new AcroFormExtractor(doc, null).extract(mappings);
            assertNull(result.get("missing").getValue());
            assertEquals(Arrays.asList(), result.get("missing").getPages());
        }
    }

    @Test
    public void typeMismatchRaisesExplicitError() throws Exception {
        File pdf = buildStandardPdf();
        try (PDDocument doc = open(pdf)) {
            // a text field requested as checkbox
            FormExtractionException e = assertThrows(FormExtractionException.class,
                    () -> new AcroFormExtractor(doc, null).extract(Arrays.asList(
                            mapping("declaration", "bad", FormFieldType.CHECKBOX))));
            assertTrue(e.getMessage().contains("declaration"));
            assertTrue(e.getMessage().contains("text"));
            assertTrue(e.getMessage().contains("checkbox"));
        }
        try (PDDocument doc = open(pdf)) {
            // a choice field requested as text
            FormExtractionException e = assertThrows(FormExtractionException.class,
                    () -> new AcroFormExtractor(doc, null).extract(Arrays.asList(
                            mapping("terms", "bad", FormFieldType.TEXT))));
            assertTrue(e.getMessage().contains("terms"));
            assertTrue(e.getMessage().contains("choice"));
        }
    }

    @Test
    public void unsupportedFieldTypeRaisesExplicitError() throws Exception {
        File pdf = buildStandardPdf();
        try (PDDocument doc = open(pdf)) {
            FormExtractionException e = assertThrows(FormExtractionException.class,
                    () -> new AcroFormExtractor(doc, null).extract(Arrays.asList(
                            mapping("radio", "r", FormFieldType.CHECKBOX))));
            assertTrue(e.getMessage().contains("radio"));
        }
    }

    @Test
    public void duplicateFullPathInDocumentRaisesConflictError() throws Exception {
        // Two independent terminal fields sharing the exact same full path
        TestFormPdfBuilder builder = TestFormPdfBuilder.create(1);
        builder.page(1).addTextField("dup", "first", 1);
        builder.page(1).addTextField("dup", "second", 1);
        File pdf = builder.save(folder.getRoot(), "conflict.pdf");

        try (PDDocument doc = open(pdf)) {
            FormExtractionException e = assertThrows(FormExtractionException.class,
                    () -> new AcroFormExtractor(doc, null).extract(Arrays.asList(
                            mapping("dup", "d", FormFieldType.TEXT))));
            assertTrue(e.getMessage().contains("dup"));
            assertTrue(e.getMessage().contains("unique"));
        }
    }

    @Test
    public void documentWithoutAcroFormYieldsNulls() throws Exception {
        TestFormPdfBuilder builder = TestFormPdfBuilder.create(1);
        File pdf = builder.save(folder.getRoot(), "noform.pdf");
        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result = new AcroFormExtractor(doc, null).extract(Arrays.asList(
                    mapping("anything", "a", FormFieldType.TEXT)));
            assertNull(result.get("a").getValue());
        }
    }

    @Test
    public void mappingANonTerminalNodeRaisesTypeError() throws Exception {
        File pdf = buildStandardPdf();
        try (PDDocument doc = open(pdf)) {
            // "shipper" is a non-terminal grouping node with no widgets, not a
            // terminal text field
            FormExtractionException e = assertThrows(FormExtractionException.class,
                    () -> new AcroFormExtractor(doc, null).extract(Arrays.asList(
                            mapping("shipper", "g", FormFieldType.TEXT))));
            assertTrue(e.getMessage().contains("shipper"));
        }
    }

    @Test
    public void findsWidgetPageThroughPageAnnotationsWhenPRefMissing() throws Exception {
        TestFormPdfBuilder builder = TestFormPdfBuilder.create(2);
        // widget has no /P back-reference but is in page 2's Annots array
        builder.addOrphanWidgetTextField("orphan", "located via fallback", 2);
        File pdf = builder.save(folder.getRoot(), "orphan.pdf");

        try (PDDocument doc = open(pdf)) {
            Map<String, FormFieldValue> result = new AcroFormExtractor(doc, Arrays.asList(1, 2))
                    .extract(Arrays.asList(mapping("orphan", "o", FormFieldType.TEXT)));
            assertEquals("located via fallback", result.get("o").getValue());
            assertEquals(Arrays.asList(2), result.get("o").getPages());
        }
        try (PDDocument doc = open(pdf)) {
            // selecting only page 1 excludes it even with the fallback
            Map<String, FormFieldValue> result = new AcroFormExtractor(doc, Arrays.asList(1))
                    .extract(Arrays.asList(mapping("orphan", "o", FormFieldType.TEXT)));
            assertNull(result.get("o").getValue());
        }
    }
}
