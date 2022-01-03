package dev.sacode.flowrun
package edit

import scalajs.js
import org.scalajs.dom
import scalatags.JsDom.all.{name => _, *}
import reactify.*
import dev.sacode.flowrun.ProgramModel.Request
import dev.sacode.flowrun.parse.*

/** Editor for selected statement. */
class StatementEditor(
    programModel: ProgramModel,
    flowrunChannel: Channel[FlowRun.Event],
    flowRunElements: FlowRunElements
) {

  private val EditableNodeTypes =
    Set("Begin", "Return", "Declare", "Assign", "Input", "Output", "Call", "If", "While", "DoWhile")

  def setup(): Unit = {
    flowRunElements.drawArea.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        event.preventDefault()

        getSvgNode(event.target) match {
          case ("NODE", n) =>
            flowrunChannel := FlowRun.Event.SyntaxSuccess
            val idParts = n.id.split("#")
            val nodeId = idParts(0)
            val tpe = idParts(1)
            if EditableNodeTypes(tpe) then doEdit(nodeId)
          case _ =>
            flowrunChannel := FlowRun.Event.Deselected
        }
      }
    )
  }

  private def doEdit(nodeId: String): Unit = {
    val node = programModel.findStatement(nodeId)
    val nodeType = node.getClass.getSimpleName.filterNot(_ == '$')

    // skip Begin if main function
    if nodeType == "Begin" && programModel.currentFunction.isMain then return

    // skip Return if function doesn't return anything
    if nodeType == "Return" && programModel.currentFunction.tpe == Expression.Type.Void then return

    println(s"Editing statement: $nodeType")

    // clear first, prepare for new inputs
    flowRunElements.scratchpad.innerText = ""
    flowRunElements.scratchpad.appendChild(div(s"Editing $nodeType:").render)

    // name input
    val nameInputElem = flowRunElements.newInputText
    nameInputElem.value = Statement.name(node, programModel.currentFunction.name)
    nameInputElem.placeholder = if nodeType == "Begin" then "myFun" else "x"
    nameInputElem.oninput = { (_: dom.Event) =>
      val newName = nameInputElem.value.trim
      val errorMsg: Option[String] = NameUtils.validateIdentifier(newName)
      if errorMsg.isEmpty then {
        node match {
          case _: Statement.Declare =>
            programModel.updateDeclare(Request.UpdateDeclare(nodeId, name = Some(newName)))
          case _: Statement.Input =>
            programModel.updateInput(Request.UpdateInput(nodeId, name = newName))
          case _: Statement.Begin =>
            programModel.updateFunction(Request.UpdateFunction(nodeId, name = Some(newName)))
          case _: Statement.Assign =>
            programModel.updateAssign(Request.UpdateAssign(nodeId, name = Some(newName)))
          case _ => ()
        }
        flowrunChannel := FlowRun.Event.SyntaxSuccess
      }
      errorMsg.foreach(msg => flowrunChannel := FlowRun.Event.SyntaxError(msg))
    }

    var filledName = false
    if Statement.hasName(node, programModel.currentFunction.name) then
      filledName = nameInputElem.value.nonEmpty
      flowRunElements.scratchpad.appendChild(nameInputElem)

    // type input
    val typeSelectElem = flowRunElements.newInputSelect
    val types =
      if nodeType == "Begin" then Expression.Type.values
      else Expression.Type.VarTypes
    types.foreach { tpe =>
      val typeItem = option(value := tpe.toString)(tpe.toString).render
      typeSelectElem.add(typeItem)
    }
    typeSelectElem.onchange = { (e: dom.Event) =>
      val thisElem = e.target.asInstanceOf[dom.html.Select]
      val newType = Expression.Type.valueOf(thisElem.value)
      if nodeType == "Declare" then programModel.updateDeclare(Request.UpdateDeclare(nodeId, tpe = Some(newType)))
      else programModel.updateFunction(Request.UpdateFunction(nodeId, tpe = Some(newType)))
      flowrunChannel := FlowRun.Event.SyntaxSuccess
    }

    if Statement.hasTpe(node, programModel.currentFunction.tpe.toString) then
      typeSelectElem.value = Statement.tpe(node, programModel.currentFunction.tpe.toString) // select appropriate type
      flowRunElements.scratchpad.appendChild(span(": ").render)
      flowRunElements.scratchpad.appendChild(typeSelectElem)

    // expression input
    val exprInputElem = flowRunElements.newInputText
    exprInputElem.value = Statement.expr(node)
    exprInputElem.placeholder =
      if nodeType == "Output" then "\"Hello!\""
      else if nodeType == "Call" then "myFun(x)"
      else "x + 1"
    exprInputElem.oninput = _ => {
      import scala.util.*
      val newExprText = exprInputElem.value.trim
      val maybeNewExpr = Try(parseExpr(nodeId, newExprText))
      maybeNewExpr match {
        case Failure(e) =>
          if nodeType == "Declare" && newExprText.isEmpty then
            programModel.updateDeclare(Request.UpdateDeclare(nodeId, expr = Some(None)))
          else if nodeType == "Return" && newExprText.isEmpty then
            programModel.updateReturn(Request.UpdateReturn(nodeId, expr = Some(None)))
          else flowrunChannel := FlowRun.Event.SyntaxError(e.getMessage)
        case Success(_) =>
          flowrunChannel := FlowRun.Event.SyntaxSuccess
          if nodeType == "Declare" then
            programModel.updateDeclare(
              Request.UpdateDeclare(nodeId, expr = Some(Some(newExprText)))
            )
          else if nodeType == "Assign" then
            programModel.updateAssign(Request.UpdateAssign(nodeId, expr = Some(newExprText)))
          else if nodeType == "If" then programModel.updateIf(Request.UpdateIf(nodeId, expr = newExprText))
          else if nodeType == "While" then programModel.updateWhile(Request.UpdateWhile(nodeId, expr = newExprText))
          else if nodeType == "DoWhile" then
            programModel.updateDoWhile(Request.UpdateDoWhile(nodeId, expr = newExprText))
          else if nodeType == "Call" then programModel.updateCall(Request.UpdateCall(nodeId, expr = newExprText))
          else if nodeType == "Return" then
            programModel.updateReturn(
              Request.UpdateReturn(nodeId, expr = Some(Some(newExprText)))
            )
          else programModel.updateOutput(Request.UpdateOutput(nodeId, newExprText))
      }
    }

    if Statement.hasExpr(node) then
      if Set("Declare", "Assign").contains(nodeType) then flowRunElements.scratchpad.appendChild(span(" = ").render)
      flowRunElements.scratchpad.appendChild(exprInputElem)
    end if

    // params inputs
    if nodeType == "Begin" then
      val addParamElem = flowRunElements.addParamButton
      addParamElem.onclick = _ => {
        val name = ""
        val tpe = Expression.Type.Integer.toString
        val params = getParams()
        val idx = params.length
        val newParams = params ++ List(name -> tpe)
        val paramNameInput = getParamNameInput(nodeId, "", idx)
        val paramTpeInput = getParamTpeInput(nodeId, tpe, idx)

        flowRunElements.scratchpad.appendChild(
          div(paramNameInput, paramTpeInput).render
        )
        paramNameInput.focus()
        programModel.updateFunction(
          Request.UpdateFunction(nodeId, parameters = Some(newParams))
        )
      }
      flowRunElements.scratchpad.appendChild(div(addParamElem).render)

      val params = getParams()
      params.zipWithIndex.foreach { case ((name, tpe), idx) =>
        val paramNameInput = getParamNameInput(nodeId, name, idx)
        val paramTpeInput = getParamTpeInput(nodeId, tpe, idx)
        flowRunElements.scratchpad.appendChild(
          div(paramNameInput, paramTpeInput).render
        )
      }
    end if

    // focus
    if !Statement.hasExpr(node) || (Statement.hasName(
        node,
        programModel.currentFunction.name
      ) && !filledName)
    then
      nameInputElem.focus()
      nameInputElem.select()
    else
      exprInputElem.focus()
      val exprStr = exprInputElem.value
      if isQuotedStringLiteral(exprStr) then exprInputElem.setSelectionRange(1, exprStr.length - 1)
  }

  private def getParams(): List[(String, String)] = {
    programModel.currentFunction.parameters.map((name, tpe) => (name, tpe.toString))
  }

  private def getParamNameInput(
      nodeId: String,
      name: String,
      idx: Int
  ) = {
    val paramNameInput = flowRunElements.newInputText
    paramNameInput.value = name
    paramNameInput.oninput = _ => {
      val params = getParams()
      val newParam = params(idx).copy(_1 = paramNameInput.value)
      val newParams = params.patch(idx, List(newParam), 1)
      programModel.updateFunction(Request.UpdateFunction(nodeId, parameters = Some(newParams)))
    }
    paramNameInput
  }

  private def getParamTpeInput(
      nodeId: String,
      selValue: String,
      idx: Int
  ) = {
    val paramTpeInput = flowRunElements.newInputSelect
    Expression.Type.VarTypes.foreach { tpe =>
      val typeItem = option(value := tpe.toString)(tpe.toString).render
      paramTpeInput.add(typeItem)
    }
    paramTpeInput.value = selValue
    paramTpeInput.onchange = (e: dom.Event) => {
      val params = getParams()
      val newParam = params(idx).copy(_2 = paramTpeInput.value)
      val newParams = params.patch(idx, List(newParam), 1)
      programModel.updateFunction(Request.UpdateFunction(nodeId, parameters = Some(newParams)))
    }
    paramTpeInput
  }

  private def isQuotedStringLiteral(str: String) =
    str.length > 2 && str.head == '"' && str.last == '"'

}
