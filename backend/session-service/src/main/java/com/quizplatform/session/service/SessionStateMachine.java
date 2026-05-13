package com.quizplatform.session.service;

import com.quizplatform.common.exception.InvalidStateTransitionException;
import com.quizplatform.session.model.SessionStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Session state machine that enforces valid state transitions.
 *
 * Valid transitions:
 * - CREATED → LOBBY
 * - LOBBY → QUESTION_OPEN
 * - QUESTION_OPEN → QUESTION_CLOSED
 * - QUESTION_CLOSED → REVEAL
 * - REVEAL → QUESTION_OPEN (next question)
 * - REVEAL → ENDED (last question)
 * - Any → PAUSED
 * - PAUSED → previous state (restored via resume)
 * - Any → ENDED
 */
@Service
public class SessionStateMachine {

    private static final Map<SessionStatus, Set<SessionStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(SessionStatus.class);

        VALID_TRANSITIONS.put(SessionStatus.CREATED, EnumSet.of(
                SessionStatus.LOBBY, SessionStatus.PAUSED, SessionStatus.ENDED
        ));

        VALID_TRANSITIONS.put(SessionStatus.LOBBY, EnumSet.of(
                SessionStatus.QUESTION_OPEN, SessionStatus.PAUSED, SessionStatus.ENDED
        ));

        VALID_TRANSITIONS.put(SessionStatus.QUESTION_OPEN, EnumSet.of(
                SessionStatus.QUESTION_CLOSED, SessionStatus.PAUSED, SessionStatus.ENDED
        ));

        VALID_TRANSITIONS.put(SessionStatus.QUESTION_CLOSED, EnumSet.of(
                SessionStatus.REVEAL, SessionStatus.PAUSED, SessionStatus.ENDED
        ));

        VALID_TRANSITIONS.put(SessionStatus.REVEAL, EnumSet.of(
                SessionStatus.QUESTION_OPEN, SessionStatus.ENDED, SessionStatus.PAUSED
        ));

        VALID_TRANSITIONS.put(SessionStatus.PAUSED, EnumSet.of(
                SessionStatus.CREATED, SessionStatus.LOBBY, SessionStatus.QUESTION_OPEN,
                SessionStatus.QUESTION_CLOSED, SessionStatus.REVEAL, SessionStatus.ENDED
        ));

        VALID_TRANSITIONS.put(SessionStatus.ENDED, EnumSet.noneOf(SessionStatus.class));
    }

    /**
     * Validate that a transition from the current state to the target state is allowed.
     *
     * @param current the current session state
     * @param target  the desired target state
     * @throws InvalidStateTransitionException if the transition is not valid
     */
    public void validateTransition(SessionStatus current, SessionStatus target) {
        Set<SessionStatus> allowed = VALID_TRANSITIONS.get(current);

        if (allowed == null || !allowed.contains(target)) {
            Set<String> allowedNames = (allowed != null)
                    ? allowed.stream().map(Enum::name).collect(Collectors.toSet())
                    : Set.of();

            throw new InvalidStateTransitionException(
                    current.name(),
                    target.name(),
                    allowedNames
            );
        }
    }

    /**
     * Get the set of valid target states from the given current state.
     *
     * @param current the current session state
     * @return set of valid target states
     */
    public Set<SessionStatus> getValidTransitions(SessionStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(SessionStatus.class));
    }
}
