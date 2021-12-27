package dev.sacode.flowrun

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import org.scalajs.dom
import scalatags.JsDom.all.*
import org.getshaka.nativeconverter.NativeConverter
import reactify.*
import dev.sacode.flowrun.eval.Interpreter
import dev.sacode.flowrun.edit.FunctionEditor
import dev.sacode.flowrun.edit.FunctionSelector
import dev.sacode.flowrun.edit.StatementEditor
import dev.sacode.flowrun.edit.OutputArea
import dev.sacode.flowrun.edit.DebugArea

@JSExportTopLevel("FlowRun")
class FlowRun(mountElem: dom.Element, programJson: Option[String] = None) {

  private val mountElemText = mountElem.innerText.trim

  private val maybeTemplate = dom.document.getElementById("flowrun-template")
  private val flowRunElements = FlowRunElements.resolve(maybeTemplate)
  mountElem.innerText = ""
  mountElem.appendChild(flowRunElements.template)

  private val maybeJson = programJson.orElse(
    Option.when(mountElemText.nonEmpty)(mountElemText)
  )
  private val program = maybeJson match
    case Some(json) => NativeConverter[Program].fromNative(js.JSON.parse(json))
    case None =>
      Program(
        AST.newId,
        "program",
        Function(
          "main",
          "main",
          statements = List(Statement.Begin(true), Statement.Return(AST.newId))
        ),
        List.empty
      )

  private val flowrunChannel = Channel[FlowRun.Event]
  private val programModel = ProgramModel(program, flowrunChannel)
  private val functionEditor = FunctionEditor(programModel, flowrunChannel, flowRunElements)
  private val functionSelector = FunctionSelector(programModel, flowrunChannel, flowRunElements)
  private val statementEditor = StatementEditor(programModel, flowrunChannel, flowRunElements)
  private var interpreter = Interpreter(programModel, flowrunChannel)
  private var outputArea = OutputArea(interpreter, flowRunElements)
  private var debugArea = DebugArea(interpreter, flowRunElements)

  private var lastRun: String = ""

  flowRunElements.metaData.innerText = program.name

  functionSelector.loadFunctions()
  statementEditor.setup()

  dom.document.getElementById("gencode").asInstanceOf[dom.html.Button].onclick = _ => {
    val generator = new dev.sacode.flowrun.codegen.ScalaGenerator(programModel.ast)
    println(generator.generate)
  }

  def json(): js.Any =
    programModel.ast.toNative

  // run the program
  flowRunElements.runButton.onclick = _ => {
    outputArea.clearErrors()
    functionEditor.clearErrors()

    lastRun = getNowTime
    flowRunElements.output.innerText = ""
    flowRunElements.output.appendChild(s"Started at: $lastRun".render)
    flowRunElements.output.appendChild(br.render)
    flowRunElements.output.appendChild(br.render)

    interpreter = Interpreter(programModel, flowrunChannel) // fresh SymTable etc
    outputArea = OutputArea(interpreter, flowRunElements)
    debugArea = DebugArea(interpreter, flowRunElements)

    interpreter.run()
    functionEditor.disable()
  }

  flowRunElements.addFunButton.onclick = _ => programModel.addNewFunction()

  import FlowRun.Event.*
  flowrunChannel.attach {
    case EvalSuccess =>
      flowRunElements.output.appendChild(br.render)
      flowRunElements.output.appendChild(br.render)
      flowRunElements.output.appendChild(s"Finished at: $getNowTime".render)
      functionEditor.enable()
    case SyntaxSuccess =>
      // TODO REMOVE SAMO SYNTAX ERORE !!!!!!!!!??
      outputArea.clearErrors()
      functionEditor.loadCurrentFunction()
    case SyntaxError(msg) =>
      var output = "Syntax Error: " + msg
      outputArea.displayError(output)
      functionEditor.enable()
    case EvalError(_, msg) =>
      var output = s"Started at: $lastRun"
      output += "\n\nError: " + msg
      outputArea.displayError(output)
      functionEditor.enable()
    case EvalOutput(output) =>
      val newOutput = pre(output).render
      flowRunElements.output.appendChild(newOutput)
    case EvalInput(nodeId, name) =>
      outputArea.evalInput(nodeId, name)
    case SymbolTableUpdated =>
      debugArea.showVariables()
    case FunctionUpdated =>
      functionEditor.loadCurrentFunction()
      functionSelector.loadFunctions()
    case Deselected =>
      flowRunElements.editStatement.innerText = ""
  }
}

object FlowRun:

  enum Event:
    case EvalSuccess
    case EvalError(nodeId: String, msg: String)
    case EvalOutput(msg: String)
    case EvalInput(nodeId: String, name: String)
    case SyntaxSuccess
    case SyntaxError(msg: String)
    case SymbolTableUpdated
    case FunctionUpdated
    case Deselected
end FlowRun
