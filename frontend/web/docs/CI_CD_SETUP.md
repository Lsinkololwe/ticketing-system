# CI/CD Integration for Radix UI Compliance

This guide explains how the compliance testing is integrated into the CI/CD pipeline.

## Table of Contents

- [Overview](#overview)
- [GitHub Actions Workflow](#github-actions-workflow)
- [Workflow Jobs](#workflow-jobs)
- [PR Comments](#pr-comments)
- [Troubleshooting](#troubleshooting)
- [Local Testing](#local-testing)

## Overview

The Radix UI compliance testing runs automatically on every pull request to ensure code quality and adherence to design standards before merging.

### When Tests Run

- On pull request creation
- On every push to the pull request branch
- When pull request is updated

### What Gets Tested

1. ESLint with custom radix-compliance rules
2. React Testing Library unit tests
3. Playwright E2E tests
4. Visual regression tests

## GitHub Actions Workflow

File: `.github/workflows/radix-ui-compliance.yml`

### Workflow Configuration

```yaml
name: Radix UI Compliance

on:
  pull_request:
    branches:
      - main
      - develop
    paths:
      - 'frontend/web/**'
```

The workflow only runs when frontend files are changed, avoiding unnecessary runs on backend changes.

## Workflow Jobs

### Job 1: compliance-check

Runs ESLint and React Testing Library tests.

**Steps:**
1. Checkout code
2. Setup Node.js 20
3. Setup pnpm 8
4. Install dependencies
5. Run ESLint with radix-compliance rules
6. Run React Testing Library compliance tests
7. Parse violations
8. Comment on PR with results
9. Fail if violations found (strict mode)

**Duration:** ~5 minutes

### Job 2: e2e-compliance

Runs Playwright E2E tests for visual regression and component behavior.

**Steps:**
1. Checkout code
2. Setup Node.js 20
3. Setup pnpm 8
4. Install dependencies
5. Install Playwright browsers
6. Run E2E compliance tests
7. Upload Playwright report
8. Upload screenshots
9. Comment on PR with results

**Duration:** ~10 minutes

## PR Comments

### Successful Run

```markdown
✅ All compliance checks passed!

## Radix UI Compliance Report

### ESLint Violations: 0
### React Testing Library Failures: 0

All checks passed! 🎉
```

### Failed Run with Violations

```markdown
❌ Compliance violations detected

## Radix UI Compliance Report

### ESLint Violations: 5
### React Testing Library Failures: 2

#### ESLint Violations

```
/src/components/Dashboard.tsx
  12:7  error  Avoid inline color styles  radix-compliance/no-inline-styles
  15:9  warn   Use <Button> from @radix-ui/themes  radix-compliance/require-radix-components
```

#### Test Failures

```
✗ should not have inline color styles
  Expected: 0
  Received: 3
```

---

**What to do next:**

1. Fix ESLint violations by replacing raw HTML elements with Radix UI components
2. Remove inline styles and use Radix UI props instead
3. Replace hardcoded colors with Radix UI color tokens
4. Review failed tests and ensure components use Radix UI primitives
5. Verify theme token usage in your components

**Resources:**
- [Radix UI Themes Documentation](https://www.radix-ui.com/themes/docs)
- [Project Compliance Standards](../docs/COMPLIANCE_STANDARDS.md)
- [ESLint Plugin README](../tools/eslint-plugin-radix-compliance/README.md)
```

### E2E Test Results

```markdown
## ✅ E2E compliance tests passed

<details>
<summary>View E2E Test Results</summary>

```
Running 12 tests using 3 workers

  ✓ theme-consistency.spec.ts:10:5 › should not have hardcoded colors (2s)
  ✓ theme-consistency.spec.ts:20:5 › should use consistent colors (1.5s)
  ✓ radix-components.spec.ts:15:5 › should use Radix buttons (1s)
```

</details>

[View full Playwright report](https://github.com/your-org/ticketing-system/actions/runs/123456)
```

## Troubleshooting

### Workflow Not Running

**Problem:** Workflow doesn't run on pull request.

**Solutions:**
1. Check that changes are in `frontend/web/**` path
2. Verify workflow file is in `.github/workflows/` directory
3. Ensure pull request targets `main` or `develop` branch
4. Check GitHub Actions are enabled for repository

### Workflow Fails to Install Dependencies

**Problem:** `pnpm install` fails.

**Solutions:**
1. Check `pnpm-lock.yaml` is committed
2. Verify Node.js version matches (20)
3. Check for dependency conflicts
4. Clear cache by re-running workflow

### Playwright Tests Timeout

**Problem:** E2E tests timeout after 20 minutes.

**Solutions:**
1. Reduce number of tests running in parallel
2. Increase timeout in workflow (max 60 minutes)
3. Check if app is starting correctly
4. Review test performance locally

### False Positives

**Problem:** Tests fail but code is compliant.

**Solutions:**
1. Update baseline screenshots if UI intentionally changed
2. Review ESLint rule configuration
3. Check test assertions for correctness
4. Add exceptions for edge cases

## Local Testing

Before pushing to PR, run tests locally to catch violations early.

### Quick Check

```bash
cd frontend/web

# Run all compliance checks
pnpm lint:all && \
pnpm test libs/shared/src/__tests__/compliance --run && \
pnpm e2e:org-admin apps/organization-admin/e2e/compliance
```

### Individual Checks

```bash
# ESLint only
pnpm lint:all

# Unit tests only
pnpm test libs/shared/src/__tests__/compliance --run

# E2E tests only
pnpm e2e:org-admin apps/organization-admin/e2e/compliance
```

### Watch Mode for Development

```bash
# Unit tests in watch mode
pnpm test libs/shared/src/__tests__/compliance --watch

# E2E in headed mode (see browser)
pnpm e2e:org-admin:headed apps/organization-admin/e2e/compliance
```

## Workflow Artifacts

The workflow saves artifacts for debugging:

### Playwright Report

- **Name:** `playwright-compliance-report`
- **Path:** `frontend/web/playwright-report/`
- **Retention:** 7 days
- **Access:** Download from GitHub Actions run page

### Screenshots

- **Name:** `playwright-screenshots`
- **Path:** `frontend/web/apps/organization-admin/e2e/.playwright/screenshots/`
- **Retention:** 7 days
- **Access:** Download from GitHub Actions run page

### Viewing Artifacts

1. Go to the GitHub Actions run page
2. Scroll to "Artifacts" section at the bottom
3. Click to download artifact ZIP file
4. Extract and view locally

For Playwright reports:
```bash
# Extract artifact
unzip playwright-compliance-report.zip

# View report
npx playwright show-report playwright-report/
```

## Customizing the Workflow

### Change Strictness

Edit `.github/workflows/radix-ui-compliance.yml`:

```yaml
# Strict mode - fail on any violation
- name: Fail if violations found (strict mode)
  if: steps.parse-violations.outputs.eslint_violations > 0 || steps.parse-violations.outputs.rtl_failures > 0
  run: |
    echo "❌ Compliance violations detected. Please fix before merging."
    exit 1

# Warning mode - allow violations but warn
- name: Warn if violations found (warning mode)
  if: steps.parse-violations.outputs.eslint_violations > 0 || steps.parse-violations.outputs.rtl_failures > 0
  run: |
    echo "⚠️ Compliance violations detected. Please fix when possible."
    exit 0
```

### Skip E2E Tests

Add skip label to PR:

```yaml
jobs:
  e2e-compliance:
    if: "!contains(github.event.pull_request.labels.*.name, 'skip-e2e')"
```

Then add `skip-e2e` label to PR to skip E2E tests.

### Change Node.js Version

```yaml
- name: Setup Node.js
  uses: actions/setup-node@v4
  with:
    node-version: '22'  # Change to desired version
```

### Add Slack Notifications

Add Slack notification step:

```yaml
- name: Notify Slack on failure
  if: failure()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
    payload: |
      {
        "text": "Compliance tests failed for PR #${{ github.event.pull_request.number }}"
      }
```

## Required GitHub Secrets

The workflow uses the default `GITHUB_TOKEN` provided by GitHub Actions. No additional secrets are required.

Optional secrets for extended functionality:
- `SLACK_WEBHOOK_URL` - For Slack notifications
- `DISCORD_WEBHOOK_URL` - For Discord notifications

## Performance Optimization

### Caching

The workflow uses pnpm store caching to speed up dependency installation:

```yaml
- name: Setup pnpm cache
  uses: actions/cache@v4
  with:
    path: ${{ steps.pnpm-cache.outputs.STORE_PATH }}
    key: ${{ runner.os }}-pnpm-store-${{ hashFiles('**/pnpm-lock.yaml') }}
```

### Parallel Execution

Jobs run in parallel when possible:
- `compliance-check` and `e2e-compliance` run concurrently

### Selective Testing

Only run tests when relevant files change:

```yaml
on:
  pull_request:
    paths:
      - 'frontend/web/**'
```

## Monitoring

### View Workflow Status

1. Go to repository on GitHub
2. Click "Actions" tab
3. Click on workflow run
4. View job details and logs

### Workflow Metrics

Track over time:
- Average run duration
- Failure rate
- Most common violations

### Setting Up Alerts

Configure GitHub notifications:
1. Go to repository settings
2. Click "Notifications"
3. Enable "Actions" notifications
4. Choose notification method (email, web, mobile)

## Best Practices

1. **Run tests locally before pushing** - Catch violations early
2. **Fix violations immediately** - Don't let them accumulate
3. **Review PR comments** - They provide helpful guidance
4. **Update baselines when needed** - Visual changes require screenshot updates
5. **Monitor workflow performance** - Optimize if tests become slow

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Playwright CI/CD](https://playwright.dev/docs/ci)
- [pnpm in CI](https://pnpm.io/continuous-integration)
- [Compliance Testing Guide](./COMPLIANCE_TESTING.md)

## Support

If you encounter issues with the CI/CD workflow:

1. Check workflow logs for detailed error messages
2. Review this documentation
3. Run tests locally to reproduce the issue
4. Create an issue with workflow run link and error details
5. Contact the team in Slack #frontend-help channel
