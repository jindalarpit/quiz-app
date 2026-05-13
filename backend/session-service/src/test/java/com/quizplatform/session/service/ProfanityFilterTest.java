package com.quizplatform.session.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfanityFilterTest {

    private ProfanityFilter profanityFilter;

    @BeforeEach
    void setUp() {
        profanityFilter = new ProfanityFilter();
    }

    @Test
    @DisplayName("Returns false for clean nicknames")
    void cleanNicknames() {
        assertThat(profanityFilter.containsProfanity("Player One")).isFalse();
        assertThat(profanityFilter.containsProfanity("QuizMaster")).isFalse();
        assertThat(profanityFilter.containsProfanity("John123")).isFalse();
        assertThat(profanityFilter.containsProfanity("Cool Player")).isFalse();
    }

    @Test
    @DisplayName("Returns true for profane words")
    void profaneWords() {
        assertThat(profanityFilter.containsProfanity("fuck")).isTrue();
        assertThat(profanityFilter.containsProfanity("shit")).isTrue();
        assertThat(profanityFilter.containsProfanity("damn")).isTrue();
    }

    @Test
    @DisplayName("Case-insensitive detection")
    void caseInsensitive() {
        assertThat(profanityFilter.containsProfanity("FUCK")).isTrue();
        assertThat(profanityFilter.containsProfanity("Shit")).isTrue();
        assertThat(profanityFilter.containsProfanity("DaMn")).isTrue();
    }

    @Test
    @DisplayName("Detects profanity within multi-word text")
    void multiWordText() {
        assertThat(profanityFilter.containsProfanity("my shit name")).isTrue();
        assertThat(profanityFilter.containsProfanity("fuck you")).isTrue();
    }

    @Test
    @DisplayName("Detects profanity embedded without spaces")
    void embeddedProfanity() {
        assertThat(profanityFilter.containsProfanity("myfuckname")).isTrue();
    }

    @Test
    @DisplayName("Returns false for null or blank input")
    void nullOrBlank() {
        assertThat(profanityFilter.containsProfanity(null)).isFalse();
        assertThat(profanityFilter.containsProfanity("")).isFalse();
        assertThat(profanityFilter.containsProfanity("   ")).isFalse();
    }
}
