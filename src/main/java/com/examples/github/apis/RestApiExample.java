package com.examples.github.apis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Example using direct REST API calls with OkHttp.
 * This approach gives maximum control and flexibility.
 */
public class RestApiExample {
    private static final Logger logger = LoggerFactory.getLogger(RestApiExample.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String token;
    private final Gson gson;

    public RestApiExample(String token) {
        this.token = token;
        this.client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build();
                return chain.proceed(request);
            })
            .build();
        this.gson = new Gson();
    }

    /**
     * Updates a single file using GitHub REST API.
     * Endpoint: PUT /repos/{owner}/{repo}/contents/{path}
     */
    public String updateSingleFile(String owner, String repo, String filePath,
                                  String branch, String newContent) throws IOException {
        logger.info("Updating file via REST API: {}/{}/{}", owner, repo, filePath);

        // Step 1: Get current file to retrieve SHA
        String currentSha = getFileSha(owner, repo, filePath, branch);
        logger.info("Current file SHA: {}", currentSha);

        // Step 2: Prepare update request
        String url = String.format("%s/repos/%s/%s/contents/%s",
            GITHUB_API_BASE, owner, repo, filePath);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("message", "Update " + filePath + " via REST API");
        requestBody.addProperty("content",
            Base64.getEncoder().encodeToString(newContent.getBytes(StandardCharsets.UTF_8)));
        requestBody.addProperty("sha", currentSha);
        requestBody.addProperty("branch", branch);

        Request request = new Request.Builder()
            .url(url)
            .put(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        // Step 3: Execute request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Failed to update file: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            String commitSha = jsonResponse.getAsJsonObject("commit").get("sha").getAsString();

            logger.info("File updated successfully via REST. Commit: {}", commitSha);
            return commitSha;
        }
    }

    /**
     * Gets the SHA of a file.
     * Endpoint: GET /repos/{owner}/{repo}/contents/{path}
     */
    private String getFileSha(String owner, String repo, String filePath, String branch) throws IOException {
        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
            GITHUB_API_BASE, owner, repo, filePath, branch);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get file SHA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.get("sha").getAsString();
        }
    }

    /**
     * Creates a new file.
     * Endpoint: PUT /repos/{owner}/{repo}/contents/{path}
     */
    public String createNewFile(String owner, String repo, String filePath,
                               String branch, String content) throws IOException {
        logger.info("Creating new file via REST API: {}/{}/{}", owner, repo, filePath);

        String url = String.format("%s/repos/%s/%s/contents/%s",
            GITHUB_API_BASE, owner, repo, filePath);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("message", "Create " + filePath + " via REST API");
        requestBody.addProperty("content",
            Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        requestBody.addProperty("branch", branch);

        Request request = new Request.Builder()
            .url(url)
            .put(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Failed to create file: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            String commitSha = jsonResponse.getAsJsonObject("commit").get("sha").getAsString();

            logger.info("File created successfully. Commit: {}", commitSha);
            return commitSha;
        }
    }

    /**
     * Deletes a file.
     * Endpoint: DELETE /repos/{owner}/{repo}/contents/{path}
     */
    public String deleteFile(String owner, String repo, String filePath, String branch) throws IOException {
        logger.info("Deleting file via REST API: {}/{}/{}", owner, repo, filePath);

        String currentSha = getFileSha(owner, repo, filePath, branch);
        String url = String.format("%s/repos/%s/%s/contents/%s",
            GITHUB_API_BASE, owner, repo, filePath);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("message", "Delete " + filePath + " via REST API");
        requestBody.addProperty("sha", currentSha);
        requestBody.addProperty("branch", branch);

        Request request = new Request.Builder()
            .url(url)
            .delete(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to delete file: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            String commitSha = jsonResponse.getAsJsonObject("commit").get("sha").getAsString();

            logger.info("File deleted successfully. Commit: {}", commitSha);
            return commitSha;
        }
    }

    /**
     * Gets current rate limit information.
     * Endpoint: GET /rate_limit
     */
    public void checkRateLimit() throws IOException {
        String url = GITHUB_API_BASE + "/rate_limit";

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                JsonObject core = json.getAsJsonObject("resources").getAsJsonObject("core");

                logger.info("Rate limit: {}/{} remaining",
                    core.get("remaining").getAsInt(),
                    core.get("limit").getAsInt());
                logger.info("Reset at: {}",
                    new java.util.Date(core.get("reset").getAsLong() * 1000));
            }
        }
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

            RestApiExample example = new RestApiExample(token);

            // Check rate limit
            example.checkRateLimit();

            // Example: Update a file
            String commitSha = example.updateSingleFile(
                "your-username",
                "your-repo",
                "README.md",
                "main",
                "# Updated README\n\nUpdated via direct REST API!"
            );

            logger.info("Operation completed. Commit SHA: {}", commitSha);

        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
        }
    }
}
