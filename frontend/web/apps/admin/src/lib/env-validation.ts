const requiredEnvVars: Record<string, string | undefined> = {
  MONGODB_URI: process.env.MONGODB_URI,
  MONGODB_DATABASE: process.env.MONGODB_DATABASE,
  BETTER_AUTH_URL: process.env.BETTER_AUTH_URL,
  BETTER_AUTH_SECRET: process.env.BETTER_AUTH_SECRET,
  NEXT_PUBLIC_BETTER_AUTH_URL: process.env.NEXT_PUBLIC_BETTER_AUTH_URL,
  NEXT_PUBLIC_GRAPHQL_ENDPOINT: process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT,
};

/**
 * Validate critical environment variables.
 *
 * This should only run on the server to avoid leaking server-side configuration
 * details into the client bundle.
 */
export function validateEnvVars(): void {
  if (typeof window !== 'undefined') {
    // Never validate server env vars in the browser
    return;
  }

  const missing: string[] = [];

  Object.entries(requiredEnvVars).forEach(([key, value]) => {
    if (!value) {
      missing.push(key);
    }
  });

  if (missing.length > 0) {
    // Throwing here fails fast during boot in misconfigured environments,
    // which is preferable to running with insecure defaults.
    throw new Error(
      `Missing required environment variables: ${missing.join(', ')}`
    );
  }
}


