package technology.tabula.form;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class TestFormConfig {

    private List<FormFieldMapping> parse(String json) throws FormExtractionException {
        return FormConfig.parse(new StringReader(json));
    }

    @Test
    public void acceptsArrayPathsAndDottedPaths() throws Exception {
        List<FormFieldMapping> mappings = parse(
                "{\"fields\": [" +
                "{\"path\": [\"shipper\", \"name\"], \"column\": \"a\", \"type\": \"text\"}," +
                "{\"path\": \"consignee.name\", \"column\": \"b\", \"type\": \"checkbox\"}," +
                "{\"path\": \"terms\", \"column\": \"c\", \"type\": \"CHOICE\"}" +
                "]}");

        assertEquals(3, mappings.size());
        assertEquals(java.util.Arrays.asList("shipper", "name"), mappings.get(0).getPath());
        assertEquals("shipper.name", mappings.get(0).getFullPath());
        assertEquals("a", mappings.get(0).getOutputColumn());
        assertEquals(FormFieldType.TEXT, mappings.get(0).getType());

        assertEquals(java.util.Arrays.asList("consignee", "name"), mappings.get(1).getPath());
        assertEquals(FormFieldType.CHECKBOX, mappings.get(1).getType());
        // type names are case-insensitive
        assertEquals(FormFieldType.CHOICE, mappings.get(2).getType());
    }

    @Test
    public void rejectsDuplicateFieldPaths() {
        FormExtractionException e = assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [" +
                "{\"path\": \"a.b\", \"column\": \"x\", \"type\": \"text\"}," +
                "{\"path\": [\"a\", \"b\"], \"column\": \"y\", \"type\": \"text\"}" +
                "]}"));
        assertTrue(e.getMessage().contains("Duplicate field path"));
    }

    @Test
    public void rejectsDuplicateOutputColumns() {
        FormExtractionException e = assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [" +
                "{\"path\": \"a\", \"column\": \"same\", \"type\": \"text\"}," +
                "{\"path\": \"b\", \"column\": \"same\", \"type\": \"text\"}" +
                "]}"));
        assertTrue(e.getMessage().contains("Duplicate output column"));
    }

    @Test
    public void rejectsUnsupportedType() {
        FormExtractionException e = assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [{\"path\": \"a\", \"column\": \"x\", \"type\": \"radio\"}]}"));
        assertTrue(e.getMessage().contains("Unsupported form field type"));
    }

    @Test
    public void rejectsMalformedJson() {
        assertThrows(FormExtractionException.class, () -> parse("{not json"));
    }

    @Test
    public void rejectsMissingFieldsArray() {
        FormExtractionException e = assertThrows(FormExtractionException.class, () -> parse("{}"));
        assertTrue(e.getMessage().contains("fields"));
    }

    @Test
    public void rejectsEmptyPathSegment() {
        assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [{\"path\": \"a..b\", \"column\": \"x\", \"type\": \"text\"}]}"));
    }

    @Test
    public void rejectsMissingRequiredKeys() {
        assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [{\"column\": \"x\", \"type\": \"text\"}]}"));
        assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [{\"path\": \"a\", \"type\": \"text\"}]}"));
        assertThrows(FormExtractionException.class, () -> parse(
                "{\"fields\": [{\"path\": \"a\", \"column\": \"x\"}]}"));
    }

    @Test
    public void rejectsEmptyMappingList() {
        assertThrows(FormExtractionException.class, () -> parse("{\"fields\": []}"));
    }
}
