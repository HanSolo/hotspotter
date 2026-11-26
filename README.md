# Hotspotter

Counts how often each method of your application is called, with minimal overhead, and lets you analyse the result afterwards. Package: `eu.hansolo.hotspotter`.

## How it works

The agent injects `Counters.COUNTS[id]++` at the start of every method that matches your filter. The counters live in memory in one `long[]`, a daemon thread writes a CSV snapshot every N seconds. No I/O, locking or allocation happens on application threads.

## Modules

| Module | Purpose | Runs on |
|---|---|---|
| `core` | counters, filter, registry, flusher, agent entry points | JDK 8+ |
| `agent-asm` | agent JAR using ASM (shaded) | target JVM JDK 8+ |
| `agent-classfile` | agent JAR using the Classfile API, no dependencies | target JVM JDK 24+ |
| `attach` | CLI to attach/stop the agent in a running JVM | JDK 11+ |
| `analyzer-core` | analysis logic shared by both analyzer front ends (no UI) | JDK 17+ |
| `analyzer` | command line analyzer | JDK 17+ |
| `analyzer-fx` | JavaFX 25 analyzer: table, bar chart, snapshot range, live mode | JDK 23+ |

`agent-classfile` is only part of the build when Gradle itself runs on JDK 24+, `analyzer-fx` when it runs on JDK 23+.

## Build

    gradle build

Results: `agent-asm/build/libs/hotspotter-agent-asm-1.0.0.jar`, `agent-classfile/build/libs/hotspotter-agent-classfile-1.0.0.jar`, `attach/build/libs/hotspotter-attach-1.0.0.jar`, `analyzer/build/libs/hotspotter-analyzer-1.0.0.jar`.

## Use

At startup:

    java -javaagent:hotspotter-agent-asm-1.0.0.jar=include=com.acme.*,interval=10 -jar app.jar

Attach to a running JVM, and detach again (removes the instrumentation):

    java -jar hotspotter-attach-1.0.0.jar list
    java -jar hotspotter-attach-1.0.0.jar <pid> hotspotter-agent-asm-1.0.0.jar "include=com.acme.*,interval=10"
    java -jar hotspotter-attach-1.0.0.jar <pid> hotspotter-agent-asm-1.0.0.jar stop

Agent options (comma separated, `include`/`exclude`/`excludeMethods` may be repeated, `*` is the only wildcard):

| Option | Meaning | Default |
|---|---|---|
| `include=<glob>` | class names to monitor (required) | none |
| `exclude=<glob>` | class names to skip | none |
| `excludeMethods=<glob>` | method names to skip, e.g. `get*`, `<init>` | none |
| `out=<dir>` | output directory, a `run-<time>-pid<pid>` folder is created in it | `hotspotter-out` |
| `interval=<s>` | snapshot interval | 10 |
| `max=<n>` | maximum number of instrumented methods | 1048576 |

JDK classes (`java.*`, `javax.*`, `jdk.*`, `sun.*`) and the agent itself are never instrumented.

## Analyse (command line)

    java -jar hotspotter-analyzer-1.0.0.jar snapshots hotspotter-out
    java -jar hotspotter-analyzer-1.0.0.jar top hotspotter-out --n 20
    java -jar hotspotter-analyzer-1.0.0.jar top hotspotter-out --by package --depth 3
    java -jar hotspotter-analyzer-1.0.0.jar top hotspotter-out --from -4 --to -1      # calls/s between two snapshots
    java -jar hotspotter-analyzer-1.0.0.jar top hotspotter-out --filter 'acme\.billing' --csv

## Analyse (JavaFX)

    ./gradlew :analyzer-fx:run --args="/absolute/path/to/hotspotter-out"

Or start it without arguments and use *Open folder…*. Open the base output folder to always follow the newest run. Pick any two snapshots to compare (calls and calls/s between them), group by method/class/package, filter classes with a regex and export the table as CSV. Switch on **Live** to follow a running application: the view refreshes every 2 seconds and shows the calls/s of the last snapshot interval.

Tip: to measure one scenario, take the snapshot just before and just after it and use `--from`/`--to`. Combine with a JFR recording (`jcmd <pid> JFR.start`) to see where time is spent, call counts alone do not show that.

## Notes

* Counters use plain increments (no atomics): under heavy multi-thread contention counts can be slightly low, the ranking stays valid.
* Appending the counter class to the bootstrap class path makes the JVM print a CDS warning at startup and disables class data sharing for app classes.
* A stopped agent cannot be restarted in the same JVM.
* `dev/local-test.sh` is a smoke test that works without ASM/network access (uses the JDK-internal ASM).
