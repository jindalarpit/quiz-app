package com.quizplatform.session.service;

import com.quizplatform.common.exception.InvalidStateTransitionException;
import com.quizplatform.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration tests for all session state transitions including edge cases.
 * Tests the full lifecycle and pause/resume from each state.
 */
class SessionLifecycleIntegrationTest {

    private SessionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new SessionStateMachine();
    }

    @Nested
    @DisplayName("Full Lifecycle: CREATED → LOBBY → QUESTION_OPEN → QUESTION_CLOSED → REVEAL → ENDED")
    class FullLifecycle {

        @Test
        @DisplayName("Complete happy path lifecycle")
        void completeLifecycle() {
            // CREATED → LOBBY
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.CREATED, SessionStatus.LOBBY));

            // LOBBY → QUESTION_OPEN
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.QUESTION_OPEN));

            // QUESTION_OPEN → QUESTION_CLOSED
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED));

            // QUESTION_CLOSED → REVEAL
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL));

            // REVEAL → ENDED (last question)
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.ENDED));
        }

        @Test
        @DisplayName("Multi-question lifecycle with loop back from REVEAL to QUESTION_OPEN")
        void multiQuestionLifecycle() {
            // First question
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.QUESTION_OPEN));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL));

            // Second question (loop back)
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.QUESTION_OPEN));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL));

            // Third question (loop back again)
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.QUESTION_OPEN));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL));

            // End after last question
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.ENDED));
        }
    }

    @Nested
    @DisplayName("Pause/Resume from Each State")
    class PauseResumeFromEachState {

        @Test
        @DisplayName("Pause from LOBBY and resume back to LOBBY")
        void pauseResumeFromLobby() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.LOBBY));
        }

        @Test
        @DisplayName("Pause from QUESTION_OPEN and resume back to QUESTION_OPEN")
        void pauseResumeFromQuestionOpen() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.QUESTION_OPEN));
        }

        @Test
        @DisplayName("Pause from QUESTION_CLOSED and resume back to QUESTION_CLOSED")
        void pauseResumeFromQuestionClosed() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.QUESTION_CLOSED));
        }

        @Test
        @DisplayName("Pause from REVEAL and resume back to REVEAL")
        void pauseResumeFromReveal() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.REVEAL));
        }

        @Test
        @DisplayName("Pause from CREATED and resume back to CREATED")
        void pauseResumeFromCreated() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.CREATED, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.CREATED));
        }

        @Test
        @DisplayName("End session while paused")
        void endWhilePaused() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.PAUSED));
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.ENDED));
        }
    }

    @Nested
    @DisplayName("Invalid Transitions Are Rejected")
    class InvalidTransitionsRejected {

        @Test
        @DisplayName("Cannot skip states in the question flow")
        void cannotSkipStates() {
            // Cannot go directly from LOBBY to QUESTION_CLOSED
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.QUESTION_CLOSED))
                    .isInstanceOf(InvalidStateTransitionException.class);

            // Cannot go directly from LOBBY to REVEAL
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.REVEAL))
                    .isInstanceOf(InvalidStateTransitionException.class);

            // Cannot go directly from QUESTION_OPEN to REVEAL
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.REVEAL))
                    .isInstanceOf(InvalidStateTransitionException.class);

            // Cannot go directly from QUESTION_CLOSED to QUESTION_OPEN
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.QUESTION_OPEN))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot go backwards in the flow")
        void cannotGoBackwards() {
            // Cannot go from QUESTION_OPEN back to LOBBY
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.LOBBY))
                    .isInstanceOf(InvalidStateTransitionException.class);

            // Cannot go from REVEAL back to LOBBY
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.LOBBY))
                    .isInstanceOf(InvalidStateTransitionException.class);

            // Cannot go from REVEAL back to QUESTION_CLOSED
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.QUESTION_CLOSED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Cannot transition from ENDED to any state")
        void cannotTransitionFromEnded() {
            for (SessionStatus target : SessionStatus.values()) {
                assertThatThrownBy(() ->
                        stateMachine.validateTransition(SessionStatus.ENDED, target))
                        .isInstanceOf(InvalidStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("Cannot pause an already ended session")
        void cannotPauseEndedSession() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.ENDED, SessionStatus.PAUSED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("Invalid transition exception contains useful information")
        void invalidTransitionExceptionHasDetails() {
            try {
                stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.REVEAL);
            } catch (InvalidStateTransitionException e) {
                assertThat(e.getCurrentState()).isEqualTo("LOBBY");
                assertThat(e.getAttemptedState()).isEqualTo("REVEAL");
                assertThat(e.getAllowedTransitions()).isNotEmpty();
                assertThat(e.getAllowedTransitions()).contains("QUESTION_OPEN", "PAUSED", "ENDED");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Self-transitions are not allowed for any state")
        void selfTransitionsNotAllowed() {
            for (SessionStatus state : SessionStatus.values()) {
                Set<SessionStatus> validTargets = stateMachine.getValidTransitions(state);
                // Self-transitions should not be in valid targets
                assertThat(validTargets).doesNotContain(state);
            }
        }

        @Test
        @DisplayName("PAUSED can resume to any non-PAUSED, non-ENDED state")
        void pausedCanResumeToAnyActiveState() {
            Set<SessionStatus> fromPaused = stateMachine.getValidTransitions(SessionStatus.PAUSED);
            assertThat(fromPaused).contains(
                    SessionStatus.CREATED,
                    SessionStatus.LOBBY,
                    SessionStatus.QUESTION_OPEN,
                    SessionStatus.QUESTION_CLOSED,
                    SessionStatus.REVEAL,
                    SessionStatus.ENDED
            );
            assertThat(fromPaused).doesNotContain(SessionStatus.PAUSED);
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"ENDED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Every non-ENDED state can reach ENDED")
        void everyNonEndedStateCanReachEnded(SessionStatus state) {
            Set<SessionStatus> validTargets = stateMachine.getValidTransitions(state);
            assertThat(validTargets).contains(SessionStatus.ENDED);
        }

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"ENDED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Every non-ENDED state can reach PAUSED")
        void everyNonEndedStateCanReachPaused(SessionStatus state) {
            Set<SessionStatus> validTargets = stateMachine.getValidTransitions(state);
            assertThat(validTargets).contains(SessionStatus.PAUSED);
        }
    }
}
