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

import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder
import de.learnlib.api.oracle.EquivalenceOracle
import de.learnlib.api.oracle.MembershipOracle
import de.learnlib.api.query.DefaultQuery
import de.learnlib.api.query.Query
import de.learnlib.filter.cache.dfa.DFACacheOracle
import de.learnlib.util.Experiment
import de.learnlib.util.statistics.SimpleProfiler
import edu.cmu.isr.robust.ltsa.*
import net.automatalib.automata.fsa.DFA
import net.automatalib.words.Alphabet
import net.automatalib.words.Word
import net.automatalib.words.impl.Alphabets
import java.lang.StringBuilder

class LStarLearner(private val sys: String, private val dev: String, private val p: String) {

  private val alphabetSys: Set<String>
  private val nameSys: String
  private val nameDev: String
  private val nameP: String

  init {
    val sysComp = LTSACall.doCompile(sys)
    alphabetSys = sysComp.alphabetNoTau()
    nameSys = sysComp.getCompositeName()

    val devComp = LTSACall.doCompile(dev)
    nameDev = devComp.getCompositeName()

    val pComp = LTSACall.doCompile(p)
    nameP = pComp.getCompositeName()
  }

  fun synthesize(): String {
    val alphabet = Alphabets.fromCollection(alphabetSys)
    val mqOracle = DFACacheOracle.createDAGCacheOracle<String>(
      alphabet,
      LTSAMembershipQuery(alphabet, dev, nameDev, p, nameP)
    )
    val eqOracle = LTSAEQOracle(alphabet, dev, nameDev, p, nameP)
    val lstar = ClassicLStarDFABuilder<String>()
      .withAlphabet(alphabet)
      .withOracle(mqOracle)
      .create()
    val experiment = Experiment.DFAExperiment(lstar, eqOracle, alphabet)
    experiment.setProfile(true)
    experiment.setLogModels(true)
    experiment.run()

    val result = experiment.finalHypothesis
    println("================= Learning Statistics ================")
    println(SimpleProfiler.getResults())
    println(experiment.rounds.summary)
    println()

    println("================= Final hypothesis model ================")
    val spec = buildFSP(result as DFA<Any, String>, alphabet)
    println(spec)
    return spec
  }

  /**
   * Query || Dev |= P
   */
  private class LTSAMembershipQuery(
    private val alphabet: Alphabet<String>,
    private val devSpec: String,
    private val devName: String,
    private val pSpec: String,
    private val pName: String
  ) : MembershipOracle.DFAMembershipOracle<String> {

    override fun processQueries(queries: MutableCollection<out Query<String, Boolean>>?) {
      if (queries == null)
        return
      for (q in queries) {
        val queryTrace = if (q.input.isEmpty)
          "Q = STOP+{${alphabet.joinToString(", ")}}."
        else
          "Q = (${q.input.joinToString(" -> ")} -> STOP)+{${alphabet.joinToString(", ")}}."
        val answer = LTSACall.doCompile(
          "$queryTrace$devSpec\n$pSpec\n||C = (Q || $devName || $pName).", "C"
        ).doCompose().propertyCheck() == null
        q.answer(answer)
      }
    }

  }

  /**
   * Hypothesis || Dev |= P
   */
  private class LTSAEQOracle(
    private val alphabet: Alphabet<String>,
    private val devSpec: String,
    private val devName: String,
    private val pSpec: String,
    private val pName: String
  ) : EquivalenceOracle.DFAEquivalenceOracle<String> {

    override fun findCounterExample(
      hypothesis: DFA<*, String>?,
      inputs: MutableCollection<out String>?
    ): DefaultQuery<String, Boolean>? {
      if (hypothesis != null) {
        val spec = buildFSP(hypothesis as DFA<Any, String>, alphabet)
        val counterExample = LTSACall.doCompile(
          "$spec$devSpec$pSpec||C = (S0 || $devName || $pName).", "C"
        ).doCompose().propertyCheck()
        if (counterExample != null) {
          println("Counterexample found: $counterExample")
          return DefaultQuery(Word.fromList(counterExample.filter { it in alphabet }))
        }
      }
      return null
    }

  }
}

private fun buildFSP(hypothesis: DFA<Any, String>, alphabet: Alphabet<String>) : String {
  val graph = hypothesis.transitionGraphView(alphabet)
  val nodeNames = mutableMapOf<Any, String>()
  val nodes = graph.nodes.filter { hypothesis.isAccepting(it) }
  for ((i, node) in nodes.withIndex()) {
    nodeNames[node] = "S${i}"
  }
  val builder = StringBuilder()
  for (node in nodes) {
    builder.append("${nodeNames[node]} = (")
    builder.append(graph.outgoingEdges(node)
      .filter { it.transition in nodes }
      .joinToString(" | ") { "${it.input} -> ${nodeNames[it.transition]}" }
    )
    builder.append("),\n")
  }
  builder.delete(builder.length-2, builder.length)
  builder.append("+{${alphabet.joinToString(", ")}}.\n")
  return builder.toString()
}

fun main() {
  val sys = "SENDER = (input -> send -> getack -> SENDER).\n" +
      "RECEIVER = (rec -> output -> ack -> RECEIVER).\n" +
      "||ENV = (SENDER || RECEIVER)."

  val env = "CHANNEL = (in -> out -> CHANNEL).\n" +
      "||ENV = (trans:CHANNEL || recv:CHANNEL)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."

  val envLossy = "CHANNEL = (in -> TRANSIT\n" +
      "         | in -> lose -> CHANNEL),\n" +
      "TRANSIT = (out -> CHANNEL | out -> duplicate -> TRANSIT).\n" +
      "||ENV = (trans:CHANNEL || recv:CHANNEL)/{send/trans.in, rec/trans.out, ack/recv.in, getack/recv.out}."

  val p = "property P = (input -> output -> P)."

  val learner = LStarLearner(sys, envLossy, p)
  learner.synthesize()
}