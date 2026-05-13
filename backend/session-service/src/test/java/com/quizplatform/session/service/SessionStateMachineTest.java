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

class SessionStateMachineTest {

    private SessionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new SessionStateMachine();
    }

    @Nested
    @DisplayName("Valid Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("CREATED → LOBBY is valid")
        void createdToLobby() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.CREATED, SessionStatus.LOBBY));
        }

        @Test
        @DisplayName("LOBBY → QUESTION_OPEN is valid")
        void lobbyToQuestionOpen() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.QUESTION_OPEN));
        }

        @Test
        @DisplayName("QUESTION_OPEN → QUESTION_CLOSED is valid")
        void questionOpenToQuestionClosed() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED));
        }

        @Test
        @DisplayName("QUESTION_CLOSED → REVEAL is valid")
        void questionClosedToReveal() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL));
        }

        @Test
        @DisplayName("REVEAL → QUESTION_OPEN is valid (next question)")
        void revealToQuestionOpen() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.QUESTION_OPEN));
        }

        @Test
        @DisplayName("REVEAL → ENDED is valid (last question)")
        void revealToEnded() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.ENDED));
        }
    }

    @Nested
    @DisplayName("Pause/Resume Transitions")
    class PauseResumeTransitions {

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"ENDED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any state (except ENDED) → PAUSED is valid")
        void anyStateToPaused(SessionStatus state) {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(state, SessionStatus.PAUSED));
        }

        @Test
        @DisplayName("PAUSED → LOBBY is valid (resume to previous state)")
        void pausedToLobby() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.LOBBY));
        }

        @Test
        @DisplayName("PAUSED → QUESTION_OPEN is valid (resume to previous state)")
        void pausedToQuestionOpen() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.QUESTION_OPEN));
        }

        @Test
        @DisplayName("PAUSED → QUESTION_CLOSED is valid (resume to previous state)")
        void pausedToQuestionClosed() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.QUESTION_CLOSED));
        }

        @Test
        @DisplayName("PAUSED → REVEAL is valid (resume to previous state)")
        void pausedToReveal() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.REVEAL));
        }

        @Test
        @DisplayName("PAUSED → ENDED is valid")
        void pausedToEnded() {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(SessionStatus.PAUSED, SessionStatus.ENDED));
        }
    }

    @Nested
    @DisplayName("End Session Transitions")
    class EndSessionTransitions {

        @ParameterizedTest
        @EnumSource(value = SessionStatus.class, names = {"ENDED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any state (except ENDED) → ENDED is valid")
        void anyStateToEnded(SessionStatus state) {
            assertDoesNotThrow(() ->
                    stateMachine.validateTransition(state, SessionStatus.ENDED));
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("ENDED → any state is invalid")
        void endedToAnyState() {
            for (SessionStatus target : SessionStatus.values()) {
                if (target == SessionStatus.ENDED) continue;
                assertThatThrownBy(() ->
                        stateMachine.validateTransition(SessionStatus.ENDED, target))
                        .isInstanceOf(InvalidStateTransitionException.class);
            }
        }

        @Test
        @DisplayName("CREATED → QUESTION_OPEN is invalid (must go through LOBBY)")
        void createdToQuestionOpen() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.CREATED, SessionStatus.QUESTION_OPEN))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("LOBBY → REVEAL is invalid")
        void lobbyToReveal() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.REVEAL))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("LOBBY → QUESTION_CLOSED is invalid")
        void lobbyToQuestionClosed() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.LOBBY, SessionStatus.QUESTION_CLOSED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("QUESTION_OPEN → REVEAL is invalid (must go through QUESTION_CLOSED)")
        void questionOpenToReveal() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_OPEN, SessionStatus.REVEAL))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("QUESTION_CLOSED → QUESTION_OPEN is invalid (must go through REVEAL)")
        void questionClosedToQuestionOpen() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.QUESTION_CLOSED, SessionStatus.QUESTION_OPEN))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("REVEAL → QUESTION_CLOSED is invalid")
        void revealToQuestionClosed() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.QUESTION_CLOSED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("REVEAL → LOBBY is invalid")
        void revealToLobby() {
            assertThatThrownBy(() ->
                    stateMachine.validateTransition(SessionStatus.REVEAL, SessionStatus.LOBBY))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Exception Details")
    class ExceptionDetails {

        @Test
        @DisplayName("Exception contains current state, attempted state, and allowed transitions")
        void exceptionContainsDetails() {
            try {
                stateMachine.validateTransition(SessionStatus.CREATED, SessionStatus.QUESTION_OPEN);
            } catch (InvalidStateTransitionException e) {
                assertThat(e.getCurrentState()).isEqualTo("CREATED");
                assertThat(e.getAttemptedState()).isEqualTo("QUESTION_OPEN");
                assertThat(e.getAllowedTransitions()).contains("LOBBY", "PAUSED", "ENDED");
            }
        }
    }

    @Nested
    @DisplayName("getValidTransitions")
    class GetValidTransitions {

        @Test
        @DisplayName("CREATED has valid transitions to LOBBY, PAUSED, ENDED")
        void createdValidTransitions() {
            Set<SessionStatus> transitions = stateMachine.getValidTransitions(SessionStatus.CREATED);
            assertThat(transitions).containsExactlyInAnyOrder(
                    SessionStatus.LOBBY, SessionStatus.PAUSED, SessionStatus.ENDED);
        }

        @Test
        @DisplayName("ENDED has no valid transitions")
        void endedNoTransitions() {
            Set<SessionStatus> transitions = stateMachine.getValidTransitions(SessionStatus.ENDED);
            assertThat(transitions).isEmpty();
        }

        @Test
        @DisplayName("PAUSED can transition to any state except PAUSED itself")
        void pausedTransitions() {
            Set<SessionStatus> transitions = stateMachine.getValidTransitions(SessionStatus.PAUSED);
            assertThat(transitions).contains(
                    SessionStatus.CREATED, SessionStatus.LOBBY,
                    SessionStatus.QUESTION_OPEN, SessionStatus.QUESTION_CLOSED,
                    SessionStatus.REVEAL, SessionStatus.ENDED);
            assertThat(transitions).doesNotContain(SessionStatus.PAUSED);
        }
    }
}
