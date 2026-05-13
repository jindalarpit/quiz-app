package com.quizplatform.session.service;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Simple word-based profanity filter.
 * Checks if the given text contains any profane words (case-insensitive).
 */
@Service
public class ProfanityFilter {

    private static final Set<String> PROFANE_WORDS = Set.of(
            "ass", "bastard", "bitch", "damn", "dick", "fuck", "hell",
            "shit", "crap", "piss", "cock", "cunt", "whore", "slut",
            "nigger", "faggot", "retard", "twat", "wanker", "bollocks"
    );

    /**
     * Check if the given text contains profanity (case-insensitive).
     * Splits text into words and checks each word against the profanity list.
     *
     * @param text the text to check
     * @return true if profanity is detected, false otherwise
     */
    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String lowerText = text.toLowerCase().trim();

        // Check each word in the text
        String[] words = lowerText.split("\\s+");
        for (String word : words) {
            if (PROFANE_WORDS.contains(word)) {
                return true;
            }
        }

        // Also check if the entire text (without spaces) contains profanity
        String noSpaces = lowerText.replaceAll("\\s+", "");
        for (String profaneWord : PROFANE_WORDS) {
            if (noSpaces.contains(profaneWord)) {
                return true;
            }
        }

        return false;
    }
}
