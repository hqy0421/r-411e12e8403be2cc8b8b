package technology.tabula.form;

import java.io.IOException;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Serializes extracted AcroForm values to JSON. Each output column becomes a
 * key holding a {@code value} (string, boolean, array of strings or null) and
 * a {@code pages} array of 1-based page numbers.
 */
public class FormJsonWriter {

    private final Gson gson;

    public FormJsonWriter() {
        // Null values must be emitted explicitly, distinguishing a field that
        // is absent from the selected pages from an empty text field.
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    public void write(Appendable out, Map<String, FormFieldValue> values) throws IOException {
        out.append(gson.toJson(toJsonObject(values)));
    }

    public String writeAsString(Map<String, FormFieldValue> values) {
        return gson.toJson(toJsonObject(values));
    }

    private JsonObject toJsonObject(Map<String, FormFieldValue> values) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, FormFieldValue> entry : values.entrySet()) {
            FormFieldValue fieldValue = entry.getValue();
            JsonObject object = new JsonObject();
            object.add("value", gson.toJsonTree(fieldValue.getValue()));
            object.add("pages", gson.toJsonTree(fieldValue.getPages()));
            root.add(entry.getKey(), object);
        }
        return root;
    }
}
