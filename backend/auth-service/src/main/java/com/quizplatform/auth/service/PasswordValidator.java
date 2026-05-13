package com.quizplatform.auth.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates password strength requirements.
 * Requirements: at least 8 characters, one uppercase, one lowercase, one digit.
 */
public class PasswordValidator {

    private PasswordValidator() {
        // Utility class
    }

    /**
     * Validates the given password against strength requirements.
     *
     * @param password the password to validate
     * @return a list of validation error messages (empty if valid)
     */
    public static List<String> validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.length() < 8) {
            errors.add("Password must be at least 8 characters long");
        }

        if (password == null || !password.chars().anyMatch(Character::isUpperCase)) {
            errors.add("Password must contain at least one uppercase letter");
        }

        if (password == null || !password.chars().anyMatch(Character::isLowerCase)) {
            errors.add("Password must contain at least one lowercase letter");
        }

        if (password == null || !password.chars().anyMatch(Character::isDigit)) {
            errors.add("Password must contain at least one digit");
        }

        return errors;
    }
}
