package edu.cmu.isr.ltsa

import lts.*

class LTSACall {
  init {
    SymbolTable.init()
  }

  fun doCompile(fsp: String, compositeName: String = "DEFAULT"): CompositeState {
//    println("========== Compile fsp spec ==========")
//    println(fsp)

    val ltsInput = StringLTSInput(fsp)
    val ltsOutput = StringLTSOutput()
    val compiler = LTSCompiler(ltsInput, ltsOutput, System.getProperty("user.dir"))
    try {
      val compositeState = compiler.compile(compositeName)
//            println(ltsOutput.getText())
      return compositeState
    } catch (e: LTSException) {
      println(e)
      throw Exception("Failed to compile the fsp source string of machine '${compositeName}'.")
    }
  }

  fun doCompose(compositeState: CompositeState) {
    val ltsOutput = StringLTSOutput()
    try {
      compositeState.compose(ltsOutput)
//            println(ltsOutput.getText())
    } catch (e: LTSException) {
      println(e)
      throw Exception("Failed to compose machine '${compositeState.name}'.")
    }
  }

  fun propertyCheck(compositeState: CompositeState): List<String>? {
    val ltsOutput = StringLTSOutput()
    compositeState.analyse(ltsOutput)
    val out = ltsOutput.getText()
//    println(out)
    return if (out.contains("""property.*violation""".toRegex())) {
      compositeState.errorTrace.map { (it as String) }
    } else {
      null
    }
  }

  fun getAllAlphabet(compositeState: CompositeState): MutableSet<String> {
    val alphabet = compositeState.machines
      .flatMap { (it as CompactState).alphabet.toList() }
      .fold(mutableSetOf<String>()) { s, a ->
        s.add(a)
        s
      }
    alphabet.remove("tau")
    return alphabet
  }

  fun minimise(compositeState: CompositeState) {
    val ltsOutput = StringLTSOutput()
    compositeState.minimise(ltsOutput)
  }

  fun determinise(compositeState: CompositeState) {
    val ltsOutput = StringLTSOutput()
    compositeState.determinise(ltsOutput)
  }

  fun getCompositeName(compositeState: CompositeState): String {
    doCompose(compositeState)
    return if (compositeState.name != "DEFAULT") {
      compositeState.name
    } else {
      (compositeState.machines[0] as CompactState).name
    }
  }
}