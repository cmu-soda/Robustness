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

package edu.cmu.isr.robust.ltsa

import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Trace
import lts.*
import java.util.*

object LTSACall {
  init {
    SymbolTable.init()
  }

  /**
   * Compile a given FSP spec. This should behave the same as the compile button in the LTSA tool.
   * @param compositeName The name of the targeting composition process. This will affect which process
   * would be composed when calling the doCompose() function. By default, the name is "DEFAULT" which will
   * create an implicit process named DEFAULT which is the composition of all the processes in the spec.
   */
  fun doCompile(fsp: String, compositeName: String = "DEFAULT"): CompositeState {
    val ltsInput = StringLTSInput(fsp)
    val ltsOutput = StringLTSOutput()
    val compiler = LTSCompiler(ltsInput, ltsOutput, System.getProperty("user.dir"))
    try {
      return compiler.compile(compositeName)
    } catch (e: LTSException) {
      println(e)
      throw Exception("Failed to compile the fsp source string of machine '${compositeName}'.")
    }
  }

  /**
   * Get the actions in a given menu from the last compilation
   */
  fun menuActions(name: String): List<String> {
    val def = (MenuDefinition.definitions[name] ?: error("No such menu named '$name'")) as MenuDefinition
    val actionField = MenuDefinition::class.java.getDeclaredField("actions")
    actionField.isAccessible = true
    val actions = actionField.get(def)
    val actionVectorField = actions.javaClass.getDeclaredField("actions")
    actionVectorField.isAccessible = true
    return (actionVectorField.get(actions) as Vector<String>).toList()
  }
}

/**
 * This behaves the same as the Compose option in the LTSA tool.
 */
fun CompositeState.doCompose(): CompositeState {
  val ltsOutput = StringLTSOutput()
  try {
    this.compose(ltsOutput)
    return this
  } catch (e: LTSException) {
    println(e)
    throw Exception("Failed to compose machine '${this.name}'.")
  }
}

/**
 * This behaves the same as the Check -> Safety option in the LTSA tool.
 * @return A list of actions leading to a property violation. Note: the property check will only return a trace
 * to deadlock when deadlock and property violation both exist in the system. In this case, this function returns
 * null, however, it does indicate that the system does not violate the property.
 */
fun CompositeState.propertyCheck(): Trace? {
//  val ltsOutput = StringLTSOutput()
//  this.analyse(ltsOutput)
//  val out = ltsOutput.getText()
//  return if (out.contains("""property.*violation""".toRegex())) {
//    this.errorTrace.map { (it as String) }
//  } else {
//    null
//  }
  val sm = StateMachine(this)
  if (sm.transitions.hasErrorState()) {
    return sm.pathFromInit(-1)
  }
  return null
}

/**
 * FIXME: The LTSA tool will return either deadlock or safety property violation when both errors exist,
 * depending on which one has a shorter error trace.
 *
 * This behaves the same as the Check -> Safety option in the LTSA tool.
 * @return A list of actions leading to a deadlock.
 */
fun CompositeState.deadlockCheck(): Trace? {
  val ltsOutput = StringLTSOutput()
  this.analyse(ltsOutput)
  val out = ltsOutput.getText()
  return if (out.contains("""DEADLOCK""".toRegex())) {
    this.errorTrace.map { (it as String) }
  } else {
    null
  }
}

/**
 * @return The alphabet of all the processes without tau. The alphabet has also been escaped.
 */
fun CompositeState.alphabetNoTau(): Set<String> {
  val alphabet = this.machines
      .flatMap { (it as CompactState).alphabet.toList() }
      .fold(mutableSetOf<String>()) { s, a ->
        s.add(a)
        s
      }
  alphabet.remove("tau")
  return alphabet.map(::escapeEvent).toSet()
}

/**
 * @return The alphabet list of the composed state machine including tau. The alphabet has also been escaped.
 */
fun CompositeState.alphabetWithTau(): List<String> {
  return this.composition.alphabet.map(::escapeEvent)
}

/**
 * The composite state should be composed first.
 */
fun CompositeState.minimise(): CompositeState {
  val ltsOutput = StringLTSOutput()
  this.minimise(ltsOutput)
  return this
}

/**
 * Use the builtin determinise function in LTSA.
 * @requires The composite state should be composed first.
 */
fun CompositeState.determinise(): CompositeState {
  val ltsOutput = StringLTSOutput()
  this.determinise(ltsOutput)
  return this
}

/**
 *
 */
fun CompositeState.getCompositeName(): String {
  doCompose()
  return if (this.name != "DEFAULT") {
    this.name
  } else {
    (this.machines[0] as CompactState).name
  }
}

/**
 * Simply concat multiple specs.
 * TODO: In ideal, when combining multiple specs, we should also fix the conflicting constant names.
 */
fun combineSpecs(vararg specs: String): String {
  return specs.joinToString("\n")
}

/**
 * Rename the events:
 * if e == "tau" then e' = "_tau_"
 * if e match abc.123 then e' = abc[123]
 */
private fun escapeEvent(e: String): String {
  if (e == "tau")
    return "_tau_"

  var escaped = e
  var lastIdx = e.length
  while (true) {
    val idx = escaped.substring(0, lastIdx).lastIndexOf('.')
    if (idx == -1)
      return escaped
    val suffix = escaped.substring(idx + 1, lastIdx)
    if (suffix.toIntOrNull() != null)
      escaped = "${escaped.substring(0, idx)}[$suffix]${escaped.substring(lastIdx)}"
    lastIdx = idx
  }
}

/**
 * Build a trace to a process: for a trace <a, b, c>, this function returns a spec:
 * TRACE = (a -> b -> c -> ERROR). It uses ERROR state to indicate the end of the state.
 */
fun buildTrace(t: Trace, alphabet: Iterable<String>): String {
  return "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${alphabet.joinToString(",")}}."
}