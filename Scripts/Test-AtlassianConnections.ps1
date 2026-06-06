[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$ConfigPath = "config.json",

    [Parameter(Mandatory = $false)]
    [int]$TimeoutSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRootPath = if ($PSScriptRoot) {
    $PSScriptRoot
} else {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}

function Resolve-AbsolutePath {
    param([string]$PathValue)

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }

    $scriptRelativePath = Join-Path -Path $ScriptRootPath -ChildPath $PathValue
    if (Test-Path -Path $scriptRelativePath) {
        return $scriptRelativePath
    }

    $projectRootPath = Split-Path -Parent $ScriptRootPath
    return Join-Path -Path $projectRootPath -ChildPath $PathValue
}

function Get-AtlassianRootUrl {
    param([string]$BaseUrl)

    if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
        throw "Base URL is empty."
    }

    $uri = [Uri]$BaseUrl
    return "{0}://{1}" -f $uri.Scheme, $uri.Host
}

function New-BasicAuthHeader {
    param(
        [string]$Email,
        [string]$ApiToken
    )

    if ([string]::IsNullOrWhiteSpace($Email) -or [string]::IsNullOrWhiteSpace($ApiToken)) {
        throw "Email or API token is missing in config."
    }

    $pair = "{0}:{1}" -f $Email, $ApiToken
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($pair)
    $encoded = [Convert]::ToBase64String($bytes)

    return @{ Authorization = "Basic $encoded" }
}

function Test-JiraConnection {
    param([pscustomobject]$JiraConfig)

    $jiraRoot = Get-AtlassianRootUrl -BaseUrl $JiraConfig.base_url
    $headers = New-BasicAuthHeader -Email $JiraConfig.email -ApiToken $JiraConfig.api_token
    $endpoint = "$jiraRoot/rest/api/3/myself"

    try {
        $response = Invoke-RestMethod -Method Get -Uri $endpoint -Headers $headers -TimeoutSec $TimeoutSec
        return [pscustomobject]@{
            System      = "Jira"
            Status      = "WORKING"
            HttpStatus  = 200
            Message     = "Connected as $($response.displayName)"
            Endpoint    = $endpoint
        }
    }
    catch {
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        $message = if ($statusCode -eq 401 -or $statusCode -eq 403) {
            "Credentials are wrong or do not have required permissions."
        } elseif ($statusCode) {
            "Request failed with HTTP status $statusCode."
        } else {
            "Request failed: $($_.Exception.Message)"
        }

        return [pscustomobject]@{
            System      = "Jira"
            Status      = "FAILED"
            HttpStatus  = $statusCode
            Message     = $message
            Endpoint    = $endpoint
        }
    }
}

function Test-ConfluenceConnection {
    param([pscustomobject]$ConfluenceConfig)

    $confluenceRoot = Get-AtlassianRootUrl -BaseUrl $ConfluenceConfig.base_url
    $headers = New-BasicAuthHeader -Email $ConfluenceConfig.email -ApiToken $ConfluenceConfig.api_token
    $endpoint = "$confluenceRoot/wiki/rest/api/space?limit=1"

    try {
        [void](Invoke-RestMethod -Method Get -Uri $endpoint -Headers $headers -TimeoutSec $TimeoutSec)
        return [pscustomobject]@{
            System      = "Confluence"
            Status      = "WORKING"
            HttpStatus  = 200
            Message     = "Connected successfully."
            Endpoint    = $endpoint
        }
    }
    catch {
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        $message = if ($statusCode -eq 401 -or $statusCode -eq 403) {
            "Credentials are wrong or do not have required permissions."
        } elseif ($statusCode) {
            "Request failed with HTTP status $statusCode."
        } else {
            "Request failed: $($_.Exception.Message)"
        }

        return [pscustomobject]@{
            System      = "Confluence"
            Status      = "FAILED"
            HttpStatus  = $statusCode
            Message     = $message
            Endpoint    = $endpoint
        }
    }
}

$resolvedConfigPath = Resolve-AbsolutePath -PathValue $ConfigPath
if (-not (Test-Path -Path $resolvedConfigPath)) {
    throw "Config file not found: $resolvedConfigPath"
}

$config = Get-Content -Path $resolvedConfigPath -Raw | ConvertFrom-Json

$jiraResult = Test-JiraConnection -JiraConfig $config.jira
$confluenceResult = Test-ConfluenceConnection -ConfluenceConfig $config.confluence
$results = @($jiraResult, $confluenceResult)

Write-Host ""
Write-Host "Atlassian Connection Status" -ForegroundColor Cyan
Write-Host "===========================" -ForegroundColor Cyan

foreach ($item in $results) {
    $color = if ($item.Status -eq "WORKING") { "Green" } else { "Red" }
    Write-Host ("{0}: {1}" -f $item.System, $item.Status) -ForegroundColor $color
    Write-Host ("  Message : {0}" -f $item.Message)
    if ($item.HttpStatus) {
        Write-Host ("  HTTP    : {0}" -f $item.HttpStatus)
    }
    Write-Host ("  Endpoint: {0}" -f $item.Endpoint)
}

$allPassed = @($results | Where-Object { $_.Status -ne "WORKING" }).Count -eq 0
Write-Host ""
if ($allPassed) {
    Write-Host "Overall: WORKING (Jira + Confluence credentials are valid)." -ForegroundColor Green
    exit 0
}

Write-Host "Overall: FAILED (one or more connections did not authenticate)." -ForegroundColor Red
exit 1

