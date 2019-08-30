import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main(args: Array<String>) {
  try {
    val var1 = UIManager.getSystemLookAndFeelClassName()
    UIManager.setLookAndFeel(var1)
  } catch (var2: Exception) {
  }


  val var3 = HPWindow(null as AppletButton?)
  var3.title = "LTS Analyser"
  var3.pack()
  HPWindow.centre(var3)
  var3.isVisible = true
  if (args.size > 0) {
    SwingUtilities.invokeLater(HPWindow.ScheduleOpenFile(var3, args[0]))
  } else {
    var3.currentDirectory = System.getProperty("user.dir")
  }
}