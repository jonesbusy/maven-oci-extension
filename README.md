# Maven OCI extension

[![Build](https://github.com/jonesbusy/maven-oci-extension/actions/workflows/build.yml/badge.svg)](https://github.com/jonesbusy/maven-oci-extension/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/jonesbusy/maven-oci-extension)](LICENSE)

Use an OCI registry (Docker Hub, GHCR, Harbor, Zot, ...) as a Maven repository. This Maven 4 core
extension adds `oci://` repository URLs, so `mvn deploy` pushes artifacts to the registry and
dependency resolution pulls them back. Registry access goes through
[oras-java](https://github.com/oras-project/oras-java).

> [!WARNING]
> The project is in **alpha**. Configuration and behavior may change between releases. It also
> depends on `oras-java-sdk`, which is itself alpha.

## Requirements

- Maven 4. The extension is built against the Maven 4 resolver API and will not load in Maven 3.x.
- Java 17 or later at runtime.

## Getting started

Register the extension in your project's `.mvn/extensions.xml`:

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.2.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.2.0 http://maven.apache.org/xsd/core-extensions-1.2.0.xsd">
    <extension>
        <groupId>io.github.jonesbusy</groupId>
        <artifactId>oci-extension-core</artifactId>
        <version>VERSION</version>
    </extension>
</extensions>
```

Then point a repository at your registry. Two URL schemes are supported:

| Scheme       | Transport  | Typical use                    |
| ------------ | ---------- | ------------------------------ |
| `oci://`     | HTTPS      | Any real registry              |
| `oci+http://`| Plain HTTP | A local registry for testing   |

The path after the host (`my-namespace` in the examples below) is a prefix. Every artifact is
stored under it.

### Publish

```xml
<distributionManagement>
    <repository>
        <id>my-registry</id>
        <url>oci://registry.example.com/my-namespace</url>
    </repository>
</distributionManagement>
```

```shell
mvn deploy
```

Each module is pushed as soon as it is built, to its own OCI repository. There is no staging step
and nothing else to configure.

### Consume

Add the registry as a repository and declare dependencies as usual:

```xml
<repositories>
    <repository>
        <id>my-registry</id>
        <url>oci://registry.example.com/my-namespace</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>
</dependency>
```

To fetch a single artifact without a project:

```shell
mvn dependency:get -Dartifact=com.example:my-app:1.0.0 \
    -DremoteRepositories=my-registry::default::oci://registry.example.com/my-namespace
```

> [!NOTE]
> Nothing stores `maven-metadata.xml` in the registry. When Maven asks for it (for example to
> resolve a version range), the extension builds it from the repository's tags. SNAPSHOT-level
> metadata is not supported yet.

## How artifacts are stored

Each `groupId:artifactId` gets its own OCI repository, and each version is a tag. For
`com.example:my-app:1.0.0` that is `registry.example.com/my-namespace/com/example/my-app:1.0.0`.
Characters that OCI tags don't allow, such as `+`, are escaped in a reversible way.

The manifest behind the tag holds the POM and the main artifact as layers. Classified artifacts
(`-sources`, `-javadoc`, ...) are not bundled into it. Each one is pushed as a separate
[OCI referrer](https://github.com/opencontainers/distribution-spec/blob/main/spec.md#listing-referrers)
pointing back at that manifest, so you can list them with `oras discover`:

```shell
$ oras discover registry.example.com/my-namespace/com/example/my-app:1.0.0
registry.example.com/my-namespace/com/example/my-app@sha256:1a2b3c...
└── application/vnd.maven.artifact.attachment.v1
    └── sha256:4d5e6f...   # e.g. my-app-1.0.0-sources.jar
```

| Manifest                  | `artifactType`                                 |
| ------------------------- | ---------------------------------------------- |
| Primary (POM + main jar)  | `application/vnd.maven.artifact.v1`            |
| Referrer (classified)     | `application/vnd.maven.artifact.attachment.v1` |

## Building from source

You need JDK 25 and Maven 4 (`.sdkmanrc` pins the versions, so `sdk env install` sets both up).

```shell
mvn verify
```

Component tests and integration tests start a [Zot](https://zotregistry.dev) registry with
Testcontainers, so they need Docker. Without it they are skipped. The integration tests also need a
Maven 4 install to run the sample projects with; set `OCI_IT_MAVEN_HOME` if it isn't found under
`~/.sdkman/candidates/maven` or on `PATH`.
