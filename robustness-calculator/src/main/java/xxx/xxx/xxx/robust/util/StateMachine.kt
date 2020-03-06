package xxx.xxx.xxx.robust.util

import xxx.xxx.xxx.robust.ltsa.*
import lts.CompositeState
import java.util.*

class StateMachine {
  /**
   * The transitions of this state machine. States and labels are all represented in integers.
   */
  val transitions: Transitions

  /**
   * The array of alphabets which contains tau event.
   */
  val alphabet: List<String>

  /**
   * The index number which represents the tau event.
   */
  val tau: Int

  constructor(comp: CompositeState) {
    transitions = SimpleTransitions(comp)
    alphabet = comp.compositionAlphabet()
    tau = alphabet.indexOf("_tau_")
  }

  constructor(transitions: Transitions, alphabet: List<String>) {
    this.transitions = transitions
    this.alphabet = alphabet
    this.tau = alphabet.indexOf("_tau_") // will be escaped to _tau_
  }

  /**
   * Return true if the state machine has the error state.
   */
  fun hasError(): Boolean {
    return -1 in transitions.inTrans()
  }

  /**
   * Convert the state machine into a FSP spec
   */
  fun buildFSP(name: String = "A", unused: Boolean = true): String {
    if (transitions.isEmpty()) {
      return "$name = END."
    }

    // Escaped event names
    val groups = transitions.outTrans()
    val used = transitions.allEvents()
    // The alphabets that are not used in the actual transitions
    val extra = alphabet.indices - used

    // A helper function to generate LTSA process names.
    fun processName(i: Int): String {
      return if (i == 0) name else if (i in groups.keys) "${name}_$i" else "END"
    }

    var fsp = groups.map { entry ->
      val processes = entry.value.joinToString(" | ") { "${alphabet[it.second]} -> ${processName(it.third)}" }
      "${processName(entry.key)} = ($processes)"
    }.joinToString(",\n")

    val add = extra.filter { it != tau }.map { alphabet[it] }
    if (add.isNotEmpty() && unused) {
      fsp = "$fsp+{${add.joinToString(", ")}}"
    }
    if (tau in used) {
      fsp = "$fsp\\{_tau_}"
    }
    return "$fsp.\n"
  }

  /**
   * Convert the state machine to a FSP spec and use the built-in minimization function in LTSA.
   */
  fun minimize(): StateMachine {
    val fsp = this.buildFSP()
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(fsp).doCompose().minimise()
    return StateMachine(composite)
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

  /**
   * Perform tau elimination and subset construction.
   */
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

  /**
   * Return the max index of the states of this state machine.
   */
  fun maxIndexOfState(): Int {
    return transitions.allStates().max() ?: error("Cannot find the state with the max index number")
  }

  /**
   * Perform subset construction to convert NFA to DFA
   */
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

  /**
   * Return a map where the key is the state, and the value is the shortest path from the initial state to the state.
   */
  fun pathFromInit(): Map<Int, List<String>> {
    return pathFromInitHelper { false }
  }

  fun pathFromInit(stop: Set<Int>): Map<Int, List<String>> {
    return pathFromInitHelper { it.keys.containsAll(stop) }
  }

  fun pathFromInit(stop: Int): List<String> {
    return (pathFromInitHelper { stop in it })[stop]!!
  }

  /**
   * The helper function for pathFromInit, the search stops when the stop function evaluates to true.
   */
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