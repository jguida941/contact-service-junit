# CS320 Milestone 1 – Contact Service
[![Java CI](https://github.com/jguida941/cs320-contact-service-junit/actions/workflows/java-ci.yml/badge.svg)](https://github.com/jguida941/cs320-contact-service-junit/actions/workflows/java-ci.yml)

Small Java project for the CS320 Contact Service milestone. The work breaks down into two pieces:
1. Build the `Contact` and `ContactService` classes exactly as described in the requirements.
2. Prove every rule with unit tests (length limits, null checks, unique IDs, and add/update/delete behavior) using the shared `Validation` helper so exceptions surface clear messages.

Everything is packaged under `contactapp`; production classes live in `src/main/java` and the JUnit tests in `src/test/java`.

## Getting Started
1. Install Java 17 and Apache Maven (3.9+).
2. Run `mvn verify` from the project root to compile everything, execute the JUnit suite, and run Checkstyle/SpotBugs/JaCoCo quality gates.
3. Open the folder in IntelliJ/VS Code if you want IDE assistance—the Maven project model is auto-detected.

## Folder Highlights
| Path                       | Description                                                |
|----------------------------|------------------------------------------------------------|
| `src/main/java/contactapp` | `Contact`, `ContactService`, and an optional `Main` class. |
| `src/test/java/contactapp` | Unit tests for the milestone.                              |
| `requirements/`            | Assignment write-up and checklist from the instructor.     |
| `docs/index.md`            | Quick reference guide for the repo layout.                 |
| `pom.xml`                  | Maven build file (dependencies, plugins, compiler config). |
| `config/checkstyle/...`    | Checkstyle configuration used by Maven/CI quality gates.   |

## Notes
- Keep the `package contactapp;` declaration at the top of every Java file so the folder layout and compiler stay in sync.
- `Validation.java` centralizes the not-blank, length, and 10-digit checks; the unit tests assert on those messages via AssertJ.
- Every requirement from `requirements/requirements.md` has at least one test case to demonstrate full coverage.
- GitHub Actions (`.github/workflows/java-ci.yml`) now runs `mvn -B verify` (tests + Checkstyle + JaCoCo, SpotBugs to be reintroduced later) on every push/PR to `main`/`master` across Java 17 and 21, with caching for faster builds and artifacts for all reports; published releases automatically build a JAR and upload it as an artifact. Dependabot and CodeQL workflows keep dependencies up to date and scan for security issues.
- `docs/index.md` shows all the key files and folders for easy navigation.
