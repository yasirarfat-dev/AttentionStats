# Human factors and wellbeing design

**Status:** Accepted experience direction; synthetic preview in AS-033; native experience unimplemented. Updated 2026-10-27.
**Responsibility:** Governs supportive engagement, psychological rationale, language, trust, interruptions and humane evaluation.
**Related specifications:** [Vision](VISION.md), [Product](PRODUCT.md), [UX](UX-SPEC.md), [Design system](DESIGN-SYSTEM.md), [Protection](PROTECTION-SPEC.md), [Privacy](PRIVACY.md), [Preview](../design/prototype/README.md).
**Unresolved feasibility:** F-01–F-04, F-08 and F-09 in [Decisions](DECISIONS.md); no psychological benefit, device smoothness or universal emotional response to this design has been demonstrated.

## The experience we are building

Attention Stats should feel like a dependable instrument and a considerate companion: clear, composed, patient and useful when needed. Its purpose is peace of mind, self-understanding and support with boundaries the person chooses. Success includes leaving after getting an answer. Make the app pleasant to trust and return to voluntarily, with no pressure to spend longer in it.

Observe → track → categorize → predict → protect is the underlying system, not a program everyone must complete. Observation-only use is welcome. Rest, entertainment, communication and work can all matter; minutes alone cannot distinguish fulfilling use from unwanted use. The app never decides that reading is virtuous or social use is bad.

## Psychological rationale and limits

Self-determination theory describes autonomy, competence and relatedness as important to motivation. Our **design interpretation**, not a proven application outcome, is to support choice, make evidence understandable and communicate respectfully. Equal permission refusal, editable boundaries and inspectable explanations follow that interpretation. A software companion does not substitute for human relationships or claim to know a person's feelings. [Primary theory overview](https://selfdeterminationtheory.org/the-theory/).

Warm colors, generous spacing and restrained motion are aesthetic hypotheses, not therapies. Evaluate comfort across cultural expectations, vision, sensory preferences, neurodivergence and language. Avoid universal claims such as “green reduces anxiety” or “this animation rewires your brain.”

Repeated reopening, long sessions and late-night use can be useful observations. They cannot establish addiction, dopamine levels, intent or an internal attention state. Explain that feeds can make continuing easy, then offer a person-chosen boundary. Never tell someone their brain is broken or depleted, or prescribe a dopamine detox.

## Turn intent into a small, reversible choice

| Need | Interface behavior | Reason / boundary |
|---|---|---|
| Understand a day | A finite daily snapshot, coverage, selected app totals and one pattern card | Progressive disclosure limits initial complexity without hiding evidence |
| Interrupt an unwanted return | Explain observed reopenings; offer a reminder and “No change for now” | A pattern invites reflection; it does not prove unwanted use |
| Take a pause | Pick target, duration and response; preview before activation | A specific boundary is clearer than an unexplained global mode |
| Recover after an unplanned day | “You can adjust this boundary.” Retain accurate history and ordinary control | No lost rank, streak or moral status |
| Get access immediately | Visible unconditional emergency access; explain ordinary override scope/expiry | Agency and safety remain invariant in commitment modes |
| Keep something private | Refuse, pause, exclude or delete without repeated persuasion | Trust requires a usable refusal path |
| Explore content patterns | Future local topic summaries with separate consent, unknown fraction and correction | Metadata can describe topics, not diagnose what a person is addicted to |

Do not infer intent from app category. A future optional intention-setting flow must allow skip/edit; storing personal free text needs a model/privacy update first. The preview collects no personal intentions, moods, journal entries or questionnaires.

## Engagement and restraint

- Favor a finite useful summary, predictable controls and a clear stopping point. No infinite insight feed, collectible badges, randomized praise or slot-machine reveals.
- No streak loss, leaderboards, guilt, scarcity, urgency or red badges to pull people back. A missed check-in has no penalty.
- Notifications start off and follow explicit reminder rules and frequency limits. Never send engagement campaigns, “we miss you” messages or automatic re-permission nags. Future check-ins need opt-in, quiet hours and easy cancellation.
- A chosen budget can show factual progress with units. Do not turn total screen time into a wellness grade or present less use as automatic improvement.
- Reflection is optional. No confession, reason, PIN or biometric for emergency access. No persona that claims to feel disappointed, lonely or dependent on the person.
- Motion explains state and orientation. No breathing loops, pulsing timers, confetti or rewards for opening the app. Sessions end quietly.

