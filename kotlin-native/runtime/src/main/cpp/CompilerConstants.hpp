/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_COMPILER_CONSTANTS_H
#define RUNTIME_COMPILER_CONSTANTS_H

#include <cstdint>
#if __has_include(<string_view>)
#include <string_view>
#elif __has_include(<experimental/string_view>)
// TODO: Remove when wasm32 is gone.
#include <xlocale.h>
#include <experimental/string_view>
namespace std {
using string_view = std::experimental::string_view;
}
#else
#error "No <string_view>"
#endif

#include "Common.h"

// Prefer to use getter functions below. These constants are exposed to simplify the job of the inliner.

// These are defined by setRuntimeConstGlobals in IrToBitcode.kt
extern "C" const int32_t KonanNeedDebugInfo;
extern "C" const int32_t Kotlin_runtimeAssertsMode;
extern "C" const char* const Kotlin_runtimeLogs;

namespace kotlin {
namespace compiler {

// Must match DestroyRuntimeMode in DestroyRuntimeMode.kt
enum class DestroyRuntimeMode : int32_t {
    kLegacy = 0,
    kOnShutdown = 1,
};

// Must match RuntimeAssertsMode in RuntimeAssertsMode.kt
enum class RuntimeAssertsMode : int32_t {
    kIgnore = 0,
    kLog = 1,
    kPanic = 2,
};

// Must match WorkerExceptionHandling in WorkerExceptionHandling.kt
enum class WorkerExceptionHandling : int32_t {
    kLegacy = 0,
    kUseHook = 1,
};

DestroyRuntimeMode destroyRuntimeMode() noexcept;

bool gcAggressive() noexcept;

ALWAYS_INLINE inline bool shouldContainDebugInfo() noexcept {
    return KonanNeedDebugInfo != 0;
}

ALWAYS_INLINE inline RuntimeAssertsMode runtimeAssertsMode() noexcept {
    return static_cast<RuntimeAssertsMode>(Kotlin_runtimeAssertsMode);
}

WorkerExceptionHandling workerExceptionHandling() noexcept;

ALWAYS_INLINE inline std::string_view runtimeLogs() noexcept {
    return Kotlin_runtimeLogs == nullptr ? std::string_view() : std::string_view(Kotlin_runtimeLogs);
}

bool freezingEnabled() noexcept;

} // namespace compiler
} // namespace kotlin

#endif // RUNTIME_COMPILER_CONSTANTS_H
