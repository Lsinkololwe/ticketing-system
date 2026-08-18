/**
 * Microcks client for the onboarding e2e suite.
 *
 * ## The two things this file exists to work around
 *
 * **1. Upload must not go through the library's helper.**
 * `microcks-testcontainers@0.3.5` builds its multipart body by hand and sets
 * `Content-Length` explicitly. Node 22's undici rejects that outright with
 * `invalid content-length header`, so `importAsMainArtifact` /
 * `importAsSecondaryArtifact` throw before they reach Microcks. Native
 * `FormData` produces a correct body and lets undici compute the length, so the
 * uploads here call Microcks' documented `/api/artifact/upload` endpoint
 * directly — the same endpoint the library targets.
 *
 * **2. Re-importing does not replace a response; it appends.**
 * `myOwnedOrganization` takes no arguments, so Microcks assigns it the *empty*
 * dispatcher and always serves "the first available response" for the
 * operation. Re-importing a secondary artifact with a different status adds a
 * second response that the empty dispatcher will never reach — verified
 * directly: three imports in a row kept serving the first one. Switching status
 * therefore means deleting the service and importing both artifacts afresh,
 * which {@link setOrganizationState} does.
 *
 * @see collection.ts — the GraphQL mock dataset
 * @see documents-openapi.ts — the REST mock dataset
 */

import { mkdtempSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

import {
  buildCollection,
  buildErrorCollection,
  MICROCKS_API_NAME,
  MICROCKS_API_VERSION,
  type OrganizationOptions,
} from './collection';
import {
  buildDocumentsOpenApi,
  DOCUMENTS_API_NAME,
  DOCUMENTS_API_VERSION,
  type StubDocument,
} from './documents-openapi';

// =============================================================================
// PATHS
// =============================================================================

/** The GraphQL IDL, checked in beside this file. */
export const GRAPHQL_SCHEMA_PATH = path.join(__dirname, 'onboarding-identity.graphql');

const scratch = mkdtempSync(path.join(tmpdir(), 'microcks-onboarding-'));

function writeArtifact(name: string, contents: unknown): string {
  const file = path.join(scratch, name);
  writeFileSync(file, JSON.stringify(contents, null, 2));
  return file;
}

// =============================================================================
// LOW-LEVEL API
// =============================================================================

/**
 * Upload an artifact to Microcks.
 *
 * @param mainArtifact `true` for the IDL/OpenAPI, `false` for the example dataset
 */
export async function uploadArtifact(
  microcksUrl: string,
  filePath: string,
  mainArtifact: boolean
): Promise<string> {
  const form = new FormData();
  form.append('file', new Blob([await readFile(filePath)]), path.basename(filePath));

  const url = `${microcksUrl}/api/artifact/upload${mainArtifact ? '' : '?mainArtifact=false'}`;
  const response = await fetch(url, { method: 'POST', body: form });

  if (response.status !== 201) {
    throw new Error(
      `Microcks rejected ${path.basename(filePath)} (${response.status}): ${await response.text()}`
    );
  }
  return response.text();
}

/** Remove a service so the next import starts from a clean slate. */
export async function deleteService(
  microcksUrl: string,
  name: string,
  version: string
): Promise<void> {
  const services = (await (await fetch(`${microcksUrl}/api/services`)).json()) as Array<{
    id: string;
    name: string;
    version: string;
  }>;

  const service = services.find((s) => s.name === name && s.version === version);
  if (!service) return;

  const response = await fetch(`${microcksUrl}/api/services/${service.id}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new Error(`Failed to delete ${name}:${version} — ${response.status}`);
  }
}

// =============================================================================
// STATE CONTROL
// =============================================================================

export interface OnboardingState {
  /** `null` means the caller owns no organization. */
  organization: OrganizationOptions | null;
  documents?: StubDocument[];
}

/**
 * Point the mocks at a given onboarding state.
 *
 * Delete-then-import rather than import-over-the-top, for the append reason in
 * the module docstring. Both APIs are reset together so a spec can never see a
 * half-updated pair.
 */
export async function setOnboardingState(microcksUrl: string, state: OnboardingState) {
  await Promise.all([
    deleteService(microcksUrl, MICROCKS_API_NAME, MICROCKS_API_VERSION),
    deleteService(microcksUrl, DOCUMENTS_API_NAME, DOCUMENTS_API_VERSION),
  ]);

  // Main artifacts first: the secondary dataset attaches to the service the
  // main artifact creates, and importing it against nothing is a silent no-op.
  await uploadArtifact(microcksUrl, GRAPHQL_SCHEMA_PATH, true);
  await uploadArtifact(
    microcksUrl,
    writeArtifact('onboarding-collection.json', buildCollection(state.organization)),
    false
  );

  await uploadArtifact(
    microcksUrl,
    writeArtifact('documents-openapi.json', buildDocumentsOpenApi(state.documents ?? [])),
    true
  );

  // Do not return until the mock actually serves this state.
  //
  // Import is not instantaneous, and a spec that navigates too early gets
  // whatever Microcks was serving before — most often the null baseline, which
  // renders the welcome screen and fails with "element not found" while looking
  // exactly like a broken app. That is a race, so it appears under load and
  // vanishes on a rerun, which is the worst way for a suite to fail.
  await waitForServedStatus(microcksUrl, state.organization?.status ?? null);
}

/** Poll the mock until it reports `expected`, or give up loudly. */
async function waitForServedStatus(
  microcksUrl: string,
  expected: string | null,
  timeoutMs = 10_000
) {
  const endpoint = graphqlMockEndpoint(microcksUrl);
  const deadline = Date.now() + timeoutMs;
  let last: string | null | undefined = undefined;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          operationName: 'MyOrganization',
          // Inlined, not a fragment: Microcks 500s on fragment spreads.
          query: 'query MyOrganization { myOwnedOrganization { id name status } }',
        }),
      });

      if (response.ok) {
        const payload = (await response.json()) as {
          data?: { myOwnedOrganization?: { status?: string } | null };
        };
        last = payload.data?.myOwnedOrganization?.status ?? null;
        if (last === expected) return;
      }
    } catch {
      // Microcks is still settling; keep polling until the deadline.
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }

  throw new Error(
    `Microcks did not serve the requested state within ${timeoutMs}ms ` +
      `(wanted ${expected ?? 'no organization'}, last saw ${last ?? 'no organization'})`
  );
}

/**
 * Make the GraphQL API disappear entirely.
 *
 * With the service deleted, Microcks answers the mock endpoint with a non-2xx —
 * which is what the app's server-side fetch sees when the backend is down. The
 * assertion that matters is what the app does next: a retry screen, never the
 * setup form.
 */
export async function simulateBackendUnavailable(microcksUrl: string) {
  await deleteService(microcksUrl, MICROCKS_API_NAME, MICROCKS_API_VERSION);
}

/**
 * Serve a GraphQL authorization error for the status query.
 *
 * Distinct from {@link simulateBackendUnavailable}: here the transport succeeds
 * and the *payload* carries the failure, which is the subtler case the old code
 * mishandled.
 */
export async function simulateAuthorizationError(microcksUrl: string) {
  await deleteService(microcksUrl, MICROCKS_API_NAME, MICROCKS_API_VERSION);
  await uploadArtifact(microcksUrl, GRAPHQL_SCHEMA_PATH, true);
  await uploadArtifact(
    microcksUrl,
    writeArtifact('onboarding-error-collection.json', buildErrorCollection()),
    false
  );
}

// =============================================================================
// ENDPOINTS
// =============================================================================

/** Where the app should send GraphQL. */
export function graphqlMockEndpoint(microcksUrl: string): string {
  return `${microcksUrl}/graphql/${encodeURIComponent(MICROCKS_API_NAME)}/${MICROCKS_API_VERSION}`;
}

/**
 * What `NEXT_PUBLIC_API_URL` should be.
 *
 * The app appends `/api/v1/organizations/...` to it, and the OpenAPI artifact
 * declares its paths with that same prefix, so the two compose into a real
 * Microcks REST mock path.
 */
export function documentsMockRoot(microcksUrl: string): string {
  return `${microcksUrl}/rest/${encodeURIComponent(DOCUMENTS_API_NAME)}/${DOCUMENTS_API_VERSION}`;
}

/** Is Microcks reachable? Used to skip rather than fail when it is absent. */
export async function microcksIsRunning(microcksUrl: string): Promise<boolean> {
  try {
    const response = await fetch(`${microcksUrl}/api/services`, {
      signal: AbortSignal.timeout(2000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

export { uploaded, rejected } from './documents-openapi';
export type { StubDocument } from './documents-openapi';
export type { OrganizationOptions, OrgStatus, BusinessType } from './collection';
