package org.attentionstats.protection

import java.security.SecureRandom

/** Pure parent-secret rule checks: no Android, Keystore, credentials file or live protection involved. */
fun main() {
    var checks = 0
    fun expect(value: Boolean, label: String) { check(value) { label }; checks++ }
    fun chars(value: String) = value.toCharArray()
    val rules = ParentSecretRules

    // PIN: 4..12 ASCII digits; legacy 6..12 digit PINs remain valid.
    expect(!rules.validPin(chars("123")), "three digits rejected")
    expect(rules.validPin(chars("1234")), "four digits accepted")
    expect(rules.validPin(chars("583194")), "legacy six digits accepted")
    expect(rules.validPin(chars("123456789012")), "twelve digits accepted")
    expect(!rules.validPin(chars("1234567890123")), "thirteen digits rejected")
    expect(!rules.validPin(chars("12a4")), "letters rejected")
    expect(!rules.validPin(chars("١٢٣٤")), "non-ASCII digits rejected")
    expect(!rules.validPin(CharArray(0)), "empty rejected")

    // Phrases: 3..128 after NFC/trim/collapse; legacy normalization unchanged.
    expect(rules.normalizePhrase(chars("ab")) == null, "two characters rejected")
    expect(String(rules.normalizePhrase(chars("abc"))!!) == "abc", "three characters accepted")
    expect(rules.normalizePhrase(chars("  ab  ")) == null, "trimmed below minimum rejected")
    expect(String(rules.normalizePhrase(chars("  a   b c "))!!) == "a b c", "whitespace trimmed and collapsed")
    expect(String(rules.normalizePhrase(chars("Café river"))!!) == "Café river", "NFC applied")
    expect(String(rules.normalizePhrase(chars("Tree"))!!) != "tree", "case preserved")
    expect(rules.normalizePhrase(CharArray(128) { 'x' }) != null, "128 characters accepted")
    expect(rules.normalizePhrase(CharArray(129) { 'x' }) == null, "129 characters rejected")
    expect(String(rules.normalizePhrase(chars("synthetic orchard silver"))!!) == "synthetic orchard silver", "legacy phrase unchanged")
    val good = listOf("red", "blue sky", "green").map(::chars)
    expect(rules.phraseProblems(good, good.map { it.copyOf() }).all { it == null }, "valid phrase set")
    expect(rules.phraseProblems(listOf(chars("ok")) + good.drop(1), good)[0] == ParentSecretRules.PhraseProblem.LENGTH, "short phrase flagged")
    expect(rules.phraseProblems(good, listOf(chars("red"), chars("blue  sky "), chars("grey")))[2] == ParentSecretRules.PhraseProblem.MISMATCH, "mismatch flagged after normalization")
    expect(rules.phraseProblems(good, listOf(chars("red"), chars(" blue sky"), chars("green"))).all { it == null }, "confirmation compared normalized")
    val repeated = listOf("red", "blue", " red ").map(::chars)
    expect(rules.phraseProblems(repeated, repeated)[2] == ParentSecretRules.PhraseProblem.DUPLICATE, "normalized duplicate flagged")

    // Pattern geometry: skipped middle cells are auto-included only when unvisited.
    expect(rules.middle(0, 2) == 1 && rules.middle(0, 8) == 4 && rules.middle(6, 0) == 3 && rules.middle(2, 6) == 4, "middle cells")
    expect(rules.middle(0, 1) == null && rules.middle(0, 5) == null && rules.middle(0, 7) == null, "adjacent and knight moves have no middle")
    var path = IntArray(0)
    for (cell in intArrayOf(0, 2, 8, 6)) path = rules.extend(path, cell)
    expect(path.contentEquals(intArrayOf(0, 1, 2, 5, 8, 7, 6)), "drag auto-includes skipped dots")
    expect(rules.extend(path, 2).contentEquals(path), "revisited dot ignored")
    expect(rules.extend(intArrayOf(1, 0), 2).contentEquals(intArrayOf(1, 0, 2)), "visited middle passed over")
    expect(rules.extend(intArrayOf(4), 0).contentEquals(intArrayOf(4, 0)), "diagonal neighbour")
    expect(rules.validPattern(intArrayOf(0, 1, 2, 5)), "four dots valid")
    expect(!rules.validPattern(intArrayOf(0, 1, 2)), "three dots rejected")
    expect(!rules.validPattern(intArrayOf(0, 2, 5, 8)), "unvisited skipped middle rejected")
    expect(rules.validPattern(intArrayOf(1, 0, 2, 5)), "visited middle may be crossed")
    expect(!rules.validPattern(intArrayOf(0, 1, 1, 2)), "repeated dot rejected")
    expect(!rules.validPattern(intArrayOf(0, 1, 2, 9)), "out of range rejected")
    expect(rules.validPattern(intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8)), "all nine valid")
    expect(rules.validPattern(intArrayOf(0, 5, 6, 1)), "knight moves valid")

    // Pattern encoding: domain-separated, ordered, round-trips, never just the digits.
    val encoded = rules.encodePattern(intArrayOf(0, 1, 2, 5))
    expect(String(encoded).endsWith(":1236") && String(encoded) != "1236", "encoded with domain prefix")
    expect(rules.decodePattern(encoded)!!.contentEquals(intArrayOf(0, 1, 2, 5)), "round trip")
    expect(!String(rules.encodePattern(intArrayOf(5, 2, 1, 0))).contentEquals(String(encoded)), "direction matters")
    expect(runCatching { rules.encodePattern(intArrayOf(0, 1, 2)) }.isFailure, "short pattern cannot be encoded")
    expect(runCatching { rules.encodePattern(intArrayOf(0, 2, 5, 8)) }.isFailure, "skipping pattern cannot be encoded")
    expect(rules.decodePattern(chars("1236")) == null, "bare digits rejected")
    expect(rules.decodePattern(chars(String(encoded).dropLast(1))) == null, "decoded short pattern rejected")
    expect(rules.decodePattern(chars(String(encoded).dropLast(1) + "0")) == null, "decoded zero digit rejected")
    expect(rules.decodePattern(chars(String(encoded).replace(":1236", ":1396"))) == null, "decoded skip rejected")

    // Change authority: knowledge only; biometrics/phone credential can never widen methods or replace secrets.
    val actions = listOf("reset", "methods", "biometric-on", "biometric-off", "pin", "pattern", "remove-pin", "remove-pattern", "disable", "phrases")
    for (action in actions) for (authority in listOf(ParentAuthority.STRONG_BIOMETRIC, ParentAuthority.WEAK_BIOMETRIC, ParentAuthority.DEVICE_CREDENTIAL))
        expect(!rules.acceptsForChange(action, authority), "$authority rejected for $action")
    expect(rules.acceptsForChange("methods", ParentAuthority.PIN) && rules.acceptsForChange("methods", ParentAuthority.PATTERN) &&
        rules.acceptsForChange("methods", ParentAuthority.PHRASE), "methods accept PIN, pattern or phrase")
    expect(rules.acceptsForChange("reset", ParentAuthority.PHRASE) && !rules.acceptsForChange("reset", ParentAuthority.PIN), "reset needs a phrase")
    expect(rules.acceptsForChange("disable", ParentAuthority.PATTERN) && !rules.acceptsForChange("disable", ParentAuthority.PHRASE), "disable needs PIN or pattern")
    expect(rules.acceptsForChange("phrases", ParentAuthority.PIN) && !rules.acceptsForChange("phrases", ParentAuthority.PHRASE), "phrase rotation needs PIN or pattern")
    expect(!rules.acceptsForChange("unknown", ParentAuthority.PIN), "unknown action rejected")
    expect(rules.clearsMethods("reset", false) && rules.clearsMethods("pin", true) && !rules.clearsMethods("pattern", false) &&
        !rules.clearsMethods("remove-pin", true), "secret replacement clears methods; additions do not")

    // Method policy: defaults, normalization and widening.
    val defaults = UnlockMethods()
    expect(defaults.strong && !defaults.weak && !defaults.device, "defaults: fingerprint on, face and phone lock off")
    expect(UnlockMethods(strong = false, weak = true).normalized() == UnlockMethods.NONE, "weak cannot stand without strong")
    expect(defaults.widens(defaults.copy(weak = true)) && defaults.widens(defaults.copy(device = true)), "adding methods widens")
    expect(UnlockMethods.NONE.widens(defaults), "turning fingerprint on widens")
    expect(!defaults.widens(UnlockMethods.NONE) && !defaults.widens(defaults), "narrowing or unchanged does not widen")
    expect(!UnlockMethods.NONE.widens(UnlockMethods(strong = false, weak = true)), "normalized weak-only is not wider")

    // Ordinary authorization routes.
    val all = UnlockMethods(strong = true, weak = true, device = true)
    expect(rules.authorizesAction(ParentAuthority.PIN, UnlockMethods.NONE, false, 26), "PIN always authorizes")
    expect(rules.authorizesAction(ParentAuthority.PATTERN, UnlockMethods.NONE, false, 26), "pattern always authorizes")
    expect(!rules.authorizesAction(ParentAuthority.PHRASE, all, true, 36), "phrase does not authorize ordinary actions")
    expect(rules.authorizesAction(ParentAuthority.STRONG_BIOMETRIC, defaults, true, 26), "strong when allowed and key valid")
    expect(!rules.authorizesAction(ParentAuthority.STRONG_BIOMETRIC, defaults, false, 36), "strong rejected after enrollment change")
    expect(!rules.authorizesAction(ParentAuthority.STRONG_BIOMETRIC, UnlockMethods.NONE, true, 36), "strong rejected when off")
    expect(rules.authorizesAction(ParentAuthority.WEAK_BIOMETRIC, all, true, 36), "weak when allowed")
    expect(!rules.authorizesAction(ParentAuthority.WEAK_BIOMETRIC, defaults, true, 36), "weak off by default")
    expect(!rules.authorizesAction(ParentAuthority.WEAK_BIOMETRIC, all, false, 36), "weak needs the enrollment tripwire")
    expect(!rules.authorizesAction(ParentAuthority.WEAK_BIOMETRIC, UnlockMethods(strong = false, weak = true), true, 36), "weak needs strong")
    expect(rules.authorizesAction(ParentAuthority.DEVICE_CREDENTIAL, all, false, 30), "phone lock on API 30")
    expect(!rules.authorizesAction(ParentAuthority.DEVICE_CREDENTIAL, all, true, 29), "phone lock unavailable before API 30")
    expect(!rules.authorizesAction(ParentAuthority.DEVICE_CREDENTIAL, defaults, true, 36), "phone lock off by default")
    expect(rules.biometricRoute(all, true, true) == ParentAuthority.WEAK_BIOMETRIC, "weak route preferred when allowed")
    expect(rules.biometricRoute(all, false, true) == ParentAuthority.STRONG_BIOMETRIC, "invalid key falls back to strong route")
    expect(rules.biometricRoute(defaults, true, true) == ParentAuthority.STRONG_BIOMETRIC, "strong route by default")
    expect(rules.biometricRoute(UnlockMethods.NONE, true, true) == null, "no route when biometrics off")
    expect(rules.resultMatches(ParentAuthority.WEAK_BIOMETRIC, PromptResultType.BIOMETRIC) &&
        !rules.resultMatches(ParentAuthority.WEAK_BIOMETRIC, PromptResultType.DEVICE_CREDENTIAL), "weak result type")
    expect(rules.resultMatches(ParentAuthority.DEVICE_CREDENTIAL, PromptResultType.DEVICE_CREDENTIAL) &&
        !rules.resultMatches(ParentAuthority.DEVICE_CREDENTIAL, PromptResultType.BIOMETRIC), "device result type")
    expect(!rules.resultMatches(ParentAuthority.STRONG_BIOMETRIC, PromptResultType.BIOMETRIC), "strong never uses the non-crypto path")

    // One-use challenges: replay, staleness, purpose/authority binding and expiry.
    val random = SecureRandom()
    val challenges = OneUseChallenges({ random.nextBytes(it) }, lifetimeMs = 1_000)
    val first = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 0)
    expect(challenges.redeem(first, "request-a", ParentAuthority.WEAK_BIOMETRIC, 10), "fresh challenge redeems")
    expect(!challenges.redeem(first, "request-a", ParentAuthority.WEAK_BIOMETRIC, 11), "replay rejected")
    val stale = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 20)
    val current = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 30)
    expect(!challenges.redeem(stale, "request-a", ParentAuthority.WEAK_BIOMETRIC, 40), "replaced challenge rejected")
    expect(challenges.pending(), "stale presentation leaves the current challenge")
    expect(challenges.redeem(current, "request-a", ParentAuthority.WEAK_BIOMETRIC, 41), "current challenge redeems")
    val other = challenges.issue("request-a", ParentAuthority.DEVICE_CREDENTIAL, 50)
    expect(!challenges.redeem(other, "request-b", ParentAuthority.DEVICE_CREDENTIAL, 51), "other request rejected")
    expect(!challenges.redeem(other, "request-a", ParentAuthority.DEVICE_CREDENTIAL, 52), "mismatched presentation consumed it")
    val wrongKind = challenges.issue("request-a", ParentAuthority.DEVICE_CREDENTIAL, 60)
    expect(!challenges.redeem(wrongKind, "request-a", ParentAuthority.WEAK_BIOMETRIC, 61), "other authority rejected")
    val expired = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 100)
    expect(!challenges.redeem(expired, "request-a", ParentAuthority.WEAK_BIOMETRIC, 1_100), "expired rejected")
    val revoked = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 200)
    challenges.revoke()
    expect(!challenges.pending() && !challenges.redeem(revoked, "request-a", ParentAuthority.WEAK_BIOMETRIC, 201), "revoked rejected")
    val kept = challenges.issue("request-a", ParentAuthority.WEAK_BIOMETRIC, 300)
    challenges.revoke(first)
    expect(challenges.pending() && challenges.redeem(kept, "request-a", ParentAuthority.WEAK_BIOMETRIC, 301), "revoking an old challenge keeps the current one")
    expect(runCatching { challenges.issue("request-a", ParentAuthority.PIN, 0) }.isFailure, "knowledge secrets never use challenges")
    expect(runCatching { challenges.issue("request-a", ParentAuthority.STRONG_BIOMETRIC, 0) }.isFailure, "strong uses the crypto path")
    println("PASS: $checks parent secret, pattern, unlock-method and one-use challenge checks")
}
