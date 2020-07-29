package edu.cmu.isr.robust.fuzz

import edu.cmu.isr.robust.fuzz.android.ADBHelper
import edu.cmu.isr.robust.fuzz.android.UIIndex
import edu.cmu.isr.robust.fuzz.android.UIModelBuilder
import edu.cmu.isr.robust.fuzz.android.UIParser
import org.junit.jupiter.api.Assertions.assertEquals
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
    val builder = UIParser(dump)
    val actions = builder.parseFireableUI().map { it.toShortName() }
    assertEquals(listOf(
        "tap__191_136",
        "click_editText_403_311",
        "longclick_editText_403_311",
        "click_button_922_314"
    ), actions)
  }

  @Test
  fun processAliveTest() {
    ADBHelper.shutdown("com.example.myfirstapp")
    Thread.sleep(500)
    assertEquals(ADBHelper.isProcessAlive("com.example.myfirstapp"), false)
    ADBHelper.startActivity("com.example.myfirstapp", "com.example.myfirstapp.MainActivity")
    Thread.sleep(500)
    assertEquals(ADBHelper.isProcessAlive("com.example.myfirstapp"), true)
  }

  @Test
  fun parseFireableUIFilesTest() {
    val dump = ClassLoader.getSystemResource("android/files_drawer.xml").readText()
    val builder = UIParser(dump)
    val actions = builder.parseFireableUI().map { it.toShortName() }
    assertEquals(listOf(
        "click_show_roots_73_136",
        "tap__320_136",
        "click_option_menu_search_786_136",
        "click_option_menu_grid_912_136",
        "click_more_options_1027_136",
        "longclick_more_options_1027_136",
        "click_sort_dimen_dropdown_786_289",
        "click_sort_arrow_985_289",
        "click_empty_540_1081",
        "tap_drawer_edge_16_1002",
        "tap__98_136",
        "tap_icon_95_293",
        "tap_title_420_293",
        "tap__682_293"
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
    val builder = UIModelBuilder("com.android.documentsui", "com.android.documentsui.files.FilesActivity",
        steps = 500, restart = 100)
    builder.build()
  }

  @Test
  fun uiIndexTest() {
    val index = UIIndex()
    index.put(ClassLoader.getSystemResource("android/files_downloads.xml").readText(), 1)
    assertEquals(1, index.get(ClassLoader.getSystemResource("android/files_images.xml").readText()))
  }

  @Test
  fun keyboardTest() {
    ADBHelper.shutdown("com.example.myfirstapp")
    ADBHelper.startActivity("com.example.myfirstapp", "com.example.myfirstapp.MainActivity")
    Thread.sleep(3000)
    println(System.currentTimeMillis())
    assertEquals(false, ADBHelper.isKeyboardUp())
    println(System.currentTimeMillis())
    ADBHelper.input(arrayOf("tap", "403", "311"))
    Thread.sleep(500)
    assertEquals(true, ADBHelper.isKeyboardUp())
    ADBHelper.backButton()
    Thread.sleep(500)
    assertEquals(false, ADBHelper.isKeyboardUp())
  }

  @Test
  fun getScreenSizeTest() {
    val (width, height) = ADBHelper.getScreenSize()
    assertEquals(1080, width)
    assertEquals(1920, height)
  }
}