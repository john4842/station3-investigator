# Credits and references

## Team

Team name: New Team 7

Members: Kristopher Johnson, Soad Shajid, Dylan Channell, Alexis Patino

## Project

Station 3 Investigator was developed for the Physical AI for the Factory Floor
Hackathon, sponsored by Rockwell Automation and NVIDIA, using the MSOE event
materials and CSI Station 3 fluid-mixing problem.

The original investigation used Dataset 2, including Run 5 recipe labels,
supplied camera recordings, and derived appearance measurements. These
materials are excluded from the public source distribution. See
[SOURCE_ASSETS.md](SOURCE_ASSETS.md).

## Method

Color differences use CIEDE2000. Method reference:

G. Sharma, W. Wu, and E. N. Dalal, “The CIEDE2000 Color-Difference Formula:
Implementation Notes, Supplementary Test Data, and Mathematical Observations,”
Color Research & Application, 30(1), 21–30, 2005.
[Implementation notes and test data](https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/).

The project does not bundle the reference MATLAB or Excel implementations.

## Software

- Java 21 and Maven.
- Spring Boot 3.5.7, including Spring Web and Jackson.
- JUnit 5 and Spring Boot's testing dependencies.
- HTML, CSS, and JavaScript; Node.js for browser-script regression checks.

Dependency versions are resolved by `pom.xml`. Third-party components retain
their own licenses.

## Development assistance

OpenAI Codex assisted with implementation, tests, debugging, and documentation.
The application uses deterministic analysis rules, not a trained model.
