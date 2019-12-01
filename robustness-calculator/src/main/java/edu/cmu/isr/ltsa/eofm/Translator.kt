package edu.cmu.isr.ltsa.eofm

import java.lang.StringBuilder

fun main(args: Array<String>) {
  val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/pca.xml"))
  val builder = StringBuilder()
  val translator = Translator(pca)

  translator.translate(builder)
  println(builder.toString())
}

class Translator(eofms: EOFMS) {

  private val consts: List<Constant> = eofms.constants
  private val userDefinedTypes: List<UserDefineType> = eofms.userDefinedTypes
  private val actions: Map<String, HumanAction> = eofms.humanOperators.flatMap { it.humanActions }.map { it.name to it }.toMap()
  private val inputVariables: List<InputVariable> = eofms.humanOperators.flatMap { it.inputVariables }
  private val topLevelActivities: List<Activity> = eofms.humanOperators.flatMap { it.eofms.map { eofm ->  eofm.activity } }
  private val activities: MutableMap<String, Activity> = mutableMapOf()

  init {
    fun recursive(activity: Activity) {
      activities.put(activity.name, activity)
      for (it in activity.decomposition.subActivities) {
        if (it is Activity) {
          recursive(it)
        }
      }
    }

    for (humanOperator in eofms.humanOperators) {
      for (eofm in humanOperator.eofms) {
        recursive(eofm.activity)
      }
    }
  }

  fun translate(builder: StringBuilder) {
    builder.append("const Ready = 0\n")
    builder.append("const Executing = 1\n")
    builder.append("const Done = 2\n")
    builder.append("range ActState = Ready..Done\n\n")
    for (it in consts)
      builder.appendConstant(it)
    builder.append('\n')
    for (it in userDefinedTypes)
      builder.appendUserDefinedType(it)
    for (i in topLevelActivities.indices)
      ActivityTranslator(topLevelActivities[i], inputVariables, activities, postfix = "$i").translate(builder)
  }

  private fun StringBuilder.appendConstant(constant: Constant) {
    this.append("const ${constant.name} = ${constant.value}\n")
  }

  private fun StringBuilder.appendUserDefinedType(userDefineType: UserDefineType) {
    val value = userDefineType.value
    val consts = value.substring(1 until value.length-1).split(""",\s*""".toRegex())
    for (i in consts.indices)
      this.append("const ${consts[i]} = $i\n")
    this.append("range ${userDefineType.name} = ${consts[0]}..${consts[consts.size-1]}")
    this.append("\n\n")
  }

//  fun appendAction(builder: java.lang.StringBuilder, action: Action, parent: String, preSibling: String? = null,
//                   siblings: List<String> = emptyList(), postfix: String = "") {
//    val name = "${action.humanAction?.capitalize()}$postfix"
//    val eventPrefix = "${action.humanAction}$postfix"
//
//    // A list of process variables for later use
//    val processVars = listOf("self", parent)
//
//    // Append process header
//    builder.append("${name}[self:ActState][$parent:ActState]")
//    builder.append(" = (\n")
//
//    // From Ready to Executing
//    builder.append("\t\t// Ready to Executing\n")
//    builder.append("\t\twhen (self == Ready")
//    appendStartCondition(builder, op, parent, preSibling, siblings)
//    appendPrecondition(builder, activity.preConditions)
//    appendCompletionCondition(builder, activity.completionConditions, false)
//    builder.append(")\n\t\t\tset_$eventPrefix[Executing] -> $name")
//    appendProcessVars(builder, processVars, "self" to "Executing")
//  }

}

