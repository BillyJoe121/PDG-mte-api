package co.edu.icesi.pdg.mte.common;

/**
 * Formats CSV cells for exports opened in spreadsheets. Values are always quoted, and values
 * that a spreadsheet would run as a formula get a leading apostrophe so they stay plain text.
 */
public final class CsvCells {
    private CsvCells() {
    }

    public static String quote(String value) {
        if (value == null) {
            return "";
        }
        String text = startsLikeFormula(value) ? "'" + value : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    static boolean startsLikeFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
