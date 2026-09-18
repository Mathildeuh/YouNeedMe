package fr.mathildeuh.youneedme.api.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EconomyResultTest {

    @Test
    void successCarriesTheResultingBalanceAndNoMessage() {
        EconomyResult result = EconomyResult.success(150.0);

        assertTrue(result.isSuccess());
        assertEquals(EconomyResult.Status.SUCCESS, result.status());
        assertEquals(150.0, result.balanceAfter());
        assertNull(result.message());
    }

    @Test
    void failureCarriesTheStatusCurrentBalanceAndAReason() {
        EconomyResult result =
                EconomyResult.failure(
                        EconomyResult.Status.INSUFFICIENT_FUNDS, 10.0, "not enough money");

        assertFalse(result.isSuccess());
        assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.status());
        assertEquals(10.0, result.balanceAfter());
        assertEquals("not enough money", result.message());
    }
}
