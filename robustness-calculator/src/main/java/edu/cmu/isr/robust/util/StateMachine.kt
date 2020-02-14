package edu.cmu.isr.robust.util

import edu.cmu.isr.robust.ltsa.LTSACall
import edu.cmu.isr.robust.ltsa.doCompose
import edu.cmu.isr.robust.ltsa.escapeEvent
import edu.cmu.isr.robust.ltsa.minimise
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
        next.toMutableSet()
      }
    }
    // Calculate epsilon closure
    while (hasTau) {
      hasTau = false
      for (s in reachTable.indices) {
        val size = reachTable[s][tau].size
        for (i in reachTable[s][tau].toSet()) {
          if (i != -1)
            reachTable[s][tau].addAll(reachTable[i][tau])
        }
        if (reachTable[s][tau].size != size)
          hasTau = true
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

        val next = mutableSetOf<Int>()
        for (i in ss) {
          if (i != -1)
            next.addAll(reachTable[i][a])
        }
        for (i in next.toSet()) {
          if (i != -1)
            next.addAll(reachTable[i][tau])
        }
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

  fun pathFromInit(): Map<Int, List<String>> {
    return pathFromInitHelper { false }
  }

  fun pathFromInit(stop: Set<Int>): Map<Int, List<String>> {
    return pathFromInitHelper { it.keys.containsAll(stop) }
  }

  fun pathFromInit(stop: Int): List<String> {
    return (pathFromInitHelper { stop in it })[stop]!!
  }

  private fun pathFromInitHelper(stop: (Map<Int, List<String>>) -> Boolean): Map<Int, List<String>> {
    val traces = mutableMapOf<Int, List<String>>(0 to emptyList())
    val visited = mutableListOf<Int>()

    val outTrans = transitions.outTrans()
    var search = mutableSetOf(0)
    while (search.isNotEmpty() && !stop(traces)) {
      visited.addAll(search)

      val nextSearch = mutableSetOf<Int>()
      for (s in search) {
        for (t in outTrans[s] ?: emptyList()) {
          if (t.third !in visited) {
            traces[t.third] = traces[t.first]!! + alphabet[t.second]
            nextSearch.add(t.third)
          }
        }
      }
      search = nextSearch
    }
    return traces
  }

}