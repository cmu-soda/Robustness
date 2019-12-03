package edu.cmu.isr.ltsa.eofm

import java.io.File
import java.lang.StringBuilder

fun main(args: Array<String>) {
  val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/coffee.xml"))
  val builder = StringBuilder()
  val translator = EOFMTranslator(pca, mapOf("iBrewing" to "False", "iMugState" to "Absent", "iHandleDown" to "True", "iPodState" to "EmptyOrUsed"))
  val processes = mutableListOf<String>()

  translator.translate(builder, processes)
//  println(builder.toString())
  File("C:\\Users\\changjiz\\Desktop\\LTSA-Robust\\robustness-calculator\\src\\main\\resources\\specs\\coffee_eofm.lts").writeText(builder.toString())
}

class EOFMTranslator(eofms: EOFMS, initValues: Map<String, String>) {

  private val consts: List<Constant> = eofms.constants
  private val userDefinedTypes: List<UserDefineType> = eofms.userDefinedTypes
  private val actions: List<HumanAction> = eofms.humanOperators.flatMap { it.humanActions }
  private val inputVariables: List<InputVariable> = eofms.humanOperators.flatMap { it.inputVariables }
  private val topLevelActivities: List<Activity> = eofms.humanOperators.flatMap { it.eofms.map { eofm ->  eofm.activity } }
  private val activities: MutableMap<String, Activity> = mutableMapOf()

  init {
    fun recursive(activity: Activity) {
      activities[activity.name] = activity
      for (it in activity.decomposition.subActivities) {
        if (it is Activity) {
          recursive(it)
        }
      }
    }

    // Find all the activities defined
    for (humanOperator in eofms.humanOperators) {
      for (eofm in humanOperator.eofms) {
        recursive(eofm.activity)
      }
    }
    // Set the initial value for input variables
    for (it in inputVariables) {
      it.initialValue = initValues[it.name] ?: error("The initial value for ${it.name} is not presented")
    }
  }

