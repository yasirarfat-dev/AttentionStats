package com.attentionstats.autoscroll.domain

/** Standalone host-runner decisions: no Android services, provider content or gestures. */
fun main() {
    var checks = 0
    fun test(name: String, action: () -> Unit) {
        try { action() } catch (failure: Throwable) { throw AssertionError(name, failure) }
        checks++
    }
    val key = CompletionGate.Context("synthetic", 1, 1, 1)
    val advancing = (0..10).map { .4 + it * .05 }
    val terminal = advancing + listOf(.999, .999)
    fun feed(gate: CompletionGate, values: List<Double>, start: Long = 1_000,
        step: Long = 300, context: CompletionGate.Context = key): List<Boolean> =
        values.mapIndexed { index, position -> gate.sample(context, start + index * step, position, true) }
    fun armed(): CompletionGate = CompletionGate().also { check(feed(it, advancing).none { result -> result }) }
    fun noTerminal(gate: CompletionGate, start: Long = 4_600, context: CompletionGate.Context = key) {
        check(feed(gate, listOf(.999, .999, .999), start, context = context).none { it })
    }

    test("observed mid-playback advancement permits exactly the second terminal sample") {
        val results = feed(CompletionGate(), terminal + listOf(.999, 0.0, .999))
        check(results.withIndex().filter { it.value }.map { it.index } == listOf(terminal.lastIndex))
    }
    test("near-end to beginning loop fires once") {
        val results = feed(CompletionGate(), advancing + listOf(.98, .02, .03))
        check(results.count { it } == 1 && results[advancing.size + 1])
    }
    test("terminal 999 of 1000 qualifies without a full 1.0") {
        check(feed(CompletionGate(), terminal).last())
    }
    test("fresh end-only values never qualify") {
        check(feed(CompletionGate(), List(30) { .999 }).none { it })
    }
    test("paused partial and near-end values never qualify") {
        for (position in listOf(.4, .9, .98)) {
            check(feed(CompletionGate(), advancing + List(30) { position }).none { it })
        }
    }
    test("fewer than eight advances cannot qualify terminal or loop") {
        val short = (0..6).map { .7 + it * .04 }
        check(feed(CompletionGate(), short + listOf(.999, .999, 0.0)).none { it })
    }
    test("fast samples cannot substitute for two seconds observed playback") {
        check(feed(CompletionGate(), terminal, step = 100).none { it })
    }
    test("large forward seek discards prior progress") {
        val gate = CompletionGate()
        check(feed(gate, (0..15).map { it * .05 }).none { it })
        check(!gate.sample(key, 5_800, .999, true))
        noTerminal(gate, 6_100)
    }
    test("backward seek discards prior progress") {
        val gate = armed()
        check(!gate.sample(key, 4_300, .85, true))
        noTerminal(gate)
    }
    test("early reset is not a completion loop") {
        check(feed(CompletionGate(), advancing + listOf(.02)).none { it })
    }
    test("one terminal sample is insufficient") {
        check(feed(CompletionGate(), advancing + listOf(.999)).none { it })
    }
    test("exactly eight advances qualify after the required observation span") {
        val positions = (0..7).map { .2 + it * .1 } + listOf(.999, .999)
        check(feed(CompletionGate(), positions).last())
    }
    test("a nonterminal sample interrupts consecutive terminal evidence") {
        val gate = armed()
        check(!gate.sample(key, 4_300, .999, true))
        check(!gate.sample(key, 4_600, .998, true))
        noTerminal(gate, 4_900)
    }
    for ((name, context) in listOf(
        "app" to key.copy(app = "other.synthetic"),
        "window" to key.copy(window = 2),
        "page" to key.copy(page = 2),
        "epoch" to key.copy(epoch = 2),
    )) test("changed $name discards accumulated evidence") { noTerminal(armed(), context = context) }
    for ((name, at) in listOf("duplicate" to 4_000L, "backward" to 3_999L, "stale" to 5_501L)) {
        test("$name timestamp discards accumulated evidence") {
            val gate = armed()
            check(!gate.sample(key, at, .999, true))
            noTerminal(gate, at + 300)
        }
    }
    test("maximum allowed sample gap remains fresh") {
        val gate = armed()
        check(!gate.sample(key, 5_500, .999, true))
        check(gate.sample(key, 5_800, .999, true))
    }
    for ((name, position) in listOf("NaN" to Double.NaN, "positive infinity" to Double.POSITIVE_INFINITY,
        "negative infinity" to Double.NEGATIVE_INFINITY, "below zero" to -.01, "above one" to 1.01)) {
        test("$name input clears accumulated evidence") {
            val gate = armed()
            check(!gate.sample(key, 4_300, position, true))
            noTerminal(gate)
        }
    }
    test("lost eligibility clears accumulated evidence") {
        val gate = armed()
        check(!gate.sample(key, 4_300, .95, false))
        noTerminal(gate)
    }
    test("negative time clears accumulated evidence") {
        val gate = armed()
        check(!gate.sample(key, -1, .95, true))
        noTerminal(gate)
    }
    test("explicit interruption requires fresh observed advancement") {
        val gate = armed()
        gate.reset()
        noTerminal(gate)
        check(feed(gate, terminal, 6_000).last())
    }
    test("same item cannot fire twice without reset") {
        val gate = CompletionGate()
        check(feed(gate, terminal).last())
        check(feed(gate, terminal, 6_000).none { it })
        gate.reset()
        check(feed(gate, terminal, 11_000).last())
    }
    test("new item needs its own advancement after prior completion") {
        val gate = CompletionGate()
        check(feed(gate, terminal).last())
        val next = key.copy(page = 2)
        noTerminal(gate, 6_000, next)
        check(feed(gate, terminal, 8_000, context = next).last())
    }
    println("PASS: $checks synthetic auto-scroll completion checks (host JVM; no source/device acceptance)")
}
