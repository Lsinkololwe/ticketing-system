/**
 * The platform error contract.
 *
 * <p><b>Exposes.</b> {@code DomainRefusal} and the error-code vocabulary every service raises and the gateway renders.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Nothing but the JDK.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. R5 names the error contract explicitly. ET-PLT-005 is the spec that fills it out.
 */
package com.pml.shared.exception;
