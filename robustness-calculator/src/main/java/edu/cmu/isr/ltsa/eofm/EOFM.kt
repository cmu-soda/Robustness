package edu.cmu.isr.ltsa.eofm

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

@JsonRootName("eofms")
data class EOFMS(
    @JsonProperty("constant")
    val constants: List<Constant> = emptyList(),

    @JsonProperty("userdefinedtype")
    val userDefinedTypes: List<UserDefineType> = emptyList(),

    @JsonProperty("humanoperator")
    val humanOperators: List<HumanOperator> = emptyList()
)

@JsonRootName("constant")
data class Constant(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JacksonXmlProperty(isAttribute = true, localName = "basictype")
    val basicType: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "userdefinedtype")
    val userDefinedType: String? = null,

    @JacksonXmlText
    val value: String
) {
  @JsonCreator
  constructor(name: String, basicType: String?, userDefinedType: String?): this(name, basicType, userDefinedType, "")
}

@JsonRootName("userdefinedtype")
data class UserDefineType(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JacksonXmlText
    val value: String
) {
  @JsonCreator
  constructor(name: String): this(name, "")
}

@JsonRootName("humanoperator")
data class HumanOperator(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JsonProperty("inputvariable")
    val inputVariables: List<InputVariable> = emptyList(),

    @JsonProperty("inputvariablelink")
    val inputVariableLinks: List<Link> = emptyList(),

    @JsonProperty("localvariable")
    val localVariables: List<LocalVariable> = emptyList(),

    @JsonProperty("humanaction")
    val humanActions: List<HumanAction> = emptyList(),

    @JsonProperty("eofm")
    val eofms: List<EOFM> = emptyList()
)

@JsonRootName("inputvariable")
data class InputVariable(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JacksonXmlProperty(isAttribute = true, localName = "basictype")
    val basicType: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "userdefinedtype")
    val userDefinedType: String? = null
)

@JsonRootName("localvariable")
data class LocalVariable(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JsonProperty("initialvalue")
    val initialValue: String,

    @JacksonXmlProperty(isAttribute = true, localName = "basictype")
    val basicType: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "userdefinedtype")
    val userDefinedType: String? = null
)

@JsonRootName("humanaction")
data class HumanAction(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JacksonXmlProperty(isAttribute = true, localName = "behavior")
    val behavior: String
)

@JsonRootName("eofm")
data class EOFM(
    @JsonProperty("activity")
    val activity: Activity
)

@JsonRootName("activity")
data class Activity(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JsonProperty("precondition")
    val preConditions: List<String> = emptyList(),

    @JsonProperty("completioncondition")
    val completionConditions: List<String> = emptyList(),

    @JsonProperty("repeatcondition")
    val repeatConditions: List<String> = emptyList(),

    @JsonProperty("decomposition")
    val decomposition: Decomposition
)

@JsonRootName("decomposition")
data class Decomposition(
    @JacksonXmlProperty(isAttribute = true, localName = "operator")
    val operator: String,

    @JsonProperty("activity")
    val activities: List<Activity> = emptyList(),

    @JsonProperty("activitylink")
    val activityLinks: List<Link> = emptyList(),

    @JsonProperty("action")
    val actions: List<Action> = emptyList()
)

@JsonRootName("action")
data class Action(
    @JacksonXmlProperty(isAttribute = true, localName = "humanaction")
    val humanAction: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "localvariable")
    val localVariable: String? = null,

    @JacksonXmlText
    val value: String? = null
) {
  @JsonCreator
  constructor(humanAction: String?, localVariable: String?) : this(humanAction, localVariable, null)
}

data class Link(
    @JacksonXmlProperty(isAttribute = true, localName = "link")
    val link: String
)

fun main(args: Array<String>) {
  val xmlModule = JacksonXmlModule()
  xmlModule.setDefaultUseWrapper(false)

  val mapper = XmlMapper(xmlModule)
  mapper.registerKotlinModule()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

//  val eofms = EOFMS(
//      constants = listOf(Constant("a", value = "0", basicType = "INTEGER"), Constant("b", value = "On", userDefinedType = "tLight")),
//      userDefinedTypes = listOf(UserDefineType(name = "tLight", value = "{On, Off}")),
//      humanOperators = listOf(HumanOperator(
//          name = "human",
//          inputVariables = listOf(InputVariable(name = "i", basicType = "INTEGER"), InputVariable(name = "j", userDefinedType = "tLight")),
//          inputVariableLinks = listOf(Link(link = "i_link")),
//          localVariables = listOf(LocalVariable(name = "l_i", basicType = "INTEGER", initialValue = "0")),
//          humanActions = listOf(HumanAction(name = "act", behavior = "autoreset")),
//          eofms = listOf(EOFM(
//              activity = Activity(
//                  name = "activity1",
//                  preConditions = listOf("iInterfaceMessage = SystemOff", "iInterfaceMessage = TreatmentAdministering"),
//                  completionConditions = listOf("iInterfaceMessage /= SystemOff"),
//                  decomposition = Decomposition(
//                      operator = "optor_seq",
//                      activities = listOf(Activity(
//                          name = "activity1-1",
//                          repeatConditions = listOf("true"),
//                          decomposition = Decomposition(
//                              operator = "ord",
//                              actions = listOf(Action(humanAction = "act"))
//                          )
//                      )),
//                      activityLinks = listOf(Link(link = "activity_link-1"))
//                  )
//              )
//          ))
//      ))
//  )
//
//  println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(eofms))

  val pca: EOFMS = mapper.readValue(ClassLoader.getSystemResource("eofms/pca.xml"))
  println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pca))
}