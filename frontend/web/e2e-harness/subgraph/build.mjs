/**
 * Bundles the subgraph fixture into one dependency-free file.
 *
 * <h2>Why a script instead of a vite.config.ts</h2>
 * `vite` is not a direct dependency of this workspace — it arrives through
 * vitest and lives in pnpm's store, so a config file cannot `import { defineConfig }
 * from 'vite'`: config files are resolved from their own directory, and vite is
 * not reachable from there. Locating it explicitly and calling the JS API avoids
 * the chicken-and-egg entirely, and keeps the bundling step honest about the
 * fact that it is borrowing someone else's toolchain.
 *
 * <h2>Why bundle at all</h2>
 * The fixture container runs a bare `node` image. Bind-mounting `node_modules`
 * does not survive pnpm — its layout is symlinks into `.pnpm`, which dangle once
 * mounted — so `graphql` and `@graphql-tools/mock` are bundled into the output
 * and the container needs the single file and nothing else.
 *
 * Run:  node e2e-harness/subgraph/build.mjs
 */

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const here = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(here, '..', '..');
const require = createRequire(path.join(workspace, 'noop.js'));

/** The vite entry inside pnpm's store, found rather than assumed. */
function locateVite() {
  const store = path.join(workspace, 'node_modules', '.pnpm');
  const candidate = fs
    .readdirSync(store)
    .filter((entry) => entry.startsWith('vite@'))
    .map((entry) => path.join(store, entry, 'node_modules', 'vite', 'dist', 'node', 'index.js'))
    .find((entry) => fs.existsSync(entry));

  if (!candidate) {
    throw new Error(
      'vite not found in the pnpm store. It is a transitive dependency of ' +
        'vitest; if that changed, this bundling step needs a real dependency.'
    );
  }
  return candidate;
}

const { build } = await import(locateVite());

await build({
  configFile: false,
  root: here,
  logLevel: 'warn',
  build: {
    ssr: true,
    target: 'node20',
    outDir: path.join(here, 'dist'),
    emptyOutDir: true,
    minify: false,
    rollupOptions: {
      input: path.join(here, 'server.mjs'),
      output: {
        entryFileNames: 'server.mjs',
        format: 'esm',
      },
      // One file, no chunks: the container is given a single artifact, so a
      // split bundle would leave it importing siblings that were never copied.
      codeSplitting: false,
      external: [/^node:/],
    },
  },
  resolve: {
    alias: {
      // Resolved from the workspace root, where these actually are.
      graphql: path.dirname(require.resolve('graphql/package.json')),
      '@graphql-tools/mock': path.dirname(
        require.resolve('@graphql-tools/mock/package.json')
      ),
    },
  },
});

const output = path.join(here, 'dist', 'server.mjs');
const size = (fs.statSync(output).size / 1024).toFixed(0);
console.log(`bundled ${path.relative(workspace, output)} (${size} KiB)`);
