# Jira-Helper.ps1
# Reusable PowerShell functions for common Jira operations
# Usage: . .\Scripts\Jira-Helper.ps1
$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot | Split-Path -Parent
function Get-JiraConfig {
    $configPath = Join-Path $ProjectRoot "config.json"
    if (-not (Test-Path $configPath)) {
        throw "config.json not found at $configPath"
    }
    $config = Get-Content $configPath | ConvertFrom-Json
    return $config.jira
}
function Get-JiraAuthHeader {
    $config = Get-JiraConfig
    $credentials = "$($config.email):$($config.api_token)"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($credentials))
    return @{
        Authorization = "Basic $encoded"
        "Content-Type" = "application/json"
    }
}
function Get-JiraApiUrl {
    $config = Get-JiraConfig
    $baseUrl = $config.base_url -replace '/$', '' -replace '/jira$', ''
    return "$baseUrl/rest/api/3"
}
function Get-JiraIssues {
    Push-Location $ProjectRoot
    try {
        .\gradlew.bat runJiraAgent --args="list"
    } finally {
        Pop-Location
    }
}
function Get-JiraIssue {
    param(
        [Parameter(Mandatory=$true)]
        [string]$IssueKey,
        [switch]$Raw
    )
    Push-Location $ProjectRoot
    try {
        $args = "get --id $IssueKey"
        if ($Raw) { $args += " --raw" }
        .\gradlew.bat runJiraAgent --args="$args"
    } finally {
        Pop-Location
    }
}
function Get-JiraTransitions {
    param(
        [Parameter(Mandatory=$true)]
        [string]$IssueKey
    )
    $apiUrl = Get-JiraApiUrl
    $headers = Get-JiraAuthHeader
    $uri = "$apiUrl/issue/$IssueKey/transitions"
    Write-Host ""
    Write-Host "Available transitions for $IssueKey :" -ForegroundColor Cyan
    (Invoke-RestMethod -Uri $uri -Headers $headers -Method Get).transitions | 
        Select-Object id, name | 
        Format-Table -AutoSize
}
function Set-JiraTransition {
    param(
        [Parameter(Mandatory=$true)]
        [string]$IssueKey,
        [Parameter(Mandatory=$true)]
        [string]$TransitionId
    )
    $apiUrl = Get-JiraApiUrl
    $headers = Get-JiraAuthHeader
    $uri = "$apiUrl/issue/$IssueKey/transitions"
    $body = @{ transition = @{ id = $TransitionId } } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri $uri -Headers $headers -Method Post -Body $body | Out-Null
        Write-Host ""
        Write-Host "[SUCCESS] $IssueKey transitioned successfully!" -ForegroundColor Green
        Write-Host ""
    }
    catch {
        Write-Host ""
        Write-Host "[ERROR] Failed to transition $IssueKey" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
        Write-Host ""
    }
}
function Move-JiraToInProgress {
    param([Parameter(Mandatory=$true)][string]$IssueKey)
    Set-JiraTransition -IssueKey $IssueKey -TransitionId "21"
}
function Move-JiraToDone {
    param([Parameter(Mandatory=$true)][string]$IssueKey)
    Set-JiraTransition -IssueKey $IssueKey -TransitionId "31"
}
function Move-JiraToTodo {
    param([Parameter(Mandatory=$true)][string]$IssueKey)
    Set-JiraTransition -IssueKey $IssueKey -TransitionId "11"
}
function New-FeatureFromJira {
    param(
        [Parameter(Mandatory=$true)]
        [string]$IssueKey,
        [string]$Tag = "automation"
    )
    Push-Location $ProjectRoot
    try {
        .\gradlew.bat runJiraAgent --args="generate-feature --id $IssueKey --tag $Tag"
    } finally {
        Pop-Location
    }
}
function Add-JiraComment {
    param(
        [Parameter(Mandatory=$true)]
        [string]$IssueKey,
        [Parameter(Mandatory=$true)]
        [string]$Comment
    )
    $tempFile = Join-Path $env:TEMP "jira_comment_$(Get-Date -Format 'yyyyMMddHHmmss').txt"
    $Comment | Out-File -FilePath $tempFile -Encoding UTF8
    Push-Location $ProjectRoot
    try {
        .\gradlew.bat runJiraAgent --args="comment --id $IssueKey --body-file `"$tempFile`""
    } finally {
        Remove-Item $tempFile -ErrorAction SilentlyContinue
        Pop-Location
    }
}
function Show-JiraHelp {
    Write-Host ""
    Write-Host "Jira Helper Functions" -ForegroundColor Yellow
    Write-Host "=====================" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "List Issues:" -ForegroundColor Cyan
    Write-Host "  Get-JiraIssues"  
    Write-Host ""
    Write-Host "Get Issue:" -ForegroundColor Cyan
    Write-Host "  Get-JiraIssue -IssueKey KAN-1"
    Write-Host "  Get-JiraIssue -IssueKey KAN-1 -Raw"
    Write-Host ""
    Write-Host "Transitions:" -ForegroundColor Cyan
    Write-Host "  Get-JiraTransitions -IssueKey KAN-1"
    Write-Host "  Set-JiraTransition -IssueKey KAN-1 -TransitionId 21"
    Write-Host ""
    Write-Host "Quick Transitions:" -ForegroundColor Cyan
    Write-Host "  Move-JiraToInProgress -IssueKey KAN-1"
    Write-Host "  Move-JiraToDone -IssueKey KAN-1"
    Write-Host "  Move-JiraToTodo -IssueKey KAN-1"
    Write-Host ""
    Write-Host "Generate Feature:" -ForegroundColor Cyan
    Write-Host "  New-FeatureFromJira -IssueKey KAN-1"
    Write-Host "  New-FeatureFromJira -IssueKey KAN-1 -Tag myfeature"
    Write-Host ""
    Write-Host "Add Comment:" -ForegroundColor Cyan
    Write-Host "  Add-JiraComment -IssueKey KAN-1 -Comment 'Work completed'"
    Write-Host ""
}
Write-Host ""
Write-Host "[OK] Jira Helper loaded. Type 'Show-JiraHelp' for available functions." -ForegroundColor Green
Write-Host ""
