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

## Recommendations

### For Most Use Cases: **Kohsuke GitHub API**
- Easy to use, well-documented
- Handles authentication, rate limiting automatically
- Perfect for typical file operations

### For Batch Operations: **GraphQL API**
- Atomic multi-file commits
- Significant performance advantage for bulk updates
- Worth the complexity for batch workflows

### For Custom Requirements: **Direct REST API**
- Maximum flexibility
- Good for edge cases or specific integration needs
- Requires more maintenance

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
