package ic.org.ast.build

/**
 * We check the semantics of a WACC program by building an AST that enforces a valid representation. While building
 * this AST, if something invalid is encoutered (like `int x = true`) we return a list of errors (defined in the
 * package util) instead.
 *
 * Thus, we use a Validated in order to return either a succesful value or a list of errors, and the caller dels with
 * that accordingly, thanks to higher order functions like `validate` (see util package).
 */
import antlr.WACCParser.*
import arrow.core.Validated.Valid
import arrow.core.invalid
import arrow.core.valid
import ic.org.ast.*
import ic.org.util.*
import kotlinx.collections.immutable.plus

/**
 * Entry level of recursive AST conversion. Takes a [Scope], [gScope] or creates a [GlobalScope]
 * by default, from which all scopes (except [FuncScope]s) inherit from.
 *
 * Validates every function's _header_ first (in order to enable mutual recursion), then the actual functions, then the
 * body of the program.
 *
 * Symbol tables of every body are represented as [Scope]s.
 * Every other construct of the program has a representation defined in the package [ast].
 */
fun ProgContext.asAst(gScope: GlobalScope = GlobalScope()): Parsed<Prog> {
  val funcs = func()
    // Parse every function's arguments and put each identifier in the [gScope], but conserve antlr's context
    .map { ctx ->
      val t = ctx.type().asAst()
      val ident = Ident(ctx.ID().text)
      val params = ctx.paramsAsAst()
      val funcId = FuncIdent(t, ident, params.valids)
      val validatedFuncId = gScope.addFunction(startPosition, funcId)
      Triple(ctx, funcId, validatedFuncId)
    } // Build the Function's AST and combine its errors with the possible errors from duplicate function definition errors.
    .map { (ctx, fId, alreadyDefinedErrors: Parsed<FuncIdent>) ->
      ctx.asAst(gScope, fId.params).combineWith(alreadyDefinedErrors) { ast, _ -> ast }
    }
  val stat = stat().asAst(gScope)

  return if (funcs.areAllValid && stat is Valid)
    Prog(funcs.valids).valid()
  else
    (funcs.errors + stat.errors).invalid()
}

/**
 * Extends Antlr [FuncContext] by converting each parameters in a function definition to our representation of an AST.
 * Also checks for repeated parameters and returns a [DuplicateParamError] if any duplicate parameters are found
 */
fun FuncContext.paramsAsAst(): List<Parsed<Param>> {
  val antlrParams = param_list()?.param() ?: emptyList()
  val counts = HashMap<Param, Int>()
  return antlrParams
    .map { it.asAst() to it }
    // Count how many parameters with this name exist for this function
    .onEach { (parsed, _) -> counts[parsed] = (counts[parsed] ?: 0) + 1 }
    // Validate that there is only a single occurrence of each
    .map { (parsed, ctx) ->
      parsed.valid().validate({ counts[it] == 1 },
        { DuplicateParamError(ctx.startPosition, it.ident) })
    }
}

/**
 * Extends antlr's [FuncContext] by converting it to our representation of a function [Func].
 * Takes a [GlobalScope] [gScope] as well as list of [Param]s. Creates a [FuncScope] which is a child of the [GlobalScope]
 * Adds each parameter ot the [FuncScope]
 */
fun FuncContext.asAst(gScope: GlobalScope, params: List<Param>): Parsed<Func> {
  val ident = Ident(this.ID().text)
  val funcScope = FuncScope(ident, gScope)
  val antlrParams = param_list()?.param() ?: emptyList()

  val type = type().asAst()

  params.forEachIndexed { i, p ->
    funcScope.addVariable(antlrParams[i].startPosition, ParamVariable(p))
  }

  return stat().asAst(funcScope).map { body -> Func(type, ident, params, body) }
}

private fun ParamContext.asAst(): Param = Param(type().asAst(), Ident(ID().text))

internal fun Pair_elem_typeContext.asAst(): Type =
  when (this) {
    is BaseTPairElemContext -> base_type().asAst()
    is ArrayPairElemContext -> array_type().asAst()
    // Pair type not defined yet, it must be inferred by the caller
    else -> AnyPairTs()
  }