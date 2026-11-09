# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture
Gym Meet is an application for managing mens and womens elite artistic gymnastics competition events, participants and results. 

### Backend
The backend service is in the `backend` directory.

The backend service is a Spring Boot 4 and Java 25 application. The data is stored in a Postgres database and versioned using Flyway.

### Frontend
The frontend user interface is in the `frontend` directory.

The frontend user interface does not yet exist, but will be a React, typescript and Vite application when it is implemented.

## Tests

- When adding a new capability, relevant tests should be added.
- When modifying capability, impacted tests should be updated accordingly.
- All test must run successfully.

### Backend Testing
Tests are found in `backend/src/test/java/`. Run them with `mvn -q test` from `backend/`.

## Competition results data

Results PDFs are parsed into import CSVs under `backend/data/<year>/<event>/`. Use the `parse-results` skill (`.claude/skills/parse-results/`) for any new results document. Parsers live in `backend/tools/results-parsing/` (one per meet, sharing `common.py`).

## Documentation

If changes to the code changes, then any documentation that refers to the changed code should be updated so that the documentation is accurate.

| document | description |
| --- | --- |
| backend/docs/data-model.md | The class structures that form and contain the data. |
| backend/docs/import-file-model.md | The columns and descriptions of the CSV files (SCORES, JUDGES, PARTICIPANTS, SUMMARY, EVENT) used to import competition data. |
| backend/docs/instructions-results-parsing.md | How results PDFs are turned into those CSV files: techniques, required checks, conventions, and notes per source format. |
| backend/docs/development-setup.md | Setting up the local development environment. |


