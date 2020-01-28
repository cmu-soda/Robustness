package edu.cmu.isr.ltsa.util

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.minimise
import lts.CompactState
import lts.EventState
import java.util.*

typealias Transitions = Set<Triple<Int, Int, Int>>

class StateMachine {
  /**
   *
   */
  val transitions: Transitions
  /**
   * The array of alphabets which contains tau transition.
   */
  val alphabet: Array<String>
  val tau: Int

  constructor(m: CompactState) {
    val trans = mutableSetOf<Triple<Int, Int, Int>>()
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
    tau = alphabet.indexOf("tau")
  }

  constructor(transitions: Transitions, alphabet: Array<String>) {
    this.transitions = transitions
    this.alphabet = alphabet
    this.tau = this.alphabet.indexOf("tau")
  }

  override fun toString(): String {
    return transitions.map { Triple(it.first, this.alphabet[it.second], it.third) }.joinToString("\n")
  }

  fun buildFSP(name: String = "A"): String {
    if (transitions.isEmpty()) {
      return "$name = END."
    }

    val escaped = alphabet.map(::escapeEvent)
    val groups = transitions.groupBy { it.first }
    val used = transitions.map { it.second }.toSet()
    val extra = alphabet.indices - used

    fun processName(i: Int): String {
      return if (i == 0) name else if (i in groups.keys) "${name}_$i" else "END"
    }

    var fsp = groups.map { entry ->
      val processes = entry.value.joinToString(" | ") { "${escaped[it.second]} -> ${processName(it.third)}" }
      "${processName(entry.key)} = ($processes)"
    }.joinToString(",\n")

    val add = extra.filter { it != tau }.map { escaped[it] }
    if (add.isNotEmpty()) {
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

  fun getReachable(initial: Set<Int>): Set<Int> {
    var reachable = initial
    while (true) {
      val s = reachable union transitions.filter { it.third in reachable }.map { it.first }
      if (s.size == reachable.size)
        return reachable
      reachable = s
    }
  }

  fun tauElmAndSubsetConstr(): Pair<StateMachine, List<Set<Int>>> {
    var hasTau = false
    val reachTable = Array(maxNumOfState() + 1) { s ->
      Array(alphabet.size) { a ->
        val next = nextState(s, a)
        if (a == tau && next.isNotEmpty())
          hasTau = true
        next
      }
    }
    // Calculate epsilon closure
    while (hasTau) {
      hasTau = false
      for (s in reachTable.indices) {
        val next = reachTable[s][tau] union reachTable[s][tau].flatMap { reachTable[it][tau] }.toSet()
        if (next.size != reachTable[s][tau].size)
          hasTau = true
        reachTable[s][tau] = next
      }
    }

    // Do subset construct by using this reachability table
    // initial state of the DFA is {0} union its closure
    val dfaStates = mutableListOf(setOf(0) union reachTable[0][tau])
    val dfaTrans = mutableSetOf<Triple<Int, Int, Int>>()
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
    return Pair(StateMachine(dfaTrans, alphabet), dfaStates)
  }

  fun maxNumOfState(): Int {
    val states = transitions.map { it.first }
    return states.max() ?: error("Cannot find the state with the max index number")
  }

  fun tauElimination(): StateMachine {
    val ts = transitions.toMutableSet()
    while (true) {
      val t = ts.find { it.second == tau } ?: break
      ts.remove(t)
      // FIXME
      ts.addAll(ts.filter { it.first == t.third }.map { it.copy(first = t.first) })
    }
    return StateMachine(ts, alphabet)
  }

  fun subsetConstruct(): Pair<StateMachine, List<Set<Int>>> {
    val dfaStates = mutableListOf(setOf(0))  // initial state of the DFA is {0}
    val dfaTrans = mutableSetOf<Triple<Int, Int, Int>>()
    // create a queue for the new dfa states
    val q: Queue<Set<Int>> = LinkedList()
    q.addAll(dfaStates)
    while (q.isNotEmpty()) {
      val s = q.poll()
      val i_s = dfaStates.indexOf(s)
      for (a in alphabet.indices) {
        val n = s.flatMap { nextState(it, a) }.toSet()
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

  fun nextState(s: Int, a: Int): Set<Int> {
    return transitions.filter { it.first == s && it.second == a }.map { it.third }.toSet()
  }

  fun pathToInit(): Map<Int, List<Triple<Int, Int, Int>>> {
    val traces = mutableMapOf<Int, List<Triple<Int, Int, Int>>>(0 to emptyList())
    val visited = mutableListOf<Int>()

    var search = mutableSetOf(0)
    while (search.isNotEmpty()) {
      visited.addAll(search)

      val nextSearch = mutableSetOf<Int>()
      for (s in search) {
        val trans = this.transitions.filter { it.first == s && it.third !in visited }
        for (t in trans) {
          traces[t.third] = traces[t.first]!! + t
        }
        nextSearch.addAll(trans.map { it.third })
      }
      search = nextSearch
    }
    return traces
  }

}