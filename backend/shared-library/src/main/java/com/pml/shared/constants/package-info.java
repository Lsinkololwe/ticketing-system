/**
 * Enumerations and constant vocabularies shared across service boundaries.
 *
 * <p><b>Exposes.</b> Status enums and workflow vocabularies that appear in more than one service's documents, events or schema.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Nothing but the JDK.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. These are contracts by definition: a status that two services must agree on cannot live in either of them.
 */
package com.pml.shared.constants;
