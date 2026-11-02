package lab.mimi.station3.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDemandValidatorTest {
    @Test void acceptsSuppliedRecipeSevenDemandAndOrder() {
        assertDoesNotThrow(() -> RecipeDemandValidator.requireSafe(20, 0, 0, 50, 70, "T400>T100"));
    }

    @Test void rejectsUnsafeOrContradictoryRecipeDemand() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(20, 0, 10, 50, 80, "T300>T400>T100")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(40, 0, 0, 50, 90, "T400>T100")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(20, 55, 0, 0, 75, "T200>T100")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(5, 0, 0, 50, 55, "T400>T100")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(20, 0, 0, 50, 60, "T400>T100")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RecipeDemandValidator.requireSafe(20, 10, 0, 50, 80, "T400>T200>T100")));
    }
}
