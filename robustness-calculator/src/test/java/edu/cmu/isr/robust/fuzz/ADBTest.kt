package edu.cmu.isr.robust.fuzz

import edu.cmu.isr.robust.fuzz.android.ADBHelper
import edu.cmu.isr.robust.fuzz.android.UIModelBuilder
import edu.cmu.isr.robust.fuzz.android.UIParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ADBTest {

  @Test
  fun uiautomatorDumpTest() {
    ADBHelper.shutdown("com.example.myfirstapp")
    ADBHelper.startActivity("com.example.myfirstapp", "com.example.myfirstapp.MainActivity")
    val dump = ADBHelper.uiautomatorDump()
    assertEquals(ClassLoader.getSystemResource("android/myfirstapp.xml").readText().replace("\n\\s*".toRegex(), ""), dump)
  }

  @Test
  fun parseFireableUISimpleTest() {
    val dump = ClassLoader.getSystemResource("android/myfirstapp.xml").readText()
    val builder = UIParser(dump, "com.example.myfirstapp")
    val actions = builder.parseFireableUI().map { it.toADBInput() }
    assertEquals(listOf("tap 403 311", "tap 922 314", "touchscreen swipe 403 311 403 311 2000", "tap 191 136"), actions)
  }

  @Test
  fun parseFireableUIFilesTest() {
    val dump = ClassLoader.getSystemResource("android/files_drawer.xml").readText()
    val builder = UIParser(dump, "com.android.documentsui")
    val actions = builder.parseFireableUI().map { it.toShortName() }
    assertEquals(listOf(
        "click_Show_roots_73_136",
        "click_option_menu_search_786_136",
        "click_option_menu_grid_912_136",
        "click_More_options_1027_136",
        "click_sort_dimen_dropdown_786_289",
        "click_sort_arrow_985_289",
        "click_empty_540_1081",
        "click_eject_icon_661_997",
        "longclick_More_options_1027_136",
        "tap__320_136",
        "tap_drawer_edge_16_1002",
        "tap__98_136",
        "tap_icon_95_293",
        "tap_title_420_293",
        "tap__682_293",
        "tap_icon_95_419",
        "tap_title_420_419",
        "tap__682_419",
        "tap_icon_95_545",
        "tap_title_420_545",
        "tap__682_545",
        "tap_icon_95_671",
        "tap_title_420_671",
        "tap__682_671",
        "tap__367_768",
        "tap_icon_95_864",
        "tap_title_420_864",
        "tap__682_864",
        "tap_icon_95_997",
        "tap_title_420_974",
        "tap_summary_420_1023"
    ), actions)
  }

  @Test
  fun modelBuilderSimpleTest() {
    val builder = UIModelBuilder("com.example.myfirstapp", "com.example.myfirstapp.MainActivity")
    val sm = builder.build()
    val spec = sm.buildFSP()
    println(spec)
  }

  @Test
  fun modelBuilderAndroidFilesTest() {
    val builder = UIModelBuilder("com.android.documentsui", "com.android.documentsui.files.FilesActivity")
    builder.build()
  }
}