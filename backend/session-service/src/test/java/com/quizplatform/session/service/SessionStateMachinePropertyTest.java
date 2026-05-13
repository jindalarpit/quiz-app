package com.quizplatform.session.service;

import com.quizplatform.common.exception.InvalidStateTransitionException;
import com.quizplatform.session.model.SessionStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Property-based tests for SessionStateMachine validity.
 *
 * **Validates: Requirements 16.2, 16.3**
 */
class SessionStateMachinePropertyTest {

    private final SessionStateMachine stateMachine = new SessionStateMachine();

    /**
     * P5.1: From any valid state, only valid transitions succeed.
     *
     * For any state and any target that is in the valid transitions set,
     * the transition should succeed without throwing an exception.
     *
     * **Validates: Requirements 16.2**
     */
    @Property(tries = 500)
    void validTransitionsAlwaysSucceed(
            @ForAll("validTransitionPairs") Tuple.Tuple2<SessionStatus, SessionStatus> pair) {

        SessionStatus current = pair.get1();
        SessionStatus target = pair.get2();

        assertDoesNotThrow(() -> stateMachine.validateTransition(current, target));
    }

    /**
     * P5.2: From any valid state, invalid transitions throw InvalidStateTransitionException.
     *
     * For any state and any target that is NOT in the valid transitions set,
     * the transition should throw InvalidStateTransitionException.
     *
     * **Validates: Requirements 16.3**
     */
    @Property(tries = 500)
    void invalidTransitionsAlwaysThrow(
            @ForAll("invalidTransitionPairs") Tuple.Tuple2<SessionStatus, SessionStatus> pair) {

        SessionStatus current = pair.get1();
        SessionStatus target = pair.get2();

        assertThatThrownBy(() -> stateMachine.validateTransition(current, target))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    /**
     * P5.3: ENDED state has no valid outgoing transitions.
     *
     * From the ENDED state, transitioning to any other state should always fail.
     *
     * **Validates: Requirements 16.2**
     */
    @Property(tries = 100)
    void endedStateHasNoOutgoingTransitions(
            @ForAll("allStates") SessionStatus target) {

        if (target == SessionStatus.ENDED) {
            // ENDED → ENDED is also invalid (no self-transitions)
            assertThatThrownBy(() -> stateMachine.validateTransition(SessionStatus.ENDED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        } else {
            assertThatThrownBy(() -> stateMachine.validateTransition(SessionStatus.ENDED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        // Also verify via getValidTransitions
        Set<SessionStatus> validTargets = stateMachine.getValidTransitions(SessionStatus.ENDED);
        assertThat(validTargets).isEmpty();
    }

    /**
     * P5.4: All states can transition to ENDED (except ENDED itself).
     *
     * From any non-ENDED state, transitioning to ENDED should always succeed.
     *
     * **Validates: Requirements 16.2**
     */
    @Property(tries = 100)
    void allNonEndedStatesCanTransitionToEnded(
            @ForAll("nonEndedStates") SessionStatus current) {

        assertDoesNotThrow(() -> stateMachine.validateTransition(current, SessionStatus.ENDED));

        Set<SessionStatus> validTargets = stateMachine.getValidTransitions(current);
        assertThat(validTargets).contains(SessionStatus.ENDED);
    }

    /**
     * P5.5: All states (except ENDED) can transition to PAUSED.
     *
     * From any non-ENDED state, transitioning to PAUSED should always succeed.
     *
     * **Validates: Requirements 16.2**
     */
    @Property(tries = 100)
    void allNonEndedStatesCanTransitionToPaused(
            @ForAll("nonEndedStates") SessionStatus current) {

        assertDoesNotThrow(() -> stateMachine.validateTransition(current, SessionStatus.PAUSED));

        Set<SessionStatus> validTargets = stateMachine.getValidTransitions(current);
        assertThat(validTargets).contains(SessionStatus.PAUSED);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<SessionStatus> allStates() {
        return Arbitraries.of(SessionStatus.values());
    }

    @Provide
    Arbitrary<SessionStatus> nonEndedStates() {
        return Arbitraries.of(SessionStatus.values())
                .filter(s -> s != SessionStatus.ENDED);
    }

    @Provide
    Arbitrary<Tuple.Tuple2<SessionStatus, SessionStatus>> validTransitionPairs() {
        return Arbitraries.of(SessionStatus.values())
                .flatMap(current -> {
                    Set<SessionStatus> validTargets = stateMachine.getValidTransitions(current);
                    if (validTargets.isEmpty()) {
                        return Arbitraries.just(null);
                    }
                    return Arbitraries.of(validTargets.toArray(new SessionStatus[0]))
                            .map(target -> Tuple.of(current, target));
                })
                .filter(pair -> pair != null);
    }

    @Provide
    Arbitrary<Tuple.Tuple2<SessionStatus, SessionStatus>> invalidTransitionPairs() {
        return Arbitraries.of(SessionStatus.values())
                .flatMap(current -> {
                    Set<SessionStatus> validTargets = stateMachine.getValidTransitions(current);
                    return Arbitraries.of(SessionStatus.values())
                            .filter(target -> !validTargets.contains(target))
                            .map(target -> Tuple.of(current, target));
                });
    }
}
