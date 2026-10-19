# Pinned Error Prone class overlay

Copyright 2015 The Error Prone Authors. Apache License 2.0; see COPYING.

This is a derived receiver from the canonical com.synexia owner identified in
manifest.json. It contains eight changed class entries, not checker Java source.
The original published 2.50.0 artifact remains unchanged. Run reconstruct.py with
`--original-jar`, `--original-pom`, `--overlay` and a fresh `--output` directory.
Every admitted input and the complete final task artifact are hash checked.
The workflow reconstructs and installs only the distinct task version. The
canonical source build and independent actual compiler probe receipts remain the
qualification authority; the overlay is a self-contained public CI build input.
