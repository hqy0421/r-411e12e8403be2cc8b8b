package technology.tabula.form;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import technology.tabula.CommandLineApp;

public class TestCommandLineFormExtraction {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File buildPdf() throws Exception {
        TestFormPdfBuilder builder = TestFormPdfBuilder.create(2);
        builder.page(1).addTextField("declaration", "Export declaration", 1);
        builder.page(1).addTextField("remarks", "", 1);
        builder.page(2).addCheckBox("insured", true, 2);
        builder.page(1).addComboBox("incoterm", Arrays.asList("FOB", "CIF"), "CIF", 1);
        builder.page(1).addListBox("terms",
                Arrays.asList("EXW", "FOB", "CIF"), Arrays.asList("CIF", "EXW"), 1);
        builder.page(2).addTextField("page2only", "second page", 2);
        return builder.save(folder.getRoot(), "customs.pdf");
    }

    private File writeConfig(String json) throws Exception {
        File config = folder.newFile("mapping.json");
        Files.write(config.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private String runApp(File pdf, File config, File formOut, String... extra) throws ParseException {
        StringBuilder out = new StringBuilder();
        String[] base;
        if (formOut == null) {
            base = new String[]{pdf.getAbsolutePath(), "--form-config", config.getAbsolutePath(),
                    "-p", "1-2"};
        } else {
            base = new String[]{pdf.getAbsolutePath(), "--form-config", config.getAbsolutePath(),
                    "--form-out", formOut.getAbsolutePath(), "-p", "1-2"};
        }
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), args);
        new CommandLineApp(out, cmd).extractTables(cmd);
        return out.toString();
    }

    @Test
    public void writesFormJsonAndKeepsTableOutputOnStdout() throws Exception {
        File pdf = buildPdf();
        File config = writeConfig("{\"fields\": ["
                + "{\"path\": \"declaration\", \"column\": \"declaration\", \"type\": \"text\"},"
                + "{\"path\": \"remarks\", \"column\": \"remarks\", \"type\": \"text\"},"
                + "{\"path\": \"insured\", \"column\": \"insured\", \"type\": \"checkbox\"},"
                + "{\"path\": \"incoterm\", \"column\": \"incoterm\", \"type\": \"choice\"},"
                + "{\"path\": \"terms\", \"column\": \"terms\", \"type\": \"choice\"},"
                + "{\"path\": \"page2only\", \"column\": \"page2\", \"type\": \"text\"}"
                + "]}");
        File formOut = new File(folder.getRoot(), "forms.json");

        String stdout = runApp(pdf, config, formOut);

        // table extraction still runs and goes to stdout (empty here: the
        // generated form PDF has no tabular ruling lines)
        assertNotNull(stdout);
        assertTrue(formOut.exists());

        JsonObject root = JsonParser.parseString(
                new String(Files.readAllBytes(formOut.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("Export declaration",
                root.getAsJsonObject("declaration").get("value").getAsString());
        assertEquals(1, root.getAsJsonObject("declaration").getAsJsonArray("pages").get(0).getAsInt());

        // empty text preserved
        assertEquals("", root.getAsJsonObject("remarks").get("value").getAsString());

        // checkbox
        assertEquals(true, root.getAsJsonObject("insured").get("value").getAsBoolean());
        assertEquals(2, root.getAsJsonObject("insured").getAsJsonArray("pages").get(0).getAsInt());

        // single and multi choice values in storage order
        assertEquals("CIF",
                root.getAsJsonObject("incoterm").get("value").getAsJsonArray().get(0).getAsString());
        assertEquals("CIF",
                root.getAsJsonObject("terms").get("value").getAsJsonArray().get(0).getAsString());
        assertEquals("EXW",
                root.getAsJsonObject("terms").get("value").getAsJsonArray().get(1).getAsString());

        // field on page 2 is included because both pages are selected
        assertEquals("second page", root.getAsJsonObject("page2").get("value").getAsString());
    }

    @Test
    public void pageSelectionRestrictsFormValuesToNull() throws Exception {
        File pdf = buildPdf();
        File config = writeConfig("{\"fields\": ["
                + "{\"path\": \"page2only\", \"column\": \"page2\", \"type\": \"text\"},"
                + "{\"path\": \"declaration\", \"column\": \"declaration\", \"type\": \"text\"}"
                + "]}");
        File formOut = new File(folder.getRoot(), "p1.json");

        // restrict to page 1 via the existing -p option
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), new String[]{
                pdf.getAbsolutePath(), "--form-config", config.getAbsolutePath(),
                "--form-out", formOut.getAbsolutePath(), "-p", "1"});
        new CommandLineApp(new StringBuilder(), cmd).extractTables(cmd);

        JsonObject root = JsonParser.parseString(
                new String(Files.readAllBytes(formOut.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.getAsJsonObject("page2").get("value").isJsonNull());
        assertEquals(0, root.getAsJsonObject("page2").getAsJsonArray("pages").size());
        assertEquals("Export declaration",
                root.getAsJsonObject("declaration").get("value").getAsString());
    }

    @Test
    public void defaultsToSiblingFormFileWhenNoFormOutGiven() throws Exception {
        File pdf = buildPdf();
        File config = writeConfig("{\"fields\": ["
                + "{\"path\": \"declaration\", \"column\": \"declaration\", \"type\": \"text\"}]}");

        runApp(pdf, config, null);

        File defaultOut = new File(folder.getRoot(), "customs.form.json");
        assertTrue(defaultOut.exists());
        JsonObject root = JsonParser.parseString(
                new String(Files.readAllBytes(defaultOut.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Export declaration",
                root.getAsJsonObject("declaration").get("value").getAsString());
    }

    @Test
    public void typeMismatchFailsWithExplicitMessage() throws Exception {
        File pdf = buildPdf();
        File config = writeConfig("{\"fields\": ["
                + "{\"path\": \"declaration\", \"column\": \"bad\", \"type\": \"checkbox\"}]}");
        File formOut = new File(folder.getRoot(), "bad.json");

        ParseException e = assertThrows(ParseException.class, () -> runApp(pdf, config, formOut));
        assertTrue(e.getMessage().contains("declaration"));
        assertTrue(e.getMessage().contains("checkbox"));
    }

    @Test
    public void unknownTypeInConfigFails() throws Exception {
        File pdf = buildPdf();
        File config = writeConfig("{\"fields\": ["
                + "{\"path\": \"declaration\", \"column\": \"bad\", \"type\": \"radio\"}]}");
        File formOut = new File(folder.getRoot(), "bad.json");

        ParseException e = assertThrows(ParseException.class, () -> runApp(pdf, config, formOut));
        assertTrue(e.getMessage().contains("Unsupported form field type"));
    }

    @Test
    public void missingConfigFileFails() throws Exception {
        File pdf = buildPdf();
        File missing = new File(folder.getRoot(), "nope.json");
        File formOut = new File(folder.getRoot(), "out.json");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), new String[]{
                pdf.getAbsolutePath(), "--form-config", missing.getAbsolutePath(),
                "--form-out", formOut.getAbsolutePath()});
        ParseException e = assertThrows(ParseException.class, () ->
                new CommandLineApp(new StringBuilder(), cmd).extractTables(cmd));
        assertTrue(e.getMessage().contains("does not exist"));
    }
}
