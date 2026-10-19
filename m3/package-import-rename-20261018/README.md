# Guava full source package and import rename

Base: `dfd9dca629d44faf71846b1f85acd38556f4854d`.
Canonical reusable recipe owner: `hsoliwal/com.synexia`; this fork receives the pinned
OpenRewrite package rename and records target metadata/proof only.

Mappings: `com.google.common` -> `com.m3.collections.guava`, recursively;
`com.google.thirdparty` -> `com.m3.collections.guava.thirdparty`, recursively.
Original class/method names, Maven artifact coordinates and source rights remain.
Both JRE and Android source trees, testlib/tests, futures helpers and GWT resources
are in scope. Historical hash-pinned recipe templates are evidence and stay immutable.

`metadata-replacements.tsv` contains exact reviewed fragments, represented as JSON
strings in `old_json`/`new_json`, for runtime type literals, InlineMe annotations,
module directives, build/GWT XML values and ProGuard rules.
`metadata-preimages.tsv` binds the original owner bytes before AST relocation; an
application receipt must separately bind intermediate/postimage bytes. Apply exact
fragments only after the shared semantic package/import recipe. Refuse unknown
source drift or destination collisions. Move package-owned resources byte-for-byte.

Required order: source inventory and baseline -> qualified OpenRewrite package/import
rename -> exact metadata/resource adaptation -> compile and frozen tests -> fresh
serial replay -> zero-change fixed point and refusal checks -> target review.
A manifest, emitted patch or fixed point does not itself prove compilation or behavior.

Build: preserve Guava's declared Temurin27 compiler and Java21 test runtime. Use the
workspace proxy/system TLS trust store; never disable certificate verification.
Root reactor excludes futures/failureaccess: receiving proof must bind a relocated
helper artifact without overwriting the published original artifact in Maven cache.
External Truth/Jimfs/Caliper signatures require isolated relocated verification
artifacts or owner-specific proof; package-prefix text is not evidence of ownership.
