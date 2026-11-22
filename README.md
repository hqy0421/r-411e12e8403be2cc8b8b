tabula-java [![Build Status](https://travis-ci.org/tabulapdf/tabula-java.svg?branch=master)](https://travis-ci.org/tabulapdf/tabula-java)
===========

`tabula-java` is a library for extracting tables from PDF files — it is the table extraction engine that powers [Tabula](http://tabula.technology/) ([repo](http://github.com/tabulapdf/tabula)). You can use `tabula-java` as a command-line tool to programmatically extract tables from PDFs.

© 2014-2020 Manuel Aristarán. Available under MIT License. See [`LICENSE`](LICENSE).

## Download

Download a version of the tabula-java's jar, with all dependencies included, that works on Mac, Windows and Linux from our [releases page](../../releases).

## Commandline Usage Examples

`tabula-java` provides a command line application:

```
$ java -jar target/tabula-1.0.5-jar-with-dependencies.jar --help
usage: tabula [-a <AREA>] [-b <DIRECTORY>] [-c <COLUMNS>] [-f <FORMAT>]
       [-g] [-h] [-i] [-l] [-n] [-o <OUTFILE>] [-p <PAGES>] [-r] [-s
       <PASSWORD>] [-t] [-u] [-v]

Tabula helps you extract tables from PDFs

 -a,--area <AREA>           -a/--area = Portion of the page to analyze.
                            Example: --area 269.875,12.75,790.5,561.
                            Accepts top,left,bottom,right i.e. y1,x1,y2,x2
                            where all values are in points relative to the
                            top left corner. If all values are between
                            0-100 (inclusive) and preceded by '%', input
                            will be taken as % of actual height or width
                            of the page. Example: --area %0,0,100,50. To
                            specify multiple areas, -a option should be
                            repeated. Default is entire page
 -b,--batch <DIRECTORY>     Convert all .pdfs in the provided directory.
 -c,--columns <COLUMNS>     X coordinates of column boundaries. Example
                            --columns 10.1,20.2,30.3. If all values are
                            between 0-100 (inclusive) and preceded by '%',
                            input will be taken as % of actual width of
                            the page. Example: --columns %25,50,80.6
 -f,--format <FORMAT>       Output format: (CSV,TSV,JSON). Default: CSV
 -g,--guess                 Guess the portion of the page to analyze per
                            page.
 -h,--help                  Print this help text.
 -i,--silent                Suppress all stderr output.
 -l,--lattice               Force PDF to be extracted using lattice-mode
                            extraction (if there are ruling lines
                            separating each cell, as in a PDF of an Excel
                            spreadsheet)
 -n,--no-spreadsheet        [Deprecated in favor of -t/--stream] Force PDF
                            not to be extracted using spreadsheet-style
                            extraction (if there are no ruling lines
                            separating each cell)
 -o,--outfile <OUTFILE>     Write output to <file> instead of STDOUT.
                            Default: -
 -p,--pages <PAGES>         Comma separated list of ranges, or all.
                            Examples: --pages 1-3,5-7, --pages 3 or
                            --pages all. Default is --pages 1
 -r,--spreadsheet           [Deprecated in favor of -l/--lattice] Force
                            PDF to be extracted using spreadsheet-style
                            extraction (if there are ruling lines
                            separating each cell, as in a PDF of an Excel
                            spreadsheet)
 -s,--password <PASSWORD>   Password to decrypt document. Default is empty
 -t,--stream                Force PDF to be extracted using stream-mode
                            extraction (if there are no ruling lines
                            separating each cell)
 -u,--use-line-returns      Use embedded line returns in cells. (Only in
                            spreadsheet mode.)
 -v,--version               Print version and exit.
```

It also includes a debugging tool, run `java -cp ./target/tabula-1.0.5-jar-with-dependencies.jar technology.tabula.debug.Debug -h` for the available options.

You can also integrate `tabula-java` with any JVM language. For Java examples, see the [`tests`](src/test/java/technology/tabula/) folder.

## AcroForm field extraction

Some fillable PDFs (AcroForms) hold values in form controls that are never
drawn into the page text, so ordinary table extraction cannot see them.
`tabula-java` can optionally extract those form values as JSON, independently
of its table extraction.

Enable it with `--form-config <FILE>`, pointing at a JSON mapping that pairs
each field's **full path** with an output column and a value type:

```json
{
  "fields": [
    {"path": ["shipper", "name"], "column": "shipper_name", "type": "text"},
    {"path": "consignee.name",    "column": "consignee_name", "type": "text"},
    {"path": "hazard",            "column": "hazard", "type": "checkbox"},
    {"path": "terms",             "column": "terms", "type": "choice"}
  ]
}
```

- `path` is the field's full path from the form root. It may be written as an
  array of partial names or as a dotted string. Same-named fields under
  different parents stay distinct because the full path differs.
- `type` is one of `text`, `checkbox` or `choice` (case-insensitive).
  Text fields produce JSON strings, check boxes booleans, and combo/list boxes
  arrays of export values in storage order.
- The same `--pages` selection used for tables applies: only fields that have
  a widget annotation on a selected page are read. A field with several
  widgets yields one value plus the deduplicated, sorted list of selected
  pages carrying those widgets.
- Missing values are `null`. An empty text field stays `""`, an unchecked box
  stays `false`, and a choice with nothing selected stays `[]`.
- A full path that resolves to more than one field, or a type that does not
  match the field, aborts the run with an explicit error. Push buttons, radio
  buttons and signature fields are not supported.

The form result is always written to a file. Use `--form-out <FILE>` to set
it, otherwise a sibling `<pdfname>.form.json` is created (in `--batch` mode
each PDF gets one). Table extraction is unchanged and keeps using `--format`,
`--outfile` and stdout:

```
$ java -jar tabula.jar customs.pdf --form-config mapping.json --form-out forms.json -p all
```

Each output column maps to an object with the extracted `value` and its
`pages`:

```json
{
  "shipper_name": {"value": "Globex Trading", "pages": [1]},
  "hazard":       {"value": false, "pages": [2]},
  "terms":        {"value": ["DDP", "FOB"], "pages": [1]},
  "unmapped":     {"value": null, "pages": []}
}
```


JVM start-up time is a lot of the cost of the `tabula` command, so if you're trying to extract many tables from PDFs, you have a few options for speeding it up:

 - the -b option, which allows you to convert all pdfs in a given directory
 - the [drip](https://github.com/ninjudd/drip) utility
 - the [Ruby](http://github.com/tabulapdf/tabula-extractor), [Python](https://github.com/chezou/tabula-py), [R](https://github.com/leeper/tabulizer), and [Node.js](https://github.com/ezodude/tabula-js) bindings
 - writing your own program in any JVM language (Java, JRuby, Scala) that imports tabula-java.
 - waiting for us to implement an API/server-style system (it's on the [roadmap](https://github.com/tabulapdf/tabula-api))

## API Usage Examples

A simple Java code example which extracts all rows and cells from all tables of all pages of a PDF document:

```java
InputStream in = this.getClass().getResourceAsStream("my.pdf");
try (PDDocument document = PDDocument.load(in)) {
    SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
    PageIterator pi = new ObjectExtractor(document).extract();
    while (pi.hasNext()) {
        // iterate over the pages of the document
        Page page = pi.next();
        List<Table> table = sea.extract(page);
        // iterate over the tables of the page
        for(Table tables: table) {
            List<List<RectangularTextContainer>> rows = tables.getRows();
            // iterate over the rows of the table
            for (List<RectangularTextContainer> cells : rows) {
                // print all column-cells of the row plus linefeed
                for (RectangularTextContainer content : cells) {
                    // Note: Cell.getText() uses \r to concat text chunks
                    String text = content.getText().replace("\r", " ");
                    System.out.print(text + "|");
                }
                System.out.println();
            }
        }
    }
}
```


For more detail information check the Javadoc. 
The Javadoc API documentation can be generated (see also '_Building from Source_' section) via

```
mvn javadoc:javadoc
```

which generates the HTML files to directory ```target/site/apidocs/```

## Building from Source

Clone this repo and run:

```
mvn clean compile assembly:single
```

## Contributing

Interested in helping out? We'd love to have your help!

You can help by:

- [Reporting a bug](https://github.com/tabulapdf/tabula-java/issues).
- Adding or editing documentation.
- Contributing code via a Pull Request.
- Spreading the word about `tabula-java` to people who might be able to benefit from using it.

### Backers

You can also support our continued work on `tabula-java` with a one-time or monthly donation [on OpenCollective](https://opencollective.com/tabulapdf#support). Organizations who use `tabula-java` can also [sponsor the project](https://opencollective.com/tabulapdf#support) for acknowledgement on [our official site](http://tabula.technology/) and this README.

Special thanks to the following users and organizations for generously supporting Tabula with donations and grants:

<a href="https://opencollective.com/tabulapdf/backer/0/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/0/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/1/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/1/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/2/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/2/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/3/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/3/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/4/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/4/avatar"></a>
<a href="https://opencollective.com/tabulapdf/backer/5/website" target="_blank"><img src="https://opencollective.com/tabulapdf/backer/5/avatar"></a>

<a title="The John S. and James L. Knight Foundation" href="http://www.knightfoundation.org/" target="_blank"><img alt="The John S. and James L. Knight Foundation" src="https://knightfoundation.org/wp-content/uploads/2019/10/KF_Logotype_Icon-and-Stacked-Name.png" width="300"></a>
<a title="The Shuttleworth Foundation" href="https://shuttleworthfoundation.org/" target="_blank"><img width="200" alt="The Shuttleworth Foundation" src="https://raw.githubusercontent.com/tabulapdf/tabula/gh-pages/shuttleworth.jpg"></a>
