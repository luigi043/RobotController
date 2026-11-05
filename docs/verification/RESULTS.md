# Verification results

Completed local verification on 2026-11-05.

| Check | Result |
| --- | --- |
| Debug application compilation | Passed |
| JVM regression tests | 7 passed, 0 failed |
| Instrumentation APK compilation | Passed |
| Native API 35 walkthrough | 1 passed, 0 failed; 98.543 seconds |
| Native screenshots | 13 real Android/system captures |
| Android lint | 0 errors, 26 warnings |
| Local phone-style browser demo | Passed all workflow checks; zero page errors |
| Phone-width layout | 390px, no horizontal overflow |
| JavaScript syntax / static hosting JSON | Passed |
| Git whitespace check | Passed |

Native runtime evidence is in [instrumentation.txt](evidence/instrumentation.txt). JVM XML, lint summary and browser output are in the same evidence folder. [SCREEN-INDEX.md](SCREEN-INDEX.md) maps every capture and identifies hardware-dependent blockers.

## APK

- Package: `com.robotcontroller.debug`
- Variant: debug, signed with the standard development certificate
- Size: 6,977,065 bytes
- SHA-256: `c70fc675cd27ea28740a169cba9ecc98fe1d2a5e6bf496b49f099d48612951dc`
- Build output: `app/build/outputs/apk/debug/app-debug.apk`
- Public packaging output: `build/public-demo/robot-controller-debug.apk`

The [public artifact manifest](evidence/public-artifacts.json) records SHA-1, SHA-256 and byte size for each packaged file. Native screenshots are copied directly, without editing their content.

## Git sequence

1. `34069a5baf29865abd7aced3f4ef4ac5566644d1`: native session correctness, recovery UI, offline transport, tests and removal of duplicate source copies.

The following source/evidence and publication commits are available in the branch log. The branch is `robot-controller-verification`; main is unchanged. Author identity uses the established repository author, Luiz Fehlberg.

## Remaining limits

No physical phone, robot, account or real private app data was used. Successful physical pairing/RFCOMM, firmware acknowledgements, motor/LED response and radio-loss behavior remain unverified. Debug validation does not establish release/minification compatibility or store readiness. The 26 lint warnings cover existing dependency versions, target API, portrait orientation, backup metadata, overdraw, unused resources and text-resource suggestions. They were not hidden by a lint baseline.
