<!-- nx configuration start-->
<!-- Leave the start & end comments to automatically receive updates. -->

# General Guidelines for working with Nx

- For navigating/exploring the workspace, invoke the `nx-workspace` skill first - it has patterns for querying projects, targets, and dependencies
- When running tasks (for example build, lint, test, e2e, etc.), always prefer running the task through `nx` (i.e. `nx run`, `nx run-many`, `nx affected`) instead of using the underlying tooling directly
- Prefix nx commands with the workspace's package manager (e.g., `pnpm nx build`, `npm exec nx test`) - avoids using globally installed CLI
- You have access to the Nx MCP server and its tools, use them to help the user
- For Nx plugin best practices, check `node_modules/@nx/<plugin>/PLUGIN.md`. Not all plugins have this file - proceed without it if unavailable.
- NEVER guess CLI flags - always check nx_docs or `--help` first when unsure

## Scaffolding & Generators

- For scaffolding tasks (creating apps, libs, project structure, setup), ALWAYS invoke the `nx-generate` skill FIRST before exploring or calling MCP tools

## When to use nx_docs

- USE for: advanced config options, unfamiliar flags, migration guides, plugin configuration, edge cases
- DON'T USE for: basic generator syntax (`nx g @nx/react:app`), standard commands, things you already know
- The `nx-generate` skill handles generator discovery internally - don't call nx_docs just to look up generator syntax


<!-- nx configuration end-->

# Next.js Documentation Protocol

**IMPORTANT**: When working on Next.js or testing-related tasks, you MUST follow this protocol:

## Source of Truth: Local Next.js Docs

The local Next.js documentation is the authoritative source for this project:

```
node_modules/next/dist/docs/
├── 01-app/           # App Router (primary - use this)
├── 02-pages/         # Pages Router
├── 03-architecture/  # Next.js internals
└── 04-community/     # Contribution guides
```

### Key Documentation Paths

| Topic | Path |
|-------|------|
| **App Router Basics** | `node_modules/next/dist/docs/01-app/01-getting-started/` |
| **Routing** | `node_modules/next/dist/docs/01-app/01-getting-started/03-layouts-and-pages.md` |
| **Data Fetching** | `node_modules/next/dist/docs/01-app/02-data-fetching/` |
| **Rendering** | `node_modules/next/dist/docs/01-app/03-rendering/` |
| **Caching** | `node_modules/next/dist/docs/01-app/04-caching/` |
| **Styling** | `node_modules/next/dist/docs/01-app/05-styling/` |
| **Optimizing** | `node_modules/next/dist/docs/01-app/06-optimizing/` |
| **Configuring** | `node_modules/next/dist/docs/01-app/07-configuring/` |
| **Testing** | `node_modules/next/dist/docs/01-app/08-testing/` |
| **Deploying** | `node_modules/next/dist/docs/01-app/09-deploying/` |

## Workflow for Next.js Tasks

1. **Read Local Docs First**: Use the Read tool to consult relevant doc files
2. **Supplement with Context7**: Use Context7 MCP for additional context:
   ```
   1. mcp__context7__resolve-library-id (libraryName: "Next.js", query: "<your question>")
   2. mcp__context7__query-docs (libraryId: <resolved-id>, query: "<your question>")
   ```
3. **Implement**: Only proceed after consulting documentation
4. **Cite**: Mention which documentation you referenced

## When This Protocol Applies

- Creating/modifying pages, layouts, or components
- Implementing routing (parallel routes, intercepting routes, etc.)
- Data fetching patterns (Server Components, Server Actions)
- Testing setup and patterns
- Configuration changes (next.config.js, middleware)
- Image, font, or script optimization
- Metadata and SEO implementation