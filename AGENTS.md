# AGENTS.md

Maven 4 core extension that adds `oci://` and `oci+http://` repositories, so Maven can deploy
artifacts to an OCI registry and resolve them back. Registry calls go through `oras-java-sdk`.

## Toolchain

- Maven 4 only (`4.0.0-rc-7`, see `.sdkmanrc`). POMs use model 4.1.0 and `<subprojects>`, so Maven 3
  can't build this, and the extension can't run in a Maven 3 process.
- Build with JDK 25; the code targets Java 17 (`maven.compiler.release`). Don't use APIs newer than 17.
- The version comes from `.mvn/maven.config` (`-Drevision=...`). Don't hardcode it in a POM.

## Commands

```shell
mvn verify                                   # full build: Spotless check, unit, component and IT
mvn spotless:apply                           # fix formatting before committing
mvn verify -pl oci-extension-core -am        # one module plus what it depends on
mvn verify -pl oci-extension-it -am -Dit.test=SingleModuleRoundTripIT
```

`spotless:check` runs in every build and fails it on bad formatting (Palantir Java Format, sorted
POMs). Run `spotless:apply` rather than formatting by hand. pre-commit only fixes trailing
whitespace and end-of-file newlines.

## Modules

- `oci-extension-common`: Maven-to-OCI mapping shared by everything else. Repository paths
  (`OciRepositoryPaths`), version-to-tag encoding (`OciVersionCodec`), media types, artifact types
  and annotations. No Maven dependencies.
- `oci-extension-core`: the extension users install. `OciRepositoryConnectorFactory` is a Sisu
  component (index built by `sisu-maven-plugin`) that claims the `oci` and `oci+http` protocols;
  `OciRepositoryConnector` does the pushes and pulls. Maven and resolver dependencies stay
  `provided`.
- `oci-extension-it`: Failsafe tests that run real Maven 4 builds on the sample projects in
  `src/test/resources/it/` against a Zot registry. Never deployed.

## Tests

- Component tests (`*ComponentTest`) and ITs use Testcontainers (`ZotUnsecureContainer` from the
  oras test-jar). They are skipped when Docker is unavailable, so a green build without Docker
  doesn't prove much.
- ITs need a Maven 4 binary: `OCI_IT_MAVEN_HOME`, else `~/.sdkman/candidates/maven/4.*`, else `mvn`
  on `PATH` if it reports 4.x. They never fall back to Maven 3.
- Logback config files are named `oci-extension-*-logback.xml`, not `logback-test.xml`, because the
  oras test-jar already ships one. Keep that naming in new modules.

## Storage format

Changing anything below breaks artifacts that are already published, so treat it as a breaking
change and label the PR `breaking`:

- One OCI repository per `groupId:artifactId`, one tag per version (encoded by `OciVersionCodec`).
- POM and main artifact are layers of the tag's manifest (`application/vnd.maven.artifact.v1`).
- Classified artifacts are referrer manifests (`application/vnd.maven.artifact.attachment.v1`).
- `maven-metadata.xml` is generated from the tag list, not stored. SNAPSHOT metadata is unsupported.

## Releases

Pushing a `X.Y.Z` tag runs `.github/workflows/release.yml`, which deploys to Maven Central through
njord (`-Prelease`, not `central-publishing-maven-plugin`, which doesn't work with Maven 4) and
publishes the Release Drafter draft. PR labels drive the release notes.
