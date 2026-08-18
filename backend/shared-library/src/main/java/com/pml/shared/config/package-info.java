/**
 * Platform-wide Spring configuration contributed to every service.
 *
 * <p><b>Exposes.</b> Auto-configurations registered in {@code META-INF/spring/...AutoConfiguration.imports}: the platform {@link java.time.Clock}, the auditing {@code DateTimeProvider}, and MongoDB schema validation.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Spring Boot autoconfigure and Spring Data, both {@code provided}.
 *
 * <p><b>Internal.</b> Nothing. Every type here is a bean an application may override or exclude.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. Contributing configuration is what a shared library is for — and it is offered through auto-configuration rather than a component scan, so an application chooses it.
 */
package com.pml.shared.config;
