package technology.tabula.form;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Parses the JSON form extraction configuration. The configuration is an
 * object with a {@code fields} array; each element maps a full field path to
 * an output column and an output type:
 *
 * <pre>{@code
 * {
 *   "fields": [
 *     {"path": ["shipper", "name"], "column": "shipper_name", "type": "text"},
 *     {"path": "consignee.name",       "column": "consignee_name", "type": "text"}
 *   ]
 * }
 * }</pre>
 *
 * A path may be written either as an array of partial names or as a dotted
 * string. Same-named fields under different parents are distinguished by their
 * full paths.
 */
public final class FormConfig {

    private static final String FIELDS_KEY = "fields";
    private static final String PATH_KEY = "path";
    private static final String COLUMN_KEY = "column";
    private static final String TYPE_KEY = "type";

    private FormConfig() {
    }

    public static List<FormFieldMapping> parse(Reader reader) throws FormExtractionException {
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (JsonParseException e) {
            throw new FormExtractionException("Form mapping configuration is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || root.isJsonNull()) {
            throw new FormExtractionException("Form mapping configuration is empty.");
        }
        if (!root.isJsonObject()) {
            throw new FormExtractionException(
                    "Form mapping configuration must be a JSON object containing a \"fields\" array.");
        }

        JsonObject rootObject = root.getAsJsonObject();
        if (!rootObject.has(FIELDS_KEY) || !rootObject.get(FIELDS_KEY).isJsonArray()) {
            throw new FormExtractionException(
                    "Form mapping configuration must contain a \"fields\" array.");
        }

        JsonArray fields = rootObject.getAsJsonArray(FIELDS_KEY);
        List<FormFieldMapping> mappings = new ArrayList<>(fields.size());
        Set<String> seenPaths = new HashSet<>();
        Set<String> seenColumns = new HashSet<>();

        for (int i = 0; i < fields.size(); i++) {
            JsonElement element = fields.get(i);
            String entryLabel = "fields[" + i + "]";
            if (!element.isJsonObject()) {
                throw new FormExtractionException(entryLabel + " must be a JSON object.");
            }
            JsonObject entry = element.getAsJsonObject();

            List<String> path = parsePath(entry, entryLabel);
            if (path.isEmpty()) {
                throw new FormExtractionException(entryLabel + " has an empty field path.");
            }
            String column = requireString(entry, COLUMN_KEY, entryLabel);
            if (column.isEmpty()) {
                throw new FormExtractionException(entryLabel + " has an empty output column.");
            }
            String typeName = requireString(entry, TYPE_KEY, entryLabel);
            FormFieldType type = FormFieldType.fromConfigName(typeName);

            String fullPath = String.join(".", path);
            if (!seenPaths.add(fullPath)) {
                throw new FormExtractionException(
                        "Duplicate field path \"" + fullPath + "\" in form mapping configuration.");
            }
            if (!seenColumns.add(column)) {
                throw new FormExtractionException(
                        "Duplicate output column \"" + column + "\" in form mapping configuration.");
            }

            mappings.add(new FormFieldMapping(path, column, type));
        }

        if (mappings.isEmpty()) {
            throw new FormExtractionException(
                    "Form mapping configuration contains no field mappings.");
        }
        return mappings;
    }

    public static List<FormFieldMapping> parseFile(java.io.File configFile) throws FormExtractionException {
        try (Reader reader = new java.io.InputStreamReader(
                new java.io.FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new FormExtractionException(
                    "Cannot read form mapping configuration " + configFile + ": " + e.getMessage(), e);
        }
    }

    private static List<String> parsePath(JsonObject entry, String entryLabel) throws FormExtractionException {
        if (!entry.has(PATH_KEY)) {
            throw new FormExtractionException(entryLabel + " is missing the required \"" + PATH_KEY + "\" value.");
        }
        JsonElement pathElement = entry.get(PATH_KEY);
        List<String> path = new ArrayList<>();
        if (pathElement.isJsonArray()) {
            JsonArray array = pathElement.getAsJsonArray();
            for (JsonElement part : array) {
                if (part == null || part.isJsonNull() || !part.getAsJsonPrimitive().isString()) {
                    throw new FormExtractionException(
                            entryLabel + " path entries must all be strings.");
                }
                String name = part.getAsString();
                if (name.isEmpty()) {
                    throw new FormExtractionException(entryLabel + " contains an empty path segment.");
                }
                path.add(name);
            }
        } else if (pathElement.isJsonPrimitive() && pathElement.getAsJsonPrimitive().isString()) {
            String dotted = pathElement.getAsString();
            if (dotted.isEmpty()) {
                throw new FormExtractionException(entryLabel + " has an empty field path.");
            }
            for (String part : dotted.split("\\.", -1)) {
                if (part.isEmpty()) {
                    throw new FormExtractionException(
                            entryLabel + " path \"" + dotted + "\" contains an empty segment.");
                }
                path.add(part);
            }
        } else {
            throw new FormExtractionException(
                    entryLabel + " \"" + PATH_KEY + "\" must be a string or an array of strings.");
        }
        return path;
    }

    private static String requireString(JsonObject entry, String key, String entryLabel)
            throws FormExtractionException {
        if (!entry.has(key) || entry.get(key).isJsonNull()) {
            throw new FormExtractionException(entryLabel + " is missing the required \"" + key + "\" value.");
        }
        JsonElement value = entry.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new FormExtractionException(entryLabel + " \"" + key + "\" must be a string.");
        }
        return value.getAsString();
    }
}
