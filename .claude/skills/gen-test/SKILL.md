---
name: gen-test
description: Generate a JUnit 4 unit test for a Kotlin class in this repo (parsers, data sources), following the project's existing test setup
disable-model-invocation: true
---

# gen-test

Generate a JUnit 4 test for a given Kotlin file/class under `app/src/main/java/com/motonav/app/`.

## Steps

1. Read the target class and any interface it implements (e.g. `NavDataParser`).
2. Place the test at the mirrored path under `app/src/test/java/com/motonav/app/...`, named `<Class>Test.kt`.
3. Use plain JUnit 4 (`junit:junit:4.13.2`, already a dependency) — no Mockito/Robolectric/MockK unless the class under test requires an Android framework class Robolectric can't be avoided for.
4. Cover: the happy path, and any explicit nullability/edge case called out in the source's doc comments (e.g. `NavDataParser.parse` returning `null` for non-matching notifications).
5. No test scaffolding beyond what's needed for this one class — don't add shared base test classes or fixtures unless a second test would immediately duplicate them.
