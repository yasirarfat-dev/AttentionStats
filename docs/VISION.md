# Vision

**Status:** Accepted product direction; application unimplemented. Updated 2026-10-26.
**Responsibility:** Defines mission, audience, values, and long-term boundaries.
**Related specifications:** [Product](PRODUCT.md), [Privacy](PRIVACY.md), [Roadmap](ROADMAP.md).
**Unresolved feasibility:** F-01, F-07, F-09 in [Decisions](DECISIONS.md); breadth depends on platform access, not ambition alone.

## Mission

Give people a trustworthy account of their digital routines and practical control over what happens next. Attention Stats aims to understand every digital behavior that platforms expose and users choose to track. Completeness is an evolving goal; the app must state what it cannot see.

The experience should support peace of mind, curiosity about personal behavior and help with unwanted impulses. A calm, pleasing interface and considerate language make support approachable. Optimize for voluntary useful engagement and a clear next step outside the app. [Human factors](HUMAN-FACTORS.md) defines this direction; neither the design nor activity history establishes psychological benefit or a diagnosis.

Observe means obtaining explicitly enabled signals. Track means constructing a durable, inspectable history. Categorize means applying editable meaning. Predict means offering an uncertain, explainable forecast. Protect means executing the user's chosen rules. These stages can be used independently: a person may collect app totals without content history or protection.

## People and needs

Primary users are adults reviewing personal habits, students organizing study time, and people balancing work, rest, and entertainment. Privacy-conscious users must be able to use the product offline without a product account. Contributors need public specifications that distinguish a design from a proven capability. Users needing disability accommodations must have readable charts, accessible controls, and safe escape routes; this does not make the product an accessibility tool under store policy.

## Principles

- Ask separately for each sensitive source and detail level. OS access is necessary but does not substitute for product consent.
- Make observed facts, estimates, classifications, and predictions visually distinct. A missing signal is a gap, not zero activity.
- Treat the person as the authority. Use neutral language and editable categories, goals, exclusions, and rules.
- Prefer local computation, minimal source payloads, and explicit data movement. Preserve history until the user deletes it by default.
- Build protection that survives interface restarts while honestly reporting unavailable enforcement. Never promise tamper-proof control.
- Welcome public contributions through stable interfaces and evidence. Android implementation choices must not masquerade as universal capabilities.

## Success and boundaries

Success means a person can answer “what happened, how reliable is this, and what do I want to change?” without surrendering their history. The product is neither a medical instrument nor a covert monitor. It will not infer dopamine, diagnose addiction, rank a person's worth, sell behavior, or manage another adult's device without their active control. The design excludes mandatory accounts, developer-hosted history, ads, and remote analytics.

Long-term work includes local predictions and additional platform adapters. Unsupported functionality remains visible as unavailable rather than being simulated or marketed as working. [Product acceptance](PRODUCT.md) turns this direction into reviewable outcomes.
