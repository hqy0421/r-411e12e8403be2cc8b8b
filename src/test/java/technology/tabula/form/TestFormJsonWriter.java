package technology.tabula.form;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestFormJsonWriter {

    @Test
    public void serializesAllValueKindsAndNulls() throws IOException {
        Map<String, FormFieldValue> values = new LinkedHashMap<>();
        values.put("text", new FormFieldValue("ACME Ltd", Arrays.asList(1)));
        values.put("empty_text", new FormFieldValue("", Arrays.asList(1)));
        values.put("checked", new FormFieldValue(Boolean.TRUE, Arrays.asList(2)));
        values.put("unchecked", new FormFieldValue(Boolean.FALSE, Arrays.asList(3)));
        values.put("choice", new FormFieldValue(Arrays.asList("CIF", "EXW"), Arrays.asList(1, 2)));
        values.put("empty_choice", new FormFieldValue(Arrays.asList(), Arrays.asList(1)));
        values.put("absent", new FormFieldValue(null, Arrays.asList()));

        StringBuilder out = new StringBuilder();
        new FormJsonWriter().write(out, values);

        JsonObject root = JsonParser.parseString(out.toString()).getAsJsonObject();

        JsonObject text = root.getAsJsonObject("text");
        assertEquals("ACME Ltd", text.get("value").getAsString());
        assertEquals(1, text.getAsJsonArray("pages").get(0).getAsInt());

        // empty string is preserved, not coerced to null
        assertEquals("", root.getAsJsonObject("empty_text").get("value").getAsString());

        // booleans
        assertEquals(true, root.getAsJsonObject("checked").get("value").getAsBoolean());
        assertEquals(false, root.getAsJsonObject("unchecked").get("value").getAsBoolean());

        // arrays preserve order
        JsonElement choiceValue = root.getAsJsonObject("choice").get("value");
        assertTrue(choiceValue.isJsonArray());
        assertEquals("CIF", choiceValue.getAsJsonArray().get(0).getAsString());
        assertEquals("EXW", choiceValue.getAsJsonArray().get(1).getAsString());
        assertEquals(0, root.getAsJsonObject("empty_choice").get("value").getAsJsonArray().size());

        // null value is emitted explicitly
        assertTrue(root.getAsJsonObject("absent").get("value").isJsonNull());
        assertEquals(0, root.getAsJsonObject("absent").getAsJsonArray("pages").size());
    }

    @Test
    public void keepsInsertionOrder() throws IOException {
        Map<String, FormFieldValue> values = new LinkedHashMap<>();
        values.put("z_first", new FormFieldValue("1", Arrays.asList(1)));
        values.put("a_second", new FormFieldValue("2", Arrays.asList(1)));
        String json = new FormJsonWriter().writeAsString(values);
        assertTrue(json.indexOf("z_first") < json.indexOf("a_second"));
    }
}
