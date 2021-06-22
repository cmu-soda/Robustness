/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang, David Garlan, Eunsuk Kang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package edu.cmu.isr.robust.util

import edu.cmu.isr.robust.ltsa.*
import lts.CompositeState
import java.util.*

typealias Trace = List<String>

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

  /**
   * A map from alphabet string to its index in integer
   */
  private val alphabetMap: Map<String, Int>

  /**
   * The End state (not deadlock)
   */
  val endState: Set<Int>

  constructor(comp: CompositeState) {
    transitions = SimpleTransitions(comp)
    alphabet = comp.alphabetWithTau()
    tau = alphabet.indexOf("_tau_")
    alphabetMap = alphabet.mapIndexed { index, s -> s to index }.toMap()

    val endseq = comp.composition.javaClass.getDeclaredField("endseq")
    endseq.isAccessible = true
    endState = setOf(endseq.getInt(comp.composition))
  }

//  constructor(transitions: Transitions, alphabet: List<String>) {
//    this.transitions = transitions
//    this.alphabet = alphabet
//    this.tau = alphabet.indexOf("_tau_")
//    this.alphabetMap = alphabet.mapIndexed { index, s -> s to index }.toMap()
//    this.endState = emptySet()
//  }

  constructor(transitions: Transitions, alphabet: List<String>, endState: Set<Int>) {
    this.transitions = transitions
    this.alphabet = alphabet
    this.tau = alphabet.indexOf("_tau_")
    this.alphabetMap = alphabet.mapIndexed { index, s -> s to index }.toMap()
    this.endState = endState
  }

  fun alphabetIdx(a: String): Int {
    return alphabetMap[a]?: -1
  }

  /**
   * Return true if the state machine has the error state.
   */
  fun hasError(): Boolean {
    return -1 in transitions.inTrans()
  }

  fun isDeterministic(): Boolean {
    for (s in transitions.outTrans()) {
      for (a in s.value.groupBy { it.second }) {
        if (a.key == tau || a.value.size > 1)
          return false
      }
    }
    return true
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
      return when (i) {
        0 -> name
        -1 -> "ERROR"
        in groups.keys -> "${name}_$i"
        in endState -> "END"
        else -> "STOP"
      }
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
   * Return the minimized state machine. It converts the state machine to a FSP spec and use the built-in
   * minimization function in LTSA.
   */
  fun minimize(): StateMachine {
    val fsp = this.buildFSP()
    val composite = LTSACall.doCompile(fsp).doCompose().minimise()
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
    val dfaEndState = dfaStates.indices.filter { i -> endState.containsAll(dfaStates[i]) }
    return Pair(StateMachine(SimpleTransitions(dfaTrans), alphabet, dfaEndState.toSet()), dfaStates)
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
    val dfaEndState = dfaStates.indices.filter { i -> endState.containsAll(dfaStates[i]) }
    return Pair(StateMachine(SimpleTransitions(dfaTrans), alphabet, dfaEndState.toSet()), dfaStates)
  }

  /**
   * TODO: This function needs evaluation on how to handle end state
   */
  fun makeInputErrEnable(inputs: List<String>, outputs: List<String>): StateMachine {
    assert(alphabet.containsAll(inputs))
    assert(alphabet.containsAll(outputs))

    val newTrans = transitions.toMutableList()
    val indexOfStates = 0..maxIndexOfState()
    val outTrans = transitions.outTrans()
    val io = inputs + outputs
    val inputsIdx = inputs.map { alphabet.indexOf(it) }
    val ioIdx = io.map { alphabet.indexOf(it) }
    for (s in indexOfStates) {
      for (a in inputsIdx) {
        if (outTrans[s]?.find { it.second == a } == null) {
          if (outTrans[s]?.map { it.second }?.toSet()?.intersect(ioIdx)?.isNotEmpty() == true) {
//            for (s_ in indexOfStates) {
//              newTrans.add(Transition(s, a, s_))
//            }
            newTrans.add(Transition(s, a, -1))
          }
        }
      }
    }
    // TODO: An input enabled machine should have no end state?
    return StateMachine(SimpleTransitions(newTrans), alphabet, emptySet())
  }

  /**
   * Return a map where the key is a state s, and the value is the shortest path from the initial state s0 to
   * that state s.
   */
  fun pathFromInit(): Map<Int, Trace> {
    return pathFromInitHelper { false }
  }

  /**
   * Return a map where the key is a state s, and the value is the shortest path from the initial state s0 to
   * that state s. Stop the search when all the states in the stop set have been visited.
   */
  fun pathFromInit(stop: Set<Int>): Map<Int, Trace> {
    return pathFromInitHelper { it.keys.containsAll(stop) }
  }

  /**
   * Return a map where the key is a state s, and the value is the shortest path from the initial state s0 to
   * that state s. Stop the search when the shortest trace has been found for the stop state.
   */
  fun pathFromInit(stop: Int): Trace {
    return (pathFromInitHelper { stop in it })[stop]!!
  }

  /**
   * The helper function for pathFromInit, the search stops when the stop function evaluates to true.
   */
  private fun pathFromInitHelper(stop: (Map<Int, Trace>) -> Boolean): Map<Int, Trace> {
    val traces = mutableMapOf<Int, Trace>(0 to emptyList())
    val visited = mutableListOf<Int>()

    val outTrans = transitions.outTrans()
    var search = mutableSetOf(0)
    while (search.isNotEmpty() && !stop(traces)) {
      visited.addAll(search)

      val nextSearch = mutableSetOf<Int>()
      for (s in search) {
        for (t in outTrans[s] ?: emptyList()) {
          if (t.third !in visited && t.third !in nextSearch) {
            traces[t.third] = traces[t.first]!! + alphabet[t.second]
            nextSearch.add(t.third)
          }
        }
      }
      search = nextSearch
    }
    return traces
  }

  fun compose(sm: StateMachine): StateMachine {
    val q: Queue<Int> = LinkedList()
    val pair2state = mutableMapOf(Pair(0, 0) to 0)
    val state2Pair = mutableMapOf(0 to Pair(0, 0))
    var maxState = 0
    val newAlphabet = (this.alphabet.toSet() union sm.alphabet.toSet()).toList()
    val newTrans = mutableListOf<Transition>()
    val endState = mutableSetOf<Int>()

    fun addNewState(pair: Pair<Int, Int>): Int {
      if (pair.first == -1 || pair.second == -1)
        return -1
      if (pair !in pair2state) {
        maxState++
        pair2state[pair] = maxState
        state2Pair[maxState] = pair
        q.offer(maxState)
        return maxState
      }
      return pair2state[pair]!!
    }

    q.offer(0)
    while (q.isNotEmpty()) {
      var isEnd = true
      val s = q.poll()
      val pair = state2Pair[s]!!
      for (i in newAlphabet.indices) {
        val a = newAlphabet[i]
        if (a == "_tau_" || (a in this.alphabet && a !in sm.alphabet)) {
          for (s1 in this.transitions.nextStates(pair.first, this.alphabetIdx(a))) {
            val ss = addNewState(Pair(s1, pair.second))
            newTrans.add(Transition(s, i, ss))
            isEnd = false
          }
        }
        if (a == "_tau_" || (a !in this.alphabet && a in sm.alphabet)) {
          for (s2 in sm.transitions.nextStates(pair.second, sm.alphabetIdx(a))) {
            val ss = addNewState(Pair(pair.first, s2))
            newTrans.add(Transition(s, i, ss))
            isEnd = false
          }
        }
        if (a != "_tau_" && a in this.alphabet && a in sm.alphabet) {
          for (s1 in this.transitions.nextStates(pair.first, this.alphabetIdx(a))) {
            for (s2 in sm.transitions.nextStates(pair.second, sm.alphabetIdx(a))) {
              val ss = addNewState(Pair(s1, s2))
              newTrans.add(Transition(s, i, ss))
              isEnd = false
            }
          }
        }
      }
      if (isEnd && (pair.first in this.endState || pair.second in sm.endState))
        endState.add(s)
    }

    return StateMachine(SimpleTransitions(newTrans), newAlphabet, endState)
  }

  fun checkSafety(): Trace? {
    if (this.transitions.hasErrorState())
      return pathFromInit(-1)
    return null
  }

  fun checkDeadlock(): Trace? {
    val ends = this.transitions.findEndStates() - endState
    if (ends.isNotEmpty()) {
      val map = pathFromInitHelper { (it.keys intersect ends).isNotEmpty() }
      return map[(map.keys intersect ends).first()]
    }
    return null
  }

  /**
   * TODO: ignore unknown alphabets and tau
   */
  fun applyTrace(trace: Trace): List<Transition> {
    val trans = mutableListOf<Transition>()
    var s = 0

    for (a in trace.map { alphabetIdx(it) }.filter { it != -1 && it != tau }) {
      val t = transitions.outTrans()[s]!!.find { it.second == a }!!
      trans.add(t)
      s = t.third
    }
    return trans
  }

}