private class ActivityTranslator(
    val act: Activity, val inputVariables: List<InputVariable>, val activities: Map<String, Activity>,
    val parent: String? = null, val preSibling: String? = null, val siblings: List<String> = emptyList(),
    val postfix: String = ""
) {
  private val processName = "${act.name.capitalize()}$postfix"
  private val eventName = "${act.name}$postfix"

  // Get all the input variables related to the activity
  private val inputs = inputVariables.filter { i ->
    act.preConditions.find { it.indexOf(i.name) != -1 } != null ||
        act.completionConditions.find { it.indexOf(i.name) != -1 } != null ||
        act.repeatConditions.find { it.indexOf(i.name) != -1 } != null
  }
  // Get the decomposition operator
  private val op = act.decomposition.operator

  // Get all the sub-activities
  private val subacts = act.decomposition.subActivities.map {
    when (it) {
      is Activity -> it.name
      is ActivityLink -> activities[it.link]?.name ?: throw IllegalArgumentException("No activity named ${it.link} found")
      is Action -> it.humanAction
      else -> throw IllegalArgumentException("Unknown type of activity/action in decomposition.")
    }
  }.mapIndexed { idx, s -> "$s$postfix$idx" }

  private var processVars = emptyList<String>()

  fun translate(builder: StringBuilder) {
    // Append process header, and set the list of process variables for later user
    builder.appendProcessHeader()
    // From Ready to Executing
    builder.appendReadyToExecuting()
    // From Ready to Done
    builder.appendReadyToDone()
    // From Executing to Executing
    builder.appendExecutingToExecuting()
    // From Executing to Done
    builder.appendExecutingToDone()
    // From Done to Ready (reset)
    builder.appendDoneToReady()

    // Synchronize on variable value changes
    for (v in inputs) {
      builder.append("\t\t// input ${v.name}\n")
      builder.append("\t|\tset_${v.name}[i:${v.userDefinedType}] -> $processName")
      builder.appendProcessVars(v.name to "i")
    }

    // Synchronize on parent activity
    if (parent != null) {
      builder.append("\t|\twhen (!(self == Done && $parent == Executing))\n")
      builder.append("\t\t\tset_$parent[Executing] -> $processName")
      builder.appendProcessVars(parent to "Executing")
      builder.append("\t|\tset_$parent[Done] -> $processName")
      builder.appendProcessVars(parent to "Done")
      builder.append("\t|\twhen (!(self == Done && $parent == Done))\n")
      builder.append("\t\t\tset_$parent[Ready] -> $processName")
      builder.appendProcessVars(parent to "Ready")
    }

    // Synchronize on sub-activities
    for (sub in subacts) {
      builder.append("\t\t// sub-activity $sub\n")
      builder.append("\t|\tset_$sub[i:ActState] -> $processName")
      builder.appendProcessVars(sub to "i")
    }

    // Append process ending
    builder.append(").\n\n")

    // Append sub-activities
    for (i in subacts.indices) {
      val subact = act.decomposition.subActivities[i]
      when (subact) {
        is Activity -> ActivityTranslator(
            act = subact,
            inputVariables = inputVariables,
            activities = activities,
            parent = eventName,
            preSibling = if (i > 0) subacts[i-1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i },
            postfix = postfix + i
        ).translate(builder)
        is ActivityLink -> ActivityTranslator(
            act = activities[subact.link]!!,
            inputVariables = inputVariables,
            activities = activities,
            parent = eventName,
            preSibling = if (i > 0) subacts[i-1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i },
            postfix = postfix + i
        ).translate(builder)
      }
    }
  }

  private fun StringBuilder.appendProcessHeader() {
    // A list of process variables for later use
    processVars = (if (parent != null) listOf("self", parent) else listOf("self")) +
        siblings + subacts + inputs.map { it.name }

    // Append process header
    this.append("${processName}[self:ActState]")
    if (parent != null)
      this.append("[$parent:ActState]")
    for (it in siblings)
      this.append("[$it:ActState]")
    for (it in subacts)
      this.append("[$it:ActState]")
    for (it in inputs)
      this.append("[${it.name}:${it.userDefinedType}]")
    this.append(" = (\n")
  }

  private fun StringBuilder.appendReadyToExecuting() {
    this.append("\t\t// Ready to Executing\n")
    this.append("\t\twhen (self == Ready")
    this.appendStartCondition()
    this.appendPrecondition()
    this.appendCompletionCondition(false)
    this.append(")\n\t\t\tset_$eventName[Executing] -> $processName")
    this.appendProcessVars("self" to "Executing")
  }

  private fun StringBuilder.appendReadyToDone() {
    this.append("\t\t// Ready to Done\n")
    this.append("\t|\twhen (self == Ready")
    this.appendStartCondition()
    this.appendCompletionCondition()
    this.append(")\n\t\t\tset_$eventName[Done] -> $processName")
    this.appendProcessVars("self" to "Done")
  }

  private fun StringBuilder.appendExecutingToExecuting() {
    this.append("\t\t// Executing to Executing\n")
    this.append("\t|\twhen (self == Executing")
    this.appendEndCondition()
    this.appendRepeatCondition()
    this.appendCompletionCondition(false)
    this.append(")\n\t\t\tset_$eventName[Executing] -> $processName")
    this.appendProcessVars("self" to "Executing")
  }

  private fun StringBuilder.appendExecutingToDone() {
    this.append("\t\t// Executing to Done\n")
    this.append("\t|\twhen (self == Executing")
    this.appendEndCondition()
    this.appendCompletionCondition()
    this.append(")\n\t\t\tset_$eventName[Done] -> $processName")
    this.appendProcessVars("self" to "Done")
  }

  private fun StringBuilder.appendDoneToReady() {
    this.append("\t\t// Done to Ready (reset)\n")
    if (parent == null) {
      this.append("\t\t// No parent, by default true.\n")
      this.append("\t|\twhen (self == Done)\n")
      this.append("\t\t\tset_$eventName[Ready] -> $processName")
      this.appendProcessVars("self" to "Ready")
    } else {
      this.append("\t\t// Should synchronize on the parent's reset condition\n")
      this.append("\t|\twhen (self == Done && $parent == Done)\n")
      this.append("\t\t\tset_$parent[Ready] -> set_$eventName[Ready] -> $processName")
      this.appendProcessVars("self" to "Ready", parent to "Ready")
      this.append("\t|\twhen (self == Done && $parent == Executing)\n")
      this.append("\t\t\tset_$parent[Executing] -> set_$eventName[Ready] -> $processName")
      this.appendProcessVars("self" to "Ready", parent to "Executing")
    }
  }

  private fun StringBuilder.appendStartCondition() {
    if (parent != null)
      this.append(" && $parent == Executing")

    when (op) {
      "and_par", "or_par", "optor_par", "sync" -> return
      "ord" -> if (preSibling != null) this.append(" && $preSibling == Done")
      "xor" -> for (it in siblings) this.append(" && $it == Ready")
      else -> for (it in siblings) this.append(" && $it != Executing")
    }
  }

  private fun StringBuilder.appendEndCondition() {
    for (it in subacts) this.append(" && $it != Executing")
    when (op) {
      "and_seq", "and_par", "sync" -> for (it in subacts) this.append(" && $it == Done")
      "or_seq", "or_par", "xor" -> {
        this.append(" && (")
        this.append(subacts.joinToString(" || ") { "$it == Done" })
        this.append(")")
      }
      "optor_seq", "optor_par" -> return
      "ord" -> if (subacts.isNotEmpty()) this.append(" && ${subacts.last()} == Done")
      else -> throw java.lang.IllegalArgumentException("Cannot recognize operator: $op")
    }
  }

  private fun StringBuilder.appendProcessVars(vararg replaces: Pair<String, String>) {
    var s = processVars.joinToString("") { "[$it]" }
    for (it in replaces) {
      s = s.replace(it.first, it.second)
    }
    this.append(s)
    this.append('\n')
  }

  private fun StringBuilder.appendPrecondition() {
    for (it in act.preConditions)
      this.append(" && $it")
  }

  private fun StringBuilder.appendCompletionCondition(sign: Boolean = true) {
    if (!sign && act.completionConditions.isNotEmpty()) {
      this.append(" && !(")
      this.append(act.completionConditions.joinToString(" && "))
      this.append(")")
    } else {
      for (it in act.completionConditions)
        this.append(" && $it")
    }
  }

  private fun StringBuilder.appendRepeatCondition() {
    for (it in act.repeatConditions)
      this.append(" && $it")
  }
}