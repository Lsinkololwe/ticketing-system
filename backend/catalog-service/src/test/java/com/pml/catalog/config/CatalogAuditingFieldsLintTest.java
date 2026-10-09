package com.pml.catalog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Data auditing writes a timestamp into whatever field carries {@code @CreatedDate} or
 * {@code @LastModifiedDate}, and refuses a field that cannot hold one at the first save, which for
 * a model nobody saved in a test is a production 500. The annotation binds to the NEXT field, so
 * an annotation left above a Javadoc block ends up on a boolean three fields down; this is how
 * {@code OrganizationMember.mirrorPending} became a created-date and broke organizer onboarding.
 */
@Tag("L1")
@Tag("ET-PLT-010")
@DisplayName("F-031 · catalog auditing annotations sit on date-typed fields")
class CatalogAuditingFieldsLintTest {

    private static final Set<Class<?>> TEMPORAL = Set.of(Instant.class, LocalDateTime.class, LocalDate.class,
            LocalTime.class, Date.class, Long.class, long.class);

    @TestFactory
    Stream<DynamicTest> auditingAnnotationsSitOnTemporalFields() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        List<DynamicTest> tests = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.catalog")) {
            Class<?> model = classFor(candidate.getBeanClassName());
            tests.add(DynamicTest.dynamicTest(model.getSimpleName(), () -> {
                List<String> misplaced = new ArrayList<>();
                for (Field field : model.getDeclaredFields()) {
                    if ((field.isAnnotationPresent(CreatedDate.class) || field.isAnnotationPresent(LastModifiedDate.class))
                            && !TEMPORAL.contains(field.getType())) {
                        misplaced.add(field.getName() + " is " + field.getType().getSimpleName());
                    }
                }
                assertThat(misplaced).as("%s: auditing annotation on a field that cannot hold a timestamp",
                        model.getSimpleName()).isEmpty();
            }));
        }
        assertThat(tests).as("no @Document classes found — the scan is broken").isNotEmpty();
        return tests.stream();
    }

    private static Class<?> classFor(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
