package com.bhanu.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * FlowOrchestratorAgent — Confluence -> Jira -> Feature orchestration CLI
 * =====================================================================
 *
 * Usage (via Gradle):
 *   ./gradlew runFlowOrchestrator --args="sync --confluence-title \"Payments Story\""
 *   ./gradlew runFlowOrchestrator --args="sync --confluence-title-file title.txt --tag automation"
 *   ./gradlew runFlowOrchestrator --args="sync --confluence-title \"Payments Story\" --jira-title \"Payment validation story\""
 */
public class FlowOrchestratorAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            System.err.println("ERROR: config.json is not valid JSON - " + e.getMessage());
            System.exit(1);
            return null;
        }

        List<String> missing = new ArrayList<>();
        ensureRequired(cfg.path("jira"), "jira", new String[]{"base_url", "project_key", "email", "api_token"}, missing);
        ensureRequired(cfg.path("confluence"), "confluence", new String[]{"base_url", "space_key", "email", "api_token"}, missing);

        if (!missing.isEmpty()) {
            System.err.println("ERROR: config.json is missing required field(s): " + missing);
            System.exit(1);
        }
        return cfg;
    }

    private static void ensureRequired(JsonNode section, String sectionName, String[] required, List<String> missing) {
        for (String field : required) {
            if (section.path(field).asText("").trim().isEmpty()) {
                missing.add(sectionName + "." + field);
            }
        }
    }

    static class Args {
        String command;
        String confluenceTitle;
        String confluenceTitleFile;
        String pageId;
        String jiraTitle;
        String jiraType = "Story";
        String tag = "automation";
        boolean raw;
    }

    static Args parseArgs(String[] argv) {
        if (argv.length == 0) {
            printUsageAndExit();
        }

        Args args = new Args();
        args.command = argv[0];

        for (int i = 1; i < argv.length; i++) {
            if ("--confluence-title".equals(argv[i])) {
                args.confluenceTitle = argv[++i];
            } else if ("--confluence-title-file".equals(argv[i])) {
                args.confluenceTitleFile = argv[++i];
            } else if ("--id".equals(argv[i])) {
                args.pageId = argv[++i];
            } else if ("--jira-title".equals(argv[i])) {
                args.jiraTitle = argv[++i];
            } else if ("--jira-type".equals(argv[i])) {
                args.jiraType = argv[++i];
            } else if ("--tag".equals(argv[i])) {
                args.tag = argv[++i];
            } else if ("--raw".equals(argv[i])) {
                args.raw = true;
            } else {
                System.err.println("Unknown argument: " + argv[i]);
                System.exit(1);
            }
        }

        return args;
    }

    static String resolveTitleInput(String title, String titleFile) throws IOException {
        if (title != null && titleFile != null) {
            System.err.println("ERROR: Provide either --confluence-title or --confluence-title-file, not both");
            System.exit(1);
        }
        if (titleFile != null) {
            Path path = Paths.get(titleFile);
            if (!Files.exists(path)) {
                System.err.println("ERROR: Confluence title file not found -- '" + path.toAbsolutePath() + "'");
                System.exit(1);
            }
            String fileTitle = Files.readString(path).replaceFirst("^\\uFEFF", "").trim();
            if (fileTitle.isEmpty()) {
                System.err.println("ERROR: Confluence title file is empty -- '" + path.toAbsolutePath() + "'");
                System.exit(1);
            }
            return fileTitle;
        }
        return title;
    }

    static String normalizeSummary(String title) {
        String normalized = title == null ? "Generated Story" : title.trim().replaceAll("\\s+", " ");
        int maxSummaryLength = 240;
        return normalized.length() > maxSummaryLength ? normalized.substring(0, maxSummaryLength) : normalized;
    }

    static String buildJiraDescription(JsonNode page, String confluenceBaseUrl) {
        String title = page.path("title").asText("Requirement");
        String pageId = page.path("id").asText("?");
        String webUi = page.path("_links").path("webui").asText("");
        String pageUrl = confluenceBaseUrl + "/wiki" + webUi;
        String storageBody = page.path("body").path("storage").path("value").asText("");
        String plainTextRequirements = ConfluenceAgent.storageToPlain(storageBody);

        StringBuilder description = new StringBuilder();
        description.append("Source: Confluence\n");
        description.append("Page Title: ").append(title).append("\n");
        description.append("Page ID: ").append(pageId).append("\n");
        description.append("Page URL: ").append(pageUrl).append("\n\n");
        description.append("Requirements:\n");
        description.append(plainTextRequirements.isBlank() ? "No requirements content found." : plainTextRequirements);
        return description.toString();
    }

    static void printUsageAndExit() {
        System.out.println("FlowOrchestratorAgent -- Confluence -> Jira -> Feature orchestration CLI\n");
        System.out.println("Usage (via Gradle):");
        System.out.println("  ./gradlew runFlowOrchestrator --args=\"sync --confluence-title <title>\"");
        System.out.println("  ./gradlew runFlowOrchestrator --args=\"sync --confluence-title-file <path>\"");
        System.out.println("  ./gradlew runFlowOrchestrator --args=\"sync --id <confluencePageId>\"");
        System.out.println("  ./gradlew runFlowOrchestrator --args=\"sync --confluence-title <title> --jira-title <storyTitle>\"");
        System.out.println("  ./gradlew runFlowOrchestrator --args=\"sync --confluence-title <title> --jira-type Story --tag automation\"");
        System.out.println("\nAdd --raw to print raw JSON responses where available.");
        System.exit(0);
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length == 0 || "--help".equals(argv[0]) || "-h".equals(argv[0])) {
            printUsageAndExit();
        }

        JsonNode cfg = loadConfig();
        ConfluenceAgent.ConfluenceClient confluenceClient = new ConfluenceAgent.ConfluenceClient(cfg);
        JiraAgent.JiraClient jiraClient = new JiraAgent.JiraClient(cfg);
        Args args = parseArgs(argv);

        if (!"sync".equals(args.command)) {
            System.err.println("Unknown command: " + args.command);
            printUsageAndExit();
        }

        args.confluenceTitle = resolveTitleInput(args.confluenceTitle, args.confluenceTitleFile);
        if ((args.confluenceTitle == null || args.confluenceTitle.trim().isEmpty())
                && (args.pageId == null || args.pageId.trim().isEmpty())) {
            System.err.println("ERROR: --confluence-title (or --confluence-title-file) or --id is required");
            System.exit(1);
        }

        JsonNode confluencePage = confluenceClient.readPage(args.confluenceTitle, args.pageId);
        String jiraSummary = normalizeSummary(args.jiraTitle != null ? args.jiraTitle : confluencePage.path("title").asText("Generated Story"));
        String jiraDescription = buildJiraDescription(confluencePage, cfg.path("confluence").path("base_url").asText("").replaceAll("/$", ""));

        JsonNode createdIssue = jiraClient.createIssue(args.jiraType, jiraSummary, jiraDescription);
        String issueKey = createdIssue.path("key").asText();

        System.out.println("\nJira story created successfully!");
        System.out.println("  Key    : " + issueKey);
        System.out.println("  Title  : " + jiraSummary);
        System.out.println("  URL    : " + jiraClient.getBaseUrl() + "/browse/" + issueKey);

        if (args.raw) {
            System.out.println("\nRaw Confluence page payload:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(confluencePage));
            System.out.println("\nRaw Jira create payload response:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(createdIssue));
        }

        JiraAgent.generateFeatureFile(jiraClient, issueKey, args.tag);
        System.out.println("Orchestration completed: Confluence -> Jira -> Feature file\n");
    }
}

