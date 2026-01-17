package com.examples.github.apis;

import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Example using Kohsuke's github-api library.
 * This is the most mature and widely-used Java GitHub API client.
 */
public class KohsukeGitHubExample {
    private static final Logger logger = LoggerFactory.getLogger(KohsukeGitHubExample.class);
    private final GitHub github;

    public KohsukeGitHubExample(String token) throws IOException {
        this.github = new GitHubBuilder()
            .withOAuthToken(token)
            .build();

        logger.info("Connected to GitHub. Rate limit: {}/{}",
            github.getRateLimit().getCore().getRemaining(),
            github.getRateLimit().getCore().getLimit());
    }

    /**
     * Updates a single file without checking out the repository.
     * This uses GitHub's Contents API to update files directly.
     */
    public String updateSingleFile(String owner, String repoName, String filePath,
                                   String branch, String newContent) throws IOException {
        logger.info("Updating file: {}/{}/{}", owner, repoName, filePath);

        GHRepository repo = github.getRepository(owner + "/" + repoName);

        // Get the current file to retrieve its SHA (required for updates)
        GHContent existingFile = repo.getFileContent(filePath, branch);
        String currentSha = existingFile.getSha();
        logger.info("Current file SHA: {}", currentSha);

        // Update the file
        GHContentUpdateResponse response = repo.createContent()
            .path(filePath)
            .content(newContent)
            .message("Update " + filePath + " via Kohsuke API")
            .sha(currentSha)  // Required to prevent conflicts
            .branch(branch)
            .commit();

        String commitSha = response.getCommit().getSHA1();
        logger.info("File updated successfully. Commit: {}", commitSha);

        return commitSha;
    }

    /**
     * Creates a new file (if it doesn't exist).
     */
    public String createNewFile(String owner, String repoName, String filePath,
                               String branch, String content) throws IOException {
        logger.info("Creating new file: {}/{}/{}", owner, repoName, filePath);

        GHRepository repo = github.getRepository(owner + "/" + repoName);

        GHContentUpdateResponse response = repo.createContent()
            .path(filePath)
            .content(content)
            .message("Create " + filePath + " via Kohsuke API")
            .branch(branch)
            .commit();

        String commitSha = response.getCommit().getSHA1();
        logger.info("File created successfully. Commit: {}", commitSha);

        return commitSha;
    }

    /**
     * Demonstrates updating a binary file.
     */
    public String updateBinaryFile(String owner, String repoName, String filePath,
                                  String branch, byte[] binaryContent) throws IOException {
        logger.info("Updating binary file: {}/{}/{}", owner, repoName, filePath);

        GHRepository repo = github.getRepository(owner + "/" + repoName);

        // Get current file SHA
        GHContent existingFile = repo.getFileContent(filePath, branch);
        String currentSha = existingFile.getSha();

        // Encode binary content as base64
        String base64Content = Base64.getEncoder().encodeToString(binaryContent);

        GHContentUpdateResponse response = repo.createContent()
            .path(filePath)
            .content(base64Content)
            .message("Update binary file " + filePath)
            .sha(currentSha)
            .branch(branch)
            .commit();

        return response.getCommit().getSHA1();
    }

    /**
     * Demonstrates batch file operations (multiple sequential commits).
     * Note: Kohsuke API doesn't support atomic multi-file commits.
     */
    public void updateMultipleFiles(String owner, String repoName, String branch,
                                   String[] filePaths, String[] contents) throws IOException {
        logger.info("Updating {} files sequentially", filePaths.length);

        if (filePaths.length != contents.length) {
            throw new IllegalArgumentException("File paths and contents arrays must have same length");
        }

        GHRepository repo = github.getRepository(owner + "/" + repoName);

        for (int i = 0; i < filePaths.length; i++) {
            try {
                GHContent existingFile = repo.getFileContent(filePaths[i], branch);

                repo.createContent()
                    .path(filePaths[i])
                    .content(contents[i])
                    .message("Update " + filePaths[i] + " (batch operation " + (i+1) + "/" + filePaths.length + ")")
                    .sha(existingFile.getSha())
                    .branch(branch)
                    .commit();

                logger.info("Updated file {}/{}: {}", i+1, filePaths.length, filePaths[i]);
            } catch (IOException e) {
                logger.error("Failed to update {}: {}", filePaths[i], e.getMessage());
                throw e;
            }
        }

        logger.info("All files updated successfully (in {} separate commits)", filePaths.length);
    }

    /**
     * Deletes a file without checking out the repository.
     */
    public String deleteFile(String owner, String repoName, String filePath, String branch) throws IOException {
        logger.info("Deleting file: {}/{}/{}", owner, repoName, filePath);

        GHRepository repo = github.getRepository(owner + "/" + repoName);
        GHContent fileToDelete = repo.getFileContent(filePath, branch);

        GHContentUpdateResponse response = fileToDelete.delete("Delete " + filePath, branch);
        String commitSha = response.getCommit().getSHA1();

        logger.info("File deleted successfully. Commit: {}", commitSha);
        return commitSha;
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null) {
                logger.error("Please set GITHUB_TOKEN environment variable");
                return;
            }

            KohsukeGitHubExample example = new KohsukeGitHubExample(token);

            // Example: Update a file
            String commitSha = example.updateSingleFile(
                "your-username",
                "your-repo",
                "README.md",
                "main",
                "# Updated README\n\nThis was updated programmatically!"
            );

            logger.info("Operation completed. Commit SHA: {}", commitSha);

        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        }
    }
}
