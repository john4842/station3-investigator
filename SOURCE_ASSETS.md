# Source assets

## Public source

This distribution contains code and synthetic fixtures, not the organizer
dataset. No permission to redistribute the organizer assets publicly has been
confirmed. Keep them out of public repositories, releases, screenshots, and
bundled JARs until permission is established.

The synthetic CLI example in [README.md](README.md) needs no external data.

## Full Run 5 replay

The original replay uses organizer recipe records and project-derived
observations. An organizer download alone is not the complete replay bundle.
An authorized copy must include these exact project-relative files:

| Path | Purpose |
| --- | --- |
| `src/main/resources/source/video5_vial_recipes.csv` | Recipe and vial records |
| `src/main/resources/source/video5_source_contract.json` | Source-frame contract |
| `src/main/resources/source/run5_process_context.json` | Recorded process context |
| `src/main/resources/canonical/observations.csv` | Extracted appearance measurements |
| `src/main/resources/canonical/observation_quality_falsifier.py` | Extraction source for audit |
| `src/main/resources/canonical/observation_quality_falsifier_config.json` | Extraction configuration |
| `src/main/resources/static/recipe7-evidence.svg` | Vial evidence image |
| `src/main/resources/static/run5-tank-context.svg` | Process-context image |
| `fixtures/source/video5_vial_recipes.csv` | Recipe fixture for data-dependent tests |

Obtain the organizer materials through the event's authorized channel and the
matching derived replay bundle from its maintainers, subject to permission.
The project does not provide a public download for these files.

The replay checks the expected manifest, observation, configuration, extractor,
and source identities. Expected hashes are defined in
`CanonicalReplayService.java`; the frame contract has additional checks in
`SourceContractEvidence.java`. Do not replace missing files with invented
measurements or disable these checks to make a replay pass.

After restoring the authorized bundle locally, rebuild and run all tests:

```sh
mvn -Pcanonical-data clean verify
```

This profile includes the data-dependent tests and fails when required assets
are missing. It also activates automatically when
`src/main/resources/canonical/observations.csv` exists.

For a running full-data instance, the optional replay smoke test is:

```sh
mkdir -p build/replay-check
node tools/verify-release.cjs http://127.0.0.1:8080 build/replay-check
```

It checks the replay API, evidence images, evaluation, and exports. It is not a
test of the data-free distribution. Its output contains dataset-derived results
and must remain local unless redistribution is authorized.

## Publication boundary

The repository ignore rules exclude the asset paths, build output, reports,
archives, and common local configuration files. Ignore rules do not remove
files already tracked in Git. Review staged paths before publishing. Do not
publish a JAR built after restoring the private assets.
