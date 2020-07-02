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

package edu.cmu.isr.robust.cal

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.xenomachina.argparser.*
import edu.cmu.isr.robust.eofm.EOFMS
import edu.cmu.isr.robust.eofm.parseEOFMS
import java.io.File
import java.lang.IllegalArgumentException

enum class Mode { COMPUTE, COMPARE, UNSAFE }

/**
 * This private class defines the command line options.
 * TODO: Add feature to do tau elimination and subset construction
 * TODO: Add feature to just compute the weakest assumption
 * TODO: Add feature to just output the EOFM translation and EOFM concise translation model
 * TODO: Optimize the error message
 */
private class MyArgs(parser: ArgParser) {
  val verbose by parser.flagging("-v", "--verbose", help = "enable verbose mode")
  val mode by parser.mapping(
      "--compute" to Mode.COMPUTE,
      "--compare" to Mode.COMPARE,
      "--unsafe" to Mode.UNSAFE,
      help = "operation mode"
  )
  val outputFile by parser.storing("-o","--output", help = "save the results in a JSON file").default("")
  val files by parser.positionalList("FILES", help = "system description files in JSON")
}

/**
 * The ConfigJson and EOFMConfigJson class define the structure of the JSON configuration file.
 */
private data class ConfigJson(
    @JsonProperty
    val mode: String,
    @JsonProperty
    val sys: String,
    @JsonProperty
    val env: String,
    @JsonProperty
    val prop: String,
    @JsonProperty
    val deviation: String?,
    @JsonProperty
    val eofm: EOFMConfigJson?
)

private data class EOFMConfigJson(
    @JsonProperty
    val initialValues: Map<String, String>,
    @JsonProperty
    val world: List<String>,
    @JsonProperty
    val relabels: Map<String, String>
)

/**
 * The ResultJson and RepTraceJson class define the structure of the output JSON file for holding the representative
 * traces and their explanations.
 */
private data class ResultJson(
    @JsonProperty
    val mode: String,
    @JsonProperty
    val traces: List<RepTraceJson>
)

private data class RepTraceJson(
    @JsonProperty
    val trace: String,
    @JsonProperty
    val explanation: String
)

fun main(args: Array<String>): Unit = mainBody {
  val formatter = DefaultHelpFormatter(prologue =
  """
|This program calculates the behavioral robustness of a system against a base environment E
|and a safety property P. Also, it takes a deviation model D to generate explanations for the
|system robustness. In addition, it can compare the robustness of two systems or a system under
|different properties.
|""".trimMargin())
  ArgParser(args, helpFormatter = formatter).parseInto(::MyArgs).run {
    val resultJson = when (mode) {
      Mode.COMPUTE -> {
        assert(files.size == 1)
        if (files.size != 1)
          throw IllegalArgumentException("Need one config file for computing robustness")
        val configFile = files[0]
        val config = jacksonObjectMapper().readValue<ConfigJson>(File(configFile).readText())
        val cal = createCalculator(config, verbose)
        val result = cal.computeRobustness()
        ResultJson(
            mode = "compute",
            traces = result.map { RepTraceJson(it.first.joinToString(), (it.second?:emptyList()).joinToString()) }
        )
      }
      Mode.COMPARE -> {
        if (files.size != 2)
          throw IllegalArgumentException("Need two config files for comparing robustness")
        val config1 = jacksonObjectMapper().readValue<ConfigJson>(File(files[0]).readText())
        val config2 = jacksonObjectMapper().readValue<ConfigJson>(File(files[1]).readText())
        val cal1 = createCalculator(config1, verbose)
        val cal2 = createCalculator(config2, verbose)
        cal1.nameOfWA = "WA1"
        cal2.nameOfWA = "WA2"
        val result = cal1.robustnessComparedTo(cal2.getWA(), "WA2")
        ResultJson(
            mode = "compare",
            traces = result.map { RepTraceJson(it.joinToString(), "") }
        )
      }
      Mode.UNSAFE -> {
        assert(files.size == 1)
        if (files.size != 1)
          throw IllegalArgumentException("Need one config file for computing unsafe behavior")
        val configFile = files[0]
        val config = jacksonObjectMapper().readValue<ConfigJson>(File(configFile).readText())
        val cal = createCalculator(config, verbose)
        ResultJson(
            mode = "unsafe",
            traces = cal.computeUnsafeBeh().map { RepTraceJson(it.joinToString(), "") }
        )
      }
    }
    // Write to JSON file it the output file if specified
    if (outputFile != "") {
      jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(outputFile), resultJson)
    }
  }
}

/**
 * A helper function to create the corresponding AbstractRobustCal based on the JSON config file.
 */
private fun createCalculator(config: ConfigJson, verbose: Boolean): AbstractRobustCal {
  val sys = File(config.sys).readText()
  val env = File(config.env).readText()
  val p = File(config.prop).readText()
  return when (config.mode) {
    "fsp" -> {
      if (config.deviation == null)
        throw IllegalArgumentException("Need to provide deviation model in fsp mode")
      val deviation = File(config.deviation).readText()
      FSPRobustCal(sys, env, p, deviation, verbose)
    }
    "eofm" -> {
      if (config.eofm == null)
        throw IllegalArgumentException("Need to provide eofm config in eofm mode")
      val eofm: EOFMS = parseEOFMS(env)
      EOFMRobustCal.create(sys, p, eofm, config.eofm.initialValues, config.eofm.world,
          config.eofm.relabels, verbose)
    }
    else -> throw IllegalArgumentException("Unidentified mode: '${config.mode}'")
  }
}

