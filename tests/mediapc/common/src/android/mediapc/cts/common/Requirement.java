/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Performance Class Requirement maps and req id to a set of {@link RequiredMeasurement}.
 */
public abstract class Requirement {
    private static final String TAG = Requirement.class.getSimpleName();

    protected final ImmutableMap<String, RequiredMeasurement<?>> mRequiredMeasurements;
    public final String id;
    public final String cddId;
    public final @Nullable String configId;
    public final @Nullable String variantId;

    protected Requirement(String id, String cddId, RequiredMeasurement<?>[] reqs) {
        this(id, cddId, null, null, reqs);
    }

    protected Requirement(
            String id,
            String cddId,
            @Nullable String configId,
            @Nullable String variantId,
            RequiredMeasurement<?>[] reqs) {
        this.id = id;
        this.cddId = cddId;
        this.configId = configId;
        this.variantId = variantId;

        ImmutableMap.Builder<String, RequiredMeasurement<?>> reqBuilder =
            ImmutableMap.<String, RequiredMeasurement<?>>builder();
        for (RequiredMeasurement<?> r: reqs) {
            reqBuilder.put(r.id(), r);
        }
        this.mRequiredMeasurements = reqBuilder.build();
    }

    public String id() {
        return this.id;
    }

    boolean allMeasuredValuesSet() {
        return mRequiredMeasurements.values().stream().allMatch(rm -> rm.isMeasuredValueSet());
    }

    /** Returns id cddId with config and variant if available. */
    public String toString() {
        if (configId == null && variantId == null) {
            return "[%s]".formatted(cddId);
        }
        if (variantId == null) {
            return "[%s] (config=\"%s\")".formatted(cddId, configId);
        }
        if (configId == null) {
            return "[%s] (variant=\"%s\")".formatted(cddId, variantId);
        }
        return "[%s] (config=\"%s\", variant=\"%s\")".formatted(cddId, configId, variantId);
    }

    /**
     * Finds the highest performance class where at least one RequiredMeasurement has result
     * RequirementConstants.Result.MET and none have RequirementConstants.Result.UNMET
     */
    @VisibleForTesting
    protected int computePerformanceClass() {
        Map<Integer, RequirementConstants.Result> overallPerfClassResults = new HashMap<>();

        for (RequiredMeasurement<?> rm: this.mRequiredMeasurements.values()) {
            Map<Integer, RequirementConstants.Result> perfClassResults = rm.getPerformanceClass();

            for (Integer pc: perfClassResults.keySet()) {
                RequirementConstants.Result res = perfClassResults.get(pc);

                // if one or more results are UNMET, mark the performance class as UNMET
                // otherwise if at least 1 of the results is MET, mark the performance class as MET
                if (res == RequirementConstants.Result.UNMET) {
                    overallPerfClassResults.put(pc, RequirementConstants.Result.UNMET);
                } else if (!overallPerfClassResults.containsKey(pc) &&
                        res == RequirementConstants.Result.MET) {
                    overallPerfClassResults.put(pc, RequirementConstants.Result.MET);
                }
            }
        }

        // report the highest performance class that has been MET
        int perfClass = 0;
        for (int pc: overallPerfClassResults.keySet()) {
            if (overallPerfClassResults.get(pc) == RequirementConstants.Result.MET) {
                perfClass = Math.max(perfClass, pc);
            }
        }
        return perfClass;
    }

    /** Is this requirement valid for the given performance class */
    protected boolean appliesToPerformanceClass(int pc) {
        return mRequiredMeasurements.values().stream()
                .anyMatch(rm -> rm.appliesToPerformanceClass(pc));
    }

    @VisibleForTesting
    protected boolean checkPerformanceClass(int devicePerfClass) {
        boolean noResultsUnment = true;
        for (RequiredMeasurement<?> rm: this.mRequiredMeasurements.values()) {
            RequirementConstants.Result res = rm.meetsPerformanceClass(devicePerfClass);
            if (res == RequirementConstants.Result.UNMET) {
                Log.w(Requirement.TAG, this + " " + rm.toString());
                noResultsUnment = false;
            } else {
                Log.i(Requirement.TAG, this + " " + rm.toString());
            }
        }
        return noResultsUnment;
    }

    protected <T> void setMeasuredValue(String measurement, T measuredValue) {
        RequiredMeasurement<T> rm =
                (RequiredMeasurement<T>) this.mRequiredMeasurements.get(measurement);
        rm.setMeasuredValue(measuredValue);
    }

    protected <T> T getMeasuredValue(String measurement, Class<T> clazz) {
        RequiredMeasurement<T> rm =
                (RequiredMeasurement<T>) this.mRequiredMeasurements.get(measurement);
        return clazz.cast(rm.getMeasuredValue());
    }

    /**
     * @return whether or not the requirement meets the device's specified performance class
     */
    public boolean writeLogAndCheck(ReportLog log, String testName) {

        int perfClass = this.computePerformanceClass();

        log.addValue(RequirementConstants.TN_FIELD_NAME, testName, ResultType.NEUTRAL,
            ResultUnit.NONE);
        for (RequiredMeasurement rm: this.mRequiredMeasurements.values()) {
            rm.writeValue(log);
        }
        log.addValue(RequirementConstants.PC_FIELD_NAME, perfClass, ResultType.NEUTRAL,
            ResultUnit.NONE);

        return this.checkPerformanceClass(Utils.getPerfClass());
    }
}
