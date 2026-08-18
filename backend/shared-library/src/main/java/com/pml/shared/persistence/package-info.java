/**
 * Money conversion helpers and the money-field migration.
 *
 * <p><b>Exposes.</b> {@code MoneyConversions} and {@code MoneyFieldMigrationService}, the latter contributed by {@code SharedPersistenceSupportAutoConfiguration}.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Spring Data MongoDB Reactive, {@code provided}.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>Debt.</b> R5's acceptance is an allowlist and this package is not on it — it is here for now because moving it means rewriting consumers in booking-service. Owned by <b>ET-PLT-002</b> T5, which moves money handling into booking where its only consumer lives.
 */
package com.pml.shared.persistence;
