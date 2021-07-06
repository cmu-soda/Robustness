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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.switch
import edu.cmu.isr.robust.util.SimpleTransitions
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition
import java.io.File
import java.util.*

class LTSAHelper : CliktCommand(name = "LTSAHelper", help = "Provides useful functions for LTSA.") {
  override fun run() { }
}

class Convert : CliktCommand(help = "Convert between FSP files and JSON") {

  enum class Type { LTS, JSON }

  val type by option(help = "File type of the input file").switch("--lts" to Type.LTS, "--json" to Type.JSON)
  val name by option("--name", "-n", help = "The name of the process to compose. Only works for LTS mode.")
  val file by argument(help = "Input file")

  override fun run() {
    when (type) {
      Type.LTS -> lts2json()
      Type.JSON -> json2lts()
      else -> throw PrintMessage("Please specify the file type.")
    }
  }

  private fun json2lts() {
    val obj = jacksonObjectMapper().readValue<StateMachineJson>(File(file).readText())
    val trans = obj.transitions.map { Transition(it[0], it[1], it[2]) }
    val m = StateMachine(SimpleTransitions(trans), obj.alphabet, emptySet())
    println(m.buildFSP(obj.process))
  }

  private fun lts2json() {
    val f = File(file)
    val spec = f.readText()
    val comp = name?.let { LTSACall.doCompile(spec, it).doCompose() } ?: LTSACall.doCompile(spec).doCompose()
    val m = StateMachine(comp)

    val reachable = mutableListOf<Array<Int>>()
    val q: Queue<Int> = LinkedList()
    val visited = mutableSetOf<Int>()
    val outTrans = m.transitions.outTrans()

    q.offer(0)
    while (q.isNotEmpty()) {
      val s = q.poll()
      if (s in visited)
        continue
      visited.add(s)
      outTrans[s]?.forEach {
        reachable.add(arrayOf(it.first, it.second, it.third))
        q.offer(it.third)
      }
    }

    val json = StateMachineJson(
      filename = f.absolutePath,
      process = comp.name,
      alphabet = m.alphabet,
      transitions = reachable
    )
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, json)
  }
}

private data class StateMachineJson(
  @JsonProperty
  val filename: String,
  @JsonProperty
  val process: String,
  @JsonProperty
  val alphabet: List<String>,
  @JsonProperty
  val transitions: List<Array<Int>>
)

class Abstract : CliktCommand(help = "Abstract a LTS by a given set of alphabets") {

  val model by option("--model", "-m", help = "The model file").required()
  val name by option("--name", "-n", help = "The name of the process to compose.")
  val format by option("--format", "-f", help = "Output format, either 'lts' or 'json'").default("lts")
  val events by argument(help = "The list of events that need to be abstracted").multiple()

  override fun run() {
    if (events.isEmpty())
      throw PrintMessage("Should provide at least one event to be abstracted.")

    val f = File(model)
    val spec = f.readText()
    val comp = name?.let { LTSACall.doCompile(spec, it).doCompose() } ?: LTSACall.doCompile(spec).doCompose()
    val m = StateMachine(comp)

    val abs = StringBuilder()
    abs.append(m.buildFSP("A"))
    abs.setLength(abs.length - 2) // remove ".\n"
    abs.append("\\{${events.joinToString(", ")}}.")
    abs.append("\n\n")

    abs.append(m.buildFSP("B"))
    abs.setLength(abs.length - 2) // remove ".\n"
    abs.append("@{${events.joinToString(", ")}}.")
    abs.append("\n\n")

    abs.append("||ABS = (A || B).")

    val absComp = LTSACall.doCompile(abs.toString(), "ABS").doCompose()
    val absModel = StateMachine(absComp)
    val (absDFA, _) = absModel.tauElmAndSubsetConstr()

    when (format) {
      "lts" -> println(absDFA.buildFSP("ABS"))
      "json" -> {
        val json = StateMachineJson(
          filename = "",
          process = "ABS",
          alphabet = absDFA.alphabet,
          transitions = absDFA.transitions.map { arrayOf(it.first, it.second, it.third) }
        )
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, json)
      }
      else -> throw PrintMessage("Unknown output format.")
    }
  }

}

fun main(args: Array<String>) = LTSAHelper().subcommands(Convert(), Abstract()).main(args)
