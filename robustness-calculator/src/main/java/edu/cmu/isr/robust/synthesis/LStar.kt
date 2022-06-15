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

package edu.cmu.isr.robust.synthesis

import edu.cmu.isr.robust.util.Trace
import java.lang.Exception

private typealias DFA = List<Triple<String, String, String>>

@Deprecated("This is a deprecated attempt using L* to robustify a system.")
class LStar(
  private val alphabet: List<String>,
  private val mqOracle: (Trace) -> Boolean,
  private val eqOracle: (String) -> Trace?
) {

  private val S = mutableSetOf("")
  private val E = mutableSetOf("")
  private val T = mutableMapOf("" to true)

  fun lstar(): String {
    val startTime = System.currentTimeMillis()
    while (true) {
      updateTwithQueries()
      while (true) {
        val sa = isClosed()
        if (sa.isEmpty())
          break
        S.addAll(sa)
        updateTwithQueries()
      }
      val hypothesis = buildConjecture()
      val spec = conjectureToFSP(hypothesis)
      val counterExample = eqOracle(spec)
      if (counterExample == null) {
        println("Total time: ${(System.currentTimeMillis() - startTime) / 1000.0}s")
        return spec
      } else {
//        println("Counterexample found: $counterExample")
        witnessOfCounterExample(hypothesis, counterExample)
      }
    }
  }

  private fun witnessOfCounterExample(hypothesis: DFA, counterExample: Trace) {
    val projected = counterExample.filter { it in alphabet }
    val size = projected.size
//    println("Projected counterexample: $projected")
    for (i in 0 until projected.size-1) {
      val si_1 = getStateAfterTrace(hypothesis, projected.subList(0, i))
      val si = getStateAfterTrace(hypothesis, projected.subList(0, i + 1))
      val qi_1 = concat(si_1, *projected.subList(i, size).toTypedArray())
      val qi = concat(si, *projected.subList(i + 1, size).toTypedArray())
      if (qi_1 == qi)
        continue
      if (membershipQuery(qi_1) != membershipQuery(qi)) {
        S.add(concat(si_1, projected[i]))
        E.add(concat(*projected.subList(i + 1, size).toTypedArray()))
//        println("By witness counter example, S = $S, E = $E")
        return
      }
    }
    println("Current hypothesis:")
    println(conjectureToFSP(hypothesis))
    throw NoRefinementException(counterExample)
  }

  private fun getStateAfterTrace(C: DFA, trace: Trace): String {
    var s = ""
    for (a in trace) {
      s = C.find { it.first == s && it.second == a }?.third ?: error("No such trace $trace in the hypothesis model.")
    }
    return s
  }

  private fun buildConjecture(): DFA {
    // Omit the error states
    val F = S.filter { T[it] == true }
    val delta = mutableSetOf<Triple<String, String, String>>()
    // Generate transitions of the conjecture automaton
    // The transition relation δ is defined as δ(s, a) = s' where \forall e \in E: T(sae) = T(s'e).
    for (s in F) {
      for (a in alphabet) {
        val s_ = S.find { s_ -> E.forall { e -> T[concat(s, a, e)] == T[concat(s_, e)] } }
        if (s_ in F) {
          delta.add(Triple(s, a, s_!!))
        }
      }
    }
//    println("Hypothesized state machine: $delta")
    return delta.toList()
  }

  private fun conjectureToFSP(hypothesis: DFA, name: String = "H"): String {
    // Final states of the conjecture automaton
    val F = hypothesis.map { it.first }.toSet()
    // Since F \subseteq S where S \subseteq Σ*, thus map F to other names to generate the FSP spec.
    val F_map = F.foldIndexed(mutableMapOf<String, String>()) { i, m, s ->
      m[s] = if (s == "") name else "$name$i"
      m
    }
    // Divide the FSP spec into several sub processes, like A = (a -> B), B = (b -> A).
    val sm = F_map.values.fold(mutableMapOf<String, MutableList<String>>()) { m, s ->
      m[s] = mutableListOf()
      m
    }
    for (t in hypothesis) {
      sm[F_map[t.first]]!!.add("${t.second} -> ${F_map[t.third]}")
    }
    // Build the FSP spec, C is the main process which must exist.
    val spec = StringBuilder("$name = (")
    spec.append(sm[name]!!.joinToString(" | "))
    spec.append(')')
    // Other sub-process
    val C_tmp = sm.filter { it.key != name }
    if (C_tmp.isNotEmpty()) {
      spec.append(",\n")
      val subs = C_tmp.map { "${it.key} = (${it.value.joinToString(" | ")})" }
      spec.append(subs.joinToString(",\n"))
    }
    // Add the alphabet
    spec.append(" + {${alphabet.joinToString(", ")}}")
    spec.append('.')
//    println("Constructed state machine:\n$spec")
    return spec.toString()
  }

  private fun isClosed(): Set<String> {
    val sa = mutableSetOf<String>()
    // (S, E, T) is closed when \forall s \in S, \forall a \in Σ, \exists s' \in S, \forall e \in E: T(sae) = T(s'e)
    S.forall { s ->
      alphabet.forall { a ->
        val re = S.exists { s_ -> E.forall { e -> T[concat(s, a, e)] == T[concat(s_, e)] } }
        // if (S, E, T) is not closed, add sa to S.
        if (!re) {
          sa.add(concat(s, a))
        }
        re
      }
    }
    if (sa.isEmpty()) {
//      println("(S, E, T) is closed.")
    } else {
//      println("(S, E, T) is not closed, add $sa to S.")
    }
    return sa
  }

  private fun updateTwithQueries() {
    // Update T by making membership queries on (S \cup S . Σ) . E
    val queries = (S union S.flatMap { s -> alphabet.map { a -> concat(s, a) } })
      .flatMap { s -> E.map { e -> concat(s, e) } }
    for (query in queries) {
      T[query] = membershipQuery(query)
    }
//    println("========== Updated T ==========")
//    println(T)
  }

  private fun membershipQuery(query: String): Boolean {
    if (query in T) {
      return T[query]!!
    }
    val splited = query.split(",")
    for (i in splited.indices) {
      val subQuery = splited.subList(0, i + 1).joinToString(",")
      if (subQuery in T && T[subQuery] == false) {
        return false
      }
    }
    return mqOracle(splited)
  }

  private fun concat(vararg words: String): String {
    return words.filter { it != "" }.joinToString(",")
  }

  private fun <T> Iterable<T>.forall(predicate: (T) -> Boolean): Boolean {
    for (x in this) {
      if (!predicate(x)) {
        return false
      }
    }
    return true
  }

  private fun <T> Iterable<T>.exists(predicate: (T) -> Boolean): Boolean {
    for (x in this) {
      if (predicate(x)) {
        return true
      }
    }
    return false
  }
}

class NoRefinementException(val trace: Trace) : Exception("Cannot refine the hypothesis from counterexample: $trace")