package edu.cmu.isr.robust.fuzz.android

import java.io.File
import java.nio.file.Paths

/**
 *
 */
const val AndroidHome = "/home/cj/Android/Sdk/"

/**
 *
 */
object ADBHelper {
  val ADB = Paths.get(AndroidHome, "platform-tools/adb").toAbsolutePath().toString()

  /**
   *
   */
  fun killServer(): Boolean {
    val proc = ProcessBuilder(ADB, "kill-server")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.exitValue() == 0
  }

  /**
   *
   */
  fun isFocusedApp(pkg: String): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "dumpsys", "window", "windows", "|", "grep", "'mFocusedApp'")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    val re = proc.inputStream.bufferedReader().readText()
    return re.contains(pkg)
  }

  fun isProcessAlive(pkg: String): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "pidof", pkg)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.inputStream.bufferedReader().readText() != ""
  }

  /**
   *
   */
  fun shutdown(pkg: String): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "am", "force-stop", pkg)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.exitValue() == 0
  }

  /**
   *
   */
  fun startActivity(pkg: String, activity: String): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "am", "start", "-n", "$pkg/$activity")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    val out = proc.inputStream.bufferedReader().readText()
    if (proc.exitValue() != 0) {
      error("Failed to start activity: $out")
    }
    return proc.exitValue() == 0
  }

  /**
   *
   */
  fun uiautomatorDump(): String? {
    val proc = ProcessBuilder(ADB, "shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    val result = proc.inputStream.bufferedReader().readText()
    if (result == "UI hierchary dumped to: /sdcard/window_dump.xml\n") {
      val tmp = File("./tmp")
      if (!tmp.exists())
        tmp.mkdir()
      pull("/sdcard/window_dump.xml", "./tmp/window_dump.xml")
      return File("./tmp/window_dump.xml").readText()
    }
//    error("Failed to dump the current UI layout: $result")
    return null
  }

  /**
   *
   */
  fun pull(src: String, dst: String): Boolean {
    val proc = ProcessBuilder(ADB, "pull", src, dst)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.exitValue() == 0
  }

  /**
   *
   */
  fun input(cmd: Array<String>): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "input", *cmd)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.exitValue() == 0
  }

  /**
   *
   */
  fun backButton(): Boolean {
    val proc = ProcessBuilder(ADB, "shell", "input", "keyevent", "KEYCODE_BACK")
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor()
    return proc.exitValue() == 0
  }
}