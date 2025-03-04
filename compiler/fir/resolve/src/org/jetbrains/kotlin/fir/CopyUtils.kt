/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.scopes.impl.FirIntegerOperatorCall
import org.jetbrains.kotlin.fir.scopes.impl.FirIntegerOperatorCallBuilder
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildErrorTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef

fun FirFunctionCall.copy(
    annotations: List<FirAnnotation> = this.annotations,
    argumentList: FirArgumentList = this.argumentList,
    calleeReference: FirNamedReference = this.calleeReference,
    explicitReceiver: FirExpression? = this.explicitReceiver,
    dispatchReceiver: FirExpression = this.dispatchReceiver,
    extensionReceiver: FirExpression = this.extensionReceiver,
    source: FirSourceElement? = this.source,
    typeArguments: List<FirTypeProjection> = this.typeArguments,
    resultType: FirTypeRef = this.typeRef
): FirFunctionCall {
    val builder = if (this is FirIntegerOperatorCall) {
        FirIntegerOperatorCallBuilder().apply {
            this.calleeReference = calleeReference
        }
    } else {
        FirFunctionCallBuilder().apply {
            this.calleeReference = calleeReference
        }
    }
    builder.apply {
        this.source = source
        this.annotations.addAll(annotations)
        this.argumentList = argumentList
        this.explicitReceiver = explicitReceiver
        this.dispatchReceiver = dispatchReceiver
        this.extensionReceiver = extensionReceiver
        this.typeArguments.addAll(typeArguments)
        this.typeRef = resultType
    }
    return (builder as FirCallBuilder).build() as FirFunctionCall
}

internal inline fun FirFunctionCall.copyAsImplicitInvokeCall(
    setupCopy: FirImplicitInvokeCallBuilder.() -> Unit
): FirImplicitInvokeCall {
    val original = this

    return buildImplicitInvokeCall {
        source = original.source
        annotations.addAll(original.annotations)
        typeArguments.addAll(original.typeArguments)
        explicitReceiver = original.explicitReceiver
        dispatchReceiver = original.dispatchReceiver
        extensionReceiver = original.extensionReceiver
        argumentList = original.argumentList
        calleeReference = original.calleeReference

        setupCopy()
    }
}

fun FirAnonymousFunction.copy(
    receiverTypeRef: FirTypeRef? = this.receiverTypeRef,
    source: FirSourceElement? = this.source,
    moduleData: FirModuleData = this.moduleData,
    origin: FirDeclarationOrigin = this.origin,
    returnTypeRef: FirTypeRef = this.returnTypeRef,
    valueParameters: List<FirValueParameter> = this.valueParameters,
    body: FirBlock? = this.body,
    annotations: List<FirAnnotation> = this.annotations,
    typeRef: FirTypeRef = this.typeRef,
    label: FirLabel? = this.label,
    controlFlowGraphReference: FirControlFlowGraphReference? = this.controlFlowGraphReference,
    invocationKind: EventOccurrencesRange? = this.invocationKind
): FirAnonymousFunction {
    return buildAnonymousFunction {
        this.source = source
        this.moduleData = moduleData
        this.origin = origin
        this.returnTypeRef = returnTypeRef
        this.receiverTypeRef = receiverTypeRef
        symbol = this@copy.symbol
        isLambda = this@copy.isLambda
        this.valueParameters.addAll(valueParameters)
        this.body = body
        this.annotations.addAll(annotations)
        this.typeRef = typeRef
        this.label = label
        this.controlFlowGraphReference = controlFlowGraphReference
        this.invocationKind = invocationKind
    }
}

fun FirTypeRef.resolvedTypeFromPrototype(
    type: ConeKotlinType
): FirResolvedTypeRef {
    return if (type is ConeKotlinErrorType) {
        buildErrorTypeRef {
            source = this@resolvedTypeFromPrototype.source
            diagnostic = type.diagnostic
        }
    } else {
        buildResolvedTypeRef {
            source = this@resolvedTypeFromPrototype.source
            this.type = type
            annotations += this@resolvedTypeFromPrototype.annotations
        }
    }
}

fun FirTypeRef.errorTypeFromPrototype(
    diagnostic: ConeDiagnostic
): FirErrorTypeRef {
    return buildErrorTypeRef {
        source = this@errorTypeFromPrototype.source
        this.diagnostic = diagnostic
    }
}

fun FirTypeParameter.copy(
    bounds: List<FirTypeRef> = this.bounds,
    annotations: List<FirAnnotation> = this.annotations
): FirTypeParameter {
    return buildTypeParameter {
        source = this@copy.source
        resolvePhase = this@copy.resolvePhase
        moduleData = this@copy.moduleData
        name = this@copy.name
        symbol = this@copy.symbol
        variance = this@copy.variance
        isReified = this@copy.isReified
        this.bounds += bounds
        this.annotations += annotations
    }
}

fun FirWhenExpression.copy(
    resultType: FirTypeRef = this.typeRef,
    calleeReference: FirReference = this.calleeReference,
    annotations: List<FirAnnotation> = this.annotations
): FirWhenExpression = buildWhenExpression {
    source = this@copy.source
    subject = this@copy.subject
    subjectVariable = this@copy.subjectVariable
    this.calleeReference = calleeReference
    branches += this@copy.branches
    typeRef = resultType
    this.annotations += annotations
    usedAsExpression = this@copy.usedAsExpression
    exhaustivenessStatus = this@copy.exhaustivenessStatus
}

fun FirTryExpression.copy(
    resultType: FirTypeRef = this.typeRef,
    calleeReference: FirReference = this.calleeReference,
    annotations: List<FirAnnotation> = this.annotations
): FirTryExpression = buildTryExpression {
    source = this@copy.source
    tryBlock = this@copy.tryBlock
    finallyBlock = this@copy.finallyBlock
    this.calleeReference = calleeReference
    catches += this@copy.catches
    typeRef = resultType
    this.annotations += annotations
}

fun FirCheckNotNullCall.copy(
    resultType: FirTypeRef = this.typeRef,
    calleeReference: FirReference = this.calleeReference,
    annotations: List<FirAnnotation> = this.annotations
): FirCheckNotNullCall = buildCheckNotNullCall {
    source = this@copy.source
    this.calleeReference = calleeReference
    argumentList = this@copy.argumentList
    this.typeRef = resultType
    this.annotations += annotations
}

fun FirDeclarationStatus.copy(
    isExpect: Boolean = this.isExpect,
    newModality: Modality? = null,
    newVisibility: Visibility? = null,
    newEffectiveVisibility: EffectiveVisibility? = null,
    isOperator: Boolean = this.isOperator
): FirDeclarationStatus {
    return if (this.isExpect == isExpect && newModality == null && newVisibility == null && this.isOperator == isOperator) {
        this
    } else {
        require(this is FirDeclarationStatusImpl) { "Unexpected class ${this::class}" }
        this.resolved(
            newVisibility ?: visibility,
            newModality ?: modality!!,
            newEffectiveVisibility ?: EffectiveVisibility.Public
        ).apply {
            this.isExpect = isExpect
            this.isOperator = isOperator
        }
    }
}
