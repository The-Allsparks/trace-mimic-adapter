# trace-mimic-adapter

Optional adapter that copies [MIMIC](https://github.com/The-Allsparks/MIMIC) `MimicEvent`s onto a [TRACE](https://github.com/The-Allsparks/TRACE) `TraceSession`.

This is not a robot framework. Installing the adapter does not move motors. It does not call `setPower`, `setVelocity`, or write servos. It does not add a scheduler. TRACE and MIMIC still build and publish independently; this artifact depends on both of them plus [`allsparks-contracts`](https://github.com/The-Allsparks/allsparks-contracts).

Maven coordinates: `org.allsparks:trace-mimic-adapter:0.1.0-SNAPSHOT`.

## Dependency direction

```text
allsparks-contracts
        ^
   TRACE     MIMIC     (no compile edge either way)
        ^       ^
     trace-mimic-adapter
        ^
   TeamCode / OpMode (optional)
```

A student can say: **adapters depend on two libraries; the libraries do not depend on each other.**

## Example

Pass a `TraceSession`. Do not require the `Trace` static singleton.

```java
import org.allsparks.adapter.tracemimic.MimicToTraceAdapter;
import org.allsparks.mimic.log.MimicEvent;
import org.allsparks.trace.session.TraceSession;

public final class MimicTraceExample {
    private final MimicToTraceAdapter adapter = new MimicToTraceAdapter();

    public void onEvent(MimicEvent event, TraceSession session) {
        adapter.record(event, session);
    }
}
```

`FAULT` and `SENSOR_INVALID` use TRACE `TraceMappings` for severity. Other event types record as `INFO`. A mechanism id field (`id` or `mechanismId`) becomes a contracts `ComponentId` in the TRACE signal name when present.

## Build

Sibling directories are required because TRACE and MIMIC are not on Maven Central:

```text
The Allsparks/
  TRACE/
  MIMIC/
  allsparks-contracts/
  trace-mimic-adapter/
```

```text
./gradlew check
./gradlew javadoc
```

On Windows: `.\gradlew.bat check`.

Java 11 source and target. CI uses Temurin 17. `settings.gradle` `includeBuild`s the three siblings when their `settings.gradle` files exist.

## What this is not

- Not a reason to make MIMIC depend on TRACE, or TRACE depend on MIMIC
- Not hardware authority
- Not OpMode lifecycle ownership
- Not a TRACE writer, MIMIC session, or TeamCode composition root

## License

MIT. See [LICENSE](LICENSE).
