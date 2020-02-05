package edu.cmu.isr.ltsa.util

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.minimise
import lts.CompactState
import java.util.*

class StateMachine {
  /**
   * The transitions of this state machine. States and labels are all represented in integers.
   */
  val transitions: Transitions

  /**
   * The array of alphabets which contains tau event.
   */
  val alphabet: Array<String>

  /**
   * The index number which represents the tau event.
   */
  val tau: Int

  constructor(m: CompactState) {
    transitions = SimpleTransitions(m)
    alphabet = m.alphabet
    tau = alphabet.indexOf("tau")
  }

  constructor(transitions: Transitions, alphabet: Array<String>) {
    this.transitions = transitions
    this.alphabet = alphabet
    this.tau = this.alphabet.indexOf("tau")
  }

  fun hasError(): Boolean {
    return -1 in transitions.inTrans()
  }

  fun buildFSP(name: String = "A", unused: Boolean = true): String {
    if (transitions.isEmpty()) {
      return "$name = END."
    }

    // Escaped event names
    val escaped = alphabet.map(::escapeEvent)
    val groups = transitions.outTrans()
    val used = transitions.allEvents()
    // The alphabets that are not used in the actual transitions
    val extra = alphabet.indices - used

    // A helper function to generate LTSA process names.
    fun processName(i: Int): String {
      return if (i == 0) name else if (i in groups.keys) "${name}_$i" else "END"
    }

    var fsp = groups.map { entry ->
      val processes = entry.value.joinToString(" | ") { "${escaped[it.second]} -> ${processName(it.third)}" }
      "${processName(entry.key)} = ($processes)"
    }.joinToString(",\n")

    val add = extra.filter { it != tau }.map { escaped[it] }
    if (add.isNotEmpty() && unused) {
      fsp = "$fsp+{${add.joinToString(", ")}}"
    }
    if (tau in used) {
      fsp = "$fsp\\{_tau_}"
    }
    return "$fsp.\n"
  }

  fun minimize(): StateMachine {
    val fsp = this.buildFSP()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(fsp).doCompose().minimise()
    return StateMachine(composite.composition)
  }

  /**
   * Rename the events:
   * if e == "tau" then e' = "_tau_"
   * if e match abc.123 then e' = abc[123]
   */
  private fun escapeEvent(e: String): String {
    if (e == "tau")
      return "_tau_"
    val idx = e.lastIndexOf('.')
    val suffix = e.substring(idx + 1)
    if (suffix.toIntOrNull() != null) {
      return "${e.substring(0, idx)}[$suffix]"
    }
    return e
  }

  /**
   * Get all the states that have a path to the given final states.
   */
  fun getBackwardReachable(final: Set<Int>): Set<Int> {
    var reachable = final
    while (true) {
      val s = reachable union reachable.flatMap { transitions.prevStates(it) }
      if (s.size == reachable.size)
        return reachable
      reachable = s
    }
  }

  fun tauElmAndSubsetConstr(): Pair<StateMachine, List<Set<Int>>> {
    var hasTau = false
    val reachTable = Array(maxIndexOfState() + 1) { s ->
      Array(alphabet.size) { a ->
        val next = transitions.nextStates(s, a)
        if (a == tau && next.isNotEmpty())
          hasTau = true
        next
      }
    }
    // Calculate epsilon closure
    while (hasTau) {
      hasTau = false
      for (s in reachTable.indices) {
        val next = reachTable[s][tau] union reachTable[s][tau].flatMap {
          if (it != -1) reachTable[it][tau] else emptySet()
        }.toSet()
        if (next.size != reachTable[s][tau].size)
          hasTau = true
        reachTable[s][tau] = next
      }
    }

    // Do subset construct by using this reachability table
    // initial state of the DFA is {0} union its closure
    val dfaStates = mutableListOf(setOf(0) union reachTable[0][tau])
    val dfaTrans = mutableSetOf<Transition>()
    // create a queue for the new dfa states
    val q: Queue<Set<Int>> = LinkedList()
    q.addAll(dfaStates)
    while (q.isNotEmpty()) {
      val ss = q.poll()
      val i_s = dfaStates.indexOf(ss)
      for (a in alphabet.indices) {
        if (a == tau)
          continue

        var next = ss.flatMap { if (it != -1) reachTable[it][a] else emptySet() }.toSet()
        next = next union next.flatMap { if (it != -1) reachTable[it][tau] else emptySet() }.toSet()
        if (next.isEmpty())
          continue
        val i_n = if (next !in dfaStates) {
          dfaStates.add(next)
          q.add(next)
          dfaStates.size - 1
        } else {
          dfaStates.indexOf(next)
        }
        dfaTrans.add(Triple(i_s, a, i_n))
      }
    }
    return Pair(StateMachine(SimpleTransitions(dfaTrans), alphabet), dfaStates)
  }

  private fun maxIndexOfState(): Int {
    return transitions.allStates().max() ?: error("Cannot find the state with the max index number")
  }

  fun subsetConstruct(): Pair<StateMachine, List<Set<Int>>> {
    val dfaStates = mutableListOf(setOf(0))  // initial state of the DFA is {0}
    val dfaTrans = mutableSetOf<Transition>()
    // create a queue for the new dfa states
    val q: Queue<Set<Int>> = LinkedList()
    q.addAll(dfaStates)
    while (q.isNotEmpty()) {
      val s = q.poll()
      val i_s = dfaStates.indexOf(s)
      for (a in alphabet.indices) {
        val n = s.flatMap { transitions.nextStates(it, a) }.toSet()
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
    return Pair(StateMachine(SimpleTransitions(dfaTrans), alphabet), dfaStates)
  }

  fun pathToInit(): Map<Int, List<String>> {
    return pathToInitHelper { true }
  }

  fun pathToInit(stop: Int): List<String>? {
    val traces = pathToInitHelper { stop !in it }
    return traces[stop]
  }

  private fun pathToInitHelper(stop: (Map<Int, List<String>>) -> Boolean): Map<Int, List<String>> {
    val traces = mutableMapOf<Int, List<String>>(0 to emptyList())
    val visited = mutableListOf<Int>()

    val outTrans = transitions.outTrans()
    var search = mutableSetOf(0)
    while (search.isNotEmpty() && stop(traces)) {
      visited.addAll(search)

      val nextSearch = mutableSetOf<Int>()
      for (s in search) {
        val trans = (outTrans[s] ?: emptyList()).filter { it.third !in visited }
        for (t in trans) {
          traces[t.third] = traces[t.first]!! + alphabet[t.second]
        }
        nextSearch.addAll(trans.map { it.third })
      }
      search = nextSearch
    }
    return traces
  }

}