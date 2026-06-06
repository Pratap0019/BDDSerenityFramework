# JIRA_AGENT.md - Reusable Jira Agent Guide

## Purpose
The Jira agent is a reusable CLI-driven assistant for backlog and requirement operations in Jira, with optional conversion of Jira requirements into executable BDD feature files for this Serenity framework.

It is designed to support:
- creating new Jira work items (Task, Story, Epic)
- updating existing items (title, description, acceptance criteria, priority, labels, assignee, etc.)
- reading requirement details and generating `.feature` files under `src/test/resources/features/`

## Scope and Responsibilities

### 1) Create work items
- Create `Task`, `Story`, or `Epic` in the configured Jira project.
- Accept inline content (`--title`, `--body`) or file-based input (`--title-file`, `--body-file`).
- Return issue key, URL, and a short summary after creation.

### 2) Update work items
- Update fields of an existing issue via issue key (`--id`, e.g., `BT-123`).
- Typical update targets:
  - summary/title
  - description
  - acceptance criteria
  - priority
  - labels/components
  - assignee
- Support safe partial updates (only the provided fields are changed).

### 3) Requirement retrieval and BDD generation
- Fetch full requirement details from Jira (summary, description, AC, attachments/links metadata where available).
- Convert requirement into Gherkin scenarios.
- Save generated feature file(s) directly in `src/test/resources/features/`.
- Keep generated output aligned to this project conventions:
  - feature-level tags for execution control
  - business-readable scenarios and steps
  - API/UI intent separation when possible

## Configuration
The agent reads credentials/settings from `config.json` at project root.

Expected `jira` config shape:

```json
{
  "jira": {
    "base_url": "https://<your-domain>.atlassian.net",
    "project_key": "BT",
    "email": "<jira-email>",
    "api_token": "<jira-api-token>"
  },
  "agent": {
    "max_results": 50,
    "output_format": "markdown"
  }
}
```

## CLI Usage (Gradle Task)
Run Jira agent through Gradle:

```powershell
.\gradlew.bat runJiraAgent --args="<command> <options>"
```

### Common commands

```powershell
.\gradlew.bat runJiraAgent --args="list"
.\gradlew.bat runJiraAgent --args="get --id BT-1"
.\gradlew.bat runJiraAgent --args="search --jql project=BT ORDER BY created DESC"
.\gradlew.bat runJiraAgent --args="comment --id BT-1 --body-file .\notes\comment.txt"
.\gradlew.bat runJiraAgent --args="transition --id BT-1 --status InProgress"
```

## Workflows

### Workflow A: Create Task / Story / Epic

```powershell
.\gradlew.bat runJiraAgent --args="create --type Task --title \"Login API negative validation\" --body-file .\notes\task.txt"
.\gradlew.bat runJiraAgent --args="create --type Story --title-file .\notes\story-title.txt --body-file .\notes\story-body.txt"
.\gradlew.bat runJiraAgent --args="create --type Epic --title \"Checkout hardening\" --body \"Epic for checkout resiliency and edge cases\""
```

### Workflow B: Update existing issue fields

```powershell
.\gradlew.bat runJiraAgent --args="update --id BT-101 --title \"Refine login acceptance criteria\""
.\gradlew.bat runJiraAgent --args="update --id BT-101 --body-file .\notes\updated-description.txt"
.\gradlew.bat runJiraAgent --args="update --id BT-101 --acceptance-file .\notes\ac.txt"
```

If your current Jira agent implementation uses different option names for acceptance criteria or metadata fields, keep this document as behavioral intent and align actual flag names in implementation.

### Workflow C: Generate BDD feature from Jira requirement

```powershell
.\gradlew.bat runJiraAgent --args="generate-feature --id BT-101"
```

Expected output behavior:
- Reads Jira requirement details.
- Produces one or more scenarios in Gherkin.
- Writes output to `src/test/resources/features/`.
- Applies an execution tag (example: `@testingAPI` or a custom tag supplied by argument).

## Suggested generated feature template

```gherkin
@testingAPI
Feature: <Jira Summary>

  Background:
    Given preconditions for <context>

  Scenario: <Primary happy path>
    Given <initial state>
    When <action>
    Then <expected result>

  Scenario: <Negative or edge condition>
    Given <boundary condition>
    When <action>
    Then <error or fallback behavior>
```

## Definition of Done for this Agent
A Jira-agent run is complete when:
- command exits successfully
- output clearly states affected Jira key(s)
- changes are visible in Jira
- for `generate-feature`, a valid `.feature` file is created in the framework path

## Guardrails
- Never log tokens or secrets.
- Validate required arguments per command and fail fast with actionable messages.
- Preserve existing content when performing partial updates.
- For generated Gherkin, avoid implementation details in scenario text.

## Future Enhancements
- Link generated feature files back to Jira issue comments.
- Auto-generate step-definition stubs under `src/test/java/com/bhanu/steps/`.
- Bulk generation from JQL query results.
- Add dry-run mode for update operations.

