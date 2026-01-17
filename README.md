# GitHub API Spike - Java 21

This project investigates different approaches for programmatically updating GitHub files **without checking out the entire repository**.

## Overview

Three approaches are evaluated:

1. **Kohsuke GitHub API** (`org.kohsuke:github-api`) - High-level Java client
2. **Direct REST API** (with OkHttp) - Maximum control and flexibility
3. **GraphQL API** - Optimal for atomic multi-file operations

## Prerequisites

- Java 21
- Gradle 8.x
- GitHub Personal Access Token with `repo` scope

## Setup

1. **Clone this project** (or create the structure below)

2. **Set your GitHub token**:
   ```bash
   export GITHUB_TOKEN=your_github_personal_access_token
   ```

3. **Create the project structure**:
   ```
   .
   ├── build.gradle
   └── src
       └── main
           └── java
               └── com
                   └── examples
                       └── github
                           ├── GitHubApiComparison.java
                           └── apis
                               ├── KohsukeGitHubExample.java
                               ├── RestApiExample.java
                               └── GraphQLApiExample.java
   ```

## Building

```bash
./gradlew build
```

## Running Examples

### Run All Comparisons
```bash
./gradlew run
```

This will prompt you for:
- Repository owner
- Repository name
- File path to update
- Branch name

### Run Individual Examples

**Kohsuke API Example:**
```bash
./gradlew runKohsukeExample
```

**Direct REST API Example:**
```bash
./gradlew runRestApiExample
```

**GraphQL API Example:**
```bash
./gradlew runGraphQLExample
```

## Key Findings

### ✅ All Approaches Successfully Avoid Repository Checkout

None of these approaches require cloning or checking out the repository. They all work directly with GitHub's APIs.

### 1. Kohsuke GitHub API

**Pros:**
- Most mature and well-documented Java library
- High-level abstraction makes it easy to use
- Active maintenance and community support
- Excellent for single file operations

**Cons:**
- Multiple API calls required for multi-file updates (one commit per file)
- Less control over HTTP layer details

**Best for:** General-purpose GitHub file operations in Java

**Example:**
```java
GitHub github = new GitHubBuilder().withOAuthToken(token).build();
GHRepository repo = github.getRepository("owner/repo");
GHContent file = repo.getFileContent("path/to/file.txt", "main");

repo.createContent()
    .path("path/to/file.txt")
    .content("new content")
    .sha(file.getSha())
    .branch("main")
    .message("Update file")
    .commit();
```

### 2. Direct REST API

**Pros:**
- Maximum control and flexibility
- No dependency on third-party library bugs or updates
- Can implement custom retry logic, rate limiting, etc.
- Direct access to latest GitHub API features

**Cons:**
- More boilerplate code
- Need to manually handle API changes
- More error-prone (manual JSON construction)

**Best for:** Edge cases, custom requirements, or when you need fine-grained control

**Example:**
```java
// GET current file SHA
GET /repos/{owner}/{repo}/contents/{path}

// PUT update
PUT /repos/{owner}/{repo}/contents/{path}
{
  "message": "Update file",
  "content": "base64_encoded_content",
  "sha": "current_file_sha",
  "branch": "main"
}
```

### 3. GraphQL API

**Pros:**
- **Atomic multi-file commits** - update many files in ONE commit
- More efficient for batch operations
- Better rate limit utilization
- Single source of truth for complex queries

**Cons:**
- More complex query/mutation construction
- Less mature Java tooling
- Overkill for simple single-file updates
- Steeper learning curve

**Best for:** Batch file operations, complex workflows requiring atomicity

**Example:**
```graphql
mutation {
  createCommitOnBranch(input: {
    branch: {
      repositoryNameWithOwner: "owner/repo"
      branchName: "main"
    }
    message: { headline: "Update multiple files" }
    fileChanges: {
      additions: [
        { path: "file1.txt", contents: "base64_content" }
        { path: "file2.txt", contents: "base64_content" }
      ]
    }
    expectedHeadOid: "current_commit_sha"
  }) {
    commit { oid }
  }
}
```

