# Agent and contributor engineering rules

This file is the short contract for humans and coding agents working in `trace-mimic-adapter`.

This JAR translates `MimicEvent` onto `TraceSession.event`. It is not a robot runtime. It does not command hardware. It does not add a scheduler. TRACE and MIMIC must still build without each other.

## Commands

```powershell
.\gradlew.bat check
.\gradlew.bat spotlessApply
.\gradlew.bat javadoc
```

`check` compiles production code, tests, architecture tests, javadoc, and Spotless. Format with `.\gradlew.bat spotlessApply` (Palantir Java Format, 4-space).

Sibling checkouts of `TRACE`, `MIMIC`, and `allsparks-contracts` are required. `settings.gradle` `includeBuild`s them when present. Do not publish this adapter onto Maven Central as a way to hide those compile edges.

## Allowed contents

| May exist | Must not exist |
| --------- | -------------- |
| Mapping from `MimicEvent` to `TraceSession.event` | `setPower`, `setVelocity`, servo writes |
| `ComponentId` from a mechanism id field | FTC SDK, Android, AndroidX |
| `TraceMappings` for FAULT / SENSOR_INVALID severity | Scheduler, event bus, background threads |
| Unit tests with a recording `TraceSession` | `Trace` static singleton as a required path |
| Imports of `org.allsparks.trace` and `org.allsparks.mimic` | Compile edges inside TRACE or MIMIC toward each other |

## Tests expected for new work

- Behavior change: unit test next to the adapter.
- Production sources: architecture tests must still forbid Android/FTC, `setPower`, and threads.
- Do not add `Thread`, `Executor`, `Timer`, or network I/O in production sources.
