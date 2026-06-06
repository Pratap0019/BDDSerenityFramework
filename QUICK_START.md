# Quick Start Guide — BDDSerenityFramework

## Prerequisites
1. **Java 11+** installed
2. **Chrome browser** installed
3. `config.json` configured with Jira/Confluence credentials

## Common Commands (PowerShell)

### Running Tests
```powershell
# Run all tests with current tag
.\gradlew.bat test

# Run specific tag
.\gradlew.bat test -Dcucumber.filter.tags="@automation"

# Generate HTML report
.\gradlew.bat aggregate
```

### Jira Operations
```powershell
# List all issues in project
.\gradlew.bat runJiraAgent --args="list"

# Get specific issue
.\gradlew.bat runJiraAgent --args="get --id KAN-1"

# Get raw JSON (includes all fields)
.\gradlew.bat runJiraAgent --args="get --id KAN-1 --raw"

# Create new task
.\gradlew.bat runJiraAgent --args="create --type Task --title ""My Task"" --body ""Description"""

# Update issue
.\gradlew.bat runJiraAgent --args="update --id KAN-1 --title ""Updated title"""

# Add comment
.\gradlew.bat runJiraAgent --args="comment --id KAN-1 --body ""My comment"""

# Transition to single-word status
.\gradlew.bat runJiraAgent --args="transition --id KAN-1 --status Done"

# Generate feature file from Jira
.\gradlew.bat runJiraAgent --args="generate-feature --id KAN-1 --tag automation"
```

### Transition to Multi-Word Status (Workaround)
```powershell
# Get transition IDs
$uri = "https://<your-domain>.atlassian.net/rest/api/3/issue/KAN-1/transitions"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<email>:<token>"))
$headers = @{ Authorization = "Basic $auth"; "Content-Type" = "application/json" }
(Invoke-RestMethod -Uri $uri -Headers $headers -Method Get).transitions | Select-Object id, name

# Apply transition (use ID from above)
$body = @{ transition = @{ id = "21" } } | ConvertTo-Json
Invoke-RestMethod -Uri $uri -Headers $headers -Method Post -Body $body
```

### Using Jira Helper Script (Recommended)
```powershell
# Load helper functions
. .\Scripts\Jira-Helper.ps1

# Get available transitions
Get-JiraTransitions -IssueKey KAN-1

# Quick transitions
Move-JiraToInProgress -IssueKey KAN-1
Move-JiraToDone -IssueKey KAN-1
Move-JiraToTodo -IssueKey KAN-1

# Generate feature file
New-FeatureFromJira -IssueKey KAN-1 -Tag automation
```

### Confluence Operations
```powershell
# List pages in space
.\gradlew.bat runConfluenceAgent --args="list"

# Read specific page
.\gradlew.bat runConfluenceAgent --args="read --title ""Page Title"""

# Create page
.\gradlew.bat runConfluenceAgent --args="create --title-file title.txt --body-file content.md"
```

## Typical Workflow

### 1. Pick a Jira Task
```powershell
.\gradlew.bat runJiraAgent --args="list"
```

### 2. Move to In Progress
```powershell
# Direct API (recommended for multi-word status)
$uri = "https://epam-team-lst7jf27.atlassian.net/rest/api/3/issue/KAN-1/transitions"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("your-email:your-token"))
$headers = @{ Authorization = "Basic $auth"; "Content-Type" = "application/json" }
$body = @{ transition = @{ id = "21" } } | ConvertTo-Json
Invoke-RestMethod -Uri $uri -Headers $headers -Method Post -Body $body
```

### 3. Fetch Acceptance Criteria
```powershell
.\gradlew.bat runJiraAgent --args="get --id KAN-1 --raw" | Out-String
```

### 4. Generate Feature File
```powershell
.\gradlew.bat runJiraAgent --args="generate-feature --id KAN-1 --tag automation"
```

### 5. Implement Step Definitions
Create/edit file in `src/test/java/com/bhanu/steps/`

### 6. Run Tests
```powershell
.\gradlew.bat test -Dcucumber.filter.tags="@automation"
.\gradlew.bat aggregate
```

### 7. Mark as Done
```powershell
# Option A: Using helper script (if multi-word status)
. .\Scripts\Jira-Helper.ps1
Move-JiraToDone -IssueKey KAN-1

# Option B: Gradle agent (for single-word status only)
.\gradlew.bat runJiraAgent --args="transition --id KAN-1 --status Done"
```

## Reusable Helper Script
The `Scripts\Jira-Helper.ps1` script provides PowerShell functions that simplify common Jira operations:
- Handles multi-word status transitions automatically
- Shorter command syntax
- Type `Show-JiraHelp` after loading to see all available functions

**Load once per session:**
```powershell
. .\Scripts\Jira-Helper.ps1
```

## Configuration Setup

### config.json Template
```json
{
  "jira": {
    "base_url": "https://<your-domain>.atlassian.net",
    "project_key": "KAN",
    "email": "your-email@example.com",
    "api_token": "<your-api-token>"
  },
  "confluence": {
    "base_url": "https://<your-domain>.atlassian.net",
    "space_key": "YourSpace",
    "email": "your-email@example.com",
    "api_token": "<your-api-token>"
  },
  "agent": {
    "max_results": 50,
    "output_format": "markdown"
  }
}
```

**Get API Token:** https://id.atlassian.com/manage-profile/security/api-tokens

## Troubleshooting

### Error: "Task 'Progress' not found"
**Cause:** Gradle misinterprets spaces in `--status "In Progress"`  
**Fix:** Use direct API call (see "Transition to Multi-Word Status" above)

### Error: "config.json not found"
**Fix:** Ensure `config.json` exists in project root with valid credentials

### Error: "Authentication failed"
**Fix:** Verify API token is correct and has not expired

### Tests not running
**Fix:** Check tag in `TestRunner.java` matches your feature file tags

## Further Reading
- **AGENTS.md** — Framework architecture and conventions
- **JIRA_AGENT.md** — Complete Jira agent documentation
- **Serenity BDD Docs** — https://serenity-bdd.github.io/

