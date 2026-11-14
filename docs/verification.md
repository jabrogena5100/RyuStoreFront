# Verification log

Each claim is marked **run** (output seen), **user-reported** (the owner ran it and reported the result) or **not done**. Nothing here is a claim about performance or scale unless a row has numbers and a setup.

| Date | Phase | Claim | Status | Evidence |
| --- | --- | --- | --- | --- |
| 2026-11-14 | 0 | `./mvnw -B verify` passes locally (context loads against Postgres in Testcontainers, jar builds) | user-reported | Maven output: 1 test, 0 failures, BUILD SUCCESS, 51 s |
| 2026-11-14 | 0 | CI workflow `ci` is green on `main` | user-reported | `gh run list`: success on the Phase 0 push |
| 2026-11-14 | 0 | Ruleset `main-protection` active; secret scanning and push protection enabled | user-reported | `gh api` output: enforcement `active`, both `enabled` |
| 2026-11-14 | 0 | `scripts/aws-terraform.sh version` runs Terraform 1.17.2 in Docker using `aws login` credentials | user-reported | Output: `Terraform v1.17.2`; `get-caller-identity` returned the IAM user |
| | 1 | Migrations apply on a fresh database and in Testcontainers; constraint tests pass | not done | |
