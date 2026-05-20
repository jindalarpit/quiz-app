package com.quizplatform.session.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SnapshotEntry DTO.
 * 
 * Tests serialization to and from Redis hash value format.
 */
class SnapshotEntryTest {

    @Nested
    @DisplayName("toRedisValue")
    class ToRedisValue {

        @Test
        @DisplayName("Should serialize to correct format")
        void shouldSerializeToCorrectFormat() {
            // Given
            SnapshotEntry entry = SnapshotEntry.builder()
                    .participantId("p1")
                    .rank(1)
                    .cumulativeScore(1000)
                    .roundScore(500)
                    .avgResponseTimeMs(1500)
                    .build();

            // When
            String result = entry.toRedisValue();

            // Then
            assertThat(result).isEqualTo("1|1000|500|1500");
        }

        @Test
        @DisplayName("Should handle zero values")
        void shouldHandleZeroValues() {
            // Given
            SnapshotEntry entry = SnapshotEntry.builder()
                    .participantId("p1")
                    .rank(0)
                    .cumulativeScore(0)
                    .roundScore(0)
                    .avgResponseTimeMs(0)
                    .build();

            // When
            String result = entry.toRedisValue();

            // Then
            assertThat(result).isEqualTo("0|0|0|0");
        }

        @Test
        @DisplayName("Should handle large values")
        void shouldHandleLargeValues() {
            // Given
            SnapshotEntry entry = SnapshotEntry.builder()
                    .participantId("p1")
                    .rank(1000)
                    .cumulativeScore(Long.MAX_VALUE)
                    .roundScore(Integer.MAX_VALUE)
                    .avgResponseTimeMs(Long.MAX_VALUE)
                    .build();

            // When
            String result = entry.toRedisValue();

            // Then
            assertThat(result).isEqualTo("1000|" + Long.MAX_VALUE + "|" + Integer.MAX_VALUE + "|" + Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("fromRedisValue")
    class FromRedisValue {

        @Test
        @DisplayName("Should parse valid format correctly")
        void shouldParseValidFormatCorrectly() {
            // Given
            String participantId = "p1";
            String redisValue = "1|1000|500|1500";

            // When
            SnapshotEntry result = SnapshotEntry.fromRedisValue(participantId, redisValue);

            // Then
            assertThat(result.getParticipantId()).isEqualTo("p1");
            assertThat(result.getRank()).isEqualTo(1);
            assertThat(result.getCumulativeScore()).isEqualTo(1000);
            assertThat(result.getRoundScore()).isEqualTo(500);
            assertThat(result.getAvgResponseTimeMs()).isEqualTo(1500);
        }

        @Test
        @DisplayName("Should handle zero values")
        void shouldHandleZeroValues() {
            // Given
            String participantId = "p1";
            String redisValue = "0|0|0|0";

            // When
            SnapshotEntry result = SnapshotEntry.fromRedisValue(participantId, redisValue);

            // Then
            assertThat(result.getRank()).isEqualTo(0);
            assertThat(result.getCumulativeScore()).isEqualTo(0);
            assertThat(result.getRoundScore()).isEqualTo(0);
            assertThat(result.getAvgResponseTimeMs()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw exception for null value")
        void shouldThrowExceptionForNullValue() {
            // Given
            String participantId = "p1";

            // When/Then
            assertThatThrownBy(() -> SnapshotEntry.fromRedisValue(participantId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for empty value")
        void shouldThrowExceptionForEmptyValue() {
            // Given
            String participantId = "p1";

            // When/Then
            assertThatThrownBy(() -> SnapshotEntry.fromRedisValue(participantId, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for invalid format - too few parts")
        void shouldThrowExceptionForInvalidFormatTooFewParts() {
            // Given
            String participantId = "p1";
            String redisValue = "1|1000|500"; // Missing avgResponseTimeMs

            // When/Then
            assertThatThrownBy(() -> SnapshotEntry.fromRedisValue(participantId, redisValue))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid snapshot value format");
        }

        @Test
        @DisplayName("Should throw exception for invalid format - too many parts")
        void shouldThrowExceptionForInvalidFormatTooManyParts() {
            // Given
            String participantId = "p1";
            String redisValue = "1|1000|500|1500|extra";

            // When/Then
            assertThatThrownBy(() -> SnapshotEntry.fromRedisValue(participantId, redisValue))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid snapshot value format");
        }

        @Test
        @DisplayName("Should throw exception for non-numeric values")
        void shouldThrowExceptionForNonNumericValues() {
            // Given
            String participantId = "p1";
            String redisValue = "1|abc|500|1500";

            // When/Then
            assertThatThrownBy(() -> SnapshotEntry.fromRedisValue(participantId, redisValue))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number format");
        }
    }

    @Nested
    @DisplayName("Round-trip serialization")
    class RoundTripSerialization {

        @Test
        @DisplayName("Should preserve all values through round-trip")
        void shouldPreserveAllValuesThroughRoundTrip() {
            // Given
            SnapshotEntry original = SnapshotEntry.builder()
                    .participantId("p1")
                    .rank(5)
                    .cumulativeScore(12345)
                    .roundScore(678)
                    .avgResponseTimeMs(9876)
                    .build();

            // When
            String serialized = original.toRedisValue();
            SnapshotEntry deserialized = SnapshotEntry.fromRedisValue("p1", serialized);

            // Then
            assertThat(deserialized.getParticipantId()).isEqualTo(original.getParticipantId());
            assertThat(deserialized.getRank()).isEqualTo(original.getRank());
            assertThat(deserialized.getCumulativeScore()).isEqualTo(original.getCumulativeScore());
            assertThat(deserialized.getRoundScore()).isEqualTo(original.getRoundScore());
            assertThat(deserialized.getAvgResponseTimeMs()).isEqualTo(original.getAvgResponseTimeMs());
        }
    }
}
