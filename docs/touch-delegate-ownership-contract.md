# Global TouchDelegate Ownership Contract

AquaLight expands undersized clickable controls to a 48dp interaction area without changing their rendered size or layout.

## Ownership

- The canonical Android window content host owns at most one AquaLight installation.
- A `TouchDelegate` already installed by Android, a library or feature code is treated as a foreign upstream delegate.
- The foreign delegate receives first refusal for every new gesture. AquaLight handles the gesture only when the foreign delegate does not claim `ACTION_DOWN`.
- AquaLight never clears a foreign delegate. When no AquaLight expansion is required, the exact upstream delegate instance is restored.
- If another owner replaces the delegate after AquaLight installation, the next rebuild captures that delegate as the new upstream owner instead of overwriting or stacking it.

## Dynamic hierarchy

- One `ViewTreeObserver.OnGlobalLayoutListener` observes the window host for added, removed, recycled, resized, enabled or visibility-changed controls.
- Rebuild requests are coalesced onto the host message queue.
- AquaLight does not call `setOnHierarchyChangeListener`, so application or library hierarchy listeners remain untouched.
- Removed targets are released on the next layout pass and cannot receive stale taps.
- A detached host stops observing and restores the upstream delegate; reattachment resumes observation and rebuilds from the current hierarchy.

## Release evidence

The instrumentation contract verifies:

1. deterministic routing where expanded AquaLight targets overlap;
2. preservation, priority and exact restoration of a pre-existing foreign delegate;
3. dynamic target addition after installation;
4. stale-target removal without a manual reinstall call;
5. dynamic replacement at a new location;
6. API 27 and API 35 minified release-smoke execution.

The JVM architecture test prevents direct delegate clearing, foreign hierarchy-listener replacement and additional production owners of `View.touchDelegate`.
