package com.pml.catalog.service.referencedata;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares which existing Java enum backs which reference type.
 *
 * <h2>Why a registry of CLASSES and not a list of values</h2>
 * The values are already written down — they are the enum constants the code
 * has compiled against for years. Re-typing them into a seed file creates a
 * second copy that drifts the first time somebody adds a constant and forgets
 * the seed, and the drift is silent: the picker simply never offers the new
 * value.
 *
 * <p>So this registers the enum CLASS and the bootstrapper reflects over it.
 * Adding a constant to {@code TicketStatus} makes it appear as configurable
 * reference data on the next start, with no seed file to remember.
 */
public final class ReferenceDataSource {

    private ReferenceDataSource() {}

    public record Registration(
            ReferenceType type,
            Class<? extends Enum<?>> enumClass,
            /** Mutable so registerExtra can top it up. */
            Map<String, WorkflowSemantic> semantics
    ) {}

    private static final Map<ReferenceType, Registration> REGISTRY = new LinkedHashMap<>();

    static void register(ReferenceType type,
                         Class<? extends Enum<?>> enumClass,
                         Map<String, WorkflowSemantic> semantics) {
        REGISTRY.put(type, new Registration(type, enumClass, new LinkedHashMap<>(semantics)));
    }

    /**
     * Add semantics to an already-registered type.
     *
     * <p>Exists only because {@code Map.of} tops out at ten pairs and two of
     * these enums have eleven constants.
     */
    static void registerExtra(ReferenceType type, Map<String, WorkflowSemantic> extra) {
        Registration existing = REGISTRY.get(type);
        if (existing == null) {
            throw new IllegalStateException("registerExtra before register for " + type);
        }
        existing.semantics().putAll(extra);
    }

    public static Map<ReferenceType, Registration> registry() {
        return Map.copyOf(REGISTRY);
    }

    /**
     * {@code PENDING_FINANCE_APPROVAL} becomes {@code Pending finance approval}.
     *
     * <p>Derived rather than authored, so a new constant arrives with a
     * presentable name immediately. An administrator can rename it afterwards
     * and that edit is never overwritten.
     */
    public static String humanize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String spaced = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
