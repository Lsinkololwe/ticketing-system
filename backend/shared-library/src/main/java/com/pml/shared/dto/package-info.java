/**
 * Data-transfer shapes crossing a service boundary.
 *
 * <p><b>Exposes.</b> Request and response records shared between services or between a service and the gateway.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Nothing but the JDK and Jackson annotations.
 *
 * <p><b>Internal.</b> Nothing.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. Cross-cutting contracts. A DTO owned by exactly one service belongs in that service, not here.
 */
package com.pml.shared.dto;
