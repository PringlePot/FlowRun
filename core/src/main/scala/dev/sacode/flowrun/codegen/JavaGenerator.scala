package dev.sacode.flowrun.codegen

import reactify.*
import dev.sacode.flowrun.toIdentifier
import dev.sacode.flowrun.ast.*, Expression.Type
import dev.sacode.flowrun.indented
import dev.sacode.flowrun.FlowRun
import dev.sacode.flowrun.eval.SymbolTable
import dev.sacode.flowrun.eval.SymbolKey
import dev.sacode.flowrun.eval.Symbol
import scala.util.Try
import dev.sacode.flowrun.spaces

// TODO prettify empty lines
class JavaGenerator(programAst: Program) extends CodeGenerator {

  private val indent = 2

  private val dummyChannel = Channel[FlowRun.Event]
  private val symTab = SymbolTable(dummyChannel) // needed for types of vars

  private var initInput = false

  def generate: Try[String] = Try {

    val statements = programAst.main.statements.map(genStatement).map(_.indented(indent)).filterNot(_.trim.isEmpty)
    val functions = programAst.functions.map(genFunction)
    val maybeScanner = if initInput then "\nstatic Scanner scanner = new Scanner(System.in);\n\n" else ""
    s"""|import java.util.*;
        |
        |public class ${programAst.name.toIdentifier} {
        |${maybeScanner.indented(indent)}
        |${indent.spaces}public static void main(String args[]) {
        |${statements.mkString("\n")}
        |${indent.spaces}}
        |
        |${functions.mkString("\n\n")}
        |}
        |""".stripMargin.trim
  }

  private def genFunction(function: Function): String = {
    symTab.enterScope(function.id, function.name)
    val statements = function.statements.map(genStatement).filterNot(_.trim.isEmpty)
    val params = function.parameters.map(p => s"${getType(p.tpe)} ${p.name}").mkString(", ")
    symTab.exitScope()
    s"""|public static ${getType(function.tpe)} ${function.name}($params) {
        |${statements.mkString("\n")}
        |}
        |""".stripMargin.indented(indent)
  }

  private def genStatement(stmt: Statement): String =
    import Statement._
    stmt match
      case _: Begin =>
        ""
      case Declare(id, name, tpe, maybeInitValue) =>
        val key = SymbolKey(name, Symbol.Kind.Variable, id)
        symTab.add(id, key, tpe, None)
        val initValue = maybeInitValue.map(v => s" = $v").getOrElse("")
        s"${getType(tpe)} $name$initValue;".indented(indent)
      case Assign(_, name, value) =>
        s"$name = $value;".indented(indent)
      case Call(_, value) =>
        s"$value;".indented(indent)
      case Input(_, name) =>
        initInput = true
        val symOpt = Try(symTab.getSymbolVar("", name)).toOption
        val readFun = readFunction(symOpt.map(_.tpe))
        s"$name = scanner.$readFun();".indented(indent)
      case Output(_, value, newline) =>
        s"System.out.println($value);".indented(indent)
      case Block(blockId, statements) =>
        statements.map(genStatement).mkString("\n")
      case Return(_, maybeValue) =>
        maybeValue.map(v => s"return $v;").getOrElse("").indented(indent)
      case If(_, condition, trueBlock, falseBlock) =>
        s"""|if ($condition) {
            |${genStatement(trueBlock)}
            |} else {
            |${genStatement(falseBlock)}
            |}""".stripMargin.trim.indented(indent)
      case While(_, condition, block) =>
        s"""|while ($condition) {
            |${genStatement(block)}
            |}""".stripMargin.trim.indented(indent)
      case DoWhile(_, condition, block) =>
        s"""|do {
            |${genStatement(block)}
            |} while ($condition);""".stripMargin.trim.indented(indent)
      case ForLoop(_, varName, start, incr, end, block) =>
        s"""|for (int $varName = $start; i <= $end; i += $incr) {
            |${genStatement(block)}
            |}""".stripMargin.trim.indented(indent)

  private def getType(tpe: Expression.Type): String =
    import Expression.Type, Type._
    tpe match
      case Void    => "void"
      case Integer => "int"
      case Real    => "double"
      case String  => "String"
      case Boolean => "boolean"

  private def readFunction(tpeOpt: Option[Type]): String = tpeOpt match
    case None => "nextLine"
    case Some(tpe) =>
      tpe match
        case Type.Integer => "nextInt"
        case Type.Real    => "nextDouble"
        case Type.Boolean => "nextBoolean"
        case _            => "nextLine"

}
