package com.quizplatform.auth.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    @Test
    void shouldReturnEmptyListForValidPassword() {
        List<String> errors = PasswordValidator.validate("Password1");
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForStrongPassword() {
        List<String> errors = PasswordValidator.validate("MyStr0ngP@ss");
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldRejectPasswordShorterThan8Characters() {
        List<String> errors = PasswordValidator.validate("Pass1");
        assertThat(errors).contains("Password must be at least 8 characters long");
    }

    @Test
    void shouldRejectPasswordWithoutUppercase() {
        List<String> errors = PasswordValidator.validate("password1");
        assertThat(errors).contains("Password must contain at least one uppercase letter");
    }

    @Test
    void shouldRejectPasswordWithoutLowercase() {
        List<String> errors = PasswordValidator.validate("PASSWORD1");
        assertThat(errors).contains("Password must contain at least one lowercase letter");
    }

    @Test
    void shouldRejectPasswordWithoutDigit() {
        List<String> errors = PasswordValidator.validate("Password");
        assertThat(errors).contains("Password must contain at least one digit");
    }

    @Test
    void shouldReturnMultipleErrorsForWeakPassword() {
        List<String> errors = PasswordValidator.validate("abc");
        assertThat(errors).hasSize(3);
        assertThat(errors).contains("Password must be at least 8 characters long");
        assertThat(errors).contains("Password must contain at least one uppercase letter");
        assertThat(errors).contains("Password must contain at least one digit");
    }

    @Test
    void shouldRejectNullPassword() {
        List<String> errors = PasswordValidator.validate(null);
        assertThat(errors).hasSize(4);
    }

    @Test
    void shouldAcceptExactly8CharacterPassword() {
        List<String> errors = PasswordValidator.validate("Abcdef1x");
        assertThat(errors).isEmpty();
    }
}