## Language at the moment of use

Address an adult in short, concrete sentences. Pair an observation with an optional next step. Explain the rule before its action. Never call the person weak, lazy, a failure or an addict, or command them to “Be good.”

| Moment | Preferred copy / behavior |
|---|---|
| Home | “Notice your patterns. Choose what comes next.” |
| Permission refusal | “Nothing is being tracked. You can explore without enabling a source.” |
| Empty history | “No observations yet.” Explain enabled versus covered zero separately |
| Uncovered time | “30 minutes not covered.” Link to source/repair; no invented total |
| Pattern | “3 reopenings in 10 minutes. There could be many reasons.” |
| Intervention | “You chose a 45-minute social budget.” Offer return, scoped continuation and emergency access |
| Early session end | “Focus session ended.” No failure badge or lost-progress animation |
| Budget reached | “Your chosen budget was reached.” Offer configured behavior and adjustment |
| Insufficient evidence | “No insight is better than a guess.” Explain missing requirements |
| Failure | Name the unavailable function, retained data and actionable next step without blame |

“Preview” and “demo” belong only to demonstrations. This prototype labels its observations, timers and rules as synthetic/non-enforcing. Never use fake “protected,” “encrypted” or “all clear” states.

## Trust made inspectable

The optional [awareness counter](LIVE-OVERLAY-SPEC.md) supports a glance at this visit and today while in a qualified short-video surface. Its display is off by default, removable and separate from tracking/protection. Avoid turning a live number into a challenge or a reason to keep checking. Default to minimal information, meaningful user-chosen reminders and easy silence. Context-specific usability review must test whether it helps or increases preoccupation.

Put evidence and coverage beside the claim. Show last successful check and distinguish OS grant, consent and collector health. Native “saved” requires authoritative success. Explain unavailable enforcement before activation.

Make privacy reachable from every main screen. Link source, release commit, license, support matrix and plain-language data flows. Open source enables inspection; an independent audit, verified builds and device tests remain separate facts. Do not imply an audit has happened.

The [privacy promise](PRIVACY.md) means no developer recipient, local history by default and explicit optional encrypted transfers. On-device tracking is real collection; “collects no data” is inaccurate. Explain provider-visible metadata beside optional transfers.

## Sensory and cognitive comfort

Use one clear heading, one primary next step per section, labelled groups and predictable back/close actions. Prefer recognition over recall: rule cards show target, trigger and response; review shows exceptions and exits together. Preserve drafts after recoverable errors.

Minimum 48dp targets follow [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views?hl=en). Apply text/non-text contrast, focus and status semantics using [WCAG 2.2](https://www.w3.org/TR/WCAG22/) as a reference, without claiming native conformance from browser scans. Honor system reduced motion plus a stronger in-app override. [W3C motion guidance](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html).

## Evaluate what helps

Use consented, task-based review with synthetic histories. Ask people to explain a gap, distinguish an estimate, decline access, adjust a reminder and escape an intervention. Include large type, screen readers, reduced motion and RTL. A small design review does not establish population-level benefit.

Success questions: can the person understand the evidence, choose a useful boundary, recover control immediately and leave with a clearer next step? Optional explicit feedback can ask whether the experience felt comprehensible, respectful and reassuring. These are questions to investigate, not measured benefits today.

Do not optimize daily active users, notification opens, time in Attention Stats, guilt-driven retention or blanket reductions in all phone use. No remote analytics or hidden experiments are added for evaluation. Future local personal review measures need consent/storage/deletion specifications first. Public feedback must use synthetic context and exclude personal histories.

X-04–X-07 in [Testing](TESTING.md) define design acceptance. AS-033 verifies a browser prototype only; AS-018 owns native implementation and manual/device review.
