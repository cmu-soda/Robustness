package edu.cmu.isr.ltsa.weakest

import lts.CompactState
import lts.EventState
import java.util.*
import kotlin.math.min

typealias Transitions = List<Triple<Int, Int, Int>>

class StateMachine {
    val transitions: Transitions
    val alphabet: Array<String>

    constructor(m: CompactState) {
        val trans = mutableListOf<Triple<Int, Int, Int>>()
        for (s in m.states.indices) {
            for (a in m.alphabet.indices) {
                val nexts: IntArray? = EventState.nextState(m.states[s], a)
                if (nexts != null) {
                    for (n in nexts) {
                        trans.add(Triple(s, a, n))
                    }
                }
            }
        }
        transitions = trans
        alphabet = m.alphabet
    }

    constructor(transitions: Transitions, alphabet: Array<String>) {
        this.transitions = transitions
        this.alphabet = alphabet
    }

    override fun toString(): String {
        return transitions.map { Triple(it.first, this.alphabet[it.second], it.third) }.joinToString("\n")
    }

    fun buildFSP(name: String = "A"): String {
        val escaped = alphabet.map(::escapeEvent)
        val groups = transitions.groupBy { it.first }

        fun processName(i: Int): String {
            return if (i == 0) name else if (i in groups.keys) "${name}_$i" else "STOP"
        }

        val fsp = groups.map { entry ->
            val processes = entry.value.joinToString(" | ") { "${escaped[it.second]} -> ${processName(it.third)}" }
            "${processName(entry.key)} = ($processes)"
        }.joinToString(",\n")
        return "$fsp+{${escaped.filter { it != "tau" }.joinToString(", ")}}.\n"
    }

    private fun escapeEvent(e: String): String {
        val idx = e.lastIndexOf('.')
        val suffix = e.substring(idx + 1)
        if (suffix.toIntOrNull() != null) {
            return "${e.substring(0, idx)}[$suffix]"
        }
        return e
    }

}

fun Transitions.getReachable(initial: Set<Int>): Set<Int> {
    var reachable = initial
    while (true) {
        val s = reachable union this.filter { it.third in reachable }.map { it.first }
        if (s.size == reachable.size)
            return reachable
        reachable = s
    }
}

fun Transitions.tauElimination(tau: Int): Transitions {
    var ts = this.toMutableList()
    while (true) {
        val t = ts.find { it.second == tau } ?: break
        val s = min(t.first, t.third)
        ts.remove(t)
        ts = ts.map {
            var copy = it
            if (it.first == t.first || it.first == t.third) {
                copy = copy.copy(first = s)
            }
            if (it.third == t.first || it.third == t.third) {
                copy = copy.copy(third = s)
            }
            copy
        }.toMutableList()
    }
    return ts.removeDuplicate()
}

fun Transitions.subsetConstruct(alphabet: Array<String>)
    : Pair<StateMachine, List<Set<Int>>> {
    val dfaStates = mutableListOf(setOf(0))  // initial state of the DFA is {0}
    val dfaTrans = mutableListOf<Triple<Int, Int, Int>>()
    // create a queue for the new dfa states
    val q: Queue<Set<Int>> = LinkedList()
    q.addAll(dfaStates)
    while (q.isNotEmpty()) {
        val s = q.poll()
        val i_s = dfaStates.indexOf(s)
        for (a in alphabet.indices) {
            val n = s.flatMap { this.nextState(it, a) }.toSet()
            if (n.isEmpty())
                continue
            val i_n = if (n !in dfaStates) {
                dfaStates.add(n)
                q.add(n)
                dfaStates.size - 1
            } else {
                dfaStates.indexOf(n)
            }
            dfaTrans.add(Triple(i_s, a, i_n))
        }
    }
    return Pair(StateMachine(dfaTrans, alphabet), dfaStates)
}

fun Transitions.removeDuplicate(): Transitions {
    return this.fold(mutableListOf()) { l, t ->
        if (!l.contains(t))
            l.add(t)
        l
    }
}

fun Transitions.nextState(s: Int, a: Int): List<Int> {
    return this.filter { it.first == s && it.second == a }.map { it.third }
}