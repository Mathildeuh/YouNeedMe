package fr.mathildeuh.youneedme.api.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurrencyTest {

    @Test
    void formatsWithASymbolAndTheConfiguredDecimalPlaces() {
        Currency dollars = new Currency("default", "$", "dollar", "dollars", 2);
        assertEquals("$1,234.50", dollars.format(1234.5));
    }

    @Test
    void zeroDecimalPlacesRoundsToWholeNumbers() {
        Currency coins = new Currency("coins", "", "coin", "coins", 0);
        assertEquals("5 coins", coins.format(5.0));
    }

    @Test
    void singularIsUsedForExactlyOneUnitWhenThereIsNoSymbol() {
        Currency coins = new Currency("coins", "", "coin", "coins", 0);
        assertEquals("1 coin", coins.format(1.0));
    }

    @Test
    void aSymbolIsUsedInsteadOfTheWordFormEvenForOneUnit() {
        Currency dollars = new Currency("default", "$", "dollar", "dollars", 2);
        assertEquals("$1.00", dollars.format(1.0));
    }
}
