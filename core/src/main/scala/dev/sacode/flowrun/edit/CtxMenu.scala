package dev.sacode.flowrun.edit

import org.scalajs.dom
import dev.sacode.flowrun.FlowRunElements
import dev.sacode.flowrun.ProgramModel
import dev.sacode.flowrun.ProgramModel.Request
import dev.sacode.flowrun.Expression.Type
import dev.sacode.flowrun.AST
import dev.sacode.flowrun.getSvgNode

class CtxMenu(
    flowRunElements: FlowRunElements,
    programModel: ProgramModel
) {

  /** used for delete */
  private var nodeId = ""

  private var afterId = ""
  private var blockId = ""

  private val DeleteableNodeTypes =
    Set("Declare", "Assign", "Input", "Output", "Call", "If", "While", "DoWhile")

  def init(): Unit = {
    val edgeContextMenu =
      dom.document.getElementById("flowrun-edge-context-menu").asInstanceOf[dom.html.Element]
    val nodeContextMenu =
      dom.document.getElementById("flowrun-node-context-menu").asInstanceOf[dom.html.Element]

    flowRunElements.drawArea.addEventListener(
      "contextmenu",
      (event: dom.MouseEvent) => {

        event.preventDefault()

        // here we know which NODE/EDGE is right-clicked
        // we save relevant ids, and then use them when a button is clicked

        hideAllMenus()

        getSvgNode(event.target) match {
          case ("NODE", n) =>
            val idParts = n.id.split("#")
            nodeId = idParts(0)
            val nodeTpe = idParts(1)
            if DeleteableNodeTypes(nodeTpe) then {
              nodeContextMenu.style.left = s"${event.clientX}px"
              nodeContextMenu.style.top = s"${event.clientY}px"
              nodeContextMenu.classList.add("active")
            }

          case ("EDGE", e) =>
            val canActivate = setEdgeIds(e.id)
            if canActivate then
              edgeContextMenu.style.left = s"${event.clientX}px"
              edgeContextMenu.style.top = s"${event.clientY}px"
              edgeContextMenu.classList.add("active")

          case _ =>
        }
      }
    )

    attachListeners()
  }

  private def hideAllMenus(): Unit =
    dom.document
      .getElementsByClassName("flowrun-context-menu")
      .foreach { e =>
        e.asInstanceOf[dom.html.Element].classList.remove("active")
      }

  private def setEdgeIds(edgeId: String): Boolean = {
    val parts = edgeId.split("@")
    if parts.length != 2 then return false

    afterId = parts(0)
    blockId = parts(1)

    if afterId.startsWith("end_") then afterId = afterId.drop("end_".length)
    else if afterId.startsWith("true_dummy_up_") then afterId = afterId.drop("true_dummy_up_".length)
    else if afterId.startsWith("true_dummy_down_") then afterId = afterId.drop("true_dummy_down_".length)
    else if afterId.startsWith("false_dummy_up_") then afterId = afterId.drop("false_dummy_up_".length)
    else if afterId.startsWith("false_dummy_down_") then afterId = afterId.drop("false_dummy_down_".length)

    true
  }

  private def attachListeners(): Unit = {
    // close menu when clicked anywhere
    dom.window.addEventListener("click", event => hideAllMenus())

    val edgeContextMenu = dom.document.getElementById("flowrun-edge-context-menu").asInstanceOf[dom.html.Element]
    val nodeContextMenu = dom.document.getElementById("flowrun-node-context-menu").asInstanceOf[dom.html.Element]

    val deleteButton = nodeContextMenu.querySelector("#flowrun-delete").asInstanceOf[dom.html.Element]
    val addDeclareButton = edgeContextMenu.querySelector("#flowrun-add-declare").asInstanceOf[dom.html.Element]
    val addAssignButton = edgeContextMenu.querySelector("#flowrun-add-assign").asInstanceOf[dom.html.Element]
    val addInputButton = edgeContextMenu.querySelector("#flowrun-add-input").asInstanceOf[dom.html.Element]
    val addOutputButton = edgeContextMenu.querySelector("#flowrun-add-output").asInstanceOf[dom.html.Element]
    val addCallButton = edgeContextMenu.querySelector("#flowrun-add-call").asInstanceOf[dom.html.Element]
    val addIfButton = edgeContextMenu.querySelector("#flowrun-add-if").asInstanceOf[dom.html.Element]
    val addWhileButton = edgeContextMenu.querySelector("#flowrun-add-while").asInstanceOf[dom.html.Element]
    val addDoWhileButton = edgeContextMenu.querySelector("#flowrun-add-do-while").asInstanceOf[dom.html.Element]

    deleteButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.delete(Request.Delete(nodeId))
      }
    )

    addDeclareButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addDeclare(
          Request.AddDeclare(AST.newId, "x", Type.Integer, afterId, blockId)
        )
      }
    )

    addAssignButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addAssign(Request.AddAssign(AST.newId, afterId, blockId))
      }
    )

    addInputButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addInput(Request.AddInput(AST.newId, afterId, blockId))
      }
    )

    addOutputButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addOutput(Request.AddOutput(AST.newId, afterId, blockId))
      }
    )

    addCallButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addCall(Request.AddCall(AST.newId, afterId, blockId))
      }
    )

    addIfButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addIf(
          Request.AddIf(AST.newId, AST.newId, AST.newId, afterId, blockId)
        )
      }
    )

    addWhileButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addWhile(Request.AddWhile(AST.newId, AST.newId, afterId, blockId))
      }
    )

    addDoWhileButton.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        programModel.addDoWhile(Request.AddDoWhile(AST.newId, AST.newId, afterId, blockId))
      }
    )
  }

}
