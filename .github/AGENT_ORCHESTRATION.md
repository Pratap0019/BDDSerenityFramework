# AGENT_ORCHESTRATION.md

## Confluence -> Jira -> BDD Feature Flow

`FlowOrchestratorAgent` automates the full chain:
1. Read requirement content from a Confluence page (by title or page id)
2. Create a Jira story from those requirements
3. Generate a BDD feature file under `src/test/resources/features/`

## Run Commands

```powershell
.\gradlew.bat runFlowOrchestrator --args="sync --confluence-title \"My Requirement Page\""
.\gradlew.bat runFlowOrchestrator --args="sync --id 123456 --tag automation"
.\gradlew.bat runFlowOrchestrator --args="sync --confluence-title \"My Requirement Page\" --jira-title \"Custom Story Title\" --jira-type Story"
```

## Arguments

- `sync` : required command
- `--confluence-title <title>` : Confluence page title
- `--confluence-title-file <path>` : text file containing Confluence page title
- `--id <pageId>` : Confluence page id (alternative to title)
- `--jira-title <summary>` : optional custom Jira summary (defaults to Confluence title)
- `--jira-type <type>` : Jira issue type (default: `Story`)
- `--tag <tag>` : generated feature tag (default: `automation`)
- `--raw` : print raw Confluence/Jira JSON payloads

## Output

- Jira story is created in your configured project (`config.json` -> `jira.project_key`)
- Feature file is generated via existing `JiraAgent.generateFeatureFile(...)` logic
- File path pattern: `src/test/resources/features/<ISSUE_KEY_WITH_UNDERSCORE>.feature`

## Notes

- This flow reuses existing APIs from `ConfluenceAgent` and `JiraAgent`
- Ensure `config.json` has valid Jira and Confluence credentials before running

