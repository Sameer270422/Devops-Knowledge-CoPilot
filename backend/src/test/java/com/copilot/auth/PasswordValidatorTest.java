package com.copilot.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void nullPasswordIsInvalid() {
        assertFalse(validator.isValid(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1!",           // too short (7 chars)
            "alllowercase123!",  // no uppercase
            "NoDigitsHere!!",    // no digit
            "NoSymbolsHere123",  // no symbol
    })
    void rejectsPasswordsMissingOneRequirement(String password) {
        assertFalse(validator.isValid(password, null), "expected rejected: " + password);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Correct123!",
            "AnotherGood1#",
            "Complex$Pass9word",
    })
    void acceptsPasswordsMeetingAllFourRequirements(String password) {
        assertTrue(validator.isValid(password, null), "expected accepted: " + password);
    }

    @Test
    void exactlyTenCharactersWithAllRequirementsIsAccepted() {
        // Boundary check on the length requirement specifically - 10 is the minimum, not
        // "more than 10".
        String password = "Abcdefg1$x"; // exactly 10 chars
        assertTrue(password.length() == 10);
        assertTrue(validator.isValid(password, null));
    }

    @Test
    void nineCharactersIsRejectedEvenWithAllOtherRequirementsMet() {
        String password = "Abcdefg1$"; // exactly 9 chars
        assertTrue(password.length() == 9);
        assertFalse(validator.isValid(password, null));
    }
}
