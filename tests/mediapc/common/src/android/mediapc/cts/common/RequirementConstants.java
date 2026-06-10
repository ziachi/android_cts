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

import android.hardware.camera2.cts.helpers.StaticMetadata;

import java.util.function.BiPredicate;

/**
 * Stores constants used by PerformanceClassEvaluator.  Constants relating to requirement number are
 * stored in order as they appear in the Android CDD.  Constants relating to measurements are stored
 * alphabetically.
 */
public class RequirementConstants {
    private static final String TAG = RequirementConstants.class.getSimpleName();

    public static final String REPORT_LOG_NAME = "MediaPerformanceClassTestCases";
    public static final String TN_FIELD_NAME = "test_name";
    public static final String PC_FIELD_NAME = "performance_class";

    public enum Result {
        NA, MET, UNMET
    }

    public static final BiPredicate<Long, Long> LONG_INFO = RequirementConstants.info();
    public static final BiPredicate<Long, Long> LONG_GT = RequirementConstants.gt();
    public static final BiPredicate<Long, Long> LONG_LT = RequirementConstants.lt();
    public static final BiPredicate<Long, Long> LONG_GTE = RequirementConstants.gte();
    public static final BiPredicate<Long, Long> LONG_LTE = RequirementConstants.lte();
    public static final BiPredicate<Long, Long> LONG_EQ = RequirementConstants.eq();

    public static final BiPredicate<Integer, Integer> INTEGER_INFO = RequirementConstants.info();
    public static final BiPredicate<Integer, Integer> INTEGER_GT = RequirementConstants.gt();
    public static final BiPredicate<Integer, Integer> INTEGER_LT = RequirementConstants.lt();
    public static final BiPredicate<Integer, Integer> INTEGER_GTE = RequirementConstants.gte();
    public static final BiPredicate<Integer, Integer> INTEGER_LTE = RequirementConstants.lte();
    public static final BiPredicate<Integer, Integer> INTEGER_EQ = RequirementConstants.eq();
    public static final BiPredicate<Integer, Integer> INTEGER_CAM_HW_LEVEL_GTE =
            RequirementConstants.camHwLevelGte();

    public static final BiPredicate<Double, Double> DOUBLE_INFO = RequirementConstants.info();
    public static final BiPredicate<Double, Double> DOUBLE_GT = RequirementConstants.gt();
    public static final BiPredicate<Double, Double> DOUBLE_LT = RequirementConstants.lt();
    public static final BiPredicate<Double, Double> DOUBLE_GTE = RequirementConstants.gte();
    public static final BiPredicate<Double, Double> DOUBLE_LTE = RequirementConstants.lte();
    public static final BiPredicate<Double, Double> DOUBLE_EQ = RequirementConstants.eq();

    public static final BiPredicate<Float, Float> FLOAT_INFO = RequirementConstants.info();
    public static final BiPredicate<Float, Float> FLOAT_GT = RequirementConstants.gt();
    public static final BiPredicate<Float, Float> FLOAT_LT = RequirementConstants.lt();
    public static final BiPredicate<Float, Float> FLOAT_GTE = RequirementConstants.gte();
    public static final BiPredicate<Float, Float> FLOAT_LTE = RequirementConstants.lte();
    public static final BiPredicate<Float, Float> FLOAT_EQ = RequirementConstants.eq();

    public static final BiPredicate<Boolean, Boolean> BOOLEAN_EQ = RequirementConstants.eq();
    public static final BiPredicate<Boolean, Boolean> BOOLEAN_INFO = RequirementConstants.info();

    public static final BiPredicate<String, String> STRING_INFO = RequirementConstants.info();

    /**
     * Creates a >= predicate.
     *
     * This is convenience method to get the types right.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> gte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) >= 0;
            }

            @Override
            public String toString() {
                return "Greater than or equal to";
            }
        };
    }

    /**
     * Creates a <= predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> lte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) <= 0;
            }

            @Override
            public String toString() {
                return "Less than or equal to";
            }
        };
    }

    /**
     * Creates an == predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> eq() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) == 0;
            }

            @Override
            public String toString() {
                return "Equal to";
            }
        };
    }

    /**
     * Creates a > predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> gt() {
        return RequirementConstants.<T, S>lte().negate();
    }

    /**
     * Creates a < predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> lt() {
        return RequirementConstants.<T, S>gte().negate();
    }

    /**
     * Creates a bi predicate that always returns true because the measurements is for info only.
     */
    private static <T> BiPredicate<T, T> info() {
        return new BiPredicate<T, T>() {
            @Override
            public boolean test(T actual, T expected) {
                return true;
            }

            @Override
            public String toString() {
                return "True. For info only";
            }
        };
    }

    /**
    * Creates a >= predicate for camera hardware level
    */
    private static BiPredicate<Integer, Integer> camHwLevelGte() {
        return new BiPredicate<Integer, Integer>() {
            @Override
            public boolean test(Integer actual, Integer expected) {
                return StaticMetadata.hardwareLevelPredicate(actual, expected);
            }

            @Override
            public String toString() {
                return "Camera Hardware Level Greater than or equal to";
            }
        };
    }

    private RequirementConstants() {} // class should not be instantiated
}