  fun translate(builder: StringBuilder, processes: MutableList<String>) {
    val menu = actions.map { it.name } + inputVariables.map { "set_${it.name}[${it.userDefinedType}]" }

    builder.append("const Ready = 0\n")
    builder.append("const Executing = 1\n")
    builder.append("const Done = 2\n")
    builder.append("range ActState = Ready..Done\n\n")
    for (it in consts)
      builder.appendConstant(it)
    builder.append('\n')
    for (it in userDefinedTypes)
      builder.appendUserDefinedType(it)
    builder.append("menu HDI = {${menu.joinToString()}}\n\n")
    for (i in topLevelActivities.indices)
      ActivityTranslator(topLevelActivities[i], inputVariables, activities, postfix = "$i").translate(builder, processes)
    builder.append("||EOFM = (\n\t")
    builder.append(processes.joinToString("\n||\t"))
    builder.append("\n)@{")
    builder.append(menu.joinToString(",\n"))
    builder.append("}.")
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

}

private abstract class ActTranslator(
    val parOp: String?, val parent: String?, val preSibling: String?, val siblings: List<String>
) {
  protected abstract val processName: String
  protected abstract val eventName: String
  protected var processVars = emptyList<String>()

  abstract fun translate(builder: StringBuilder, processes: MutableList<String>)
  protected abstract fun StringBuilder.appendProcessHeader()
  protected abstract fun StringBuilder.appendReadyToExecuting()
  protected abstract fun StringBuilder.appendExecutingToDone()

  protected fun StringBuilder.appendDoneToReady() {
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

  protected fun StringBuilder.appendStartCondition() {
    if (parent != null)
      this.append(" && $parent == Executing")

    if (parOp != null) {
      when (parOp) {
        "and_par", "or_par", "optor_par", "sync" -> return
        "ord" -> if (preSibling != null) this.append(" && $preSibling == Done")
        "xor" -> for (it in siblings) this.append(" && $it == Ready")
        else -> for (it in siblings) this.append(" && $it != Executing")
      }
    }
  }

  protected abstract fun StringBuilder.appendEndCondition()

  protected fun StringBuilder.appendProcessVars(vararg replaces: Pair<String, String>) {
    var s = processVars.joinToString("") { "[$it]" }
    for (it in replaces) {
      s = s.replace(it.first, it.second)
    }
    this.append(s)
    this.append('\n')
  }

  protected fun StringBuilder.appendSyncParent() {
    if (parent != null) {
      this.append("\t|\twhen (!(self == Done && $parent == Executing))\n")
      this.append("\t\t\tset_$parent[Executing] -> $processName")
      this.appendProcessVars(parent to "Executing")
      this.append("\t|\tset_$parent[Done] -> $processName")
      this.appendProcessVars(parent to "Done")
      this.append("\t|\twhen (!(self == Done && $parent == Done))\n")
      this.append("\t\t\tset_$parent[Ready] -> $processName")
      this.appendProcessVars(parent to "Ready")
    }
  }

  protected fun StringBuilder.appendSyncSiblings() {
    for (s in siblings) {
      this.append("\t\t// sibling $s\n")
      this.append("\t|\tset_$s[i:ActState] -> $processName")
      this.appendProcessVars(s to "i")
    }
  }
}

private class ActivityTranslator(
    val act: Activity, val inputVariables: List<InputVariable>,
    val activities: Map<String, Activity>, val postfix: String = "",
    parOp: String? = null, parent: String? = null, preSibling: String? = null, siblings: List<String> = emptyList()
) : ActTranslator(parOp, parent, preSibling, siblings) {
  override val processName = "${act.name.capitalize()}$postfix"
  override val eventName = "${act.name}$postfix"

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

  override fun translate(builder: StringBuilder, processes: MutableList<String>) {
    processes.add("P_$processName")
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
    builder.appendSyncParent()
    // Synchronize on siblings activities
    builder.appendSyncSiblings()
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
      when (val subact = act.decomposition.subActivities[i]) {
        is Activity -> ActivityTranslator(
            act = subact,
            inputVariables = inputVariables,
            activities = activities,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i-1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
        is ActivityLink -> ActivityTranslator(
            act = activities[subact.link] ?: error("The activity ${subact.link} is not found!"),
            inputVariables = inputVariables,
            activities = activities,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i-1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
        is Action -> ActionTranslator(
            act = subact,
            postfix = postfix + i,
            parOp = op,
            parent = eventName,
            preSibling = if (i > 0) subacts[i-1] else null,
            siblings = subacts.filterIndexed { index, _ -> index != i }
        ).translate(builder, processes)
      }
    }
  }

  override fun StringBuilder.appendProcessHeader() {
    // A list of process variables for later use
    processVars = (if (parent != null) listOf("self", parent) else listOf("self")) +
        siblings + subacts + inputs.map { it.name }

    this.append("P_$processName = $processName")
    this.append(processVars.joinToString("") { name ->
      val v = inputVariables.find { it.name == name }
      if (v != null) "[${v.initialValue}]" else "[Ready]"
    })
    this.append(",\n")

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

  override fun StringBuilder.appendReadyToExecuting() {
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

  override fun StringBuilder.appendExecutingToDone() {
    this.append("\t\t// Executing to Done\n")
    this.append("\t|\twhen (self == Executing")
    this.appendEndCondition()
    this.appendCompletionCondition()
    this.append(")\n\t\t\tset_$eventName[Done] -> $processName")
    this.appendProcessVars("self" to "Done")
  }

  override fun StringBuilder.appendEndCondition() {
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

private class ActionTranslator(
    val act: Action, val postfix: String = "",
    parOp: String, parent: String, preSibling: String? = null, siblings: List<String> = emptyList()
) : ActTranslator(parOp, parent, preSibling, siblings) {
  override val processName = "${act.humanAction?.capitalize()}$postfix"
  override val eventName = "${act.humanAction}$postfix"

  override fun translate(builder: StringBuilder, processes: MutableList<String>) {
    processes.add("P_$processName")
    // Append process header, and set the list of process variables for later user
    builder.appendProcessHeader()
    // From Ready to Executing
    builder.appendReadyToExecuting()
    // From Executing to Done
    builder.appendExecutingToDone()
    // From Done to Ready (reset)
    builder.appendDoneToReady()
    // Synchronize on parent activity
    builder.appendSyncParent()
    // Synchronize on siblings activities
    builder.appendSyncSiblings()
    // Append process ending
    builder.append(").\n\n")
  }

  override fun StringBuilder.appendProcessHeader() {
    // A list of process variables for later use
    processVars = listOf("self", parent!!) + siblings

    this.append("P_$processName = $processName")
    this.append(processVars.joinToString("") { "[Ready]" })
    this.append(",\n")

    // Append process header
    this.append("${processName}[self:ActState]")
    this.append("[$parent:ActState]")
    for (it in siblings)
      this.append("[$it:ActState]")
    this.append(" = (\n")
  }

  override fun StringBuilder.appendReadyToExecuting() {
    this.append("\t\t// Ready to Executing\n")
    this.append("\t\twhen (self == Ready")
    this.appendStartCondition()
    this.append(")\n\t\t\tset_$eventName[Executing] -> $processName")
    this.appendProcessVars("self" to "Executing")
  }

  override fun StringBuilder.appendExecutingToDone() {
    this.append("\t\t// Executing to Done\n")
    this.append("\t|\twhen (self == Executing")
    this.appendEndCondition()
    this.append(")\n\t\t\t${act.humanAction} -> set_$eventName[Done] -> $processName")
    this.appendProcessVars("self" to "Done")
  }

  override fun StringBuilder.appendEndCondition() {
    return
  }
}