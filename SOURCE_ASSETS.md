# Demonstration evidence

The repository includes the Run 5 replay bundle used in the recorded
Station 3 Investigator demonstration. It is not replaced by synthetic data.

## Included files

Maven copies `evidence/run5` into the application resources without filtering.

| Path under `evidence/run5/` | Purpose |
| --- | --- |
| `source/video5_vial_recipes.csv` | Recipe and vial records |
| `source/video5_source_contract.json` | Source-frame identity and time contract |
| `source/run5_process_context.json` | Synchronized camera and timing context |
| `canonical/observations.csv` | Extracted appearance observations |
| `canonical/observation_quality_falsifier.py` | Extraction source for audit |
| `canonical/observation_quality_falsifier_config.json` | Extraction configuration |
| `static/recipe7-evidence.svg` | Vial evidence shown in the demonstration |
| `static/run5-tank-context.svg` | Synchronized tank-view evidence |

`evidence/run5/SHA256SUMS` records the exact bundle bytes. To verify:

```sh
(cd evidence/run5 && shasum -a 256 -c SHA256SUMS)
```

The replay verifies its expected manifest and observation identities and the
recorded configuration, extractor, and source identities. Expected hashes are
defined in `CanonicalReplayService.java`; the frame contract has additional
checks in `SourceContractEvidence.java`. Verification is not evidence of
physical validation or causal carryover.

## Build and reproduce

```sh
mvn clean verify
java -jar target/station3-investigator-0.2.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080
```

The included evidence automatically activates the `canonical-data` test profile.
The browser opens the real 30-vial, 10-recipe replay by default. The synthetic
example is an optional, explicitly labeled alternative.

For the packaged replay check:

```sh
mkdir -p build/replay-check
node tools/verify-release.cjs http://127.0.0.1:8080 build/replay-check
```

This checks the replay API, evidence images, evaluation, and exports.

## Scope and rights

The bundle reproduces the demonstrated retrospective results. It does not
include the complete original camera recordings, perform fresh video extraction
on launch, provide independent defect labels, or establish physical-test results.
The five-run inventory is not a claim that every original video is included.

Organizer materials are separate from the team's MIT-licensed code. See
[LICENSE_SCOPE.md](LICENSE_SCOPE.md) and [CREDITS_AND_REFERENCES.md](CREDITS_AND_REFERENCES.md).
