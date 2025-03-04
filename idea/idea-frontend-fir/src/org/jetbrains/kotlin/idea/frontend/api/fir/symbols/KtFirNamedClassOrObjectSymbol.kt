/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.symbols

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.idea.fir.findPsi
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveState
import org.jetbrains.kotlin.idea.frontend.api.fir.KtSymbolByFirBuilder
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.annotations.containsAnnotation
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.annotations.getAnnotationClassIds
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.annotations.toAnnotationsList
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.pointers.KtFirClassOrObjectInLibrarySymbolPointer
import org.jetbrains.kotlin.idea.frontend.api.fir.utils.cached
import org.jetbrains.kotlin.idea.frontend.api.fir.utils.firRef
import org.jetbrains.kotlin.idea.frontend.api.fir.utils.weakRef
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtClassKind
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtAnnotationCall
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.idea.frontend.api.symbols.pointers.CanNotCreateSymbolPointerForLocalLibraryDeclarationException
import org.jetbrains.kotlin.idea.frontend.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.idea.frontend.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class KtFirNamedClassOrObjectSymbol(
    fir: FirRegularClass,
    resolveState: FirModuleResolveState,
    override val token: ValidityToken,
    _builder: KtSymbolByFirBuilder
) : KtNamedClassOrObjectSymbol(), KtFirSymbol<FirRegularClass> {
    private val builder by weakRef(_builder)
    override val firRef = firRef(fir, resolveState)
    override val psi: PsiElement? by firRef.withFirAndCache { fir -> fir.findPsi(fir.moduleData.session) }
    override val name: Name get() = firRef.withFir { it.name }
    override val classIdIfNonLocal: ClassId?
        get() = firRef.withFir { fir ->
            fir.symbol.classId.takeUnless { it.isLocal }
        }

    /* FirRegularClass modality does not modified by STATUS so it can be taken from RAW */
    override val modality: Modality
        get() = getModality(
            FirResolvePhase.RAW_FIR,
            when (classKind) { // default modality
                KtClassKind.INTERFACE -> Modality.ABSTRACT
                // Enum class should not be `final`, since its entries extend it.
                // It could be either `abstract` w/o ctor, or empty modality w/ private ctor.
                KtClassKind.ENUM_CLASS -> Modality.OPEN
                else -> Modality.FINAL
            }
        )

    /* FirRegularClass visibility are not modified by STATUS only for Unknown so it can be taken from RAW */
    override val visibility: Visibility
        get() = when (val possiblyRawVisibility = getVisibility(FirResolvePhase.RAW_FIR)) {
            Visibilities.Unknown -> if (firRef.withFir { it.isLocal }) Visibilities.Local else Visibilities.Public
            else -> possiblyRawVisibility
        }

    override val annotations: List<KtAnnotationCall> by cached { firRef.toAnnotationsList() }
    override fun containsAnnotation(classId: ClassId): Boolean = firRef.containsAnnotation(classId)
    override val annotationClassIds: Collection<ClassId> by cached { firRef.getAnnotationClassIds() }

    override val isInner: Boolean get() = firRef.withFir { it.isInner }
    override val isData: Boolean get() = firRef.withFir { it.isData }
    override val isInline: Boolean get() = firRef.withFir { it.isInline }
    override val isFun: Boolean get() = firRef.withFir { it.isFun }
    override val isExternal: Boolean get() = firRef.withFir { it.isExternal }

    override val companionObject: KtFirNamedClassOrObjectSymbol? by firRef.withFirAndCache { fir ->
        fir.companionObject?.let { builder.classifierBuilder.buildNamedClassOrObjectSymbol(it) }
    }

    override val superTypes: List<KtTypeAndAnnotations> by cached {
        firRef.superTypesAndAnnotationsListForRegularClass(builder)
    }

    override val typeParameters by firRef.withFirAndCache { fir ->
        fir.typeParameters.filterIsInstance<FirTypeParameter>().map { typeParameter ->
            builder.classifierBuilder.buildTypeParameterSymbol(typeParameter.symbol.fir)
        }
    }

    override val classKind: KtClassKind
        get() = firRef.withFir { fir ->
            when (fir.classKind) {
                ClassKind.INTERFACE -> KtClassKind.INTERFACE
                ClassKind.ENUM_CLASS -> KtClassKind.ENUM_CLASS
                ClassKind.ENUM_ENTRY -> KtClassKind.ENUM_ENTRY
                ClassKind.ANNOTATION_CLASS -> KtClassKind.ANNOTATION_CLASS
                ClassKind.CLASS -> KtClassKind.CLASS
                ClassKind.OBJECT -> if (fir.isCompanion) KtClassKind.COMPANION_OBJECT else KtClassKind.OBJECT
            }
        }
    override val symbolKind: KtSymbolKind
        get() = firRef.withFir { fir ->
            when {
                fir.isLocal -> KtSymbolKind.LOCAL
                fir.symbol.classId.isNestedClass -> KtSymbolKind.MEMBER
                else -> KtSymbolKind.TOP_LEVEL
            }
        }

    override fun createPointer(): KtSymbolPointer<KtNamedClassOrObjectSymbol> {
        KtPsiBasedSymbolPointer.createForSymbolFromSource(this)?.let { return it }
        if (symbolKind == KtSymbolKind.LOCAL) {
            throw CanNotCreateSymbolPointerForLocalLibraryDeclarationException(classIdIfNonLocal?.asString().orEmpty())
        }
        return KtFirClassOrObjectInLibrarySymbolPointer(classIdIfNonLocal!!)
    }

    override fun equals(other: Any?): Boolean = symbolEquals(other)
    override fun hashCode(): Int = symbolHashCode()
}
