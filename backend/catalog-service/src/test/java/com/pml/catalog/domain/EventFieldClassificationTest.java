package com.pml.catalog.domain;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.EventFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A field added to {@code Event} must be classified before it ships: whether changing it sends an
 * approved event back for review is a decision, and an unclassified field would default to "no".
 */
@Tag("L1")
@Tag("ET-CAT-001")
@DisplayName("Every event field is classified as material, editable or system-kept, exactly once")
class EventFieldClassificationTest {

    private static Set<String> declaredFields() {
        return Arrays.stream(Event.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("the material set is exactly when, where, whose and how many")
    void materialSet() {
        assertThat(EventFields.MATERIAL)
                .containsExactlyInAnyOrder("eventDateTime", "endDateTime", "locationId", "organizationId", "totalCapacity");
    }

    @Test
    @DisplayName("no field of Event is unclassified")
    void everyFieldClassified() {
        Set<String> classified = new HashSet<>(EventFields.MATERIAL);
        classified.addAll(EventFields.EDITABLE);
        classified.addAll(EventFields.SYSTEM);

        assertThat(declaredFields()).isSubsetOf(classified);
    }

    @Test
    @DisplayName("no field is in two classes, and no class names a field Event does not have")
    void classesAreDisjointAndReal() {
        assertThat(EventFields.MATERIAL).doesNotContainAnyElementsOf(EventFields.EDITABLE)
                .doesNotContainAnyElementsOf(EventFields.SYSTEM);
        assertThat(EventFields.EDITABLE).doesNotContainAnyElementsOf(EventFields.SYSTEM);
        Set<String> classified = new HashSet<>(EventFields.MATERIAL);
        classified.addAll(EventFields.EDITABLE);
        classified.addAll(EventFields.SYSTEM);
        assertThat(classified).isSubsetOf(declaredFields());
    }
}
