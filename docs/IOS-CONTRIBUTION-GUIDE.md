# Future iPhone contributions

**Status:** Community implementation boundary; no iOS app, adapter or entitlement established. Updated 2026-10-27.
**Responsibility:** Defines shared semantics, unsupported behavior and evidence required for an iOS contribution.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Contracts](NATIVE-CONTRACTS.md), [Capabilities](CAPABILITY-MATRIX.md), [Contributing](CONTRIBUTING.md).
**Unresolved feasibility:** F-07 in [Decisions](DECISIONS.md); framework availability, entitlements, distribution approval and actual exposed data require current Apple-documentation research.

## Scope before implementation

Do not promise Android parity. A future contributor should evaluate public Apple Screen Time-related frameworks, authorization, app-selection privacy, entitlements, extensions and distribution constraints against current primary documentation. No iOS-specific feasibility has been verified in this foundation. Do not assume third-party apps can inspect other apps' accessibility trees, obtain raw histories, identify short videos or perform Android-style overlays.

Submit an iOS capability proposal mapping each C-ID to source/framework, entitlement/grant, user control, accuracy, lifecycle limitation, supported OS range and a synthetic acceptance scenario. Unknown features return unsupported/degraded. An entitlement application, simulator success or a mock adapter is not production approval. Never copy Android permission strings or fake empty success results into shared UI.

## Shared contract responsibilities

Preserve domain concepts: explicit source consent, evidence kind, coverage gaps, UTC instants and named zone grouping, provenance, bounded paginated queries, configuration revisions, user-authored deterministic rules, emergency access and portable encrypted archive semantics. Platform UI can adapt permission language and supported protection behavior, but must explain unsupported targets/actions.

The iOS native implementation owns its authoritative behavioral/configuration store and permitted background logic; it need not use Room. There must still be one store, with a native adapter to shared contracts and no behavioral MMKV mirror. Use Swift/native extensions as required only after the framework design is reviewed. JS cannot become a background execution workaround.

## Privacy and recovery

Carry forward no developer backend, ads, remote analytics, session replay, automatic diagnostics or remote prediction. Local source access stays opt-in and narrow. Platform tokenization/privacy restrictions must be preserved rather than circumvented. Imported archives cannot grant current permissions, activate protection or promise unavailable source support.

Archive portability requires documented mappings for supported common entities, platform provenance and unsupported fields. Preserve imported history where safe without pretending the current platform can continue collecting it. Evaluate key storage, app-group sharing, extension sandbox boundaries and OS cloud/device-transfer behavior explicitly; Android backup exclusions do not apply automatically to iOS.

## Acceptance and collaboration

Follow the same AS task workflow, pnpm/latest direct dependency policy, public synthetic fixtures and review process as Android. Provide an isolated macOS/Xcode build matrix at current compatible versions, physical-device tests where required, entitlement evidence without private credentials, privacy/egress checks, failure recovery and accessibility verification. Adapt L/P/B/S scenarios and X-03; document intentional differences rather than weakening shared invariants.

No date, maintainer budget or feature-parity promise is set. A bounded first contribution may improve unsupported capability reporting or shared accessible UI without introducing an unverified native feature. Update the capability matrix and current state only for actually implemented and tested scope.
