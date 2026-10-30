package com.nightbeam.omnivault.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Formatting {
    private static final String[] UNITS = {"", "K", "M", "B", "T", "Q"};
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    private Formatting() {
    }

    /** 999, 1.2K, 15K, 150K, 1.5M ... at most four characters, for slot overlays. */
    public static String compact(long value) {
        if (value < 1000) {
            return Long.toString(value);
        }
        int unit = 0;
        double scaled = value;
        while (scaled >= 1000 && unit < UNITS.length - 1) {
            scaled /= 1000;
            unit++;
        }
        // Round down so a count never reads higher than it is.
        double shown = scaled < 10 ? Math.floor(scaled * 10) / 10 : Math.floor(scaled);
        return new DecimalFormat(scaled < 10 ? "0.#" : "0", SYMBOLS).format(shown) + UNITS[unit];
    }

    /** 1,234,567 */
    public static String full(long value) {
        return new DecimalFormat("#,##0", SYMBOLS).format(value);
    }
}
