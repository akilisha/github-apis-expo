package com.examples.github;

import com.examples.github.apis.GraphQLApiExample;
import com.examples.github.apis.KohsukeGitHubExample;
import com.examples.github.apis.RestApiExample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Main class to compare different GitHub API approaches for updating files
 * without checking out the entire repository.
 */
public class GitHubApiComparison {
    private static final Logger logger = LoggerFactory.getLogger(GitHubApiComparison.class);

    public static void main(String[] args) {
        logger.info("GitHub API Spike - Comparing different approaches");
        logger.info("================================================");

        String token = getGitHubToken();
        String owner = getInput("Enter repository owner: ");
        String repo = getInput("Enter repository name: ");
        String filePath = getInput("Enter file path to update (e.g., README.md): ");
        String branch = getInput("Enter branch name (default: main): ", "main");

        logger.info("\n--- Test 1: Kohsuke GitHub API (github-api) ---");
        testKohsukeApi(token, owner, repo, filePath, branch);

        logger.info("\n--- Test 2: Direct REST API with OkHttp ---");
        testRestApi(token, owner, repo, filePath, branch);

        logger.info("\n--- Test 3: GraphQL API (for multi-file updates) ---");
        testGraphQLApi(token, owner, repo, branch);

        printEvaluationSummary();
    }

    private static void testKohsukeApi(String token, String owner, String repo,
                                       String filePath, String branch) {
        try {
            KohsukeGitHubExample example = new KohsukeGitHubExample(token);

            long startTime = System.currentTimeMillis();
            String commitSha = example.updateSingleFile(
                owner, repo, filePath, branch,
                "Updated via Kohsuke API at " + System.currentTimeMillis()
            );
            long duration = System.currentTimeMillis() - startTime;

            logger.info("✓ Success! Commit SHA: {}", commitSha);
            logger.info("  Duration: {}ms", duration);
            logger.info("  Memory efficient: YES (no checkout required)");
        } catch (Exception e) {
            logger.error("✗ Failed: {}", e.getMessage());
        }
    }

    private static void testRestApi(String token, String owner, String repo,
                                    String filePath, String branch) {
        try {
            RestApiExample example = new RestApiExample(token);

            long startTime = System.currentTimeMillis();
            String commitSha = example.updateSingleFile(
                owner, repo, filePath, branch,
                "Updated via REST API at " + System.currentTimeMillis()
            );
            long duration = System.currentTimeMillis() - startTime;

            logger.info("✓ Success! Commit SHA: {}", commitSha);
            logger.info("  Duration: {}ms", duration);
            logger.info("  Memory efficient: YES (no checkout required)");
            logger.info("  Adaptability: HIGH (direct control over HTTP requests)");
        } catch (Exception e) {
            logger.error("✗ Failed: {}", e.getMessage());
        }
    }

    private static void testGraphQLApi(String token, String owner, String repo, String branch) {
        try {
            GraphQLApiExample example = new GraphQLApiExample(token);

            logger.info("GraphQL is ideal for batch operations (multiple file updates)");
            logger.info("Example: Updating 3 files in a single atomic commit");

            long startTime = System.currentTimeMillis();
            example.demonstrateMultiFileUpdate(owner, repo, branch);
            long duration = System.currentTimeMillis() - startTime;

            logger.info("  Duration: {}ms", duration);
            logger.info("  Efficiency: HIGH (atomic multi-file commits)");
        } catch (Exception e) {
            logger.error("✗ GraphQL demo failed: {}", e.getMessage());
        }
    }

    private static void printEvaluationSummary() {
        logger.info("\n");
        logger.info("===============================================");
        logger.info("EVALUATION SUMMARY");
        logger.info("===============================================");
        logger.info("");
        logger.info("1. KOHSUKE GITHUB-API (org.kohsuke:github-api)");
        logger.info("   Pros:");
        logger.info("   - Most mature and well-documented Java client");
        logger.info("   - High-level abstraction, easy to use");
        logger.info("   - Supports single file updates without checkout");
        logger.info("   - Active maintenance");
        logger.info("   Cons:");
        logger.info("   - Multiple API calls for multi-file updates");
        logger.info("   - Less control over raw HTTP details");
        logger.info("   Recommendation: BEST for most use cases");
        logger.info("");
        logger.info("2. DIRECT REST API (with OkHttp)");
        logger.info("   Pros:");
        logger.info("   - Maximum control and flexibility");
        logger.info("   - No dependency on third-party client bugs");
        logger.info("   - Can implement custom retry/rate-limit logic");
        logger.info("   Cons:");
        logger.info("   - More boilerplate code");
        logger.info("   - Need to handle GitHub API changes manually");
        logger.info("   Recommendation: Good for edge cases or custom needs");
        logger.info("");
        logger.info("3. GRAPHQL API");
        logger.info("   Pros:");
        logger.info("   - Atomic multi-file commits (single operation)");
        logger.info("   - More efficient for batch operations");
        logger.info("   - Better rate limit utilization");
        logger.info("   Cons:");
        logger.info("   - More complex query construction");
        logger.info("   - Less mature Java tooling");
        logger.info("   - Overkill for single file updates");
        logger.info("   Recommendation: Use for multi-file operations");
        logger.info("");
        logger.info("CONCLUSION:");
        logger.info("All three approaches successfully update files WITHOUT");
        logger.info("checking out the repository. For Java 21 projects:");
        logger.info("- Use Kohsuke GitHub API for general use");
        logger.info("- Use GraphQL for batch file operations");
        logger.info("- Use direct REST for maximum control");
    }

    private static String getGitHubToken() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isEmpty()) {
            logger.warn("GITHUB_TOKEN environment variable not set");
            return getInput("Enter GitHub personal access token: ");
        }
        return token;
    }

    private static String getInput(String prompt) {
        return getInput(prompt, null);
    }

    private static String getInput(String prompt, String defaultValue) {
        System.out.print(prompt);
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        if (input.isEmpty() && defaultValue != null) {
            return defaultValue;
        }
        return input;
    }
}
