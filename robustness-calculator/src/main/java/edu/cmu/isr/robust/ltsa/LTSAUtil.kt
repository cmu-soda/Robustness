/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang
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

import lts.*
import java.util.*

class LTSACall {
  init {
    SymbolTable.init()
  }

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
 *
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
 *
 */
fun CompositeState.propertyCheck(): List<String>? {
  val ltsOutput = StringLTSOutput()
  this.analyse(ltsOutput)
  val out = ltsOutput.getText()
  return if (out.contains("""property.*violation""".toRegex())) {
    this.errorTrace.map { (it as String) }
  } else {
    null
  }
}

/**
 * @return The alphabet of all the processes without tau.
 */
fun CompositeState.alphabetSet(): Set<String> {
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
 * @return The alphabet list of the composed state machine including tau
 */
fun CompositeState.compositionAlphabet(): List<String> {
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
 * The composite state should be composed first.
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
 *
 */
private fun renameConsts(spec: String, prefix: String): String {
  val p = """(const|range)\s+([_\w]+)\s*=""".toRegex()
  var re = spec
  p.findAll(spec).forEach {
    val name = it.groupValues[2]
    re = re.replace(name, "${prefix}_$name")
  }
  return re
}

/**
 *
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
  val idx = e.lastIndexOf('.')
  val suffix = e.substring(idx + 1)
  if (suffix.toIntOrNull() != null) {
    return "${e.substring(0, idx)}[$suffix]"
  }
  return e
}

/**
 *
 */
fun buildTrace(t: List<String>, alphabet: Iterable<String>): String {
  return "TRACE = (${t.joinToString(" -> ")} -> ERROR)+{${alphabet.joinToString(",")}}."
}