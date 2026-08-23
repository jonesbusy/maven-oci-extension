# maven-oci-extension

A Maven 4 core extension that resolves artifacts from, and publishes artifacts to, an OCI registry
(Docker Hub, GHCR, Harbor, Zot, ...) via [oras-java](https://github.com/oras-project/oras-java).

> [!WARNING]
> This project is currently in **alpha** state. Configuration and behavior might change in future
> releases. It also depends on `oras-java-sdk`, itself alpha.

## Usage

Declare the extension in `.mvn/extensions.xml`:

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.2.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.2.0 http://maven.apache.org/xsd/core-extensions-1.2.0.xsd">
    <extension>
        <groupId>io.github.jonesbusy</groupId>
        <artifactId>oci-extension-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </extension>
</extensions>
```

Two protocols are supported: `oci://` (HTTPS) and `oci+http://` (plain HTTP, e.g. a local registry).

## Publish

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

Each module publishes independently, immediately, to its own OCI repository — no staging step, no
extra properties.

## Consume

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

Or resolve one artifact directly, without a project:

```shell
mvn dependency:get -Dartifact=com.example:my-app:1.0.0 \
    -DremoteRepositories=my-registry::default::oci://registry.example.com/my-namespace
```

## Artifact structure

One OCI repository per `groupId:artifactId`, one tag per version (characters invalid in OCI tags,
like `+`, are escaped reversibly). The tag's manifest holds the POM and main artifact as layers;
classified artifacts (`-sources`, `-javadoc`, ...) are attached separately as [OCI
referrers](https://github.com/opencontainers/distribution-spec/blob/main/spec.md#listing-referrers)
pointing back at that manifest, rather than bundled into it.

```shell
$ oras discover registry.example.com/com/example/my-app:1.0.0
registry.example.com/com/example/my-app@sha256:1a2b3c...
└── application/vnd.maven.artifact.attachment.v1
    └── sha256:4d5e6f...   # e.g. my-app-1.0.0-sources.jar
```

The primary manifest's `artifactType` is `application/vnd.maven.artifact.v1`; referrer manifests use
`application/vnd.maven.artifact.attachment.v1`.