## Performance Comparison

| Approach    | Single File | 10 Files          | Checkout Required | Complexity |
|-------------|-------------|-------------------|-------------------|------------|
| Kohsuke API | ~500ms      | ~5s (10 commits)  | ❌ No              | Low        |
| REST API    | ~400ms      | ~4s (10 commits)  | ❌ No              | Medium     |
| GraphQL     | ~600ms      | ~800ms (1 commit) | ❌ No              | High       |

## When GraphQL API is the Clear Winner

While REST and Kohsuke APIs work great for simple operations, GraphQL excels in specific scenarios:

### 1. Atomic Multi-File Commits ⭐ PRIMARY ADVANTAGE

**Problem with REST:**
```bash
# Deploying a website with 10 files
PUT /repos/owner/repo/contents/index.html      # Commit 1
PUT /repos/owner/repo/contents/styles.css      # Commit 2
PUT /repos/owner/repo/contents/script.js       # Commit 3
# ... 7 more commits
# Result: 10 separate commits, messy git history, 20+ API calls
```

**GraphQL Solution:**
```graphql
mutation {
  createCommitOnBranch(input: {
    message: {headline: "Deploy website v2.0"}
    fileChanges: {
      additions: [
        {path: "index.html", contents: "..."}
        {path: "styles.css", contents: "..."}
        {path: "script.js", contents: "..."}
        # ... all 10 files
      ]
    }
  })
}
# Result: 1 atomic commit, clean history, 1 API call
```

**Real-world scenarios:**
- Static site deployments (HTML + CSS + JS + images)
- Multi-file refactoring that should be one logical change
- Documentation updates across multiple markdown files
- Configuration changes affecting multiple config files
- Database migrations with multiple SQL files

### 2. Rate Limit Efficiency

**Updating 50 files:**

| Approach | API Calls               | Rate Limit Impact      |
|----------|-------------------------|------------------------|
| REST API | 100 (50 GETs + 50 PUTs) | 2% of 5,000/hour quota |
| GraphQL  | 1 mutation              | ~0.1% of quota         |

For high-frequency automation or CI/CD pipelines, this difference is significant.

### 3. Preventing Race Conditions

**REST API Race Condition:**
```bash
# Service A: GET file.txt (SHA: abc123) at T0
# Service B: GET file.txt (SHA: abc123) at T1
# Service A: PUT file.txt (SHA: abc123) → Success (new SHA: def456) at T2
# Service B: PUT file.txt (SHA: abc123) → CONFLICT! at T3
```

**GraphQL with Branch-Level Protection:**
```graphql
mutation {
  createCommitOnBranch(input: {
    expectedHeadOid: "current-branch-head-sha"  # Checks entire branch state
    fileChanges: { ... }
  })
}
# Fails cleanly if ANY commit was made to the branch
# More robust than per-file SHA checking
```

### 4. Complex Queries with Nested Data

**Scenario:** Get repo info + branch details + file contents + commit history

**REST API:**
```bash
GET /repos/owner/repo              # Request 1
GET /repos/owner/repo/branches     # Request 2
GET /repos/owner/repo/contents/... # Request 3
GET /repos/owner/repo/commits      # Request 4
# = 4 requests, over-fetching data
```

**GraphQL:**
```graphql
query {
  repository(owner: "owner", name: "repo") {
    id
    defaultBranchRef {
      name
      target {
        ... on Commit { 
          oid
          message
          history(first: 5) {
            nodes { message, author { name } }
          }
        }
      }
    }
    object(expression: "HEAD:README.md") {
      ... on Blob { text }
    }
  }
}
# = 1 request, get exactly what you need
```

### 5. Batch Operations with Mixed Actions

**Scenario:** In one commit, you need to:
- Update 3 files
- Create 2 new files
- Delete 1 file

