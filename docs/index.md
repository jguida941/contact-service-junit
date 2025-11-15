# CS320 Milestone 1 – Repository Index

Index for easy navigation of the CS320 Milestone 1 codebase.

## Folders

| Path            | Purpose                                                                                                            |
|-----------------|--------------------------------------------------------------------------------------------------------------------|
| `src/`          | Java source tree. `src/main/java/contactapp` contains application code; `src/test/java/contactapp` contains tests. |
| `requirements/` | Assignment requirements in full text and checklist form.                                                           |
| `docs/`         | Project documentation (overview, notes, and navigation index).                                                     |

## Key Files

| Path                                               | Description                                                    |
|----------------------------------------------------|----------------------------------------------------------------|
| `src/main/java/contactapp/Contact.java`            | `Contact` domain object with all field validation rules.       |
| `src/main/java/contactapp/ContactService.java`     | In-memory service that adds, updates, and deletes contacts.    |
| `src/main/java/contactapp/Validation.java`         | Shared helper with not-blank, length, and 10-digit checks.     |
| `src/main/java/contactapp/Main.java`               | Optional `main` entry point for quick manual checks or demos.  |
| `src/test/java/contactapp/ContactTest.java`        | JUnit tests covering the `Contact` validation requirements.    |
| `src/test/java/contactapp/ContactServiceTest.java` | JUnit tests covering add, delete, and update behavior.         |
| `pom.xml`                                          | Maven project file defining dependencies and plugins.          |
| `config/checkstyle/checkstyle.xml`                 | Custom Checkstyle rules enforced in CI.                        |
| `.github/workflows/*.yml`                          | GitHub Actions pipelines (CI, release packaging, CodeQL).      |

## Requirements & Notes

| Path                                     | Description                                  |
|------------------------------------------|----------------------------------------------|
| `requirements/requirements.md`           | Full assignment requirements                 |
| `requirements/requirements_checklist.md` | Checklist view of requirements for tracking. |
| `docs/index.md`                          | Documentation index and navigation entry.    |
