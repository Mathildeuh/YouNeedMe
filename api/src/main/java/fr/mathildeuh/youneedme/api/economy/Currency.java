package fr.mathildeuh.youneedme.api.economy;

/**
 * A currency YouNeedMe's economy can hold a balance in. Servers running a single currency (the
 * default) never need to think about this type at all - every {@link EconomyService} method has
 * an overload that assumes {@link #id()} {@code "default"}.
 */
public record Currency(String id, String symbol, String singularName, String pluralName, int decimalPlaces) {

    public String format(double amount) {
        String number = String.format("%,." + decimalPlaces + "f", amount);
        return symbol.isEmpty() ? number + " " + (amount == 1.0 ? singularName : pluralName) : symbol + number;
    }
}
