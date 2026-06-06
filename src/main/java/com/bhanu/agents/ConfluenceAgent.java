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
 * ConfluenceReader Agent — Java CLI
 * ==================================
 * Reads credentials exclusively from config.json at the project root.
 *
 * Usage (via Gradle):
 *   ./gradlew runConfluenceAgent --args="list"
 *   ./gradlew runConfluenceAgent --args="read --title \"My Page\""
 *   ./gradlew runConfluenceAgent --args="read --title-file \"title.txt\""
 *   ./gradlew runConfluenceAgent --args="read --id 123456"
 *   ./gradlew runConfluenceAgent --args="info --title \"My Page\""
 *   ./gradlew runConfluenceAgent --args="info --title-file \"title.txt\""
 *   ./gradlew runConfluenceAgent --args="search --query \"deployment\""
 *   ./gradlew runConfluenceAgent --args="create --title \"New Page\" --body \"## Hello\nContent\" --parent \"Parent Title\""
 *   ./gradlew runConfluenceAgent --args="create --title-file \"title.txt\" --body-file \"notes.md\""
 *   ./gradlew runConfluenceAgent --args="create --title \"New Page\" --body-file \"notes.md\" --parent \"Parent Title\""
 *   ./gradlew runConfluenceAgent --args="update --title \"Existing Page\" --body \"Updated content.\""
 *   ./gradlew runConfluenceAgent --args="update --title-file \"title.txt\" --body-file \"notes.md\""
 *   ./gradlew runConfluenceAgent --args="update --title \"Existing Page\" --body-file \"notes.md\""
 *   ./gradlew runConfluenceAgent --args="delete --title \"Old Page\""
 *   ./gradlew runConfluenceAgent --args="delete --title-file \"title.txt\" --force"
 *
 * Append --raw to any command to receive raw JSON output.
 */
