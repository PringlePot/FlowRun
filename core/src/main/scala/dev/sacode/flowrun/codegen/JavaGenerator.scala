package dev.sacode.flowrun.codegen

import scala.util.Try
import dev.sacode.flowrun.toIdentifier
import dev.sacode.flowrun.FlowRun
import dev.sacode.flowrun.ast.*, Expression.Type
import dev.sacode.flowrun.eval.SymbolTable
import dev.sacode.flowrun.eval.SymbolKey
import dev.sacode.flowrun.eval.Symbol

class JavaGenerator(val programAst: Program) extends CodeGenerator {

  def generate: Try[CodeGenRes] = Try {

    if programAst.hasInputs then
      addLine("import java.util.*;", programAst.main.id)
      addEmptyLine()

    addLine(s"public class ${programAst.name.toIdentifier} {", programAst.main.id)

    incrIndent()
    if programAst.hasInputs then addLine("static Scanner scanner = new Scanner(System.in);", "")
    genMain()
    programAst.functions.foreach(genFunction)
    decrIndent()

    addLine("}", programAst.main.id)

    CodeGenRes(lines.toList, stmtLineNums.toMap)
  }

  private def genMain(): Unit = {
    val function = programAst.main
    symTab.enterScope(function.id, function.name)

    addEmptyLine()
    addLine(
      "public static void main(String args[]) {",
      function.statements.head.id
    )

    incrIndent()
    function.statements.foreach(genStatement)
    decrIndent()

    addLine("}", function.statements.last.id)

    symTab.exitScope()
  }

  private def genFunction(function: Function): Unit = {
    symTab.enterScope(function.id, function.name)

    val params = function.parameters.map(p => s"${genType(p.tpe)} ${p.name}").mkString(", ")
    addEmptyLine()
    addLine(
      s"public static ${genType(function.tpe)} ${function.name}($params) {",
      function.statements.head.id
    )

    incrIndent()
    function.statements.foreach(genStatement)
    decrIndent()

    addLine("}", function.id)

    symTab.exitScope()
  }

  private def genStatement(stmt: Statement): Unit = {
    import Statement._
    stmt match {
      case _: Begin => // noop

      case Declare(id, name, tpe, maybeInitValue) =>
        val key = SymbolKey(name, Symbol.Kind.Variable, id)
        symTab.add(id, key, tpe, None)
        val initValue = maybeInitValue.getOrElse(defaultValue(tpe))
        val initValueExpr = parseGenExpr(initValue)
        addLine(s"${genType(tpe)} $name = $initValueExpr;", id)

      case Assign(id, name, value) =>
        val genValue = parseGenExpr(value)
        addLine(s"$name = $genValue;", id)

      case Call(id, value) =>
        val genValue = parseGenExpr(value)
        addLine(s"$genValue;", id)

      case Input(id, name, promptOpt) =>
        val prompt = promptOpt.getOrElse(s"Please enter $name: ")
        addLine(s"""System.out.print("$prompt");""", id)

        val symOpt = Try(symTab.getSymbolVar("", name)).toOption
        val readFun = readFunction(symOpt.map(_.tpe))
        addLine(s"$name = $readFun;", id)

      case Output(id, value, newline) =>
        val genValue = parseGenExpr(value)
        val text =
          if newline then s"System.out.println($genValue);"
          else s"System.out.print($genValue);"
        addLine(text, id)

      case Block(_, statements) =>
        incrIndent()
        statements.foreach(genStatement)
        decrIndent()

      case Return(id, maybeValue) =>
        maybeValue.foreach { value =>
          val genValue = parseGenExpr(value)
          addLine(s"return $genValue;", id)
        }

      case If(id, condition, trueBlock, falseBlock) =>
        val genCond = parseGenExpr(condition)
        addLine(s"if ($genCond) {", id)
        genStatement(trueBlock)
        addLine("} else {", id)
        genStatement(falseBlock)
        addLine("}", id)

      case While(id, condition, block) =>
        val genCond = parseGenExpr(condition)
        addLine(s"while ($genCond) {", id)
        genStatement(block)
        addLine("}", id)

      case DoWhile(id, condition, block) =>
        val genCond = parseGenExpr(condition)
        addLine(s"do {", id)
        genStatement(block)
        addLine(s"} while ($genCond);", id)

      case ForLoop(id, varName, start, incr, end, block) =>
        val genStart = parseGenExpr(start)
        val genIncr = parseGenExpr(incr)
        val genEnd = parseGenExpr(end)
        addLine(s"for (int $varName = $genStart; i <= $genEnd; i += $genIncr) {", id)
        genStatement(block)
        addLine("}", id)
    }
  }

  import PredefinedFunction.*
  override def predefFun(name: String, genArgs: List[String]): String = {
    def argOpt(idx: Int) = genArgs.lift(idx).getOrElse("")
    PredefinedFunction.withName(name).get match {
      case Abs           => s"Math.abs(${argOpt(0)})"
      case Floor         => s"Math.floor(${argOpt(0)})"
      case Ceil          => s"Math.ceil(${argOpt(0)})"
      case RandomInteger => s"Math.abs(${argOpt(0)})" //TODO
      case Sin           => s"Math.sin(${argOpt(0)})"
      case Cos           => s"Math.cos(${argOpt(0)})"
      case Tan           => s"Math.tan(${argOpt(0)})"
      case Ln            => s"Math.log(${argOpt(0)})"
      case Log10         => s"Math.log10(${argOpt(0)})"
      case Log2          => s"Math.log10(${argOpt(0)})/Math.log10(2)"
      case Length        => s"${argOpt(0)}.length()"
      case CharAt        => s"${argOpt(0)}.charAt(${argOpt(1)})"
      case RealToInteger => s"(int)${argOpt(0)}"
      case StringToInteger =>
        s"""Integer.parseInt(${argOpt(0)})"""
    }
  }

  override def funCall(name: String, genArgs: List[String]): String =
    s""" $name(${genArgs.mkString(", ")}) """.trim

  /* TYPE */
  private def genType(tpe: Expression.Type): String =
    import Expression.Type, Type._
    tpe match
      case Void    => "void"
      case Integer => "int"
      case Real    => "double"
      case String  => "String"
      case Boolean => "boolean"

  /* OTHER */
  private def readFunction(tpeOpt: Option[Type]): String = tpeOpt match
    case None => "scanner.nextLine()"
    case Some(tpe) =>
      tpe match
        case Type.Integer => "scanner.nextInt()"
        case Type.Real    => "scanner.nextDouble()"
        case Type.Boolean => "scanner.nextBoolean()"
        case _            => "scanner.nextLine()"

}
