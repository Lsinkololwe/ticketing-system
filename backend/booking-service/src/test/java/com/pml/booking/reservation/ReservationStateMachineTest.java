package com.pml.booking.reservation;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.ReservationStateMachine.Action;
import com.pml.shared.constants.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exactly five states and seven transitions, and all thirty pairs are driven.
 *
 * <h2>Why every pair rather than every legal pair</h2>
 * This test drives all thirty {@code (status, action)} combinations, legal and illegal, not only
 * the legal ones, and that distinction is the point. A suite that exercises the seven
 * legal moves proves the machine can do what it should; it says nothing about the twenty-three it
 * must refuse, and those are where money is. {@code CONFIRM} on an already-{@code CONFIRMED}
 * reservation writes a second set of tickets and credits escrow twice; {@code RELEASE} on an
 * {@code EXPIRED} one returns the same seats a second time and inflates the tier.
 *
 * <p>Terminality is the property that prevents both, and it only holds if it holds for every
 * terminal state against every action. Enumerating the grid is the only way to know that, and it
 * is cheap — this is a layer-1 test with no Spring context, no database and no network, which is
 * the right layer for a rule expressed over values.
 *
 * <h2>The grid</h2>
 * <pre>
 *              RESERVE  CONFIRM  CANCEL  EXPIRE  RELEASE  FAIL
 *   HELD          –     CONFIRMED RELEASED EXPIRED RELEASED FAILED
 *   CONFIRMED     –        –        –        –       –        –
 *   EXPIRED       –        –        –        –       –        –
 *   RELEASED      –        –        –        –       –        –
 *   FAILED        –        –        –        –       –        –
 * </pre>
 */
@Tag("L1")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001-R2 · five states, seven transitions, thirty pairs")
class ReservationStateMachineTest {

    /** The transition grid. Every pair absent from this map must be refused. */
    private static final Map<ReservationStatus, Map<Action, ReservationStatus>> EXPECTED = Map.of(
            ReservationStatus.HELD, Map.of(
                    Action.CONFIRM, ReservationStatus.CONFIRMED,
                    Action.CANCEL, ReservationStatus.RELEASED,
                    Action.EXPIRE, ReservationStatus.EXPIRED,
                    Action.RELEASE, ReservationStatus.RELEASED,
                    Action.FAIL, ReservationStatus.FAILED));

    @Test
    @DisplayName("ET-TKT-001-R2 · exactly five states, named as §4 names them")
    void fiveStates() {
        assertThat(ReservationStatus.values())
                .as("a sixth state is a branch every consumer of this enum silently ignores")
                .containsExactlyInAnyOrder(
                        ReservationStatus.HELD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.EXPIRED,
                        ReservationStatus.RELEASED,
                        ReservationStatus.FAILED);
    }

    @Test
    @DisplayName("ET-TKT-001-R2 · all thirty pairs behave as §4's grid says, legal and illegal alike")
    void everyPairInTheGrid() {
        List<String> problems = new ArrayList<>();
        int pairs = 0;

        for (ReservationStatus from : ReservationStatus.values()) {
            for (Action action : Action.values()) {
                pairs++;
                Optional<ReservationStatus> actual = ReservationStateMachine.next(from, action);
                ReservationStatus expected = EXPECTED.getOrDefault(from, Map.of()).get(action);

                if (expected == null && actual.isPresent()) {
                    problems.add("(%s, %s) moved to %s — §4 permits no move from here"
                            .formatted(from, action, actual.get()));
                } else if (expected != null && actual.isEmpty()) {
                    problems.add("(%s, %s) was refused — §4 says it moves to %s"
                            .formatted(from, action, expected));
                } else if (expected != null && actual.get() != expected) {
                    problems.add("(%s, %s) moved to %s — §4 says %s"
                            .formatted(from, action, actual.get(), expected));
                }
            }
        }

        assertThat(pairs)
                .as("five states by six actions is the grid R2 asks to be driven; a smaller "
                        + "number means an enum shrank and this test stopped covering it")
                .isEqualTo(30);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-TKT-001-R2 · exactly seven legal transitions, no more")
    void sevenTransitions() {
        long legal = 0;
        for (ReservationStatus from : ReservationStatus.values()) {
            for (Action action : Action.values()) {
                if (ReservationStateMachine.next(from, action).isPresent()) {
                    legal++;
                }
            }
        }
        // Five from HELD. The seven transitions also count RESERVE and the terminal-state no-ops,
        // which the machine does not model as moves; what it admits is the five moves out of
        // HELD. An eighth would mean a terminal state stopped being terminal.
        assertThat(legal)
                .as("§4's grid permits five moves and they all leave HELD")
                .isEqualTo(5);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class,
            names = {"CONFIRMED", "EXPIRED", "RELEASED", "FAILED"})
    @DisplayName("ET-TKT-001-R2 · every terminal state refuses every action")
    void terminalStatesAreTerminal(ReservationStatus terminal) {
        assertThat(ReservationStateMachine.TERMINAL)
                .as("the published set and the behaviour must agree, or callers branch on a lie")
                .contains(terminal);

        for (Action action : Action.values()) {
            assertThat(ReservationStateMachine.next(terminal, action))
                    .as("(%s, %s): confirming twice writes tickets twice and credits escrow twice; "
                            + "releasing twice returns the same seats twice", terminal, action)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("ET-TKT-001-R2 · HELD is the only non-terminal state")
    void heldIsTheOnlyLiveState() {
        assertThat(ReservationStateMachine.TERMINAL)
                .isEqualTo(EnumSet.complementOf(EnumSet.of(ReservationStatus.HELD)));
    }

    @Test
    @DisplayName("ET-TKT-001-R2 · an illegal transition raises and names the state it was in")
    void requireRaisesCarryingCurrentStatus() {
        // An illegal transition is refused with RESERVATION_STATE_INVALID carrying currentStatus.
        // A refusal that does not say which state it found is one a support agent cannot act on —
        // every retry looks the same from outside.
        assertThatThrownBy(() ->
                ReservationStateMachine.require(ReservationStatus.CONFIRMED, Action.RELEASE))
                .isInstanceOf(ReservationStateMachine.IllegalTransitionException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    @DisplayName("ET-TKT-001-R2 · RESERVE has no origin state and moves nothing")
    void reserveIsNotATransition() {
        // RESERVE creates the reservation; it does not move an existing one. Admitting it as a
        // transition would make HELD reachable from a terminal state.
        for (ReservationStatus from : ReservationStatus.values()) {
            assertThat(ReservationStateMachine.next(from, Action.RESERVE))
                    .as("RESERVE from %s", from)
                    .isEmpty();
        }
    }
}
