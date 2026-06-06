package com.bhanu.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * JiraAgent — Java CLI for Jira automation
 * ========================================
 * Reads credentials exclusively from config.json at the project root.
 *
 * Usage (via Gradle):
 *   ./gradlew runJiraAgent --args="list"
 *   ./gradlew runJiraAgent --args="get --id BT-1"
 *   ./gradlew runJiraAgent --args="create --type Task --title \"My Task\" --body \"Description\""
 *   ./gradlew runJiraAgent --args="create --type Story --title-file title.txt --body-file body.txt"
 *   ./gradlew runJiraAgent --args="update --id BT-1 --title \"Updated\""
 *   ./gradlew runJiraAgent --args="comment --id BT-1 --body-file comment.txt"
 *   ./gradlew runJiraAgent --args="transition --id BT-1 --status \"In Progress\""
 *   ./gradlew runJiraAgent --args="search --jql \"project=BT ORDER BY created DESC\""
 *   ./gradlew runJiraAgent --args="generate-feature --id BT-1"
 */
public class JiraAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================================
    // Config loader
    // =========================================================================

    static JsonNode loadConfig() {
        Path configPath = Paths.get("config.json");
        if (!Files.exists(configPath)) {
            System.err.println("ERROR: config.json not found at " + configPath.toAbsolutePath());
            System.exit(1);
        }
        JsonNode cfg;
        try {
            cfg = MAPPER.readTree(configPath.toFile());
        } catch (IOException e) {
            System.err.println("ERROR: config.json is not valid JSON — " + e.getMessage());
            System.exit(1);
            return null;
        }
        String[] required = {"base_url", "project_key", "email", "api_token"};
        List<String> missing = new ArrayList<>();
        JsonNode jira = cfg.path("jira");
        for (String field : required) {
            if (jira.path(field).asText("").trim().isEmpty()) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("ERROR: config.json is missing required field(s) under 'jira': " + missing);
            System.exit(1);
        }
        return cfg;
    }

    // =========================================================================
    // Jira REST API client
    // =========================================================================

    static class JiraClient {

        private final String baseUrl;
        private final String projectKey;
        private final String authHeader;
        private final String apiBase;
        private final int maxResults;
        private final HttpClient http;

        JiraClient(JsonNode cfg) {
            JsonNode j = cfg.path("jira");
            String baseUrlRaw = j.path("base_url").asText();
            // Remove trailing slash and /jira path if present
            baseUrlRaw = baseUrlRaw.replaceAll("/$", "").replaceAll("/jira$", "");
            this.baseUrl    = baseUrlRaw;
            this.projectKey = j.path("project_key").asText();
            String email    = j.path("email").asText();
            String token    = j.path("api_token").asText();
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (email + ":" + token).getBytes(StandardCharsets.UTF_8));
            this.apiBase    = baseUrl + "/rest/api/3";
            this.maxResults = cfg.path("agent").path("max_results").asInt(50);
            this.http       = HttpClient.newHttpClient();
        }

        // ----- Internal HTTP helpers ----------------------------------------

        private JsonNode httpGet(String path, Map<String, String> params)
                throws IOException, InterruptedException {
            String url = apiBase + path;
            if (params != null && !params.isEmpty()) {
                StringJoiner sj = new StringJoiner("&", "?", "");
                for (Map.Entry<String, String> e : params.entrySet()) {
                    sj.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                }
                url += sj.toString();
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readTree(resp.body());
        }

        private JsonNode httpPost(String path, ObjectNode payload)
                throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readTree(resp.body());
        }

        private JsonNode httpPut(String path, ObjectNode payload)
                throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
            return MAPPER.readTree(resp.body());
        }

        private void checkStatus(HttpResponse<String> resp) {
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                String errorMsg = resp.body();
                if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                    errorMsg = "Authentication failed. Check API token and permissions.";
                }
                System.err.printf("%nHTTP ERROR %d: %s%n%n", resp.statusCode(), errorMsg);
                System.exit(1);
            }
        }

        // ----- Public operations --------------------------------------------

        JsonNode listIssues() throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jql", "project=" + projectKey + " ORDER BY created DESC");
            params.put("maxResults", String.valueOf(maxResults));
            params.put("expand", "changelog");
            return httpGet("/search", params);
        }

        JsonNode getIssue(String issueKey) throws IOException, InterruptedException {
            return httpGet("/issue/" + issueKey, null);
        }

        JsonNode searchIssues(String jql) throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jql", jql);
            params.put("maxResults", String.valueOf(maxResults));
            return httpGet("/search", params);
        }

        JsonNode createIssue(String issueType, String summary, String description)
                throws IOException, InterruptedException {
            ObjectNode payload = MAPPER.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            fields.putObject("project").put("key", projectKey);
            fields.put("issuetype", MAPPER.createObjectNode().put("name", issueType));
            fields.put("summary", summary);
            if (description != null && !description.trim().isEmpty()) {
                // Convert description to ADF (Atlassian Document Format)
                ObjectNode descObj = MAPPER.createObjectNode();
                descObj.put("version", 1);
                descObj.put("type", "doc");
                ArrayNode contentArray = descObj.putArray("content");
                String[] lines = description.split("\\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        ObjectNode para = MAPPER.createObjectNode();
                        para.put("type", "paragraph");
                        ArrayNode paraContent = para.putArray("content");
                        ObjectNode text = MAPPER.createObjectNode();
                        text.put("type", "text");
                        text.put("text", line);
                        paraContent.add(text);
                        contentArray.add(para);
                    }
                }
                fields.set("description", descObj);
            }
            return httpPost("/issue", payload);
        }

        JsonNode updateIssue(String issueKey, String summary, String description)
                throws IOException, InterruptedException {
            ObjectNode payload = MAPPER.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            if (summary != null && !summary.trim().isEmpty()) {
                fields.put("summary", summary);
            }
            if (description != null && !description.trim().isEmpty()) {
                // Convert description to ADF (Atlassian Document Format)
                ObjectNode descObj = MAPPER.createObjectNode();
                descObj.put("version", 1);
                descObj.put("type", "doc");
                ArrayNode contentArray = descObj.putArray("content");
                String[] lines = description.split("\\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        ObjectNode para = MAPPER.createObjectNode();
                        para.put("type", "paragraph");
                        ArrayNode paraContent = para.putArray("content");
                        ObjectNode text = MAPPER.createObjectNode();
                        text.put("type", "text");
                        text.put("text", line);
                        paraContent.add(text);
                        contentArray.add(para);
                    }
                }
                fields.set("description", descObj);
            }
            return httpPut("/issue/" + issueKey, payload);
        }

        JsonNode addComment(String issueKey, String comment)
                throws IOException, InterruptedException {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("body", comment);
            return httpPost("/issue/" + issueKey + "/comment", payload);
        }

        JsonNode transitionIssue(String issueKey, String transitionName)
                throws IOException, InterruptedException {
            // First get available transitions
            JsonNode transitions = httpGet("/issue/" + issueKey + "/transitions", null);
            String transitionId = null;
            for (JsonNode t : transitions.path("transitions")) {
                if (t.path("name").asText().equalsIgnoreCase(transitionName)) {
                    transitionId = t.path("id").asText();
                    break;
                }
            }
            if (transitionId == null) {
                System.err.println("ERROR: Transition '" + transitionName + "' not found");
                System.exit(1);
            }
            ObjectNode payload = MAPPER.createObjectNode();
            payload.putObject("transition").put("id", transitionId);
            return httpPost("/issue/" + issueKey + "/transitions", payload);
        }

        String getBaseUrl() { return baseUrl; }
        String getProjectKey() { return projectKey; }
    }

    // =========================================================================
    // Output formatters
    // =========================================================================

    static void printIssue(JsonNode issue, boolean raw) throws Exception {
        if (raw) {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(issue));
            return;
        }
        String sep = "=".repeat(80);
        String key = issue.path("key").asText("?");
        JsonNode fields = issue.path("fields");
        String summary = fields.path("summary").asText("N/A");
        String description = fields.path("description").asText("(No description)");
        String status = fields.path("status").path("name").asText("?");
        String issueType = fields.path("issuetype").path("name").asText("?");
        String created = fields.path("created").asText("?");
        String updated = fields.path("updated").asText("?");

        System.out.println("\n" + sep);
        System.out.printf("  Key      : %s%n", key);
        System.out.printf("  Type     : %s%n", issueType);
        System.out.printf("  Summary  : %s%n", summary);
        System.out.printf("  Status   : %s%n", status);
        System.out.printf("  Created  : %s%n", created);
        System.out.printf("  Updated  : %s%n", updated);
        System.out.println(sep);
        System.out.println("  Description:");
        for (String line : description.split("\n")) {
            System.out.println("    " + line);
        }
        System.out.println(sep + "\n");
    }

    static void printList(JsonNode data, boolean raw) throws Exception {
        JsonNode issues = data.path("issues");
        if (raw) {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(issues));
            return;
        }
        if (issues.isEmpty()) { System.out.println("No issues found."); return; }
        System.out.printf("%n%-12s %-12s %-10s %s%n", "Key", "Type", "Status", "Summary");
        System.out.println("-".repeat(100));
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText();
            String type = issue.path("fields").path("issuetype").path("name").asText();
            String status = issue.path("fields").path("status").path("name").asText();
            String summary = issue.path("fields").path("summary").asText();
            if (summary.length() > 50) summary = summary.substring(0, 47) + "...";
            System.out.printf("%-12s %-12s %-10s %s%n", key, type, status, summary);
        }
        System.out.printf("%nTotal: %d issue(s)%n%n", issues.size());
    }

    // =========================================================================
    // Feature file generator
    // =========================================================================

    static void generateFeatureFile(JiraClient client, String issueKey, String tag)
            throws IOException, InterruptedException {
        JsonNode issue = client.getIssue(issueKey);
        JsonNode fields = issue.path("fields");

        String summary = fields.path("summary").asText("Feature");
        String description = fields.path("description").asText("");
        String issueType = fields.path("issuetype").path("name").asText("Story");

        // Generate Gherkin feature
        StringBuilder feature = new StringBuilder();
        feature.append("@").append(tag != null ? tag : "automation").append("\n");
        feature.append("Feature: ").append(summary).append("\n\n");

        if (!description.isEmpty()) {
            feature.append("  # ").append(issueKey).append(" - ").append(description.split("\n")[0]).append("\n\n");
        }

        feature.append("  Scenario: ").append(summary).append("\n");
        feature.append("    Given a precondition for the feature\n");
        feature.append("    When an action is performed\n");
        feature.append("    Then the expected result is verified\n");

        // Create file
        String fileName = issueKey.replace("-", "_") + ".feature";
        Path featurePath = Paths.get("src/test/resources/features").resolve(fileName);
        Files.createDirectories(featurePath.getParent());
        Files.writeString(featurePath, feature.toString(), StandardCharsets.UTF_8);

        System.out.println("\nFeature file generated successfully!");
        System.out.println("  File    : " + featurePath.toAbsolutePath());
        System.out.println("  Issue   : " + issueKey);
        System.out.println("  Summary : " + summary + "\n");
    }

    // =========================================================================
    // CLI argument parser
    // =========================================================================

    static class Args {
        String command;
        String issueKey;
        String issueType = "Task";
        String title;
        String titleFile;
        String body;
        String bodyFile;
        String jql;
        String status;
        String tag;
        boolean raw;
    }

    static String resolveBodyInput(String body, String bodyFile) throws IOException {
        if (body != null && bodyFile != null) {
            System.err.println("ERROR: Provide either --body or --body-file, not both");
            System.exit(1);
        }
        if (bodyFile != null) {
            Path path = Paths.get(bodyFile);
            if (!Files.exists(path)) {
                System.err.println("ERROR: Body file not found -- '" + path.toAbsolutePath() + "'");
                System.exit(1);
            }
            return Files.readString(path, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        }
        return body;
    }

    static String resolveTitleInput(String title, String titleFile) throws IOException {
        if (title != null && titleFile != null) {
            System.err.println("ERROR: Provide either --title or --title-file, not both");
            System.exit(1);
        }
        if (titleFile != null) {
            Path path = Paths.get(titleFile);
            if (!Files.exists(path)) {
                System.err.println("ERROR: Title file not found -- '" + path.toAbsolutePath() + "'");
                System.exit(1);
            }
            String fileTitle = Files.readString(path, StandardCharsets.UTF_8)
                    .replaceFirst("^\\uFEFF", "")
                    .trim();
            if (fileTitle.isEmpty()) {
                System.err.println("ERROR: Title file is empty -- '" + path.toAbsolutePath() + "'");
                System.exit(1);
            }
            return fileTitle;
        }
        return title;
    }

    static Args parseArgs(String[] argv) {
        if (argv.length == 0) printUsageAndExit();
        Args args = new Args();
        args.command = argv[0];
        for (int i = 1; i < argv.length; i++) {
            if ("--id".equals(argv[i]))          { args.issueKey = argv[++i]; }
            else if ("--type".equals(argv[i]))       { args.issueType = argv[++i]; }
            else if ("--title".equals(argv[i]))      { args.title = argv[++i]; }
            else if ("--title-file".equals(argv[i])) { args.titleFile = argv[++i]; }
            else if ("--body".equals(argv[i]))       { args.body = argv[++i]; }
            else if ("--body-file".equals(argv[i]))  { args.bodyFile = argv[++i]; }
            else if ("--jql".equals(argv[i]))        { args.jql = argv[++i]; }
            else if ("--status".equals(argv[i]))     { args.status = argv[++i]; }
            else if ("--tag".equals(argv[i]))        { args.tag = argv[++i]; }
            else if ("--raw".equals(argv[i]))        { args.raw = true; }
            else {
                System.err.println("Unknown argument: " + argv[i]);
                System.exit(1);
            }
        }
        return args;
    }

    static void printUsageAndExit() {
        System.out.println("JiraAgent -- CLI for Jira automation\n");
        System.out.println("Usage (via Gradle):");
        System.out.println("  ./gradlew runJiraAgent --args=\"list\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"get --id BT-1\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"search --jql \\\"project=BT ORDER BY created DESC\\\"\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"create --type Task --title \\\"My Task\\\" --body \\\"Description\\\"\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"create --type Story --title-file title.txt --body-file body.txt\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"update --id BT-1 --title \\\"Updated title\\\"\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"update --id BT-1 --body-file description.txt\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"comment --id BT-1 --body-file comment.txt\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"transition --id BT-1 --status \\\"In Progress\\\"\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"generate-feature --id BT-1\"");
        System.out.println("  ./gradlew runJiraAgent --args=\"generate-feature --id BT-1 --tag myFeature\"");
        System.out.println("\nAdd --raw to any command to print raw JSON.");
        System.exit(0);
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] argv) throws Exception {
        if (argv.length == 0 || "--help".equals(argv[0]) || "-h".equals(argv[0])) {
            printUsageAndExit();
        }

        JsonNode cfg = loadConfig();
        JiraClient client = new JiraClient(cfg);
        Args args = parseArgs(argv);

        if ("list".equals(args.command)) {
            JsonNode data = client.listIssues();
            printList(data, args.raw);

        } else if ("get".equals(args.command)) {
            if (args.issueKey == null) { System.err.println("ERROR: --id is required"); System.exit(1); }
            JsonNode issue = client.getIssue(args.issueKey);
            printIssue(issue, args.raw);

        } else if ("search".equals(args.command)) {
            if (args.jql == null) { System.err.println("ERROR: --jql is required"); System.exit(1); }
            JsonNode data = client.searchIssues(args.jql);
            printList(data, args.raw);

        } else if ("create".equals(args.command)) {
            args.title = resolveTitleInput(args.title, args.titleFile);
            if (args.title == null) { System.err.println("ERROR: --title or --title-file is required"); System.exit(1); }
            String body = resolveBodyInput(args.body, args.bodyFile);
            JsonNode created = client.createIssue(args.issueType, args.title, body);
            String key = created.path("key").asText();
            System.out.println("\nIssue created successfully!");
            System.out.println("  Key  : " + key);
            System.out.println("  Type : " + args.issueType);
            System.out.println("  URL  : " + client.getBaseUrl() + "/browse/" + key + "\n");

        } else if ("update".equals(args.command)) {
            if (args.issueKey == null) { System.err.println("ERROR: --id is required"); System.exit(1); }
            args.title = resolveTitleInput(args.title, args.titleFile);
            String body = resolveBodyInput(args.body, args.bodyFile);
            JsonNode updated = client.updateIssue(args.issueKey, args.title, body);
            System.out.println("\nIssue updated successfully!");
            System.out.println("  Key     : " + args.issueKey);
            System.out.println("  Summary : " + updated.path("fields").path("summary").asText() + "\n");

        } else if ("comment".equals(args.command)) {
            if (args.issueKey == null) { System.err.println("ERROR: --id is required"); System.exit(1); }
            String body = resolveBodyInput(args.body, args.bodyFile);
            if (body == null) { System.err.println("ERROR: --body or --body-file is required"); System.exit(1); }
            client.addComment(args.issueKey, body);
            System.out.println("\nComment added successfully to " + args.issueKey + "\n");

        } else if ("transition".equals(args.command)) {
            if (args.issueKey == null) { System.err.println("ERROR: --id is required"); System.exit(1); }
            if (args.status == null) { System.err.println("ERROR: --status is required"); System.exit(1); }
            client.transitionIssue(args.issueKey, args.status);
            System.out.println("\nIssue transitioned successfully!");
            System.out.println("  Key    : " + args.issueKey);
            System.out.println("  Status : " + args.status + "\n");

        } else if ("generate-feature".equals(args.command)) {
            if (args.issueKey == null) { System.err.println("ERROR: --id is required"); System.exit(1); }
            generateFeatureFile(client, args.issueKey, args.tag);

        } else {
            System.err.println("Unknown command: " + args.command);
            printUsageAndExit();
        }
    }
}

