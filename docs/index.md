# CS320 Milestone 1 - Repository Index

Index for easy navigation of the CS320 Milestone 1 codebase.

## Folders

| Path                                                                       | Purpose                                                                                                                      |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| [`src/`](../src)                                                           | Java source tree. <br/>`src/main/java/contactapp` contains application code; <br/>`src/test/java/contactapp` contains tests. |
| [`CI-CD/`](CI-CD/)                                                         | CI/CD design notes and pipeline planning artifacts.                                                                          |
| [`requirements/contact-requirements/`](requirements/contact-requirements/) | Assignment requirements (now collocated under `docs/`).                                                                      |

## Key Files

| Path                                                                                                      | Description                                                                            |
|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| [`src/main/java/contactapp/Contact.java`](../src/main/java/contactapp/Contact.java)                       | `Contact` domain object with all field validation rules.                               |
| [`src/main/java/contactapp/ContactService.java`](../src/main/java/contactapp/ContactService.java)         | In-memory service that adds, updates, and deletes contacts.                            |
| [`src/main/java/contactapp/Validation.java`](../src/main/java/contactapp/Validation.java)                 | Shared helper with not-blank, length, and 10-digit checks.                             |
| [`src/main/java/contactapp/Main.java`](../src/main/java/contactapp/Main.java)                             | Optional `main` entry point for manual checks/demos.                                   |
| [`src/test/java/contactapp/ContactTest.java`](../src/test/java/contactapp/ContactTest.java)               | JUnit tests covering the `Contact` validation requirements.                            |
| [`src/test/java/contactapp/ContactServiceTest.java`](../src/test/java/contactapp/ContactServiceTest.java) | JUnit tests covering add, delete, and update behavior.                                 |
| [`pom.xml`](../pom.xml)                                                                                   | Maven project file defining dependencies and plugins.                                  |
| [`config/checkstyle/checkstyle.xml`](../config/checkstyle/checkstyle.xml)                                 | Custom Checkstyle rules enforced in CI.                                                |
| [`config/owasp-suppressions.xml`](../config/owasp-suppressions.xml)                                       | Placeholder suppression list for OWASP Dependency-Check.                               |
| [`scripts/ci_metrics_summary.py`](../scripts/ci_metrics_summary.py)                                       | Prints the QA metrics table (tests/coverage/mutations/dependencies) in GitHub Actions. |
| [`docs/backlog.md`](backlog.md)                                                                           | Backlog for reporting and domain enhancements.                                         |
| [`.github/workflows`](../.github/workflows)                                                               | GitHub Actions pipelines (CI, release packaging, CodeQL).                              |

## Requirements & Notes

| Path                                                                                                                         | Description                                  |
|------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| [`requirements/contact-requirements/requirements.md`](requirements/contact-requirements/requirements.md)                     | Full assignment requirements.                |
| [`requirements/contact-requirements/requirements_checklist.md`](requirements/contact-requirements/requirements_checklist.md) | Checklist view of requirements for tracking. |
| [`docs/index.md`](index.md)                                                                                                  | Documentation index and navigation entry.    |
