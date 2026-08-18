/**
 * Pure utilities with no Spring or domain dependency.
 *
 * <p><b>Exposes.</b> Stateless helpers usable from any layer of any service.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Nothing but the JDK.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. R5 names pure utilities explicitly. A helper that needs a bean is not a utility.
 */
package com.pml.shared.util;
