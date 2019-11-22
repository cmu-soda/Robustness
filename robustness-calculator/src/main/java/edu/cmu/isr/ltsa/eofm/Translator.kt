package edu.cmu.isr.ltsa.eofm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.lang.StringBuilder

fun main(args: Array<String>) {
  val xmlModule = JacksonXmlModule()
  xmlModule.setDefaultUseWrapper(false)

  val mapper = XmlMapper(xmlModule)
  mapper.registerKotlinModule()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

  val pca: EOFMS = mapper.readValue(ClassLoader.getSystemResource("eofms/pca.xml"))
  val translator = Translator(pca)
  println(translator.translate())
}

class Translator(eofms: EOFMS) {

  private val consts: List<Constant> = eofms.constants
  private val userDefinedTypes: List<UserDefineType> = eofms.userDefinedTypes
  private val actions: List<HumanAction> = eofms.humanOperators.flatMap { it.humanActions }
  private val inputVariables: List<InputVariable> = eofms.humanOperators.flatMap { it.inputVariables }
  private val topLevelActivities: List<Activity> = eofms.humanOperators.flatMap { it.eofms.map { eofm ->  eofm.activity } }
  private val activities: MutableList<Activity> = mutableListOf()

  init {
    fun recursive(activity: Activity) {
      activities.add(activity)
      for (subactivity in activity.decomposition.subActivities) {
        if (subactivity is Activity) {
          recursive(subactivity)
        }
      }
    }

    for (humanOperator in eofms.humanOperators) {
      for (eofm in humanOperator.eofms) {
        recursive(eofm.activity)
      }
    }
  }

  fun translate(): String {
    val builder = StringBuilder()
    builder.append(consts.joinToString("\n") { translate(it) })
    builder.append(userDefinedTypes.joinToString("\n\n") { translate(it) })
    builder.append(topLevelActivities.joinToString("\n\n") { translate(it) })
    return builder.toString()
  }

  fun translate(constant: Constant): String {
    return "const ${constant.name} = ${constant.value}"
  }

  fun translate(userDefineType: UserDefineType): String {
    val builder = StringBuilder()
    val value = userDefineType.value
    val consts = value.substring(1 until value.length-1).split(""",\s*""".toRegex())
    for (i in consts.indices) {
      builder.append("const ${consts[i]} = $i\n")
    }
    builder.append("range ${userDefineType.name} = ${consts[0]}..${consts[consts.size-1]}")
    return builder.toString()
  }

  fun translate(activity: Activity, parent: Activity? = null): String {
    // Get all the input variables related to the activity
    val inputVars = inputVariables.filter { i ->
      activity.preConditions.find { it.indexOf(i.name) != -1 } != null ||
          activity.completionConditions.find { it.indexOf(i.name) != -1 } != null ||
          activity.repeatConditions.find { it.indexOf(i.name) != -1 } != null
    }
    // Get the decomposition operator
    val op = activity.decomposition.operator
    // Get all the sub-activities
    return ""
  }

}