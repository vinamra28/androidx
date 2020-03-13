/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.plugins.kotlin.compiler.lower

import androidx.compose.plugins.kotlin.ComposableEmitDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrSimpleBuiltinOperatorDescriptorImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetVariable
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrIfThenElseImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrErrorType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeAbbreviation
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.resolve.descriptorUtil.isAnnotationConstructor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.Printer
import java.util.Locale

fun IrElement.dumpSrc(): String {
    val sb = StringBuilder()
    accept(IrSourcePrinterVisitor(sb), null)
    return sb
        .toString()
            // replace tabs at beginning of line with white space
        .replace(Regex("\\n(%tab%)+", RegexOption.MULTILINE)) {
            val size = it.range.last - it.range.first - 1
            "\n" + (0..(size / 5)).joinToString("") { "  " }
        }
            // tabs that are inserted in the middle of lines should be replaced with empty strings
        .replace(Regex("%tab%", RegexOption.MULTILINE), "")
            // remove empty lines
        .replace(Regex("\\n(\\s)*$", RegexOption.MULTILINE), "")
}

private class IrSourcePrinterVisitor(
    out: Appendable
) : IrElementVisitorVoid {
    private val printer = Printer(out, "%tab%")

    private fun IrElement.print() {
        accept(this@IrSourcePrinterVisitor, null)
    }
    private fun print(obj: Any?) = printer.print(obj)
    private fun println(obj: Any?) = printer.println(obj)
    private fun println() = printer.println()

    private inline fun indented(body: () -> Unit) {
        printer.pushIndent()
        body()
        printer.popIndent()
    }

    private inline fun bracedBlock(body: () -> Unit) {
        println("{")
        indented(body)
        println()
        println("}")
    }

    private fun List<IrElement>.printJoin(separator: String = "") {
        forEachIndexed { index, it ->
            it.print()
            if (index < size - 1) print(separator)
        }
    }

    override fun visitModuleFragment(declaration: IrModuleFragment) {
//        println("// MODULE: ${declaration.name}")
        declaration.files.printJoin()
    }

    override fun visitFile(declaration: IrFile) {
//        println("// FILE: ${declaration.fileEntry.name}")
        declaration.declarations.printJoin()
    }

    override fun visitValueParameter(declaration: IrValueParameter) {
        if (declaration.isCrossinline) {
            print("crossinline ")
        }
        if (declaration.isNoinline) {
            print("noinline ")
        }
        declaration.printAnnotations()
        print(declaration.name)
        print(": ")
        print(declaration.type.renderSrc())
        declaration.defaultValue?.let { it ->
            print(" = ")
            it.print()
        }
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.origin == COMPOSABLE_DECOY_IMPL) return
        if (declaration.origin == IrDeclarationOrigin.FAKE_OVERRIDE) return
        declaration.printAnnotations(onePerLine = true)
        if (declaration.overriddenSymbols.isNotEmpty()) {
            print("override ")
        } else {
            if (
                declaration.visibility != Visibilities.PUBLIC &&
                declaration.visibility != Visibilities.LOCAL
            ) {
                print(declaration.visibility.toString().toLowerCase(Locale.ROOT))
                print(" ")
            }
            if (declaration.modality != Modality.FINAL) {
                print(declaration.modality.toString().toLowerCase(Locale.ROOT))
                print(" ")
            }
        }
        if (declaration.isSuspend) {
            print("suspend ")
        }
        print("fun ")
        if (declaration.typeParameters.isNotEmpty()) {
            print("<")
            declaration.typeParameters.printJoin(", ")
            print("> ")
        }
        declaration.extensionReceiverParameter?.let {
            print(it.type.renderSrc())
            print(".")
        }
        print(declaration.name)
        print("(")
        declaration.valueParameters.printJoin(", ")
        print(")")
        if (!declaration.returnType.isUnit()) {
            print(": ")
            print(
                declaration.returnType.renderSrc()
            )
        }
        print(" ")
        declaration.printBody()
    }

    fun IrFunction.printBody() {
        val body = body ?: return
        if (body.statements.isEmpty()) {
            println("{ }")
        } else {
            bracedBlock {
                body.print()
            }
        }
    }

    override fun visitConstructor(declaration: IrConstructor) {
        print("constructor")
        val parameters = declaration.valueParameters
        if (parameters.isNotEmpty()) {
            print("(")
            parameters.printJoin(", ")
            print(")")
        }
        declaration.printBody()
    }

    override fun visitCall(expression: IrCall) {
        val function = expression.symbol.owner
        val name = function.name.asString()
        val descriptor = function.descriptor
        val isOperator = descriptor.isOperator ||
                descriptor is IrSimpleBuiltinOperatorDescriptorImpl
        val isInfix = descriptor.isInfix
        if (isOperator) {
            val opSymbol = when (name) {
                "contains" -> "in"
                "equals" -> "=="
                "plus" -> "+"
                "not" -> "!"
                "minus" -> "-"
                "times" -> "*"
                "div" -> "/"
                "rem" -> "%"
                "rangeTo" -> ".."
                "plusAssign" -> "+="
                "minusAssign" -> "-="
                "timesAssign" -> "*="
                "divAssign" -> "/="
                "remAssign" -> "%="
                "inc" -> "++"
                "dec" -> "--"
                "greater" -> ">"
                "less" -> "<"
                "lessOrEqual" -> "<="
                "greaterOrEqual" -> ">="
                "EQEQ" -> "=="
                "EQEQEQ" -> "==="
                // no names for
                "invoke", "get", "set" -> ""
                "iterator", "hasNext", "next" -> name
                else -> error("Unhandled operator $name")
            }

            when (name) {
                // unary prefx
                "unaryPlus", "unaryMinus", "not" -> {
                    print(opSymbol)
                    expression.dispatchReceiver?.print()
                }
                // unary postfix
                "inc", "dec" -> {
                    expression.dispatchReceiver?.print()
                    print(opSymbol)
                }
                // invoke
                "invoke" -> {
                    expression.dispatchReceiver?.print()
                    expression.printArgumentList()
                }
                // get indexer
                "get" -> {
                    expression.dispatchReceiver?.print()
                    print("[")
                    for (i in 0 until expression.valueArgumentsCount) {
                        val arg = expression.getValueArgument(i)
                        arg?.print()
                    }
                    print("]")
                }
                // set indexer
                "set" -> {
                    expression.dispatchReceiver?.print()
                    print("[")
                    expression.getValueArgument(0)?.print()
                    print("] = ")
                    expression.getValueArgument(1)?.print()
                }
                // builtin static operators
                "greater", "less", "lessOrEqual", "greaterOrEqual", "EQEQ", "EQEQEQ" -> {
                    expression.getValueArgument(0)?.print()
                    print(" $opSymbol ")
                    expression.getValueArgument(1)?.print()
                }
                "iterator", "hasNext", "next" -> {
                    expression.dispatchReceiver?.print()
                    print(".")
                    print(opSymbol)
                    print("()")
                }
                // else binary
                else -> {
                    expression.dispatchReceiver?.print()
                    print(" $opSymbol ")
                    expression.getValueArgument(0)?.print()
                }
            }
            return
        }

        if (isInfix) {
            expression.dispatchReceiver?.print()
                ?: expression.extensionReceiver?.print()
            print(" $name ")
            expression.getValueArgument(0)?.print()
            return
        }

        val dispatchReceiver = expression.dispatchReceiver
        val extensionReceiver = expression.extensionReceiver
        val dispatchIsSpecial = dispatchReceiver.let {
            it is IrGetValue && it.symbol.owner.name.isSpecial
        }
        val extensionIsSpecial = extensionReceiver.let {
            it is IrGetValue && it.symbol.owner.name.isSpecial
        }

        if (dispatchReceiver != null && !dispatchIsSpecial) {
            dispatchReceiver.print()
            print(".")
        } else if (extensionReceiver != null && !extensionIsSpecial) {
            extensionReceiver.print()
            print(".")
        }

        when (function.origin) {
            IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR -> {
                val prop = (function as IrSimpleFunction).correspondingPropertySymbol!!.owner
                val propName = prop.name.asString()
                print(propName)
                if (function == prop.setter) {
                    print(" = ")
                    expression.getValueArgument(0)?.print()
                }
            }
            else -> {
                print(name)
                expression.printArgumentList()
            }
        }
    }

    private fun IrAnnotationContainer.printAnnotations(onePerLine: Boolean = false) {
        if (annotations.isNotEmpty()) {
            annotations.printJoin(if (onePerLine) "\n" else " ")
            if (onePerLine) println()
            else print(" ")
        }
    }

    private fun IrFunctionAccessExpression.printArgumentList() {
        val descriptor = descriptor
        val arguments = mutableListOf<IrExpression>()
        val paramNames = mutableListOf<String>()
        var trailingLambda: IrExpression? = null
        val isEmit = descriptor is ComposableEmitDescriptor
        val isCompoundEmit = descriptor is ComposableEmitDescriptor && descriptor.hasChildren
        val isLeafEmit = isEmit && !isCompoundEmit
        var useParameterNames = isEmit
        for (i in 0 until valueArgumentsCount) {
            val arg = getValueArgument(i)
            if (arg != null) {
                val param = symbol.owner.valueParameters[i]
                val isTrailingLambda = i == symbol.owner.valueParameters.size - 1 &&
                        !isLeafEmit &&
                        param.type.isFunction() &&
                        (
                            arg is IrFunctionExpression ||
                            (arg is IrBlock && arg.origin == IrStatementOrigin.LAMBDA)
                        )
                if (isTrailingLambda) {
                    trailingLambda = arg
                } else {
                    arguments.add(arg)
                    paramNames.add(param.name.asString())
                }
            } else {
                useParameterNames = true
            }
        }
        if (arguments.isNotEmpty() || trailingLambda == null) {
            print("(")
            if (useParameterNames) {
                // if we are using parameter names, we go on multiple lines
                println()
                indented {
                    arguments.zip(paramNames).forEachIndexed { i, (arg, name) ->
                        print(name)
                        print(" = ")
                        arg.print()
                        if (i < arguments.size - 1) println(", ")
                    }
                }
                println()
            } else {
                arguments.printJoin(", ")
            }
            print(")")
        }
        trailingLambda?.let {
            print(" ")
            it.print()
        }
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression) {
        expression.function.printAsLambda()
    }

    fun IrFunction.printAsLambda() {
        print("{")
        val parameters = valueParameters
        if (parameters.isNotEmpty()) {
            print(" ")
            parameters.printJoin(", ")
            println(" ->")
        } else {
            println()
        }
        indented {
            body?.print()
        }
        println()
        println("}")
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall) {
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                expression.argument.print()
            }
            else -> error("Unknown type operator: ${expression.operator}")
        }
    }

    override fun visitComposite(expression: IrComposite) {
        expression.statements.printJoin("\n")
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop) {
        println("do {")
        indented {
            loop.body?.print()
            println()
        }
        print("} while (")
        loop.condition.print()
        println(")")
    }

    override fun visitConstructorCall(expression: IrConstructorCall) {
        val constructedClass = expression.descriptor.constructedClass
        val name = constructedClass.name
        val isAnnotation = expression.descriptor.isAnnotationConstructor()
        if (isAnnotation) {
            print("@")
        }
        expression.dispatchReceiver?.let {
            it.print()
            print(".")
        }
        print(name)
        if (expression.valueArgumentsCount > 0 || !isAnnotation) {
            expression.printArgumentList()
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation) {
        val arguments = expression.arguments
        print("\"")
        for (i in arguments.indices step 2) {
            val stringPart = arguments[i] as IrConst<*>
            val exprPart = arguments[i + 1]
            val isSimpleExpr = when (exprPart) {
                is IrGetValue -> true
                else -> false
            }
            print(stringPart.value)
            print("$")
            if (isSimpleExpr) {
                exprPart.print()
            } else {
                print("{")
                exprPart.print()
                print("}")
            }
        }
        print("\"")
    }

    override fun visitVararg(expression: IrVararg) {
        expression
            .elements
            .map { if (it is IrSpreadElement) it.expression else it as IrExpression }
            .printJoin(", ")
    }

    override fun visitWhen(expression: IrWhen) {
        val isIf = expression.origin == IrStatementOrigin.IF || expression is IrIfThenElseImpl
        when {
            isIf -> {
                expression.branches.forEachIndexed { index, branch ->
                    val isElse = index == expression.branches.size - 1 &&
                            (branch.condition as? IrConst<*>)?.value == true
                    when {
                        index == 0 -> {
                            print("if (")
                            branch.condition.print()
                            println(") {")
                        }
                        isElse -> {
                            println("} else {")
                        }
                        else -> {
                            print("} else if (")
                            branch.condition.print()
                            println(") {")
                        }
                    }
                    indented {
                        branch.result.print()
                        println()
                    }
                }
                println("}")
            }
            else -> {
                print("when ")
                bracedBlock {
                    expression.branches.forEach {
                        val isElse = (it.condition as? IrConst<*>)?.value == true

                        if (isElse) {
                            print("else")
                        } else {
                            it.condition.print()
                        }
                        print(" -> ")
                        bracedBlock {
                            it.result.print()
                        }
                    }
                }
            }
        }
    }

    override fun visitWhileLoop(loop: IrWhileLoop) {
        if (loop.label != null) {
            print(loop.label)
            print("@")
        }
        print("while (")
        loop.condition.print()
        println(") {")
        indented {
            loop.body?.print()
            println()
        }
        println("}")
    }

    override fun visitReturn(expression: IrReturn) {
        val value = expression.value
        // only print the return statement directly if it is not a lambda
        if (expression.returnTarget.name.asString() != "<anonymous>") {
            print("return ")
        }
        if (expression.type.isUnit() || value.type.isUnit()) {
            if (value is IrGetObjectValue) {
                return
            } else {
                value.print()
            }
        } else {
            value.print()
        }
    }

    override fun visitBlock(expression: IrBlock) {
        when (expression.origin) {
            IrStatementOrigin.POSTFIX_INCR -> {
                val tmpVar = expression.statements[0] as IrVariable
                val lhs = tmpVar.initializer ?: error("Expected initializer")
                lhs.print()
                print("++")
            }
            IrStatementOrigin.POSTFIX_DECR -> {
                val tmpVar = expression.statements[0] as IrVariable
                val lhs = tmpVar.initializer ?: error("Expected initializer")
                lhs.print()
                print("--")
            }
            IrStatementOrigin.LAMBDA -> {
                val function = expression.statements[0] as IrFunction
                function.printAsLambda()
            }
            IrStatementOrigin.OBJECT_LITERAL -> {
                val classImpl = expression.statements[0] as IrClass
                classImpl.printAsObject()
            }
            else -> {
                expression.statements.printJoin("\n")
            }
        }
    }

    override fun visitVariable(declaration: IrVariable) {
        if (declaration.isLateinit) {
            print("lateinit")
        }
        when {
            declaration.isConst -> print("const ")
            declaration.isVar -> print("var ")
            else -> print("val ")
        }
        print(declaration.name)
        declaration.initializer?.let {
            print(" = ")
            it.print()
        }
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue) {
        print(expression.symbol.owner.name)
    }

    override fun visitVariableAccess(expression: IrValueAccessExpression) {
        print(expression.symbol.owner.name)
    }

    override fun visitExpressionBody(body: IrExpressionBody) {
        body.expression.accept(this, null)
    }

    override fun visitProperty(declaration: IrProperty) {
        if (declaration.getter?.origin == COMPOSABLE_DECOY_IMPL) return
        if (declaration.isLateinit) {
            print("lateinit")
        }
        when {
            declaration.isConst -> print("const ")
            declaration.isVar -> print("var ")
            else -> print("val ")
        }
        print(declaration.name)
        print(": ")
        val type = declaration.backingField?.type
            ?: declaration.getter?.returnType
            ?: error("Couldn't find return type")
        print(type.renderSrc())
        declaration.backingField?.let { field ->
            field.initializer?.let { initializer ->
                print(" = ")
                initializer.print()
            }
        }
        indented {
            declaration.getter?.let {
                if (it.origin != IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) {
                    println()
                    println("get() {")
                    indented {
                        it.body?.accept(this, null)
                    }
                    println()
                    println("}")
                }
            }
            declaration.setter?.let {
                if (it.origin != IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) {
                    println()
                    println("set(value) {")
                    indented {
                        it.body?.accept(this, null)
                    }
                    println()
                    println("}")
                }
            }
        }
    }

    override fun <T> visitConst(expression: IrConst<T>) {
        val result = when (expression.kind) {
            is IrConstKind.Null -> "${expression.value}"
            is IrConstKind.Boolean -> "${expression.value}"
            is IrConstKind.Char -> "'${expression.value}'"
            is IrConstKind.Byte -> "${expression.value}"
            is IrConstKind.Short -> "${expression.value}"
            is IrConstKind.Int -> "${expression.value}"
            is IrConstKind.Long -> "${expression.value}L"
            is IrConstKind.Float -> "${expression.value}f"
            is IrConstKind.Double -> "${expression.value}"
            is IrConstKind.String -> "\"${expression.value}\""
        }
        print(result)
    }

    override fun visitBlockBody(body: IrBlockBody) {
        body.statements.printJoin("\n")
    }

    private fun IrClass.correspondingProperty(param: IrValueParameter): IrProperty? {
        return declarations
            .mapNotNull { it as? IrProperty }
            .firstOrNull {
                if (it.name == param.name) {
                    val init = it.backingField?.initializer?.expression as? IrGetValue
                    init?.origin == IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER
                } else false
            }
    }

    override fun visitClass(declaration: IrClass) {
        val primaryConstructor = declaration.primaryConstructor
        declaration.printAnnotations(onePerLine = true)
        if (
            declaration.visibility != Visibilities.PUBLIC &&
            declaration.visibility != Visibilities.LOCAL
        ) {
            print(declaration.visibility.toString().toLowerCase(Locale.ROOT))
            print(" ")
        }
        if (declaration.isInner) {
            print("inner ")
        }
        if (declaration.isInterface) {
            print("interface ")
        } else {
            if (declaration.modality != Modality.FINAL) {
                print(declaration.modality.toString().toLowerCase(Locale.ROOT))
                print(" ")
            }
            if (declaration.isAnnotationClass) {
                print("annotation ")
            }
            print("class ")
        }
        print(declaration.name)
        if (declaration.typeParameters.isNotEmpty()) {
            print("<")
            declaration.typeParameters.printJoin(", ")
            print("> ")
        }
        val ctorProperties = mutableSetOf<IrProperty>()
        if (primaryConstructor != null) {
            if (primaryConstructor.valueParameters.isNotEmpty()) {
                print("(")
                primaryConstructor.valueParameters.forEachIndexed { index, param ->
                    val property = declaration.correspondingProperty(param)
                    if (property != null) {
                        ctorProperties.add(property)
                        print(if (property.isVar) "var " else "val ")
                    }
                    param.print()
                    if (index < primaryConstructor.valueParameters.size - 1) print(", ")
                }
                print(")")
            }
        }
        print(" ")
        if (declaration.superTypes.any { !it.isAny() }) {
            print(": ")
            print(declaration.superTypes.joinToString(", ") { it.renderSrc() })
            print(" ")
        }
        val nonParamDeclarations = declaration
            .declarations
            .filter { it != primaryConstructor && !ctorProperties.contains(it) }
            .filter { it.origin != IrDeclarationOrigin.FAKE_OVERRIDE }
        if (nonParamDeclarations.isNotEmpty()) {
            bracedBlock {
                nonParamDeclarations.printJoin("\n")
            }
        } else {
            println()
        }
    }

    fun IrClass.printAsObject() {
        print("object ")
        if (!name.isSpecial) {
            print(name)
            print(" ")
        }
        if (superTypes.any { !it.isAny() }) {
            print(": ")
            print(superTypes.joinToString(", ") { it.renderSrc() })
            print(" ")
        }
        val printableDeclarations = declarations
            .filter { it !is IrConstructor }
            .filter { it.origin != IrDeclarationOrigin.FAKE_OVERRIDE }
        if (printableDeclarations.isNotEmpty()) {
            bracedBlock {
                printableDeclarations.printJoin("\n")
            }
        } else {
            println()
        }
    }

    override fun visitBreak(jump: IrBreak) {
        print("break")
        if (jump.label != null) {
            print("@")
            print(jump.label)
        }
    }

    override fun visitContinue(jump: IrContinue) {
        print("continue")
        if (jump.label != null) {
            print("@")
            print(jump.label)
        }
    }

    override fun visitTypeParameter(declaration: IrTypeParameter) {
        print(declaration.name)
        val isNonEmpty = declaration.superTypes.isNotEmpty() &&
                !declaration.superTypes[0].isNullableAny()
        if (isNonEmpty) {
            print(": ")
            print(declaration.superTypes.joinToString(", ") { it.renderSrc() })
        }
    }

    override fun visitThrow(expression: IrThrow) {
        print("throw ")
        expression.value.print()
    }

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression) {
        super.visitFunctionAccess(expression)
    }

    override fun visitGetValue(expression: IrGetValue) {
        super.visitGetValue(expression)
    }

    override fun visitSetVariable(expression: IrSetVariable) {
        super.visitSetVariable(expression)
    }

    override fun visitBranch(branch: IrBranch) {
        print("<<BRANCH>>")
    }

    override fun visitBreakContinue(jump: IrBreakContinue) {
        print("<<BREAKCONTINUE>>")
    }

    override fun visitCatch(aCatch: IrCatch) {
        print("<<CATCH>>")
    }

    override fun visitContainerExpression(expression: IrContainerExpression) {
        print("<<CONTAINEREXPR>>")
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall) {
        print("<<DELEGATINGCTORCALL>>")
    }

    override fun visitElseBranch(branch: IrElseBranch) {
        print("<<ELSE>>")
    }

    override fun visitFunction(declaration: IrFunction) {
        print("<<FUNCTION>>")
    }

    override fun visitFunctionReference(expression: IrFunctionReference) {
        print("<<FUNCTIONREF>>")
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall) {
        print("<<INSTINIT>>")
    }

    override fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty) {
        print("<<LOCALDELPROP>>")
    }

    override fun visitLocalDelegatedPropertyReference(
        expression: IrLocalDelegatedPropertyReference
    ) {
        print("<<LOCALDELPROPREF>>")
    }

    override fun visitLoop(loop: IrLoop) {
        print("<<LOOP>>")
    }

    override fun visitPropertyReference(expression: IrPropertyReference) {
        print("<<PROPREF>>")
    }

    override fun visitSpreadElement(spread: IrSpreadElement) {
        print("<<SPREAD>>")
    }

    override fun visitTry(aTry: IrTry) {
        print("<<TRY>>")
    }

    override fun visitTypeAlias(declaration: IrTypeAlias) {
        print("<<TYPEALIAS>>")
    }

    private fun IrType.renderSrc() =
        "${renderTypeAnnotations(annotations)}${renderTypeInner()}"

    private fun IrType.renderTypeInner() =
        when (this) {
            is IrDynamicType -> "dynamic"

            is IrErrorType -> "IrErrorType"

            is IrSimpleType -> buildTrimEnd {
                append(classifier.descriptor.name)
                if (arguments.isNotEmpty()) {
                    append(
                        arguments.joinToString(prefix = "<", postfix = ">", separator = ", ") {
                            it.renderTypeArgument()
                        }
                    )
                }
                if (hasQuestionMark) {
                    append('?')
                }
                abbreviation?.let {
                    append(it.renderTypeAbbreviation())
                }
            }

            else -> "{${javaClass.simpleName} $this}"
        }

    private inline fun buildTrimEnd(fn: StringBuilder.() -> Unit): String =
        buildString(fn).trimEnd()

    private fun IrTypeAbbreviation.renderTypeAbbreviation(): String =
        buildString {
            append("{ ")
            append(renderTypeAnnotations(annotations))
            append(typeAlias.renderTypeAliasFqn())
            if (arguments.isNotEmpty()) {
                append(
                    arguments.joinToString(prefix = "<", postfix = ">", separator = ", ") {
                        it.renderTypeArgument()
                    }
                )
            }
            if (hasQuestionMark) {
                append('?')
            }
            append(" }")
        }

    private fun IrTypeArgument.renderTypeArgument(): String =
        when (this) {
            is IrStarProjection -> "*"

            is IrTypeProjection -> buildTrimEnd {
                append(variance.label)
                if (variance != Variance.INVARIANT) append(' ')
                append(type.renderSrc())
            }

            else -> "IrTypeArgument[$this]"
        }

    private fun renderTypeAnnotations(annotations: List<IrConstructorCall>) =
        if (annotations.isEmpty())
            ""
        else
            annotations.joinToString(prefix = "", postfix = " ", separator = " ") {
                "@[${renderAsAnnotation(it)}]"
            }

    private fun renderAsAnnotation(irAnnotation: IrConstructorCall): String =
        StringBuilder().also { it.renderAsAnnotation(irAnnotation) }.toString()

    private fun StringBuilder.renderAsAnnotation(irAnnotation: IrConstructorCall) {
        val annotationClassName = try {
            irAnnotation.symbol.owner.parentAsClass.name.asString()
        } catch (e: Exception) {
            "<unbound>"
        }
        append(annotationClassName)

        if (irAnnotation.valueArgumentsCount == 0) return

        val valueParameterNames = irAnnotation.getValueParameterNamesForDebug()
        var first = true
        append("(")
        for (i in 0 until irAnnotation.valueArgumentsCount) {
            if (first) {
                first = false
            } else {
                append(", ")
            }
            append(valueParameterNames[i])
            append(" = ")
            renderAsAnnotationArgument(irAnnotation.getValueArgument(i))
        }
        append(")")
    }

    private fun IrTypeAliasSymbol.renderTypeAliasFqn(): String =
        if (isBound)
            StringBuilder().also { owner.renderDeclarationFqn(it) }.toString()
        else
            "<unbound $this: ${this.descriptor}>"

    private fun IrDeclaration.renderDeclarationFqn(sb: StringBuilder) {
        renderDeclarationParentFqn(sb)
        sb.append('.')
        if (this is IrDeclarationWithName) {
            sb.append(name.asString())
        } else {
            sb.append(this)
        }
    }

    private fun IrDeclaration.renderDeclarationParentFqn(sb: StringBuilder) {
        try {
            val parent = this.parent
            if (parent is IrDeclaration) {
                parent.renderDeclarationFqn(sb)
            } else if (parent is IrPackageFragment) {
                sb.append(parent.fqName.toString())
            }
        } catch (e: UninitializedPropertyAccessException) {
            sb.append("<uninitialized parent>")
        }
    }

    private fun IrMemberAccessExpression.getValueParameterNamesForDebug(): List<String> {
        val expectedCount = valueArgumentsCount
        return if (this is IrDeclarationReference && symbol.isBound) {
            val owner = symbol.owner
            if (owner is IrFunction) {
                (0 until expectedCount).map {
                    if (it < owner.valueParameters.size)
                        owner.valueParameters[it].name.asString()
                    else
                        "${it + 1}"
                }
            } else {
                getPlaceholderParameterNames(expectedCount)
            }
        } else
            getPlaceholderParameterNames(expectedCount)
    }

    private fun getPlaceholderParameterNames(expectedCount: Int) =
        (1..expectedCount).map { "$it" }

    private fun StringBuilder.renderAsAnnotationArgument(irElement: IrElement?) {
        when (irElement) {
            null -> append("<null>")
            is IrConstructorCall -> renderAsAnnotation(irElement)
            is IrConst<*> -> {
                append('\'')
                append(irElement.value.toString())
                append('\'')
            }
            is IrVararg -> {
                appendListWith(irElement.elements, "[", "]", ", ") {
                    renderAsAnnotationArgument(it)
                }
            }
            else -> append(irElement.accept(this@IrSourcePrinterVisitor, null))
        }
    }

    override fun visitElement(element: IrElement) {
        print("<<${element::class.java.simpleName}>>")
    }
}

private inline fun <T> StringBuilder.appendListWith(
    list: List<T>,
    prefix: String,
    postfix: String,
    separator: String,
    renderItem: StringBuilder.(T) -> Unit
) {
    append(prefix)
    var isFirst = true
    for (item in list) {
        if (!isFirst) append(separator)
        renderItem(item)
        isFirst = false
    }
    append(postfix)
}