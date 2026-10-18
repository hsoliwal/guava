# Guava M3 Java 21 variant

This standalone module produces `com.google.guava:guava:33.4.8-m3-java21` with
the complete Guava 33.4.8 runtime classes/resources and native M3 storage for
`com.google.common.collect.ArrayListMultimap`. It retains that concrete class,
its public/protected API, inherited Guava multimap policy, and serialization
protocol. The private owners are the existing M3 Object-array list and indexed
array/slot map. The ordinary Guava dependencies remain declared in the POM.

Build from this fork with JDK 21 and Maven:

```sh
mvn -f m3-jre21/pom.xml package
```

The result is `m3-jre21/target/guava-33.4.8-m3-java21-qualified.jar`.
The build unpacks the official `33.4.8-jre` artifact, compiles this module's
qualified native sources, runs the unchanged 6,064-case upstream suite and four
native storage tests, then packages the complete artifact. The original
`guava/src` source and original default build remain the upstream oracle.
The module inherits the four original test sources from the pinned fork;
no test copies replace them. The smoke and archived protocol launchers are
outside the explicit default test-source includes.

Use the variant artifact with the dependencies in its POM. Its manifest and
Maven version identify `33.4.8-m3-java21`, and its minimum runtime is Java 21.
The original JPMS identity/version `com.google.common@33.4.8-jre` is deliberately
retained. Classpath and named-module execution were both checked.

The recorded artifact SHA-256 is
`84050590fa79bd8e6c40bb30cdfa33dad96ac96c0f77645f5723088863e066eb`.
See `qualification/FULL_QUALIFICATION_REPORT_V2.json` for the executed receipts:
6,068 packaging tests passed, the public/protected ABI matched the original,
1,600 seeded differential steps matched, original/candidate serialization
worked in both directions, 12 core tests and 23 existing interop tests passed.
These receipts record the local qualification; hosted CI and draft review
are separate publication checks.

`src/test/java/com/m3/validation/ArtifactSmoke.java` uses only the public Guava
and JDK APIs. Compile it separately against the finished artifact, then run
`com.m3.validation.ArtifactSmoke <actual-jar-path>`. For the named-module check,
place this Guava JAR and its runtime module dependencies on `--module-path`,
use `--add-modules com.google.common`, and place the compiled launcher on the
classpath. It checks the exact artifact CodeSource and needs no `--add-opens`.

`SOURCE_PROVENANCE.tsv` binds copied production sources to original revisions,
exact preimage/postimage hashes and the qualified recipe manifests. The pinned
fork baseline is `f06690fa3e874f65515e8fd338a74d636e2c792f` on
`m3/guava-33.4.8-jre-baseline`; unrelated newer fork master work is preserved.

Only this concrete Guava family is qualified here. The broader 107-row
collection catalogue remains in progress; this module makes no completeness
claim for its other entries, Android or GWT.
