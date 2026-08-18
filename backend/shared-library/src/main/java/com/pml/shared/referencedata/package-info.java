/**
 * Resolution of a status code to its coarse {@code WorkflowSemantic}.
 *
 * <p><b>Exposes.</b> {@code StatusSemanticResolver}, contributed by {@code SharedPersistenceSupportAutoConfiguration}.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Spring Data MongoDB Reactive, {@code provided}.
 *
 * <p><b>Internal.</b> Its per-type cache.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>Debt.</b> R5's acceptance is an allowlist and this package is not on it — it is here for now because moving it means rewriting consumers in identity-service and catalog-service. Owned by <b>ET-PLT-014</b>, the reference data engine, which owns this concept end to end.
 */
package com.pml.shared.referencedata;
