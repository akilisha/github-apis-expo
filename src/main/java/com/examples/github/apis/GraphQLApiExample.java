package com.examples.github.apis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Example using GitHub's GraphQL API.
 * GraphQL is superior for atomic multi-file operations.
 */
public class GraphQLApiExample {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLApiExample.class);
    private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String token;
    private final Gson gson;

    public GraphQLApiExample(String token) {
        this.token = token;
        this.client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();
                return chain.proceed(request);
            })
            .build();
        this.gson = new Gson();
    }

    /**
     * Gets the repository ID and current HEAD commit SHA (OID) needed for GraphQL mutations.
     */
    private JsonObject getRepositoryInfo(String owner, String repo, String branch) throws IOException {
        String query = """
            query($owner: String!, $repo: String!, $branch: String!) {
              repository(owner: $owner, name: $repo) {
                id
                ref(qualifiedName: $branch) {
                  target {
                    ... on Commit {
                      oid
                    }
                  }
                }
              }
            }
            """;

        JsonObject variables = new JsonObject();
        variables.addProperty("owner", owner);
        variables.addProperty("repo", repo);
        variables.addProperty("branch", "refs/heads/" + branch);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", query);
        requestBody.add("variables", variables);

        Request request = new Request.Builder()
            .url(GITHUB_GRAPHQL_ENDPOINT)
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get repository info: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("errors")) {
                throw new IOException("GraphQL errors: " + json.get("errors").toString());
            }

            return json.getAsJsonObject("data").getAsJsonObject("repository");
        }
    }

    /**
     * Creates an atomic commit with multiple file changes using GraphQL.
     * This is the key advantage of GraphQL - all files in ONE commit.
     */
    public String createAtomicCommit(String owner, String repo, String branch,
                                    String commitMessage, FileChange[] fileChanges) throws IOException {
        logger.info("Creating atomic commit with {} file changes", fileChanges.length);

        // Get repository info
        JsonObject repoInfo = getRepositoryInfo(owner, repo, branch);
        String repoId = repoInfo.get("id").getAsString();
        String headOid = repoInfo.getAsJsonObject("ref")
            .getAsJsonObject("target")
            .get("oid").getAsString();

        logger.info("Repository ID: {}", repoId);
        logger.info("Current HEAD: {}", headOid);

        // Build the mutation
        String mutation = """
            mutation($input: CreateCommitOnBranchInput!) {
              createCommitOnBranch(input: $input) {
                commit {
                  oid
                  url
                }
              }
            }
            """;

        // Build file changes
        JsonObject fileChangesJson = new JsonObject();
        JsonArray additions = new JsonArray();
        JsonArray deletions = new JsonArray();

        for (FileChange change : fileChanges) {
            if (change.isDelete()) {
                JsonObject deletion = new JsonObject();
                deletion.addProperty("path", change.getPath());
                deletions.add(deletion);
            } else {
                JsonObject addition = new JsonObject();
                addition.addProperty("path", change.getPath());
                addition.addProperty("contents",
                    Base64.getEncoder().encodeToString(change.getContent().getBytes(StandardCharsets.UTF_8)));
                additions.add(addition);
            }
        }

        if (additions.size() > 0) {
            fileChangesJson.add("additions", additions);
        }
        if (deletions.size() > 0) {
            fileChangesJson.add("deletions", deletions);
        }

        // Build input
        JsonObject branchInput = new JsonObject();
        branchInput.addProperty("repositoryNameWithOwner", owner + "/" + repo);
        branchInput.addProperty("branchName", branch);

        JsonObject messageInput = new JsonObject();
        messageInput.addProperty("headline", commitMessage);

        JsonObject input = new JsonObject();
        input.add("branch", branchInput);
        input.add("message", messageInput);
        input.add("fileChanges", fileChangesJson);
        input.addProperty("expectedHeadOid", headOid);

        JsonObject variables = new JsonObject();
        variables.add("input", input);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", mutation);
        requestBody.add("variables", variables);

        // Execute mutation
        Request request = new Request.Builder()
            .url(GITHUB_GRAPHQL_ENDPOINT)
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Failed to create commit: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("errors")) {
                throw new IOException("GraphQL errors: " + json.get("errors").toString());
            }

            JsonObject commit = json.getAsJsonObject("data")
                .getAsJsonObject("createCommitOnBranch")
                .getAsJsonObject("commit");

            String commitOid = commit.get("oid").getAsString();
            String commitUrl = commit.get("url").getAsString();

            logger.info("Atomic commit created successfully!");
            logger.info("Commit OID: {}", commitOid);
            logger.info("Commit URL: {}", commitUrl);

            return commitOid;
        }
    }

    /**
     * Demonstrates a multi-file update scenario.
     */
    public void demonstrateMultiFileUpdate(String owner, String repo, String branch) throws IOException {
        FileChange[] changes = {
            new FileChange("file1.txt", "Content of file 1\nUpdated at: " + System.currentTimeMillis()),
            new FileChange("file2.txt", "Content of file 2\nUpdated at: " + System.currentTimeMillis()),
            new FileChange("file3.txt", "Content of file 3\nUpdated at: " + System.currentTimeMillis())
        };

        logger.info("Demonstrating atomic multi-file commit with GraphQL");
        logger.info("This would require 3 separate commits with REST API");
        logger.info("With GraphQL: ALL changes in 1 atomic commit");

        String commitOid = createAtomicCommit(
            owner, repo, branch,
            "Update multiple files atomically via GraphQL",
            changes
        );

        logger.info("Success! All {} files updated in single commit: {}", changes.length, commitOid);
    }

    /**
     * Helper class to represent a file change.
     */
    public static class FileChange {
        private final String path;
        private final String content;
        private final boolean delete;

        public FileChange(String path, String content) {
            this.path = path;
            this.content = content;
            this.delete = false;
        }

        public FileChange(String path, boolean delete) {
            this.path = path;
            this.content = null;
            this.delete = delete;
        }

        public String getPath() { return path; }
        public String getContent() { return content; }
        public boolean isDelete() { return delete; }
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

            GraphQLApiExample example = new GraphQLApiExample(token);

            // Demonstrate multi-file update
            example.demonstrateMultiFileUpdate(
                "your-username",
                "your-repo",
                "main"
            );

        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        }
    }
}
