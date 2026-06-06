# AGENTS.md — BDDSerenityFramework

## Project Overview
A BDD test automation framework using **Serenity BDD 4.0.46** with **Cucumber JUnit** for both UI (Selenium/WebDriver) and API (REST Assured) testing. All tests are written as Gherkin feature files and executed through a single JUnit 4 runner.

---

## Architecture

```
src/test/
├── java/com/bhanu/
│   ├── executors/TestRunner.java     ← Single Cucumber runner; controls which tags run
│   ├── pages/                        ← Serenity PageObject classes (UI only)
│   │   ├── AmazonHomePage.java
│   │   └── GooglePage.java
│   └── steps/                        ← Cucumber glue code (step definitions)
│       ├── AmazonSearchSteps.java
│       ├── GoogleSearchSteps.java
│       └── PetSteps.java             ← API steps via SerenityRest
└── resources/
    ├── features/                     ← Gherkin feature files (one per feature area)
    └── serenity.conf                 ← Empty; config lives in serenity.properties
```

---

## Build & Run

```bash
# Run all tests (tag controlled in TestRunner.java)
./gradlew test

# Generate + open Serenity HTML report (auto-runs after test via finalizedBy)
./gradlew aggregate

# Run only specific tagged scenarios (override without editing TestRunner)
./gradlew test -Dcucumber.filter.tags="@testing"
```

> **Important:** `build.gradle` explicitly comments `// Do NOT use useJUnitPlatform()`. This project uses JUnit 4 (Cucumber JUnit), not JUnit 5. Never add `useJUnitPlatform()`.

Reports are written to `target/serenity/` as a single-page HTML.

---

## Key Conventions

### Tag-based test selection
The active tag is set in `TestRunner.java` (`tags = "@testingAPI"`). Feature files are tagged at the `Feature:` level (`@testing`, `@testingAPI`). Change the tag in `TestRunner` to switch which suite runs.

### PageObject pattern (UI tests)
- Page classes extend `net.serenitybdd.core.pages.PageObject`
- Use `@FindBy` (Serenity's `net.serenitybdd.core.annotations.findby.FindBy` or standard `org.openqa.selenium.support.FindBy`) for element location
- Page objects are declared as **plain fields** in step-definition classes — Serenity injects them automatically (no `new`, no `@Managed`)
- Example: `AmazonHomePage amazon;` in `AmazonSearchSteps.java`

### API tests
- Use `SerenityRest` (wrapper around REST Assured) for all HTTP calls — never raw `RestAssured`
- Store the `Response` as a class-level field; assertions call `.then()` on it in `@Then` steps
- API base URLs are hard-coded in step classes (e.g., `https://petstore.swagger.io/v2/`)

### Configuration
- `serenity.properties` (project root) is the single source of truth for driver, timeouts, base URL, and report settings
- `src/test/resources/serenity.conf` exists but is intentionally empty

---

## External Dependencies / Integration Points
| Target | Type | Location |
|---|---|---|
| [petstore.swagger.io/v2](https://petstore.swagger.io/) | REST API (public) | `PetSteps.java` |
| amazon.com | UI (Chrome) | `AmazonHomePage.java` |
| google.com | UI (Chrome) | `GooglePage.java`, `serenity.properties` |

Chrome must be installed and `chromedriver` must be on `PATH` (or managed by WebDriverManager if added as a dependency in future).

---

## Adding New Tests
1. Create a `.feature` file under `src/test/resources/features/` with an appropriate tag (e.g., `@myFeature`)
2. Add a step-definition class under `com.bhanu.steps` — Serenity discovers all classes in that package via `glue`
3. For UI tests, add a `PageObject` subclass under `com.bhanu.pages`
4. Update `tags` in `TestRunner.java` (or pass via `-Dcucumber.filter.tags`) to include the new tag

