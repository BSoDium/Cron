# CI Setup Guide

## GitHub Secrets Configuration

To enable the CI build workflow to work properly, you need to configure the following secret in your GitHub repository:

### Setting up secrets:

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add the following secret:

| Secret Name | Description | Required |
|------------|-------------|----------|
| `GOOGLE_ROUTES_API_KEY` | Your Google Routes API key | Yes |

### How to add a secret:

1. Click **New repository secret**
2. Name: `GOOGLE_ROUTES_API_KEY`
3. Secret: Paste your actual API key
4. Click **Add secret**

### Testing locally

The CI workflow will use the secret when building. For local development, make sure you have a `local.properties` file (gitignored) with:

```properties
GOOGLE_ROUTES_API_KEY=your_actual_key_here
```

### Workflow behavior

- The workflow runs on all pushes and PRs to the `main` branch
- It builds the debug APK, runs lint checks, and executes unit tests
- If any step fails, build reports are uploaded as artifacts for debugging

### Alternative: Using environment secrets

If you prefer not to store the API key in GitHub:
- You can use GitHub Environments (e.g., `development`, `production`) with environment-specific secrets
- Update the workflow to reference the environment:
  ```yaml
  jobs:
    build:
      runs-on: ubuntu-latest
      environment: development  # Add this line
  ```

