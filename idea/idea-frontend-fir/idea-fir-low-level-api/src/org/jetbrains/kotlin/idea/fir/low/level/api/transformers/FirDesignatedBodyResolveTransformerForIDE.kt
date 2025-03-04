/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.transformers

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.FirProviderInterceptor
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirTowerDataContextCollector
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.ImplicitBodyResolveComputationSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.createReturnTypeCalculatorForIDE
import org.jetbrains.kotlin.idea.fir.low.level.api.FirPhaseRunner
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirDeclarationDesignationWithFile
import org.jetbrains.kotlin.idea.fir.low.level.api.element.builder.FirIdeEnsureBasedTransformerForReturnTypeCalculator
import org.jetbrains.kotlin.idea.fir.low.level.api.lazy.resolve.ResolveTreeBuilder
import org.jetbrains.kotlin.idea.fir.low.level.api.transformers.FirLazyTransformerForIDE.Companion.updatePhaseDeep
import org.jetbrains.kotlin.idea.fir.low.level.api.util.ensurePhase

/**
 * Transform designation into BODY_RESOLVE declaration. Affects only for target declaration and it's children
 */
internal class FirDesignatedBodyResolveTransformerForIDE(
    private val designation: FirDeclarationDesignationWithFile,
    session: FirSession,
    scopeSession: ScopeSession,
    towerDataContextCollector: FirTowerDataContextCollector?,
    firProviderInterceptor: FirProviderInterceptor?,
) : FirLazyTransformerForIDE, FirBodyResolveTransformer(
    session,
    phase = FirResolvePhase.BODY_RESOLVE,
    implicitTypeOnly = false,
    scopeSession = scopeSession,
    returnTypeCalculator = createReturnTypeCalculatorForIDE(
        session,
        scopeSession,
        ImplicitBodyResolveComputationSession(),
        ::FirIdeEnsureBasedTransformerForReturnTypeCalculator
    ),
    firTowerDataContextCollector = towerDataContextCollector,
    firProviderInterceptor = firProviderInterceptor,
) {
    private val ideDeclarationTransformer = IDEDeclarationTransformer(designation)

    override fun transformDeclarationContent(declaration: FirDeclaration, data: ResolutionMode): FirDeclaration =
        ideDeclarationTransformer.transformDeclarationContent(this, declaration, data) {
            super.transformDeclarationContent(declaration, data)
        }

    override fun transformDeclaration(phaseRunner: FirPhaseRunner) {
        if (designation.declaration.resolvePhase >= FirResolvePhase.BODY_RESOLVE) return
        designation.declaration.ensurePhase(FirResolvePhase.IMPLICIT_TYPES_BODY_RESOLVE)

        ResolveTreeBuilder.resolvePhase(designation.declaration, FirResolvePhase.BODY_RESOLVE) {
            phaseRunner.runPhaseWithCustomResolve(FirResolvePhase.BODY_RESOLVE) {
                designation.firFile.transform<FirFile, ResolutionMode>(this, ResolutionMode.ContextIndependent)
            }
        }

        ideDeclarationTransformer.ensureDesignationPassed()
        updatePhaseDeep(designation.declaration, FirResolvePhase.BODY_RESOLVE, withNonLocalDeclarations = true)
        ensureResolved(designation.declaration)
        ensureResolvedDeep(designation.declaration)
    }

    override fun ensureResolved(declaration: FirDeclaration) {
        when (declaration) {
            is FirSimpleFunction, is FirConstructor, is FirTypeAlias, is FirField, is FirAnonymousInitializer ->
                declaration.ensurePhase(FirResolvePhase.BODY_RESOLVE)
            is FirProperty -> {
                declaration.ensurePhase(FirResolvePhase.BODY_RESOLVE)
//                declaration.getter?.ensurePhase(FirResolvePhase.BODY_RESOLVE)
//                declaration.setter?.ensurePhase(FirResolvePhase.BODY_RESOLVE)
            }
            is FirEnumEntry, is FirClass -> Unit
            else -> error("Unexpected type: ${declaration::class.simpleName}")
        }
    }
}

