/**
 * Tenant-isolation validation.
 *
 * <p><b>Exposes.</b> {@code TenantValidationService} — reflection-based, and currently consumed by nothing in production.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Nothing but Reactor.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>Superseded.</b> R5 does not list a service package, and this one is an orphan. <b>ET-PLT-007</b> BE-4 moves every tenant check into the repository filter, where it cannot be forgotten at a call site. Recommend deleting this package in that slice.
 */
package com.pml.shared.service;
