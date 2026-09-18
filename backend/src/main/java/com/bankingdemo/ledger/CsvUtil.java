package com.bankingdemo.ledger;

/** Small CSV helpers shared by any statement/export endpoint. */
public final class CsvUtil {

    private CsvUtil() {
    }

    /**
     * Neutralizes spreadsheet-formula-injection payloads (values starting with
     * =, +, -, @, tab, or CR) by prefixing a single quote, then applies normal
     * CSV quoting/escaping. Every untrusted text field written into a CSV
     * export MUST go through this.
     */
    public static String sanitizeCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean needsQuoting = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (needsQuoting) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
