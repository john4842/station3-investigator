# Station 3 Investigator

A Java 21 and Spring Boot application for reviewing color differences between
consecutive vials in a fluid-mixing process.

It validates recipe records and measurement quality, computes CIEDE2000 color
differences, and reports when the evidence supports review or is insufficient.
Color differences are observations, not proof of carryover or recipe correctness.

## What is included

- Java analysis, validation, CSV ingestion, and HTML/JSON reporting.
- A Spring Boot API and browser dashboard.
- Synthetic fixtures and automated tests.
- Source-identity checks for the original Run 5 replay.

Organizer data, videos, extracted images, and derived measurement files are
**not included**. Public redistribution permission has not been confirmed.
The synthetic example below works without them. The full browser replay,
evaluation, and source-upload analysis require the files listed in
[SOURCE_ASSETS.md](SOURCE_ASSETS.md).

## Build and test

Install JDK 21 and Maven 3.9 or later. Run from the repository root:

```sh
mvn verify
```

Without organizer assets, this runs 44 data-independent JUnit tests and builds
the application. Tests tagged `organizer-data` are excluded, not counted as
passing. Maven needs internet access on the first build to download dependencies.

For the JavaScript request-recovery checks, install Node.js 18 or later:

```sh
node tools/test-request-recovery.cjs
```

## Run the synthetic example

After building:

```sh
java -cp target/classes lab.mimi.station3.app.Main --manifest fixtures/synthetic_manifest.csv --measurements fixtures/synthetic_measurements.csv --out build/synthetic-report --label "Synthetic example" --synthetic
```

Open `build/synthetic-report/report.html`. The same results are written to
`report.json`. Both identify the input as synthetic. This example covers two
recipes; it does not reproduce the organizer dataset.

## Run the web application

```sh
java -jar target/station3-investigator-0.2.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080
```

Open `http://127.0.0.1:8080`. Without the excluded assets, the server and static
pages start, but the Run 5 analysis and evaluation are unavailable and the
evidence images are absent. A ZIP upload alone does not replace the required
recipe records. See [SOURCE_ASSETS.md](SOURCE_ASSETS.md) before using these
features.

This is a local review tool, not a public upload service. Authentication and
production deployment controls are outside its scope.

## Design

`ingest` reads CSV records; `validation` checks structure and recipe demand;
`domain` defines records and invariants; `analysis` applies evidence rules;
`web` and `report` expose the results. The analysis code is separate from
Spring's request handling.

The tests cover color-distance calculations, invalid inputs, record joins,
domain invariants, evidence boundaries, API behavior, and report handling.

## Limits

The project reviews recorded appearance. It does not measure dispensed volume,
control equipment, establish root cause, or verify a digital twin against
physical hardware. Thresholds are prototype settings, not calibrated production
limits. There is no labeled ground truth for a predictive-accuracy claim.

The CLI uses a single review cutoff of 10 delta E00. The web policy also reports
a sensitivity band from 7 inclusive to 10 exclusive. Both require a stable
later-vial reference before a first-vial comparison is used.

## License and credits

Original code and documentation: [MIT](LICENSE).
Organizer assets and third-party software are not relicensed:
[license scope](LICENSE_SCOPE.md).
See [credits and references](CREDITS_AND_REFERENCES.md).
