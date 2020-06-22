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

/**
 * This file defines data structures for holding the EOFM model. We use jackson fasterxml package to serialize and
 * deserialize an EOFM XML file.
 */
package edu.cmu.isr.robust.eofm

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@JsonRootName("eofms")
data class EOFMS(
    @JsonProperty("constant")
    val constants: List<Constant> = emptyList(),

    @JsonProperty("userdefinedtype")
    val userDefinedTypes: List<UserDefineType> = emptyList(),

    @JsonProperty("humanoperator")
    val humanOperators: List<HumanOperator> = emptyList()
) {
  fun toXmlString(): String {
    val xmlModule = JacksonXmlModule()
    xmlModule.setDefaultUseWrapper(false)

    val mapper = XmlMapper(xmlModule)
    mapper.registerKotlinModule()
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
        .replace("""<subactivity>|</subactivity>""".toRegex(), "")
  }
}

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
    val inputVariableLinks: List<InputVariableLink> = emptyList(),

    @JsonProperty("localvariable")
    val localVariables: List<LocalVariable> = emptyList(),

    @JsonProperty("humanaction")
    val humanActions: List<HumanAction> = emptyList(),

    @JsonProperty("eofm")
    val eofms: List<EOFM> = emptyList()
)

@JsonRootName("inputvariablelink")
data class InputVariableLink(
    @JacksonXmlProperty(isAttribute = true, localName = "link")
    val link: String
)

@JsonRootName("inputvariable")
data class InputVariable(
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    val name: String,

    @JacksonXmlProperty(isAttribute = true, localName = "basictype")
    val basicType: String? = null,

    @JacksonXmlProperty(isAttribute = true, localName = "userdefinedtype")
    val userDefinedType: String? = null
) {
  var initialValue: String = "0"
}

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
) {
  /**
   * @return The input variables related to this activity
   */
  fun getInputs(inputVariables: List<InputVariable>): List<InputVariable> {
    return inputVariables.filter { i ->
      this.preConditions.find { it.indexOf(i.name) != -1 } != null ||
          this.completionConditions.find { it.indexOf(i.name) != -1 } != null ||
          this.repeatConditions.find { it.indexOf(i.name) != -1 } != null
    }
  }
}

@JsonRootName("decomposition")
data class Decomposition(
    @JacksonXmlProperty(isAttribute = true, localName = "operator")
    val operator: String,

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes(
        JsonSubTypes.Type(value = Activity::class, name = "activity"),
        JsonSubTypes.Type(value = ActivityLink::class, name = "activitylink"),
        JsonSubTypes.Type(value = Action::class, name = "action")
    )
    @JsonProperty("subactivity")
    val subActivities: List<Any> = emptyList()
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

@JsonRootName("activitylink")
data class ActivityLink(
    @JacksonXmlProperty(isAttribute = true, localName = "link")
    val link: String
)

fun parseEOFMS(xml: String): EOFMS {
  val docFactory = DocumentBuilderFactory.newInstance()
  val docBuilder = docFactory.newDocumentBuilder()
  return _parse(docBuilder.parse(ByteArrayInputStream(xml.toByteArray())))
}

fun parseEOFMS(xmlStream: InputStream?): EOFMS {
  val docFactory = DocumentBuilderFactory.newInstance()
  val docBuilder = docFactory.newDocumentBuilder()
  return _parse(docBuilder.parse(xmlStream))
}

/**
 * When serialize and deserialize the EOFM model, we have to play a trick here:
 * under a <decomposition> node, there could be three possible subtags: <activity>, <activitylink>, and <action>.
 * However, the jackson xml parser cannot handle this correctly because they are in different names.
 * Therefore, we have to wrap these children with a <subactivity> tag in order to let the parser to parse them into
 * a generic list called subActivities of the Decomposition class.
 *
 * Therefore, when deserialize the model, we have to first manually add the <subactivity> tags to the XML file. And
 * when serialize the model, we have to manually delete all the <subactivity> tags because they not in the syntax of
 * EOFM.
 */
private fun _parse(doc: Document): EOFMS {
  val decompositions = doc.getElementsByTagName("decomposition")
  // Wrap all the sub-activities with <subactivity> tag
  for (i in 0 until decompositions.length) {
    val n = decompositions.item(i)
    val children = n.childNodes
    for (j in 0 until children.length) {
      val c = children.item(j)
      if (c.nodeType != Node.ELEMENT_NODE)
        continue
      val wrapper = doc.createElement("subactivity")
      n.replaceChild(wrapper, c)
      wrapper.appendChild(c)
    }
  }
  val transformerFactory = TransformerFactory.newInstance();
  val transformer = transformerFactory.newTransformer()
  transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
  val out = ByteArrayOutputStream()
  transformer.transform(DOMSource(doc), StreamResult(out))

  val xmlModule = JacksonXmlModule()
  xmlModule.setDefaultUseWrapper(false)

  val mapper = XmlMapper(xmlModule)
  mapper.registerKotlinModule()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

  return mapper.readValue(out.toByteArray())
}

fun main(args: Array<String>) {
  val pca = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/pca.xml"))
  println(pca)
  println(pca.toXmlString())
}