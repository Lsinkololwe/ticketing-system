/**
 * The document-migration runner and its ledger.
 *
 * <p><b>Exposes.</b> {@code MigrationRunner} and {@code MigrationLedger}, subclassed by each service.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Spring Data MongoDB Reactive, {@code provided}. Timestamps come from the injected {@link java.time.Clock} (ET-PLT-001 R3).
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>Debt.</b> R5's acceptance is an allowlist and this package is not on it — it is here for now because moving it means rewriting consumers in booking-service and identity-service. Owned by <b>ET-PLT-010</b>, which owns document migration.
 */
package com.pml.shared.migration;
