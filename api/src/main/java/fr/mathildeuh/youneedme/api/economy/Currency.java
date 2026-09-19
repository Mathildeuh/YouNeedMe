package fr.mathildeuh.youneedme.api.economy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * A currency YouNeedMe's economy can hold a balance in. Servers running a single currency (the
 * default) never need to think about this type at all - every {@link EconomyService} method has an
 * overload that assumes {@link #id()} {@code "default"}.
 */
public record Currency(
        String id, String symbol, String singularName, String pluralName, int decimalPlaces) {

    public String format(double amount) {
        String number = formatNumber(amount);
        return symbol.isEmpty()
                ? number + " " + (amount == 1.0 ? singularName : pluralName)
                : symbol + number;
    }

    /**
     * Just the grouped, fixed-precision number (no symbol/name) - for templates that place the
     * currency symbol/name separately. Built with {@link DecimalFormat} rather than a printf-style
     * pattern assembled from {@link #decimalPlaces} at runtime, since a pattern string built that
     * way can't be statically verified as well-formed.
     */
    public String formatNumber(double amount) {
        // Locale.ROOT, not the JVM default: a balance's digit grouping/decimal separator must not
        // change depending on the host machine's system locale.
        DecimalFormat formatter = new DecimalFormat();
        formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
        formatter.setGroupingUsed(true);
        int places = Math.max(0, decimalPlaces);
        formatter.setMinimumFractionDigits(places);
        formatter.setMaximumFractionDigits(places);
        return formatter.format(amount);
    }
}
