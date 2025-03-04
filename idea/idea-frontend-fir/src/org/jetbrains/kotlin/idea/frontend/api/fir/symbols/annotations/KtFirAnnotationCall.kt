/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.symbols.annotations

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.idea.fir.findPsi
import org.jetbrains.kotlin.idea.fir.low.level.api.lazy.resolve.ResolveType
import org.jetbrains.kotlin.idea.frontend.api.fir.utils.*
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtAnnotationCall
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtNamedConstantValue
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallElement

internal class KtFirAnnotationCall(
    private val containingDeclaration: FirRefWithValidityCheck<FirDeclaration>,
    annotation: FirAnnotation
) : KtAnnotationCall() {

    private val annotationCallRef by weakRef(annotation)

    override val token: ValidityToken get() = containingDeclaration.token

    override val psi: KtCallElement? by containingDeclaration.withFirAndCache { fir ->
        annotationCallRef.findPsi(fir.moduleData.session) as? KtCallElement
    }

    override val classId: ClassId? by cached {
        containingDeclaration.withFirByType(ResolveType.AnnotationType) { fir ->
            annotationCallRef.getClassId(fir.moduleData.session)
        }
    }

    override val useSiteTarget: AnnotationUseSiteTarget? get() = annotationCallRef.useSiteTarget

    override val arguments: List<KtNamedConstantValue> by containingDeclaration.withFirAndCache(ResolveType.AnnotationsArguments) { fir ->
        mapAnnotationParameters(annotationCallRef, fir.moduleData.session).map { (name, expression) ->
            KtNamedConstantValue(name, expression.convertConstantExpression())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KtFirAnnotationCall) return false
        if (this.token != other.token) return false
        return annotationCallRef == other.annotationCallRef
    }

    override fun hashCode(): Int {
        return token.hashCode() * 31 + annotationCallRef.hashCode()
    }
}
