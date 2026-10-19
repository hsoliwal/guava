# Isolated relocated verification dependencies

These task-local receiver versions never overwrite published upstream artifacts.
Failureaccess compiles the actual renamed two helper classes and module descriptor.
The other receivers use standard Maven Shade relocation on pinned external artifact
versions, retaining license/NOTICE and service metadata. Guava-family implementation
bodies are explicitly excluded: the current reactor source is the implementation.

Build after source package relocation with `mvn -f pom.xml install`. Then run the
original Guava reactor with explicit opt-in properties:

```
-Dfailureaccess.version=1.0.3-m3-relocated-20261018
-Dtruth.version=1.4.5-m3-relocated-20261018
-Djimfs.version=1.3.1-m3-relocated-20261018
-Dcaliper.version=1.0-beta-3-m3-relocated-20261018-r2
-Dtest-parameter-injector.version=1.22-m3-relocated-20261018
-Derror_prone_core.version=2.50.0-m3-guava-namespace-20261018
```

Guava default POM properties and published artifact coordinates remain unchanged.
Receivers are build/test support; no target-runtime algorithm is added. Artifact
hashes and actual compile/test receipts must be recorded before qualification.

Caliper revision 2 keeps the instrumentation dependency's embedded older publicsuffix
API within `com.google.monitoring.runtime.instrumentation.thirdparty`, alongside its
existing shaded common owner. It must not collide with the fork's current `TRIE` API.

The supported complete verification invocation uses the existing opt-in
`m3-relocated-verification` profile, which selects all receiver versions together.
Reconstruct/install the separately qualified checker receiver first, as the
workflow does. Its `error-prone-overlay/manifest.json` binds the public original
artifact, the eight changed class entries, the license and the exact resulting
qualified artifact. Its Java source/build owner remains canonical in com.synexia.
TestParameterInjector retains its original package and test-runner behavior while
its Guava signatures and internal Guava calls refer to the actual renamed reactor.

The fork's source/compiler class names, original checker severities and native
compiler/test options remain enabled. Full native commands explicitly restore the
upstream 1536 MiB test heap; Maven's reporting process uses 2560 MiB after retained
512/1024 MiB reporting-memory counterexamples. This changes reporting capacity,
not test selection. No tests are filtered in the final native suite commands.
