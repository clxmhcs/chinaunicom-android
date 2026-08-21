# M6-B Refresh State Persistence

`FileBackedRefreshCoordinator` is the durable, UI-free refresh-state layer corresponding to the iOS shared balance cache gate.

- State is atomically stored in an app-private directory supplied by the caller.
- Every read/write takes an operating-system file lock, so app, widget, and service processes share leases and last-successful cache.
- Automatic refresh returns a same-local-day, interval-fresh cache; manual refresh bypasses freshness but never duplicates an active lease.
- A failed refresh releases only its matching lease and keeps the last successful cache.
- Replacing refresh scopes invalidates cache and leases for changed scope membership.
- Cached values are encoded through `RefreshValueCodec`; this module has no account, quota, balance, network, or UI dependency.

The next repository layer must supply app-private storage and concrete quota/balance codecs, then perform the network call only after a `Granted` decision.
