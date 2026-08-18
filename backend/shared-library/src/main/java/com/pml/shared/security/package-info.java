/**
 * Authentication and authorization contracts every service enforces identically.
 *
 * <p><b>Exposes.</b> The Keycloak JWT and role converters, the revocation check, and the tenant-scoping contract.
 *
 * <p><b>May depend on.</b> Nothing in {@code com.pml.catalog}, {@code com.pml.booking} or {@code com.pml.identity} — shared-library is a leaf and {@code ModuleBoundaryLintTest} enforces it. Spring Security and Spring Data Redis, both {@code provided}.
 *
 * <p><b>Internal.</b> {@code revocation.*} implementation types, reached through their auto-configuration rather than directly.
 *
 * <p><b>ET-PLT-001 R5 status.</b> <b>On the allowlist.</b> R5 names this as belonging in shared-library. R5 names the JWT and role converters explicitly. A second implementation of any of these in a service is the defect ET-ORG-003 exists to prevent.
 */
package com.pml.shared.security;