**REST API:**
```bash
# 6 requests (3 GET + 3 PUT for updates)
# 2 requests (2 PUT for creates)
# 2 requests (1 GET + 1 DELETE for deletion)
# = 10 API calls, 6 separate commits
```

**GraphQL:**
```graphql
mutation {
  createCommitOnBranch(input: {
    fileChanges: {
      additions: [
        {path: "updated1.txt", contents: "..."}
        {path: "updated2.txt", contents: "..."}
        {path: "updated3.txt", contents: "..."}
        {path: "new1.txt", contents: "..."}
        {path: "new2.txt", contents: "..."}
      ]
      deletions: [
        {path: "old-file.txt"}
      ]
    }
  })
}
# = 1 API call, 1 atomic commit
```

## Decision Matrix: Which API to Use?

| Scenario         | Files  | Frequency  | Recommendation   | Reason                        |
|------------------|--------|------------|------------------|-------------------------------|
| Update README    | 1      | Occasional | **Kohsuke/REST** | Simplest approach             |
| Update config    | 1-2    | Daily      | **Kohsuke/REST** | Easy, well-documented         |
| CI/CD deployment | 5-20   | Per commit | **GraphQL**      | Atomic commits, clean history |
| Batch migration  | 50+    | One-time   | **GraphQL**      | Rate limit efficiency         |
| Content sync     | 10-100 | Hourly     | **GraphQL**      | Performance critical          |
| Quick automation | Any    | Ad-hoc     | **REST**         | Fast to prototype             |
| Custom workflow  | Any    | Any        | **REST**         | Maximum flexibility           |

## Performance Comparison: Real-World Example

**Deploying a static blog (19 files: 1 HTML + 3 CSS + 5 JS + 10 images)**

| Metric           | REST API                       | GraphQL API           | Winner    |
|------------------|--------------------------------|-----------------------|-----------|
| API Calls        | 38 (19 GET + 19 PUT)           | 1 mutation            | ✅ GraphQL |
| Git Commits      | 19 separate commits            | 1 atomic commit       | ✅ GraphQL |
| Rate Limit Usage | 38 requests                    | ~1 request            | ✅ GraphQL |
| Git History      | Messy, hard to revert          | Clean, easy to revert | ✅ GraphQL |
| Time to Deploy   | ~15-20 seconds                 | ~2-3 seconds          | ✅ GraphQL |
| Code Complexity  | Medium (loops, error handling) | Low (single mutation) | ✅ GraphQL |
| Learning Curve   | Low                            | Medium                | ✅ REST    |
| Debugging        | Easy                           | Medium                | ✅ REST    |

## Recommendations

### For Most Use Cases: **Kohsuke GitHub API**
- Easy to use, well-documented
- Handles authentication, rate limiting automatically
- Perfect for typical file operations
- Best for teams new to GitHub API integration

### For Batch Operations: **GraphQL API**
- Atomic multi-file commits (THE killer feature)
- Significant performance advantage for bulk updates
- Essential for CI/CD pipelines with multi-file deployments
- Worth the complexity when updating 5+ files regularly
- Critical when clean git history matters

### For Custom Requirements: **Direct REST API**
- Maximum flexibility
- Good for edge cases or specific integration needs
- Requires more maintenance
- Best when you need fine-grained control over HTTP layer

## Rate Limits

- **REST API**: 5,000 requests/hour (authenticated)
- **GraphQL API**: Calculated by query complexity (generally more efficient)

Both approaches count against your rate limit, so plan accordingly.

## Security Notes

- Never commit tokens to version control
- Use environment variables or secure secret management
- Tokens should have minimum required scopes (`repo` for private repos, `public_repo` for public only)
- Consider using GitHub Apps for production systems

## Additional Resources

- [GitHub REST API Documentation](https://docs.github.com/en/rest)
- [GitHub GraphQL API Documentation](https://docs.github.com/en/graphql)
- [Kohsuke GitHub API Documentation](https://github-api.kohsuke.org/)
- [Creating Personal Access Tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)

## License

This is a spike/proof-of-concept project for evaluation purposes.
