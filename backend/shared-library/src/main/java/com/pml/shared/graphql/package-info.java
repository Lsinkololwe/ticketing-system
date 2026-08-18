/**
 * Federation-wide GraphQL concerns: scalars, the {@code @auth} directive, error rendering.
 *
 * <p><b>Exposes.</b> {@code auth.graphqls}, the shared scalar registrations, and the wiring that makes the {@code @auth} directive resolve in every subgraph.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Netflix DGS and Spring GraphQL, both {@code provided}.
 *
 * <p><b>Internal.</b> {@code AuthDirectiveAutoConfiguration}'s nested {@code @DgsComponent} — the runtime wiring, exempted by name in {@code SharedLibraryBoundaryTest} with its justification.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. R5 names {@code graphql/auth.graphqls} explicitly.
 */
package com.pml.shared.graphql;
