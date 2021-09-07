/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCScheduler.hpp"

#include "CompilerConstants.hpp"
#include "Porting.h"

using namespace kotlin;

bool gc::GCScheduler::ThreadData::OnSafePointSlowPath() noexcept {
    const auto result = onSafePoint_(allocatedBytes_, safePointsCounter_);
    ClearCountersAndUpdateThresholds();
    return result;
}

void gc::GCScheduler::ThreadData::ClearCountersAndUpdateThresholds() noexcept {
    allocatedBytes_ = 0;
    safePointsCounter_ = 0;

    allocatedBytesThreshold_ = config_.allocationThresholdBytes;
    safePointsCounterThreshold_ = config_.threshold;
}

gc::GCSchedulerConfig::GCSchedulerConfig() noexcept {
    if (compiler::gcAggressive()) {
        // TODO: Make it even more aggressive and run on a subset of backend.native tests.
        threshold = 1000;
        allocationThresholdBytes = 10000;
        cooldownThresholdUs = 0;
    }
}

gc::GCScheduler::GCThreadData::GCThreadData(gc::GCSchedulerConfig& config, CurrentTimeCallback currentTimeCallbackUs) noexcept :
    config_(config), currentTimeCallbackUs_(std::move(currentTimeCallbackUs)), timeOfLastGcUs_(currentTimeCallbackUs_()) {}

bool gc::GCScheduler::GCThreadData::OnSafePoint(size_t allocatedBytes, size_t safePointsCounter) noexcept {
    if (allocatedBytes > config_.allocationThresholdBytes) return true;

    return currentTimeCallbackUs_() - timeOfLastGcUs_ >= config_.cooldownThresholdUs;
}

void gc::GCScheduler::GCThreadData::OnPerformFullGC() noexcept {
    timeOfLastGcUs_ = currentTimeCallbackUs_();
}

gc::GCScheduler::GCScheduler() noexcept : gcThreadData_(config_, []() { return konan::getTimeMicros(); }) {}

gc::GCScheduler::ThreadData gc::GCScheduler::NewThreadData() noexcept {
    return ThreadData(config_, [this](size_t allocatedBytes, size_t safePointsCounter) {
        return gcThreadData().OnSafePoint(allocatedBytes, safePointsCounter);
    });
}