public class ConfluenceAgent {

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
        String[] required = {"base_url", "space_key", "email", "api_token"};
        List<String> missing = new ArrayList<>();
        JsonNode confluence = cfg.path("confluence");
        for (String field : required) {
            if (confluence.path(field).asText("").trim().isEmpty()) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("ERROR: config.json is missing required field(s) under 'confluence': " + missing);
            System.exit(1);
        }
        return cfg;
    }

    // =========================================================================
    // Confluence REST API client
    // =========================================================================

    static class ConfluenceClient {

        private final String baseUrl;
        private final String spaceKey;
        private final String authHeader;
        private final String apiBase;
        private final int maxResults;
        private final HttpClient http;

        ConfluenceClient(JsonNode cfg) {
            JsonNode c = cfg.path("confluence");
            this.baseUrl    = c.path("base_url").asText().replaceAll("/$", "");
            this.spaceKey   = c.path("space_key").asText();
            String email    = c.path("email").asText();
            String token    = c.path("api_token").asText();
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (email + ":" + token).getBytes(StandardCharsets.UTF_8));
            this.apiBase    = baseUrl + "/wiki/rest/api";
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

        private void httpDelete(String path) throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .header("Authorization", authHeader)
                    .DELETE().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            checkStatus(resp);
        }

        private void checkStatus(HttpResponse<String> resp) {
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                System.err.printf("%nHTTP ERROR %d: %s%n%n", resp.statusCode(), resp.body());
                System.exit(1);
            }
        }

        // ----- Page resolution helpers --------------------------------------

        JsonNode getPageByTitle(String title) throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("spaceKey", spaceKey);
            params.put("title", title);
            params.put("expand", "body.storage,version,ancestors,space");
            params.put("limit", "1");
            JsonNode data = httpGet("/content", params);
            JsonNode results = data.path("results");
            return results.isEmpty() ? null : results.get(0);
        }

        JsonNode getPageById(String pageId) throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expand", "body.storage,version,ancestors,space");
            return httpGet("/content/" + pageId, params);
        }

        JsonNode resolvePage(String title, String pageId) throws IOException, InterruptedException {
            JsonNode page;
            if (pageId != null && !pageId.trim().isEmpty()) {
                page = getPageById(pageId);
            } else if (title != null && !title.trim().isEmpty()) {
                page = getPageByTitle(title);
            } else {
                System.err.println("ERROR: Provide --title or --id");
                System.exit(1);
                return null;
            }
            if (page == null || page.isMissingNode()) {
                System.err.println("ERROR: Page not found -- '" + (title != null ? title : pageId) + "'");
                System.exit(1);
            }
            return page;
        }

        // ----- Public operations --------------------------------------------

        JsonNode listPages() throws IOException, InterruptedException {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("spaceKey", spaceKey);
            params.put("type", "page");
            params.put("limit", String.valueOf(maxResults));
            params.put("expand", "version,ancestors");
            return httpGet("/content", params);
        }

        JsonNode readPage(String title, String pageId) throws IOException, InterruptedException {
            return resolvePage(title, pageId);
        }

        JsonNode searchPages(String query) throws IOException, InterruptedException {
            String cql = "space=\"" + spaceKey + "\" AND type=page AND text~\"" + query + "\"";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("cql", cql);
            params.put("limit", String.valueOf(maxResults));
            params.put("expand", "version");
            return httpGet("/content/search", params);
        }

        JsonNode createPage(String title, String body, String parentTitle)
                throws IOException, InterruptedException {
            String storageBody = MarkdownConverter.toStorage(body);
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("type", "page");
            payload.put("title", title);
            payload.putObject("space").put("key", spaceKey);
            payload.putObject("body")
                    .putObject("storage")
                    .put("value", storageBody)
                    .put("representation", "storage");

            if (parentTitle != null && !parentTitle.trim().isEmpty()) {
                JsonNode parent = getPageByTitle(parentTitle);
                if (parent == null) {
                    System.err.println("ERROR: Parent page not found -- '" + parentTitle + "'");
                    System.exit(1);
                }
                ArrayNode ancestors = payload.putArray("ancestors");
                ancestors.addObject().put("id", parent.path("id").asText());
            }
            return httpPost("/content", payload);
        }

        JsonNode updatePage(String title, String pageId, String newBody)
                throws IOException, InterruptedException {
            JsonNode page = resolvePage(title, pageId);
            String pid = page.path("id").asText();
            int currentVersion = page.path("version").path("number").asInt();
            String storageBody = MarkdownConverter.toStorage(newBody);

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("id", pid);
            payload.put("type", "page");
            payload.put("title", page.path("title").asText());
            payload.putObject("version").put("number", currentVersion + 1);
            payload.putObject("body")
                    .putObject("storage")
                    .put("value", storageBody)
                    .put("representation", "storage");
            return httpPut("/content/" + pid, payload);
        }

        boolean deletePage(String title, String pageId, boolean force) throws IOException, InterruptedException {
            JsonNode page = resolvePage(title, pageId);
            String pid = page.path("id").asText();
            String pageTitle = page.path("title").asText();

            System.out.println("\n  [!] You are about to DELETE page: '" + pageTitle + "' (ID: " + pid + ")");
            if (!force) {
                System.out.print("  Type 'yes' to confirm deletion: ");
                Scanner scanner = new Scanner(System.in);
                String answer = scanner.nextLine().trim();
                if (!answer.equalsIgnoreCase("yes")) {
                    System.out.println("Deletion cancelled.");
                    return false;
                }
            } else {
                System.out.println("  Force delete enabled. Proceeding...");
            }
            httpDelete("/content/" + pid);
            return true;
        }

        String getBaseUrl() { return baseUrl; }
    }

    // =========================================================================
    // Markdown to Confluence Storage Format converter
    // =========================================================================

    static class MarkdownConverter {

        /** Convert a subset of Markdown to Confluence XHTML storage format. */
        static String toStorage(String md) {
            // honour literal \n from CLI args
            String[] lines = md.replace("\\n", "\n").split("\n", -1);
            StringBuilder sb = new StringBuilder();
            boolean inCodeBlock = false;
            boolean inList = false;

            for (String line : lines) {
                if (line.trim().startsWith("```")) {
                    if (!inCodeBlock) {
                        String lang = line.trim().replaceFirst("^`+", "").trim();
                        if (lang.isEmpty()) lang = "none";
                        sb.append("<ac:structured-macro ac:name=\"code\">")
                          .append("<ac:parameter ac:name=\"language\">").append(lang).append("</ac:parameter>")
                          .append("<ac:plain-text-body><![CDATA[\n");
                        inCodeBlock = true;
                    } else {
                        sb.append("]]></ac:plain-text-body></ac:structured-macro>\n");
                        inCodeBlock = false;
                    }
                    continue;
                }

                if (inCodeBlock) { sb.append(line).append("\n"); continue; }

                if (inList && !line.startsWith("- ") && !line.startsWith("* ")) {
                    sb.append("</ul>\n");
                    inList = false;
                }

                if (line.startsWith("### ")) {
                    sb.append("<h3>").append(inline(line.substring(4))).append("</h3>\n");
                } else if (line.startsWith("## ")) {
                    sb.append("<h2>").append(inline(line.substring(3))).append("</h2>\n");
                } else if (line.startsWith("# ")) {
                    sb.append("<h1>").append(inline(line.substring(2))).append("</h1>\n");
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    if (!inList) { sb.append("<ul>\n"); inList = true; }
                    sb.append("<li>").append(inline(line.substring(2))).append("</li>\n");
                } else if (line.trim().isEmpty()) {
                    sb.append("<p></p>\n");
                } else {
                    sb.append("<p>").append(inline(line)).append("</p>\n");
                }
            }
            if (inList) sb.append("</ul>\n");
            return sb.toString();
        }

        static String inline(String text) {
            text = text.replaceAll("`([^`]+)`",                  "<code>$1</code>");
            text = text.replaceAll("\\*\\*(.+?)\\*\\*",          "<strong>$1</strong>");
            text = text.replaceAll("__(.+?)__",                   "<strong>$1</strong>");
            text = text.replaceAll("\\*(.+?)\\*",                 "<em>$1</em>");
            text = text.replaceAll("_(.+?)_",                     "<em>$1</em>");
            text = text.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
            return text;
        }
    }

    // =========================================================================
    // Output formatters
    // =========================================================================

    static String storageToPlain(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\n{3,}", "\n\n").trim();
    }

    static void printPage(JsonNode page, boolean raw) throws Exception {
        if (raw) {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(page));
            return;
        }
        String sep = "=".repeat(60);
        String bodyPlain = storageToPlain(page.path("body").path("storage").path("value").asText(""));
        System.out.println("\n" + sep);
        System.out.printf("  Title   : %s%n", page.path("title").asText("Unknown"));
        System.out.printf("  ID      : %s%n", page.path("id").asText("?"));
        System.out.printf("  Space   : %s%n", page.path("space").path("key").asText("?"));
        System.out.printf("  Version : %s%n", page.path("version").path("number").asText("?"));
        System.out.println(sep);
        for (String l : bodyPlain.split("\n")) System.out.println("  " + l);
        System.out.println(sep + "\n");
    }

    static void printList(JsonNode data, boolean raw) throws Exception {
        JsonNode results = data.path("results");
        if (raw) {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
            return;
        }
        if (results.isEmpty()) { System.out.println("No pages found."); return; }
        System.out.printf("%n%-12s %-10s %s%n", "ID", "Version", "Title");
        System.out.println("-".repeat(60));
        for (JsonNode p : results) {
            System.out.printf("%-12s %-10s %s%n",
                    p.path("id").asText(),
                    p.path("version").path("number").asText(),
                    p.path("title").asText());
        }
        System.out.printf("%nTotal: %d page(s)%n%n", results.size());
    }

    static void printInfo(JsonNode page, boolean raw) throws Exception {
        if (raw) {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(page));
            return;
        }
        JsonNode version   = page.path("version");
        JsonNode ancestors = page.path("ancestors");
        String parent = ancestors.isEmpty()
                ? "(root)"
                : ancestors.get(ancestors.size() - 1).path("title").asText();
        String sep = "=".repeat(60);
        System.out.println("\n" + sep);
        System.out.printf("  Title       : %s%n", page.path("title").asText());
        System.out.printf("  ID          : %s%n", page.path("id").asText());
        System.out.printf("  Space Key   : %s%n", page.path("space").path("key").asText("?"));
        System.out.printf("  Version     : %s%n", version.path("number").asText("?"));
        System.out.printf("  Last Updated: %s%n", version.path("when").asText("?"));
        System.out.printf("  Author      : %s%n", version.path("by").path("displayName").asText("?"));
        System.out.printf("  Parent      : %s%n", parent);
        System.out.println(sep + "\n");
    }

    // =========================================================================
    // CLI argument parser
    // =========================================================================

    static class Args {
        String command;
        String title;
        String titleFile;
        String pageId;
        String query;
        String body;
        String bodyFile;
        String parent;
        boolean raw;
        boolean force;
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
            if ("--title".equals(argv[i]))        { args.title  = argv[++i]; }
            else if ("--title-file".equals(argv[i])) { args.titleFile = argv[++i]; }
            else if ("--id".equals(argv[i]))      { args.pageId = argv[++i]; }
            else if ("--query".equals(argv[i]))   { args.query  = argv[++i]; }
            else if ("--body".equals(argv[i]))    { args.body   = argv[++i]; }
            else if ("--body-file".equals(argv[i])) { args.bodyFile = argv[++i]; }
            else if ("--parent".equals(argv[i]))  { args.parent = argv[++i]; }
            else if ("--raw".equals(argv[i]))     { args.raw    = true;      }
            else if ("--force".equals(argv[i]))   { args.force  = true;      }
            else {
                System.err.println("Unknown argument: " + argv[i]);
                System.exit(1);
            }
        }
        return args;
    }

    static void printUsageAndExit() {
        System.out.println("ConfluenceReader -- Copilot agent CLI for Confluence operations\n");
        System.out.println("Usage (via Gradle):");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"list\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"read   --title <title>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"read   --title-file <path>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"read   --id <id>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"info   --title <title>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"info   --title-file <path>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"search --query <keyword>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"create --title <t> --body <b> [--parent <p>]\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"create --title-file <path> --body-file <path> [--parent <p>]\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"create --title <t> --body-file <path> [--parent <p>]\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"update --title <t> --body <b>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"update --title-file <path> --body-file <path>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"update --title <t> --body-file <path>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"delete --title <title>\"");
        System.out.println("  ./gradlew runConfluenceAgent --args=\"delete --title-file <path> [--force]\"");
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
        ConfluenceClient client = new ConfluenceClient(cfg);
        Args args = parseArgs(argv);
        args.title = resolveTitleInput(args.title, args.titleFile);

        if ("list".equals(args.command)) {
            JsonNode data = client.listPages();
            printList(data, args.raw);

        } else if ("read".equals(args.command)) {
            JsonNode page = client.readPage(args.title, args.pageId);
            printPage(page, args.raw);

        } else if ("info".equals(args.command)) {
            JsonNode page = client.resolvePage(args.title, args.pageId);
            printInfo(page, args.raw);

        } else if ("search".equals(args.command)) {
            if (args.query == null) { System.err.println("ERROR: --query is required"); System.exit(1); }
            JsonNode data = client.searchPages(args.query);
            printList(data, args.raw);

        } else if ("create".equals(args.command)) {
            if (args.title == null) { System.err.println("ERROR: --title is required"); System.exit(1); }
            String body = resolveBodyInput(args.body, args.bodyFile);
            if (body == null) {
                System.err.println("ERROR: --body or --body-file is required");
                System.exit(1);
            }
            JsonNode page = client.createPage(args.title, body, args.parent);
            System.out.println("\nPage created successfully!");
            System.out.println("   Title : " + page.path("title").asText());
            System.out.println("   ID    : " + page.path("id").asText());
            System.out.println("   URL   : " + client.getBaseUrl()
                    + "/wiki" + page.path("_links").path("webui").asText() + "\n");

        } else if ("update".equals(args.command)) {
            String body = resolveBodyInput(args.body, args.bodyFile);
            if (body == null) {
                System.err.println("ERROR: --body or --body-file is required");
                System.exit(1);
            }
            JsonNode page = client.updatePage(args.title, args.pageId, body);
            System.out.println("\nPage updated successfully!");
            System.out.println("   Title   : " + page.path("title").asText());
            System.out.println("   Version : " + page.path("version").path("number").asText() + "\n");

        } else if ("delete".equals(args.command)) {
            boolean deleted = client.deletePage(args.title, args.pageId, args.force);
            if (deleted) System.out.println("\nPage deleted successfully.\n");

        } else {
            System.err.println("Unknown command: " + args.command);
            printUsageAndExit();
        }
    }
}

