package kotlinx.html.generate

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

fun String.quote() = "\"$this\""

fun Appendable.attributePseudoDelegate(request: AttributeRequest) {
    val classNamePrefix = request.type.classPrefix
    val className = "${classNamePrefix}Attribute"

    append("internal ")
    variable(Var(request.delegatePropertyName, "Attribute<${request.typeName}>"))
    defineIs(StringBuilder().apply {
        functionCallConsts(className, request.options)
    })
    emptyLine()
}

fun Appendable.attributeProperty(
    repository: Repository,
    attribute: AttributeInfo,
    receiver: String? = null,
    indent: Int = 1
) {
    val attributeName = attribute.name
    val request = tagAttributeVar(repository, attribute, receiver, indent)
    append("\n")

    indent(indent)
    getter().defineIs(StringBuilder().apply {
        append(request.delegatePropertyName).append("[this, ${attributeName.quote()}]")
    })

    indent(indent)
    setter {
        append(request.delegatePropertyName).append("[this, ${attributeName.quote()}] = newValue")
    }

    emptyLine()
}

fun Appendable.facade(repository: Repository, facade: AttributeFacade) {
    clazz(Clazz(facade.className, isInterface = true, parents = facade.parents)) {
    }

    facade.declaredAttributes.filter { !isAttributeExcluded(it.name) }.forEach { attribute ->
        if (attribute.name.isLowerCase() || attribute.name.lowercase() !in facade.attributeNames) {
            attributeProperty(repository, attribute, receiver = facade.className, indent = 0)
        }
    }
}

fun Appendable.eventProperty(parent: String, attribute: AttributeInfo, shouldUnsafeCast: Boolean) {
    val type = "(org.w3c.dom.events.Event) -> Unit"
    variable(
        receiver = parent,
        variable = Var(
            name = attribute.fieldName + "Function",
            type = type,
            varType = VarType.MUTABLE,
        )
    )
    emptyLine()

    getter().defineIs(StringBuilder().apply {
        append("throw ")
        functionCall("UnsupportedOperationException", listOf("You can't read variable ${attribute.fieldName}".quote()))
    })
    setter {
        receiverDot("consumer")
        val newValue = if (shouldUnsafeCast) {
            "newValue.unsafeCast<(Event) -> Unit>()"
        } else {
            "newValue"
        }
        functionCall(
            "onTagEvent", listOf(
                "this",
                attribute.name.quote(),
                newValue
            )
        )
    }
    emptyLine()
}

fun eventProperty(parent: TypeName, attribute: AttributeInfo, shouldUnsafeCast: Boolean): PropertySpec {
    val propertyType = LambdaTypeName.get(
        returnType = ClassName("kotlin", "Unit"),
        parameters = listOf(ParameterSpec.unnamed(ClassName("kotlinx.html.org.w3c.dom.events", "Event"))),
    )
    return PropertySpec.builder(attribute.fieldName + "Function", propertyType)
        .mutable()
        .receiver(parent)
        .getter(
            FunSpec.getterBuilder()
                .addStatement("throw UnsupportedOperationException(\"You can't read variable ${attribute.fieldName}\")")
                .build()
        )
        .setter(
            FunSpec.setterBuilder()
                .addParameter("newValue", propertyType)
                .addStatement(
                    "consumer.onTagEvent(this, %S, %L)",
                    attribute.name,
                    if (shouldUnsafeCast) {
                        "newValue.unsafeCast<(Event) -> Unit>()"
                    } else {
                        "newValue"
                    }
                )
                .build()
        )
        .build()
}
