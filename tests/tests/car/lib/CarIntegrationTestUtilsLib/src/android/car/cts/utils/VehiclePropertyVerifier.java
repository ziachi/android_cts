/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts.utils;

import static android.car.cts.utils.ShellPermissionUtils.CHECK_MODE_ASSUME;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.car.VehicleAreaDoor;
import android.car.VehicleAreaMirror;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleAreaWindow;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.feature.Flags;
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.GetPropertyCallback;
import android.car.hardware.property.CarPropertyManager.GetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.GetPropertyResult;
import android.car.hardware.property.CarPropertyManager.PropertyAsyncError;
import android.car.hardware.property.CarPropertyManager.SetPropertyCallback;
import android.car.hardware.property.CarPropertyManager.SetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.SetPropertyResult;
import android.car.hardware.property.CarPropertyManager.SupportedValuesChangeCallback;
import android.car.hardware.property.ErrorState;
import android.car.hardware.property.MinMaxSupportedValue;
import android.car.hardware.property.PropertyNotAvailableAndRetryException;
import android.car.hardware.property.PropertyNotAvailableErrorCode;
import android.car.hardware.property.PropertyNotAvailableException;
import android.car.hardware.property.Subscription;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.internal.annotations.GuardedBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.hamcrest.Matchers;
import org.junit.AssumptionViolatedException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A class for verifying the implementation for one VHAL property.
 *
 * @param <T> The type for the property.
 */
public class VehiclePropertyVerifier<T> {
    private static final String STEP_VERIFY_READ_APIS_PREFIX = "verifyReadApis";
    private static final String STEP_VERIFY_WRITE_APIS_PREFIX = "verifyWriteApis";

    private static final CarSvcPropsParser CAR_SVC_PROPS_PARSER = new CarSvcPropsParser();

    /** A step to verify {@link CarPropertyManager#getCarPropertyConfig} returns expected config. */
    public static final String STEP_VERIFY_PROPERTY_CONFIG = "verifyPropertyConfig";

    /** A step to verify {@link CarPropertyManager#isPropertyAvailable}. */
    public static final String STEP_VERIFY_IS_PROPERTY_AVAILABLE = "isPropertyAvailable";

    /** A step to verify that expected exceptions are thrown when missing permission. */
    public static final String STEP_VERIFY_PERMISSION_NOT_GRANTED_EXCEPTION =
            "verifyPermissionNotGrantedException";

    /**
     * A step to verify {@link CarPropertyManager#getProperty}, {@link
     * CarPropertyManager#getIntProperty}, {@link CarPropertyManager#getBooleanProperty}, {@link
     * CarPropertyManager#getFloatProperty}, {@link CarPropertyManager#getIntArrayProperty}.
     */
    public static final String STEP_VERIFY_READ_APIS_GET_PROPERTY_SYNC =
            STEP_VERIFY_READ_APIS_PREFIX + ".getProperty";

    /** A step to verify {@link CarPropertyManager#getPropertiesAsync}. */
    public static final String STEP_VERIFY_READ_APIS_GET_PROPERTY_ASYNC =
            STEP_VERIFY_READ_APIS_PREFIX + ".getPropertiesAsync";

    /** A step to verify {@link CarPropertyManager#subscribePropertyEvents}. */
    public static final String STEP_VERIFY_READ_APIS_SUBSCRIBE =
            STEP_VERIFY_READ_APIS_PREFIX + ".subscribePropertyEvents";

    /** A step to verify {@link CarPropertyManager#getMinMaxSupportedValue}. */
    public static final String STEP_VERIFY_READ_APIS_GET_MIN_MAX_SUPPORTED_VALUE =
            STEP_VERIFY_READ_APIS_PREFIX + ".getMinMaxSupportedValue";

    /** A step to verify {@link CarPropertyManager#getSupportedValuesList}. */
    public static final String STEP_VERIFY_READ_APIS_GET_SUPPORTED_VALUES_LIST =
            STEP_VERIFY_READ_APIS_PREFIX + ".getSupportedValuesList";
    /**
     * A step to verify {@link CarPropertyManager#registerSupportedValuesChangeCallback} and {@link
     * CarPropertyManager#unregisterSupportedValuesChangeCallback}
     */
    public static final String STEP_VERIFY_READ_APIS_REG_UNREG_SUPPORTED_VALUES_CHANGE =
            STEP_VERIFY_READ_APIS_PREFIX + ".regUnregSupportedValuesChangeCallback";

    /**
     * A step to verify that for ADAS properties, if the feature is disabled, the property must
     * report error state.
     *
     * <p>This step is skipped for non-adas properties.
     */
    public static final String STEP_VERIFY_READ_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE =
            STEP_VERIFY_READ_APIS_PREFIX + ".disableAdasFeatureVerifyState";

    /** A step to verify {@link CarPropertyManager#setProperty}. */
    public static final String STEP_VERIFY_WRITE_APIS_SET_PROPERTY_SYNC =
            STEP_VERIFY_WRITE_APIS_PREFIX + ".setProperty";

    /** A step to verify {@link CarPropertyManager#setPropertiesAsync}. */
    public static final String STEP_VERIFY_WRITE_APIS_SET_PROPERTY_ASYNC =
            STEP_VERIFY_WRITE_APIS_PREFIX + ".setPropertiesAsync";

    /**
     * A step to verify that for ADAS properties, if the feature is disabled, the property must
     * report error state.
     *
     * <p>This step is skipped for non-adas properties.
     */
    public static final String STEP_VERIFY_WRITE_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE =
            STEP_VERIFY_WRITE_APIS_PREFIX + ".disableAdasFeatureVerifyState";

    /**
     * A step to verify that for HVAC power dependant properties, getting should be unavailable when
     * HVAC power is off.
     */
    public static final String STEP_VERIFY_READ_APIS_DISABLE_HVAC_GET_NOT_AVAILABLE =
            STEP_VERIFY_READ_APIS_PREFIX + ".turnOffHvacPowerGetNotAvailable";

    /**
     * A step to verify that for HVAC power dependant properties, setting should be unavailable when
     * HVAC power is off.
     */
    public static final String STEP_VERIFY_WRITE_APIS_DISABLE_HVAC_SET_NOT_AVAILABLE =
            STEP_VERIFY_WRITE_APIS_PREFIX + ".turnOffHvacPowerSetNotAvailable";

    /**
     * A step to verify that for read/write properties, if the caller only has read permission,
     * caller cannot write.
     */
    public static final String STEP_VERIFY_READ_PERMISSION_CANNOT_WRITE =
            "verifyReadPermissionCannotWrite";

    /**
     * A step to verify that for read/write properties, if the caller only has write permission,
     * caller cannot read.
     */
    public static final String STEP_VERIFY_WRITE_PERMISSION_CANNOT_READ =
            "verifyWritePermissionCannotRead";

    private static final String TAG = VehiclePropertyVerifier.class.getSimpleName();
    private static final String CAR_PROPERTY_VALUE_SOURCE_GETTER = "Getter";
    private static final String CAR_PROPERTY_VALUE_SOURCE_CALLBACK = "Callback";
    private static final int GLOBAL_AREA_ID = 0;
    private static final float FLOAT_INEQUALITY_THRESHOLD = 0.00001f;
    private static final int VENDOR_ERROR_CODE_MINIMUM_VALUE = 0x0;
    private static final int VENDOR_ERROR_CODE_MAXIMUM_VALUE = 0xffff;
    private static final int SET_PROPERTY_CALLBACK_TIMEOUT_SEC = 5;
    private static final long CPM_ACTION_DELAY_MS = 20;
    private static final Object sLock = new Object();
    private static final ImmutableSet<Integer> WHEEL_AREAS =
            ImmutableSet.of(
                    VehicleAreaWheel.WHEEL_LEFT_FRONT, VehicleAreaWheel.WHEEL_LEFT_REAR,
                    VehicleAreaWheel.WHEEL_RIGHT_FRONT, VehicleAreaWheel.WHEEL_RIGHT_REAR);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_WHEEL_AREA_IDS =
            generateAllPossibleAreaIds(WHEEL_AREAS);
    private static final ImmutableSet<Integer> WINDOW_AREAS =
            ImmutableSet.of(
                    VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD,
                            VehicleAreaWindow.WINDOW_REAR_WINDSHIELD,
                    VehicleAreaWindow.WINDOW_ROW_1_LEFT, VehicleAreaWindow.WINDOW_ROW_1_RIGHT,
                    VehicleAreaWindow.WINDOW_ROW_2_LEFT, VehicleAreaWindow.WINDOW_ROW_2_RIGHT,
                    VehicleAreaWindow.WINDOW_ROW_3_LEFT, VehicleAreaWindow.WINDOW_ROW_3_RIGHT,
                    VehicleAreaWindow.WINDOW_ROOF_TOP_1, VehicleAreaWindow.WINDOW_ROOF_TOP_2);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_WINDOW_AREA_IDS =
            generateAllPossibleAreaIds(WINDOW_AREAS);
    private static final ImmutableSet<Integer> MIRROR_AREAS =
            ImmutableSet.of(
                    VehicleAreaMirror.MIRROR_DRIVER_LEFT,
                    VehicleAreaMirror.MIRROR_DRIVER_RIGHT,
                    VehicleAreaMirror.MIRROR_DRIVER_CENTER);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_MIRROR_AREA_IDS =
            generateAllPossibleAreaIds(MIRROR_AREAS);
    private static final ImmutableSet<Integer> SEAT_AREAS =
            ImmutableSet.of(
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    VehicleAreaSeat.SEAT_ROW_1_CENTER,
                    VehicleAreaSeat.SEAT_ROW_1_RIGHT,
                    VehicleAreaSeat.SEAT_ROW_2_LEFT,
                    VehicleAreaSeat.SEAT_ROW_2_CENTER,
                    VehicleAreaSeat.SEAT_ROW_2_RIGHT,
                    VehicleAreaSeat.SEAT_ROW_3_LEFT,
                    VehicleAreaSeat.SEAT_ROW_3_CENTER,
                    VehicleAreaSeat.SEAT_ROW_3_RIGHT);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_SEAT_AREA_IDS =
            generateAllPossibleAreaIds(SEAT_AREAS);
    private static final ImmutableSet<Integer> DOOR_AREAS =
            ImmutableSet.of(
                    VehicleAreaDoor.DOOR_ROW_1_LEFT, VehicleAreaDoor.DOOR_ROW_1_RIGHT,
                    VehicleAreaDoor.DOOR_ROW_2_LEFT, VehicleAreaDoor.DOOR_ROW_2_RIGHT,
                    VehicleAreaDoor.DOOR_ROW_3_LEFT, VehicleAreaDoor.DOOR_ROW_3_RIGHT,
                    VehicleAreaDoor.DOOR_HOOD, VehicleAreaDoor.DOOR_REAR);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_DOOR_AREA_IDS =
            generateAllPossibleAreaIds(DOOR_AREAS);
    private static final ImmutableSet<Integer> PROPERTY_NOT_AVAILABLE_ERROR_CODES =
            ImmutableSet.of(
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_POOR_VISIBILITY,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);
    private static final boolean AREA_ID_CONFIG_ACCESS_FLAG =
            isAtLeastV() && Flags.areaIdConfigAccess();
    private static final boolean CAR_PROPERTY_SUPPORTED_VALUE_FLAG =
            isAtLeastB() && Flags.carPropertySupportedValue();
    private static final List<Integer> VALID_CAR_PROPERTY_VALUE_STATUSES =
            Arrays.asList(
                    CarPropertyValue.STATUS_AVAILABLE,
                    CarPropertyValue.STATUS_UNAVAILABLE,
                    CarPropertyValue.STATUS_ERROR);
    private static final List<Integer> VALID_SET_ERROR_CODES =
            Arrays.asList(
                    CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN,
                    CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG,
                    CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE,
                    CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED,
                    CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);

    private static final CarPropertyValueCallback FAKE_CALLBACK =
            new CarPropertyValueCallback(
                    /* propertyName= */ "",
                    new int[] {},
                    /* totalCarPropertyValuesPerAreaId= */ 0,
                    /* timeoutMillis= */ 0);

    private static Class<?> sExceptionClassOnGet;
    private static Class<?> sExceptionClassOnSet;

    @GuardedBy("sLock")
    private static long sLastActionElapsedRealtimeNanos = 0;

    private static boolean sIsCarPropertyConfigsCached;
    // A static cache to store all property configs. This will be reused across multiple test cases
    // in order to save time.
    private static SparseArray<CarPropertyConfig<?>> sCachedCarPropertyConfigs =
            new SparseArray<>();

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final CarPropertyManager mCarPropertyManager;
    private final int mPropertyId;
    private final String mPropertyName;
    private final ImmutableSet<Integer> mAllowedAccessModes;
    private final int mAreaType;
    private final int mChangeMode;
    private final Class<T> mPropertyType;
    private final boolean mRequiredProperty;
    private final Optional<ConfigArrayVerifier> mConfigArrayVerifier;
    private final Optional<CarPropertyValueVerifier<T>> mCarPropertyValueVerifier;
    private final Optional<AreaIdsVerifier> mAreaIdsVerifier;
    private final Optional<CarPropertyConfigVerifier> mCarPropertyConfigVerifier;
    private final Optional<Integer> mDependentOnPropertyId;
    private final ImmutableSet<String> mDependentOnPropertyPermissions;
    private final ImmutableSet<Integer> mPossibleConfigArrayValues;
    private final boolean mEnumIsBitMap;
    private final ImmutableSet<T> mAllPossibleEnumValues;
    private final ImmutableSet<T> mAllPossibleUnwritableValues;
    private final ImmutableSet<T> mAllPossibleUnavailableValues;
    private final boolean mRequirePropertyValueToBeInConfigArray;
    private final boolean mVerifySetterWithConfigArrayValues;
    private final boolean mRequireMinMaxValues;
    private final boolean mRequireMinValuesToBeZero;
    private final boolean mRequireZeroToBeContainedInMinMaxRanges;
    private final boolean mPossiblyDependentOnHvacPowerOn;
    private final boolean mVerifyErrorStates;
    // Delay to wait for power state to propagate to dependent properties.
    private final int mPowerPropagationDelayMs;
    private final ImmutableSet<String> mReadPermissions;
    private final ImmutableList<ImmutableSet<String>> mWritePermissions;
    private final VerifierContext mVerifierContext;
    private final List<Integer> mStoredProperties = new ArrayList<>();

    private SparseArray<SparseArray<?>> mPropertyToAreaIdValues;

    private VehiclePropertyVerifier(
            CarPropertyManager carPropertyManager,
            int propertyId,
            ImmutableSet<Integer> allowedAccessModes,
            int areaType,
            int changeMode,
            Class<T> propertyType,
            boolean requiredProperty,
            Optional<ConfigArrayVerifier> configArrayVerifier,
            Optional<CarPropertyValueVerifier<T>> carPropertyValueVerifier,
            Optional<AreaIdsVerifier> areaIdsVerifier,
            Optional<CarPropertyConfigVerifier> carPropertyConfigVerifier,
            Optional<Integer> dependentPropertyId,
            ImmutableSet<String> dependentOnPropertyPermissions,
            ImmutableSet<Integer> possibleConfigArrayValues,
            boolean enumIsBitMap,
            ImmutableSet<T> allPossibleEnumValues,
            ImmutableSet<T> allPossibleUnwritableValues,
            ImmutableSet<T> allPossibleUnavailableValues,
            boolean requirePropertyValueToBeInConfigArray,
            boolean verifySetterWithConfigArrayValues,
            boolean requireMinMaxValues,
            boolean requireMinValuesToBeZero,
            boolean requireZeroToBeContainedInMinMaxRanges,
            boolean possiblyDependentOnHvacPowerOn,
            boolean verifyErrorStates,
            int powerPropagationDelayMs,
            ImmutableSet<String> readPermissions,
            ImmutableList<ImmutableSet<String>> writePermissions) {
        assertWithMessage("Must set car property manager").that(carPropertyManager).isNotNull();
        mCarPropertyManager = carPropertyManager;
        mPropertyId = propertyId;
        mPropertyName = VehiclePropertyIds.toString(propertyId);
        mAllowedAccessModes = allowedAccessModes;
        mAreaType = areaType;
        mChangeMode = changeMode;
        mPropertyType = propertyType;
        mRequiredProperty = requiredProperty;
        mConfigArrayVerifier = configArrayVerifier;
        mCarPropertyValueVerifier = carPropertyValueVerifier;
        mAreaIdsVerifier = areaIdsVerifier;
        mCarPropertyConfigVerifier = carPropertyConfigVerifier;
        mDependentOnPropertyId = dependentPropertyId;
        mDependentOnPropertyPermissions = dependentOnPropertyPermissions;
        mPossibleConfigArrayValues = possibleConfigArrayValues;
        mEnumIsBitMap = enumIsBitMap;
        mAllPossibleEnumValues = allPossibleEnumValues;
        mAllPossibleUnwritableValues = allPossibleUnwritableValues;
        mAllPossibleUnavailableValues = allPossibleUnavailableValues;
        mRequirePropertyValueToBeInConfigArray = requirePropertyValueToBeInConfigArray;
        mVerifySetterWithConfigArrayValues = verifySetterWithConfigArrayValues;
        mRequireMinMaxValues = requireMinMaxValues;
        mRequireMinValuesToBeZero = requireMinValuesToBeZero;
        mRequireZeroToBeContainedInMinMaxRanges = requireZeroToBeContainedInMinMaxRanges;
        mPossiblyDependentOnHvacPowerOn = possiblyDependentOnHvacPowerOn;
        mVerifyErrorStates = verifyErrorStates;
        mPowerPropagationDelayMs = powerPropagationDelayMs;
        mReadPermissions = readPermissions;
        mWritePermissions = writePermissions;
        mPropertyToAreaIdValues = new SparseArray<>();
        mVerifierContext = new VerifierContext(carPropertyManager);
    }

    /** Gets a new builder for the verifier. */
    public static <T> Builder<T> newBuilder(
            int propertyId, int access, int areaType, int changeMode, Class<T> propertyType) {
        ImmutableSet.Builder<Integer> allowedAccessModesBuilder = new ImmutableSet.Builder<>();
        allowedAccessModesBuilder.add(access);
        if (access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            allowedAccessModesBuilder.add(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ);
        }
        return new Builder<>(
                propertyId, allowedAccessModesBuilder.build(), areaType, changeMode, propertyType);
    }

    /**
     * Gets a new default verifier builder for the property Id.
     *
     * <p>The verifier verifies basic information, including access mode, area type, change mode
     * property type and permissions.
     */
    public static <T> Builder<T> newDefaultBuilder(int propertyId) {
        var vehiclePropertyIdInfo = CAR_SVC_PROPS_PARSER.getVehiclePropertyIdInfo(propertyId);
        var builder =
                new Builder<>(
                        propertyId,
                        vehiclePropertyIdInfo.allowedAccessModes,
                        vehiclePropertyIdInfo.areaType,
                        vehiclePropertyIdInfo.changeMode,
                        (Class<T>) vehiclePropertyIdInfo.propertyType);
        for (String readPermission : vehiclePropertyIdInfo.readPermissions) {
            builder.addReadPermission(readPermission);
        }
        for (ImmutableSet<String> allOfWritePermission : vehiclePropertyIdInfo.writePermissions) {
            builder.addWritePermission(allOfWritePermission);
        }
        return builder;
    }

    /** Gets the ID of the property. */
    public int getPropertyId() {
        return mPropertyId;
    }

    /** Gets the name for the property. */
    public String getPropertyName() {
        return mPropertyName;
    }

    /** Gets the default value based on the type. */
    @Nullable
    public static <U> U getDefaultValue(Class<U> clazz) {
        if (clazz == Boolean.class) {
            return (U) Boolean.TRUE;
        }
        if (clazz == Integer.class) {
            return (U) (Integer) 2;
        }
        if (clazz == Float.class) {
            return (U) (Float) 2.f;
        }
        if (clazz == Long.class) {
            return (U) (Long) 2L;
        }
        if (clazz == Integer[].class) {
            return (U) new Integer[] {2};
        }
        if (clazz == Float[].class) {
            return (U) new Float[] {2.f};
        }
        if (clazz == Long[].class) {
            return (U) new Long[] {2L};
        }
        if (clazz == String.class) {
            return (U) new String("test");
        }
        if (clazz == byte[].class) {
            return (U) new byte[] {(byte) 0xbe, (byte) 0xef};
        }
        return null;
    }

    private static String accessToString(int access) {
        switch (access) {
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE:
                return "VEHICLE_PROPERTY_ACCESS_NONE";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ:
                return "VEHICLE_PROPERTY_ACCESS_READ";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE:
                return "VEHICLE_PROPERTY_ACCESS_WRITE";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE:
                return "VEHICLE_PROPERTY_ACCESS_READ_WRITE";
            default:
                return Integer.toString(access);
        }
    }

    private static String accessSetToString(Set<Integer> accessSet) {
        return accessSet.stream()
                .map(access -> accessToString(access))
                .collect(Collectors.joining(", "));
    }

    private static String areaTypeToString(int areaType) {
        switch (areaType) {
            case VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL:
                return "VEHICLE_AREA_TYPE_GLOBAL";
            case VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW:
                return "VEHICLE_AREA_TYPE_WINDOW";
            case VehicleAreaType.VEHICLE_AREA_TYPE_DOOR:
                return "VEHICLE_AREA_TYPE_DOOR";
            case VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR:
                return "VEHICLE_AREA_TYPE_MIRROR";
            case VehicleAreaType.VEHICLE_AREA_TYPE_SEAT:
                return "VEHICLE_AREA_TYPE_SEAT";
            case VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL:
                return "VEHICLE_AREA_TYPE_WHEEL";
            case VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR:
                return "VEHICLE_AREA_TYPE_VENDOR";
            default:
                return Integer.toString(areaType);
        }
    }

    private static String changeModeToString(int changeMode) {
        switch (changeMode) {
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC:
                return "VEHICLE_PROPERTY_CHANGE_MODE_STATIC";
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE:
                return "VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE";
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS:
                return "VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS";
            default:
                return Integer.toString(changeMode);
        }
    }

    /**
     * Gets the car property config for the current property or reads from cache if already cached.
     *
     * <p>Note that we statically cache all the property configs using the shell permission.
     */
    public @Nullable CarPropertyConfig<T> getCarPropertyConfig() {
        return getCarPropertyConfig(/* useCache= */ true);
    }

    /**
     * Gets the car property config for the current property.
     *
     * @param useCache Whether to use a local cache that prefetched all the configs using shell
     *     permission.
     */
    public @Nullable CarPropertyConfig<T> getCarPropertyConfig(boolean useCache) {
        return (CarPropertyConfig<T>) getCarPropertyConfig(mPropertyId, useCache);
    }

    private @Nullable CarPropertyConfig<?> getCarPropertyConfig(int propertyId) {
        return getCarPropertyConfig(propertyId, /* useCache= */ true);
    }

    private @Nullable CarPropertyConfig<?> getCarPropertyConfig(int propertyId, boolean useCache) {
        if (!useCache) {
            return mCarPropertyManager.getCarPropertyConfig(propertyId);
        }

        if (!sIsCarPropertyConfigsCached) {
            try (PermissionContext p =
                    TestApis.permissions()
                            .withPermission(
                                    TestApis.permissions()
                                            .adoptablePermissions()
                                            .toArray(new String[0]))) {
                var configs = mCarPropertyManager.getPropertyList();
                for (int i = 0; i < configs.size(); i++) {
                    sCachedCarPropertyConfigs.put(configs.get(i).getPropertyId(), configs.get(i));
                }
            }
            sIsCarPropertyConfigsCached = true;
        }
        return sCachedCarPropertyConfigs.get(propertyId);
    }

    /** Returns whether the property is supported. */
    public boolean isSupported() {
        return getCarPropertyConfig() != null;
    }

    /** Gets all verification steps. */
    public static ImmutableList<String> getAllSteps() {
        return ImmutableList.of(
                STEP_VERIFY_PROPERTY_CONFIG,
                STEP_VERIFY_IS_PROPERTY_AVAILABLE,
                STEP_VERIFY_PERMISSION_NOT_GRANTED_EXCEPTION,
                STEP_VERIFY_READ_APIS_GET_PROPERTY_SYNC,
                STEP_VERIFY_READ_APIS_GET_PROPERTY_ASYNC,
                STEP_VERIFY_READ_APIS_SUBSCRIBE,
                STEP_VERIFY_READ_APIS_GET_MIN_MAX_SUPPORTED_VALUE,
                STEP_VERIFY_READ_APIS_GET_SUPPORTED_VALUES_LIST,
                STEP_VERIFY_READ_APIS_REG_UNREG_SUPPORTED_VALUES_CHANGE,
                STEP_VERIFY_READ_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE,
                STEP_VERIFY_WRITE_APIS_SET_PROPERTY_SYNC,
                STEP_VERIFY_WRITE_APIS_SET_PROPERTY_ASYNC,
                STEP_VERIFY_WRITE_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE,
                STEP_VERIFY_READ_APIS_DISABLE_HVAC_GET_NOT_AVAILABLE,
                STEP_VERIFY_WRITE_APIS_DISABLE_HVAC_SET_NOT_AVAILABLE,
                STEP_VERIFY_READ_PERMISSION_CANNOT_WRITE,
                STEP_VERIFY_WRITE_PERMISSION_CANNOT_READ);
    }

    /** Runs various verifications on the property. */
    public void verify() {
        verify(null);
    }

    /** Runs a specific verification step. */
    public void verify(String step, @Nullable Class<?> exceptedExceptionClass) {
        if (step.equals(STEP_VERIFY_PROPERTY_CONFIG)) {
            verifyConfig();
            return;
        }
        if (step.equals(STEP_VERIFY_IS_PROPERTY_AVAILABLE)) {
            verifyIsPropertyAvailable();
            return;
        }
        assumeTrue("Property: " + getPropertyName() + " is not supported", isSupported());
        if (step.equals(STEP_VERIFY_PERMISSION_NOT_GRANTED_EXCEPTION)) {
            verifyPermissionNotGrantedException();
        } else if (step.startsWith(STEP_VERIFY_READ_APIS_PREFIX)) {
            verifyReadApis(step, exceptedExceptionClass);
        } else if (step.startsWith(STEP_VERIFY_WRITE_APIS_PREFIX)) {
            verifyWriteApis(step, exceptedExceptionClass);
        } else if (step.equals(STEP_VERIFY_READ_PERMISSION_CANNOT_WRITE)) {
            verifyReadPermissionCannotWrite();
        } else if (step.equals(STEP_VERIFY_WRITE_PERMISSION_CANNOT_READ)) {
            verifyWritePermissionCannotRead();
        } else {
            throw new IllegalStateException("Unknown step: " + step);
        }
    }

    /**
     * Runs all verification steps on the property with exceptions expected.
     *
     * @param exceptedExceptionClass The exception class expected for reading/writing the property.
     */
    public void verify(@Nullable Class<?> exceptedExceptionClass) {
        verifySteps(getAllSteps(), exceptedExceptionClass);
    }

    private void verifySteps(List<String> steps, @Nullable Class<?> exceptedExceptionClass) {
        for (int i = 0; i < steps.size(); i++) {
            try {
                verify(steps.get(i), exceptedExceptionClass);
            } catch (AssumptionViolatedException e) {
                if (steps.get(i).equals(STEP_VERIFY_PROPERTY_CONFIG)) {
                    // If the assumption fails for verifying config. It means the property is not
                    // supported and we should not continue the rest of the steps.
                    throw e;
                } else {
                    // Otherwise, we allow one step to be skipped.
                }
            }
        }
    }

    private void verifyGetPropertyWhenConfigIsNull(ImmutableSet<String> allPermissions) {
        if (!isAtLeastU()) {
            assertWithMessage(
                            "CarPropertyManager.getProperty must return null if the property is "
                                    + "not supported for propertyId: "
                                    + mPropertyName)
                    .that(mCarPropertyManager.getProperty(mPropertyId, /* areaId= */ 0))
                    .isNull();
            return;
        }

        String errorMsg =
                "CarPropertyManager.getProperty must throw IllegalArgumentException if "
                        + "the property is not supported for propertyId: "
                        + mPropertyName;

        Exception e =
                assertThrows(
                        errorMsg,
                        Exception.class,
                        () -> mCarPropertyManager.getProperty(mPropertyId, /* areaId= */ 0));
        if (e.getClass() == SecurityException.class) {
            fail(
                    "CarPropertyManager.getProperty must throw IllegalArgumentException if the"
                        + " property is not supported, actually got SecurityException, test may not"
                        + " have correct permission granted. PropertyId: "
                            + mPropertyName
                            + ". Requested permissions: "
                            + allPermissions);
            return;
        }
        assertWithMessage(errorMsg).that(e.getClass()).isEqualTo(IllegalArgumentException.class);
    }

    private void verifySetPropertyWhenConfigIsNull(ImmutableSet<String> allPermissions) {
        String errorMsg =
                "CarPropertyManager.setProperty must throw IllegalArgumentException if "
                        + "the property is not supported for propertyId: "
                        + mPropertyName;

        Exception e =
                assertThrows(
                        errorMsg,
                        Exception.class,
                        () ->
                                mCarPropertyManager.setProperty(
                                        mPropertyType,
                                        mPropertyId,
                                        /* areaId= */ 0,
                                        getDefaultValue(mPropertyType)));

        if (e.getClass() == SecurityException.class) {
            fail(
                    "CarPropertyManager.setProperty must throw IllegalArgumentException if the"
                        + " property is not supported, actually got SecurityException, test may not"
                        + " have correct permission granted. PropertyId: "
                            + mPropertyName
                            + ". Requested permissions: "
                            + allPermissions);
            return;
        }
        assertWithMessage(errorMsg).that(e.getClass()).isEqualTo(IllegalArgumentException.class);
    }

    /** Verifies the configuration for the property. */
    public void verifyConfig() {
        ImmutableSet.Builder<String> permissionsBuilder = ImmutableSet.<String>builder();
        for (ImmutableSet<String> writePermissions : mWritePermissions) {
            permissionsBuilder.addAll(writePermissions);
        }
        ImmutableSet<String> allPermissions = permissionsBuilder.addAll(mReadPermissions).build();

        runWithShellPermissionIdentity(
                () -> {
                    CarPropertyConfig<T> carPropertyConfig =
                            getCarPropertyConfig(/* useCache= */ false);
                    if (carPropertyConfig == null) {
                        // The property is not supported.
                        boolean allAllowedAccessCanRead = true;
                        boolean allAllowedAccessCanWrite = true;
                        for (int allowedAccessMode : mAllowedAccessModes) {
                            if (!canRead(allowedAccessMode)) {
                                allAllowedAccessCanRead = false;
                            }
                            if (!canWrite(allowedAccessMode)) {
                                allAllowedAccessCanWrite = false;
                            }
                        }
                        if (allAllowedAccessCanRead) {
                            verifyGetPropertyWhenConfigIsNull(allPermissions);
                        }
                        if (allAllowedAccessCanWrite) {
                            verifySetPropertyWhenConfigIsNull(allPermissions);
                        }
                    }

                    if (mRequiredProperty) {
                        assertWithMessage("Must support " + mPropertyName)
                                .that(isSupported())
                                .isTrue();
                    } else {
                        assumeThat(
                                "Skipping "
                                        + mPropertyName
                                        + " CTS test because the property is not supported on "
                                        + "this vehicle",
                                carPropertyConfig,
                                Matchers.notNullValue());
                    }

                    verifyCarPropertyConfig();
                },
                allPermissions.toArray(new String[0]));
    }

    /** Verifies that caller can call read APIs with read permission. */
    private void verifyReadApis(String step, Class<?> exceptedExceptionClass) {
        for (String readPermission : mReadPermissions) {
            verifyReadPermissionGivesAccessToReadApis(step, readPermission, exceptedExceptionClass);
        }
    }

    /** Verifies that caller can call write APIs with write permission. */
    private void verifyWriteApis(String step, Class<?> exceptedExceptionClass) {
        for (ImmutableSet<String> writePermissions : mWritePermissions) {
            verifyWritePermissionsGiveAccessToWriteApis(
                    step, writePermissions, mReadPermissions, exceptedExceptionClass);
        }
    }

    /** Verifies that caller cannot call write APIs with only read permissions. */
    private void verifyReadPermissionCannotWrite() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (String readPermission : mReadPermissions) {
            if (AREA_ID_CONFIG_ACCESS_FLAG) {
                for (int areaId : carPropertyConfig.getAreaIds()) {
                    if (canWrite(carPropertyConfig, areaId)) {
                        verifyReadPermissionCannotWrite(readPermission, mWritePermissions, areaId);
                    }
                }
            } else if (canWrite(carPropertyConfig.getAccess())) {
                verifyReadPermissionCannotWrite(
                        readPermission, mWritePermissions, carPropertyConfig.getAreaIds()[0]);
            }
        }
    }

    /** Verifies that caller cannot call read APIs with only write permissions. */
    private void verifyWritePermissionCannotRead() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (ImmutableSet<String> writePermissions : mWritePermissions) {
            if (AREA_ID_CONFIG_ACCESS_FLAG) {
                for (int areaId : carPropertyConfig.getAreaIds()) {
                    if (canRead(carPropertyConfig, areaId)) {
                        verifyWritePermissionsCannotRead(
                                writePermissions, mReadPermissions, areaId);
                    }
                    if (canWrite(carPropertyConfig, areaId) && writePermissions.size() > 1) {
                        verifyIndividualWritePermissionsCannotWrite(writePermissions, areaId);
                    }
                }
            } else {
                int areaId = carPropertyConfig.getAreaIds()[0];
                if (canRead(carPropertyConfig.getAccess())) {
                    verifyWritePermissionsCannotRead(writePermissions, mReadPermissions, areaId);
                }
                if (canWrite(carPropertyConfig.getAccess()) && writePermissions.size() > 1) {
                    verifyIndividualWritePermissionsCannotWrite(writePermissions, areaId);
                }
            }
        }
    }

    private boolean hasWritePermissions(ImmutableList<ImmutableSet<String>> writePermissions) {
        for (ImmutableSet<String> writePermissionSet : writePermissions) {
            boolean result = true;
            for (String permission : writePermissionSet) {
                if (mContext.checkSelfPermission(permission) != PERMISSION_GRANTED) {
                    result = false;
                    break;
                }
            }
            if (result) {
                return true;
            }
        }
        return false;
    }

    private void verifyReadPermissionCannotWrite(
            String readPermission,
            ImmutableList<ImmutableSet<String>> writePermissions,
            int areaId) {
        // If the read permission is the same as the write permission and the property does not
        // require any other write permissions we skip this permission.
        for (ImmutableSet<String> writePermissionSet : writePermissions) {
            if (writePermissionSet.size() == 1 && writePermissionSet.contains(readPermission)) {
                return;
            }
        }
        // It is possible that the caller has the write permissions without adopting the shell
        // identity. In this case, we cannot revoke the write permission so we cannot test
        // setProperty without write permissions.
        if (hasWritePermissions(writePermissions)) {
            return;
        }
        runWithShellPermissionIdentity(
                () -> {
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be written to without write"
                                    + " permissions.",
                            SecurityException.class,
                            () ->
                                    mCarPropertyManager.setProperty(
                                            mPropertyType,
                                            mPropertyId,
                                            areaId,
                                            getDefaultValue(mPropertyType)));
                },
                readPermission);
    }

    private void verifyReadPermissionGivesAccessToReadApis(
            String step, String readPermission, Class<?> exceptedExceptionClass) {
        if (step.equals(STEP_VERIFY_READ_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE)) {
            assumeTrue("Not an ADAS property", mDependentOnPropertyId.isPresent());
            disableAdasFeatureIfAdasStatePropertyAndVerify(
                    ImmutableSet.<String>builder()
                            .add(readPermission)
                            .addAll(mDependentOnPropertyPermissions)
                            .build()
                            .toArray(new String[0]),
                    /* verifySet= */ false);
            return;
        }

        try {
            enableAdasFeatureIfAdasStateProperty();
            runWithShellPermissionIdentity(
                    () -> {
                        assertThat(getCarPropertyConfig(/* useCache= */ false)).isNotNull();
                        turnOnHvacPowerIfHvacPowerDependent();
                        if (step.equals(STEP_VERIFY_READ_APIS_GET_PROPERTY_SYNC)) {
                            verifyCarPropertyValueGetter();
                            if (exceptedExceptionClass != null) {
                                assertWithMessage(
                                                "Expected "
                                                        + sExceptionClassOnGet
                                                        + " to be of type "
                                                        + exceptedExceptionClass)
                                        .that(sExceptionClassOnGet)
                                        .isEqualTo(exceptedExceptionClass);
                            }
                            return;
                        }
                        if (exceptedExceptionClass != null) {
                            return;
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_GET_PROPERTY_ASYNC)) {
                            verifyGetPropertiesAsync();
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_SUBSCRIBE)) {
                            verifyCarPropertyValueCallback();
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_GET_MIN_MAX_SUPPORTED_VALUE)
                                && CAR_PROPERTY_SUPPORTED_VALUE_FLAG) {
                            verifyGetMinMaxSupportedValue();
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_GET_SUPPORTED_VALUES_LIST)
                                && CAR_PROPERTY_SUPPORTED_VALUE_FLAG) {
                            verifyGetSupportedValuesList();
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_REG_UNREG_SUPPORTED_VALUES_CHANGE)
                                && CAR_PROPERTY_SUPPORTED_VALUE_FLAG) {
                            verifyRegisterUnregisterSupportedValuesChangeCallback();
                        }

                        if (step.equals(STEP_VERIFY_READ_APIS_DISABLE_HVAC_GET_NOT_AVAILABLE)) {
                            assumeTrue(
                                    "Not depending on HVAC power", mPossiblyDependentOnHvacPowerOn);
                            ImmutableSet<Integer> areaIdsTurnedOff =
                                    turnOffHvacPowerIfHvacPowerDependent();
                            if (!areaIdsTurnedOff.isEmpty()) {
                                verifyGetNotAvailable(areaIdsTurnedOff);
                            }
                        }
                    },
                    readPermission);
        } finally {
            // Restore all property values even if test fails.
            runWithShellPermissionIdentity(
                    () -> {
                        restoreInitialValues();
                    },
                    ImmutableSet.<String>builder()
                            .add(readPermission)
                            .addAll(mDependentOnPropertyPermissions)
                            .build()
                            .toArray(new String[0]));
        }
    }

    private boolean hasReadPermissions(ImmutableSet<String> allReadPermissions) {
        for (String permission : allReadPermissions) {
            if (mContext.checkSelfPermission(permission) == PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void assertGetPropertyThrowsException(
            String msg, Class<? extends Throwable> exceptionClass, int propertyId, int areaId) {
        assertThrows(
                msg, exceptionClass, () -> mCarPropertyManager.getProperty(propertyId, areaId));
        assertThrows(
                msg,
                exceptionClass,
                () -> mCarPropertyManager.getBooleanProperty(propertyId, areaId));
        assertThrows(
                msg, exceptionClass, () -> mCarPropertyManager.getIntProperty(propertyId, areaId));
        assertThrows(
                msg,
                exceptionClass,
                () -> mCarPropertyManager.getFloatProperty(propertyId, areaId));
        assertThrows(
                msg,
                exceptionClass,
                () -> mCarPropertyManager.getIntArrayProperty(propertyId, areaId));
    }

    private void verifyWritePermissionsCannotRead(
            ImmutableSet<String> writePermissions,
            ImmutableSet<String> allReadPermissions,
            int areaId) {
        // If there is any write permission that is also a read permission we skip the permissions.
        if (!Collections.disjoint(writePermissions, allReadPermissions)) {
            return;
        }
        // It is possible that the caller has the read permissions without adopting the shell
        // identity. In this case, we cannot revoke the read permissions so we cannot test
        // getProperty without read permissions.
        if (hasReadPermissions(allReadPermissions)) {
            return;
        }
        runWithShellPermissionIdentity(
                () -> {
                    assertGetPropertyThrowsException(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be read without read"
                                    + " permissions.",
                            SecurityException.class,
                            mPropertyId,
                            areaId);
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be listened to without read"
                                    + " permissions.",
                            SecurityException.class,
                            () -> verifyCarPropertyValueCallback());
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be read without read"
                                    + " permissions.",
                            SecurityException.class,
                            () -> verifyGetPropertiesAsync());

                    // If the caller only has write permission, registerCallback throws
                    // SecurityException.
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be listened to without read"
                                    + " permission.",
                            SecurityException.class,
                            () ->
                                    mCarPropertyManager.registerCallback(
                                            FAKE_CALLBACK, mPropertyId, 0f));

                    if (isAtLeastV() && Flags.variableUpdateRate()) {
                        // For the new API, if the caller does not read permission, it throws
                        // SecurityException.
                        assertThrows(
                                mPropertyName
                                        + " - property ID: "
                                        + mPropertyId
                                        + " should not be able to be listened to without read"
                                        + " permission.",
                                SecurityException.class,
                                () ->
                                        mCarPropertyManager.subscribePropertyEvents(
                                                mPropertyId, areaId, FAKE_CALLBACK));
                    }
                },
                writePermissions.toArray(new String[0]));
    }

    private void verifyIndividualWritePermissionsCannotWrite(
            ImmutableSet<String> writePermissions, int areaId) {
        // It is possible that the caller has the write permissions without adopting
        // the shell identity. In this case, we cannot revoke individual permissions.
        if (hasWritePermissions(ImmutableList.of(writePermissions))) {
            return;
        }

        String writePermissionsNeededString = String.join(", ", writePermissions);
        for (String writePermission : writePermissions) {
            runWithShellPermissionIdentity(
                    () -> {
                        assertThat(getCarPropertyConfig(/* useCache= */ false)).isNull();
                        assertThrows(
                                mPropertyName
                                        + " - property ID: "
                                        + mPropertyId
                                        + " should not be able to be written to without all of the"
                                        + " following permissions granted: "
                                        + writePermissionsNeededString,
                                SecurityException.class,
                                () ->
                                        mCarPropertyManager.setProperty(
                                                mPropertyType,
                                                mPropertyId,
                                                areaId,
                                                getDefaultValue(mPropertyType)));
                    },
                    writePermission);
        }
    }

    private void verifyWritePermissionsGiveAccessToWriteApis(
            String step,
            ImmutableSet<String> writePermissions,
            ImmutableSet<String> readPermissions,
            Class<?> exceptedExceptionClass) {
        ImmutableSet<String> propertyPermissions =
                ImmutableSet.<String>builder()
                        .addAll(writePermissions)
                        .addAll(readPermissions)
                        .build();

        if (step.equals(STEP_VERIFY_WRITE_APIS_DISABLE_ADAS_FEATURE_VERIFY_STATE)) {
            assumeTrue("Not an ADAS property", mDependentOnPropertyId.isPresent());
            disableAdasFeatureIfAdasStatePropertyAndVerify(
                    propertyPermissions.toArray(new String[0]), /* verifySet= */ true);
            return;
        }

        try {
            // Store the current value before we call enableAdasFeatureIfAdasStateProperty, which
            // might change this.
            runWithShellPermissionIdentity(
                    () -> {
                        storeCurrentValues();
                    },
                    propertyPermissions.toArray(new String[0]));
            enableAdasFeatureIfAdasStateProperty();

            runWithShellPermissionIdentity(
                    () -> {
                        turnOnHvacPowerIfHvacPowerDependent();

                        if (step.equals(STEP_VERIFY_WRITE_APIS_SET_PROPERTY_SYNC)) {
                            verifyCarPropertyValueSetter();
                            if (exceptedExceptionClass != null) {
                                assertWithMessage(
                                                "Expected "
                                                        + sExceptionClassOnSet
                                                        + " to be of type "
                                                        + exceptedExceptionClass)
                                        .that(sExceptionClassOnSet)
                                        .isEqualTo(exceptedExceptionClass);
                            }
                        }

                        if (step.equals(STEP_VERIFY_WRITE_APIS_SET_PROPERTY_ASYNC)
                                && exceptedExceptionClass == null) {
                            verifySetPropertiesAsync();
                        }
                        if (step.equals(STEP_VERIFY_WRITE_APIS_DISABLE_HVAC_SET_NOT_AVAILABLE)) {
                            assumeTrue(
                                    "Not depending on HVAC power", mPossiblyDependentOnHvacPowerOn);
                            ImmutableSet<Integer> areaIdsTurnedOff =
                                    turnOffHvacPowerIfHvacPowerDependent();
                            if (!areaIdsTurnedOff.isEmpty()) {
                                verifySetNotAvailable(areaIdsTurnedOff);
                            }
                        }
                    },
                    propertyPermissions.toArray(new String[0]));
        } finally {
            // Restore all property values even if test fails.
            runWithShellPermissionIdentity(
                    () -> {
                        restoreInitialValues();
                    },
                    ImmutableSet.<String>builder()
                            .addAll(propertyPermissions)
                            .addAll(mDependentOnPropertyPermissions)
                            .build()
                            .toArray(new String[0]));
        }
    }

    private void turnOnHvacPowerIfHvacPowerDependent() {
        if (!mPossiblyDependentOnHvacPowerOn) {
            return;
        }

        CarPropertyConfig<Boolean> hvacPowerOnCarPropertyConfig =
                (CarPropertyConfig<Boolean>) getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
        if (hvacPowerOnCarPropertyConfig == null
                || !hvacPowerOnCarPropertyConfig.getConfigArray().contains(mPropertyId)) {
            return;
        }

        storeCurrentValuesForProperty(hvacPowerOnCarPropertyConfig);
        // Turn the power on for all supported HVAC area IDs.
        setBooleanPowerPropertyInAllAreaIds(
                hvacPowerOnCarPropertyConfig, /* setValue: */ Boolean.TRUE);
    }

    private ImmutableSet<Integer> turnOffHvacPowerIfHvacPowerDependent() {
        CarPropertyConfig<Boolean> hvacPowerOnCarPropertyConfig =
                (CarPropertyConfig<Boolean>) getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
        if (hvacPowerOnCarPropertyConfig == null
                || !hvacPowerOnCarPropertyConfig.getConfigArray().contains(mPropertyId)) {
            return ImmutableSet.of();
        }

        // Turn the power off for all supported HVAC area IDs.
        return setBooleanPowerPropertyInAllAreaIds(
                hvacPowerOnCarPropertyConfig, /* setValue: */ Boolean.FALSE);
    }

    /** Enables the ADAS feature if the property is an ADAS property. */
    public void enableAdasFeatureIfAdasStateProperty() {
        if (!mDependentOnPropertyId.isPresent()) {
            return;
        }

        runWithShellPermissionIdentity(
                () -> {
                    int adasEnabledPropertyId = mDependentOnPropertyId.get();
                    CarPropertyConfig<Boolean> adasEnabledCarPropertyConfig =
                            (CarPropertyConfig<Boolean>)
                                    getCarPropertyConfig(adasEnabledPropertyId);

                    if (adasEnabledCarPropertyConfig == null
                            || !canWrite(
                                    getAreaIdAccessOrElseGlobalAccess(
                                            adasEnabledCarPropertyConfig, GLOBAL_AREA_ID))) {
                        Log.w(
                                TAG,
                                "Cannot enable "
                                        + VehiclePropertyIds.toString(adasEnabledPropertyId)
                                        + " for testing "
                                        + VehiclePropertyIds.toString(mPropertyId)
                                        + " because property is either not implemented or READ"
                                        + " only. Manually enable if it's not already enabled.");
                        return;
                    }

                    storeCurrentValuesForProperty(adasEnabledCarPropertyConfig);
                    // Enable ADAS feature in all supported area IDs.
                    setBooleanPowerPropertyInAllAreaIds(
                            adasEnabledCarPropertyConfig, /* setValue: */ Boolean.TRUE);
                },
                mDependentOnPropertyPermissions.toArray(new String[0]));
    }

    private void disableAdasFeatureIfAdasStatePropertyAndVerify(
            String[] enabledPermissionsList, boolean verifySet) {
        try {
            ImmutableSet<Integer> areaIdsDisabled = disableAdasFeatureIfAdasStateProperty();
            if (areaIdsDisabled.isEmpty()) {
                return;
            }
            runWithShellPermissionIdentity(
                    () -> {
                        if (mVerifyErrorStates) {
                            verifyAdasPropertyErrorState(areaIdsDisabled);
                        } else if (verifySet) {
                            verifySetNotAvailable(areaIdsDisabled);
                        } else {
                            verifyGetNotAvailable(areaIdsDisabled);
                        }
                    },
                    enabledPermissionsList);
        } finally {
            // Restore all property values even if test fails.
            runWithShellPermissionIdentity(
                    () -> {
                        restoreInitialValues();
                    },
                    mDependentOnPropertyPermissions.toArray(new String[0]));
        }
    }

    /**
     * Disables the ADAS feature if the property is an ADAS property.
     *
     * @return all area IDs that are disabled
     */
    public ImmutableSet<Integer> disableAdasFeatureIfAdasStateProperty() {
        if (!mDependentOnPropertyId.isPresent()) {
            return ImmutableSet.of();
        }

        ImmutableSet.Builder<Integer> areaIdsDisabled = ImmutableSet.builder();
        runWithShellPermissionIdentity(
                () -> {
                    int adasEnabledPropertyId = mDependentOnPropertyId.get();
                    CarPropertyConfig<Boolean> adasEnabledCarPropertyConfig =
                            (CarPropertyConfig<Boolean>)
                                    getCarPropertyConfig(adasEnabledPropertyId);

                    if (adasEnabledCarPropertyConfig == null
                            || !canWrite(
                                    getAreaIdAccessOrElseGlobalAccess(
                                            adasEnabledCarPropertyConfig, GLOBAL_AREA_ID))) {
                        return;
                    }

                    storeCurrentValuesForProperty(adasEnabledCarPropertyConfig);

                    // Disable ADAS feature in all supported area IDs.
                    areaIdsDisabled.addAll(
                            setBooleanPowerPropertyInAllAreaIds(
                                    adasEnabledCarPropertyConfig, /* setValue: */ Boolean.FALSE));
                },
                mDependentOnPropertyPermissions.toArray(new String[0]));
        return areaIdsDisabled.build();
    }

    /** Stores the property's current values for all areas so that they can be restored later. */
    public void storeCurrentValues() {
        storeCurrentValuesForProperty(getCarPropertyConfig());
    }

    private <U> void storeCurrentValuesForProperty(CarPropertyConfig<U> carPropertyConfig) {
        SparseArray<U> areaIdToInitialValue =
                getInitialValuesByAreaId(carPropertyConfig, mCarPropertyManager);
        if (areaIdToInitialValue == null || areaIdToInitialValue.size() == 0) {
            return;
        }
        var propertyId = carPropertyConfig.getPropertyId();
        if (mPropertyToAreaIdValues.contains(propertyId)) {
            throw new IllegalStateException(
                    "The property: "
                            + VehiclePropertyIds.toString(propertyId)
                            + " already has a stored value");
        }
        mStoredProperties.add(propertyId);
        for (int i = 0; i < areaIdToInitialValue.size(); i++) {
            Log.i(
                    TAG,
                    "Storing initial value for property:"
                            + VehiclePropertyIds.toString(propertyId)
                            + " at area ID: "
                            + areaIdToInitialValue.keyAt(i)
                            + " to "
                            + areaIdToInitialValue.valueAt(i));
        }
        mPropertyToAreaIdValues.put(propertyId, areaIdToInitialValue);
    }

    private void restoreInitialValue(int propertyId) {
        var carPropertyConfig = getCarPropertyConfig(propertyId);
        SparseArray<?> areaIdToInitialValue = mPropertyToAreaIdValues.get(propertyId);

        if (areaIdToInitialValue == null || carPropertyConfig == null) {
            Log.w(
                    TAG,
                    "No stored values for "
                            + VehiclePropertyIds.toString(propertyId)
                            + " to restore to, ignore");
            return;
        }

        restoreInitialValuesByAreaId(carPropertyConfig, mCarPropertyManager, areaIdToInitialValue);
    }

    /**
     * Restore the property's and dependent properties values to original values stored by previous
     * {@link #storeCurrentValues}.
     *
     * <p>Do nothing if no stored current values are available.
     *
     * <p>The properties values are restored in the reverse-order as they are stored.
     */
    public void restoreInitialValues() {
        for (int i = mStoredProperties.size() - 1; i >= 0; i--) {
            restoreInitialValue(mStoredProperties.get(i));
        }
        mStoredProperties.clear();
        mPropertyToAreaIdValues.clear();
    }

    // Get a map storing the property's area Ids to the initial values.
    @Nullable
    private static <U> SparseArray<U> getInitialValuesByAreaId(
            CarPropertyConfig<U> carPropertyConfig, CarPropertyManager carPropertyManager) {
        if (!AREA_ID_CONFIG_ACCESS_FLAG
                && !(canRead(carPropertyConfig.getAccess())
                        && canWrite(carPropertyConfig.getAccess()))) {
            return null;
        }
        SparseArray<U> areaIdToInitialValue = new SparseArray<U>();
        int propertyId = carPropertyConfig.getPropertyId();
        String propertyName = VehiclePropertyIds.toString(propertyId);
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!(canRead(carPropertyConfig, areaId) && canWrite(carPropertyConfig, areaId))) {
                continue;
            }
            CarPropertyValue<U> carPropertyValue;
            try {
                carPropertyValue = carPropertyManager.getProperty(propertyId, areaId);
            } catch (PropertyNotAvailableAndRetryException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                Log.w(
                        TAG,
                        "Failed to get property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " to save initial car property value. Error: "
                                + e);
                continue;
            }
            if (carPropertyValue == null) {
                Log.w(
                        TAG,
                        "Failed to get property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " to save initial car property value.");
                continue;
            }
            if (carPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                Log.w(
                        TAG,
                        "Cannot save initial value for property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " because status: "
                                + carPropertyValue.getStatus());
                continue;
            }
            areaIdToInitialValue.put(areaId, (U) carPropertyValue.getValue());
        }
        return areaIdToInitialValue;
    }

    /**
     * Set a boolean property that represents the power state to a desired value in all supported
     * area IDs.
     *
     * <p>Power properties include {@code HVAC_POWER_ON} and all ADAS ENABLED properties such as
     * {@code AUTOMATIC_EMERGENCY_BRAKING_ENABLED}.
     *
     * @return all area IDs that are set (or already set) to {@code setValue}
     */
    private ImmutableSet<Integer> setBooleanPowerPropertyInAllAreaIds(
            CarPropertyConfig<Boolean> booleanCarPropertyConfig, Boolean setValue) {
        ImmutableSet.Builder<Integer> areaIdsUpdated = ImmutableSet.builder();
        boolean updateSentToVhal = false;
        int propertyId = booleanCarPropertyConfig.getPropertyId();
        for (int areaId : booleanCarPropertyConfig.getAreaIds()) {
            if (mCarPropertyManager.getBooleanProperty(propertyId, areaId) == setValue) {
                areaIdsUpdated.add(areaId);
                continue;
            }
            CarPropertyValue<Boolean> updatedValue =
                    setPropertyAndWaitForChange(
                            mCarPropertyManager, propertyId, Boolean.class, areaId, setValue);
            if (updatedValue == null) {
                continue;
            }
            areaIdsUpdated.add(areaId);
            updateSentToVhal = true;
        }
        if (updateSentToVhal && mPowerPropagationDelayMs != 0) {
            Log.i(
                    TAG,
                    "Setting power property:"
                            + VehiclePropertyIds.toString(propertyId)
                            + " to value: "
                            + setValue
                            + " and sleeping for: "
                            + mPowerPropagationDelayMs);
            // Wait for power status to propagate to dependent properties
            SystemClock.sleep(mPowerPropagationDelayMs);
            Log.i(TAG, "Completed sleeping for: " + mPowerPropagationDelayMs);
        }
        return areaIdsUpdated.build();
    }

    // Restore the initial values of the property provided by {@code areaIdToInitialValue}.
    private static void restoreInitialValuesByAreaId(
            CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager,
            SparseArray<?> areaIdToInitialValue) {
        int propertyId = carPropertyConfig.getPropertyId();
        String propertyName = VehiclePropertyIds.toString(propertyId);
        for (int i = 0; i < areaIdToInitialValue.size(); i++) {
            int areaId = areaIdToInitialValue.keyAt(i);
            Object originalValue = areaIdToInitialValue.valueAt(i);
            CarPropertyValue<?> currentCarPropertyValue;
            try {
                currentCarPropertyValue = carPropertyManager.getProperty(propertyId, areaId);
            } catch (PropertyNotAvailableAndRetryException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                Log.w(
                        TAG,
                        "Failed to get property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " to restore initial car property value. Error: "
                                + e);
                continue;
            }
            if (currentCarPropertyValue == null) {
                Log.w(
                        TAG,
                        "Failed to get property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " to restore initial car property value.");
                continue;
            }
            if (currentCarPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                Log.w(
                        TAG,
                        "Cannot restore initial value for property:"
                                + propertyName
                                + " at area ID: "
                                + areaId
                                + " because status: "
                                + currentCarPropertyValue.getStatus());
                continue;
            }
            if (valueEquals(originalValue, currentCarPropertyValue.getValue())) {
                continue;
            }
            Log.i(
                    TAG,
                    "Restoring value for: "
                            + propertyName
                            + " at area ID: "
                            + areaId
                            + " to "
                            + originalValue);
            setPropertyAndWaitForChange(
                    carPropertyManager, propertyId, Object.class, areaId, originalValue);
        }
    }

    /**
     * Gets the possible values that could be set to.
     *
     * <p>The values returned here must not cause {@code IllegalArgumentException} for set.
     *
     * <p>Returns {@code null} or empty array if we don't know possible values.
     */
    public @Nullable Collection<T> getPossibleValues(int areaId) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (Boolean.class.equals(carPropertyConfig.getPropertyType())) {
            return (List<T>) List.of(Boolean.TRUE, Boolean.FALSE);
        } else if (Integer.class.equals(carPropertyConfig.getPropertyType())) {
            return (List<T>) getPossibleIntegerValues(areaId);
        } else if (Float.class.equals(carPropertyConfig.getPropertyType())) {
            return getPossibleFloatValues(areaId);
        }
        return null;
    }

    public static boolean isAtLeastU() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    private static boolean isAtLeastV() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    private static boolean isAtLeastB() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA;
    }

    /** Gets the possible values for an integer property. */
    private List<Integer> getPossibleIntegerValues(int areaId) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        List<Integer> possibleValues = new ArrayList<>();
        if (mPropertyId == VehiclePropertyIds.HVAC_FAN_DIRECTION) {
            int[] availableHvacFanDirections =
                    mCarPropertyManager.getIntArrayProperty(
                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE, areaId);
            for (int i = 0; i < availableHvacFanDirections.length; i++) {
                if (availableHvacFanDirections[i] != CarHvacFanDirection.UNKNOWN) {
                    possibleValues.add(availableHvacFanDirections[i]);
                }
            }
            return possibleValues;
        }
        if (mVerifySetterWithConfigArrayValues) {
            for (Integer value : carPropertyConfig.getConfigArray()) {
                possibleValues.add(value);
            }
            return possibleValues;
        }

        if (!mAllPossibleEnumValues.isEmpty() && isAtLeastU()) {
            AreaIdConfig areaIdConfig = carPropertyConfig.getAreaIdConfig(areaId);
            for (Integer value : (List<Integer>) areaIdConfig.getSupportedEnumValues()) {
                if ((mAllPossibleUnwritableValues.isEmpty()
                                || !mAllPossibleUnwritableValues.contains(value))
                        && (mAllPossibleUnavailableValues.isEmpty()
                                || !mAllPossibleUnavailableValues.contains(value))) {
                    possibleValues.add(value);
                }
            }
        } else {
            Integer minValue = (Integer) carPropertyConfig.getMinValue(areaId);
            Integer maxValue = (Integer) carPropertyConfig.getMaxValue(areaId);
            assertWithMessage(
                            "Read-write/Write integer properties should either have a config array"
                                + " with valid set values, a set of supported enums, or valid min"
                                + " and max values set in the CarPropertyConfig. However, the"
                                + " following property has none of these: "
                                    + VehiclePropertyIds.toString(mPropertyId))
                    .that(minValue != null && maxValue != null)
                    .isTrue();
            List<Integer> valuesToSet =
                    IntStream.rangeClosed(minValue.intValue(), maxValue.intValue())
                            .boxed()
                            .collect(Collectors.toList());
            for (int i = 0; i < valuesToSet.size(); i++) {
                possibleValues.add(valuesToSet.get(i));
            }
        }
        return possibleValues;
    }

    /** Gets the possible values for a float property. */
    private Collection<T> getPossibleFloatValues(int areaId) {
        ImmutableSet.Builder<Float> possibleValuesBuilder = ImmutableSet.builder();
        if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_SET) {
            List<Integer> hvacTempSetConfigArray = getCarPropertyConfig().getConfigArray();
            if (!hvacTempSetConfigArray.isEmpty()) {
                // For HVAC_TEMPERATURE_SET, the configArray specifies the supported temperature
                // values for the property. configArray[0] is the lower bound of the supported
                // temperatures in Celsius. configArray[1] is the upper bound of the supported
                // temperatures in Celsius. configArray[2] is the supported temperature increment
                // between the two bounds. All configArray values are Celsius*10 since the
                // configArray is List<Integer> but HVAC_TEMPERATURE_SET is a Float type property.
                for (int possibleHvacTempSetValue = hvacTempSetConfigArray.get(0);
                        possibleHvacTempSetValue <= hvacTempSetConfigArray.get(1);
                        possibleHvacTempSetValue += hvacTempSetConfigArray.get(2)) {
                    possibleValuesBuilder.add((float) possibleHvacTempSetValue / 10.0f);
                }
            } else {
                // If the configArray is not specified, then use min/max values.
                Float minValueFloat = (Float) getCarPropertyConfig().getMinValue(areaId);
                Float maxValueFloat = (Float) getCarPropertyConfig().getMaxValue(areaId);
                possibleValuesBuilder.add(minValueFloat);
                possibleValuesBuilder.add(maxValueFloat);
            }
        } else if (mPropertyId == VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT) {
            List<Integer> evChargePercentLimitConfigArray = getCarPropertyConfig().getConfigArray();
            if (!evChargePercentLimitConfigArray.isEmpty()) {
                for (Integer possibleEvChargePercentLimit : evChargePercentLimitConfigArray) {
                    possibleValuesBuilder.add(possibleEvChargePercentLimit.floatValue());
                }
            } else {
                // If the configArray is not specified, then values between 0 and 100 percent must
                // be supported.
                possibleValuesBuilder.add(0f);
                possibleValuesBuilder.add(100f);
            }
        } else if (mPropertyId == VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT) {
            // First value in the configArray specifies the max current draw allowed by the vehicle.
            Integer vehicleMaxCurrentDrawLimit = getCarPropertyConfig().getConfigArray().get(0);
            possibleValuesBuilder.add(vehicleMaxCurrentDrawLimit.floatValue());
        } else if (mPropertyId == VehiclePropertyIds.RANGE_REMAINING) {
            // Test when no range is remaining
            possibleValuesBuilder.add(0f);
        }
        return (Collection<T>) possibleValuesBuilder.build();
    }

    private void verifyCarPropertyValueSetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG && !canWrite(carPropertyConfig.getAccess())) {
            verifySetPropertyFails(carPropertyConfig.getAreaIds()[0]);
            return;
        }
        if (Boolean.class.equals(carPropertyConfig.getPropertyType())) {
            verifyBooleanPropertySetter();
        } else if (Integer.class.equals(carPropertyConfig.getPropertyType())) {
            verifyIntegerPropertySetter();
        } else if (Float.class.equals(carPropertyConfig.getPropertyType())) {
            verifyFloatPropertySetter();
        } else if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION) {
            verifyHvacTemperatureValueSuggestionSetter();
        }
    }

    private void verifySetPropertyFails(int areaId) {
        if (!isAtLeastU()) {
            // The precondition check in CarPropertyService that throws an IllegalArgumentException
            // when a client tries to write a property that is implemented as read_only was added in
            // Android U. API behavior prior to Android U is undefined so skipping this check.
            return;
        }
        assertThrows(
                mPropertyName
                        + " is a read_only property so setProperty should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () ->
                        mCarPropertyManager.setProperty(
                                mPropertyType,
                                mPropertyId,
                                areaId,
                                getDefaultValue(mPropertyType)));
    }

    private void verifyBooleanPropertySetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!canWrite(carPropertyConfig, areaId)) {
                verifySetPropertyFails(areaId);
                continue;
            }
            for (Boolean valueToSet : List.of(Boolean.TRUE, Boolean.FALSE)) {
                verifySetProperty(areaId, (T) valueToSet);
            }
        }
    }

    private void verifyIntegerPropertySetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!canWrite(carPropertyConfig, areaId)) {
                verifySetPropertyFails(areaId);
                continue;
            }
            for (Integer valueToSet : getPossibleIntegerValues(areaId)) {
                verifySetProperty(areaId, (T) valueToSet);
            }
        }
        if (!mAllPossibleEnumValues.isEmpty() && isAtLeastU()) {
            for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
                if (!canWrite(carPropertyConfig, areaIdConfig.getAreaId())) {
                    continue;
                }
                for (T valueToSet : (List<T>) areaIdConfig.getSupportedEnumValues()) {
                    if (!mAllPossibleUnwritableValues.isEmpty()
                            && mAllPossibleUnwritableValues.contains(valueToSet)) {
                        assertThrows(
                                "Trying to set an unwritable value: "
                                        + valueToSet
                                        + " to property: "
                                        + mPropertyId
                                        + " should throw an "
                                        + "IllegalArgumentException",
                                IllegalArgumentException.class,
                                () ->
                                        mCarPropertyManager.setProperty(
                                                carPropertyConfig.getPropertyType(), mPropertyId,
                                                areaIdConfig.getAreaId(), valueToSet));
                    }
                    if (!mAllPossibleUnavailableValues.isEmpty()
                            && mAllPossibleUnavailableValues.contains(valueToSet)) {
                        assertThrows(
                                "Trying to set an unavailable value: "
                                        + valueToSet
                                        + " to property: "
                                        + mPropertyId
                                        + " should throw an "
                                        + "PropertyNotAvailableException",
                                PropertyNotAvailableException.class,
                                () ->
                                        mCarPropertyManager.setProperty(
                                                carPropertyConfig.getPropertyType(), mPropertyId,
                                                areaIdConfig.getAreaId(), valueToSet));
                    }
                }
            }
        }
    }

    private void verifyFloatPropertySetter() {
        for (int areaId : getCarPropertyConfig().getAreaIds()) {
            for (T valueToSet : getPossibleFloatValues(areaId)) {
                verifySetProperty(areaId, valueToSet);
            }
        }
    }

    private void verifySetProperty(int areaId, T valueToSet) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!canWrite(carPropertyConfig, areaId)) {
            return;
        }

        verifySetPropertyWithNullValueThrowsException(areaId);

        int access = getAreaIdAccessOrElseGlobalAccess(carPropertyConfig, areaId);
        if (canWrite(access) && !canRead(access)) {
            Log.w(
                    TAG,
                    "Property: "
                            + mPropertyName
                            + " will be altered during the test and it is"
                            + " not possible to restore.");
            verifySetPropertyOkayOrThrowExpectedExceptions(areaId, valueToSet);
            return;
        }
        try {
            CarPropertyValue<T> currentCarPropertyValue =
                    mCarPropertyManager.getProperty(mPropertyId, areaId);
            verifyCarPropertyValue(
                    currentCarPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_GETTER);
            if (currentCarPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                Log.w(
                        TAG,
                        "Skipping SET verification for propertyId: "
                                + mPropertyId
                                + " areaId: "
                                + areaId
                                + " valueToSet:"
                                + valueToSet
                                + " because getProperty did not have an AVAILABLE status - "
                                + currentCarPropertyValue);
                return;
            }
            if (valueEquals(valueToSet, currentCarPropertyValue.getValue())) {
                return;
            }
        } catch (PropertyNotAvailableAndRetryException e) {
            Log.w(
                    TAG,
                    "Skipping SET verification for propertyId: "
                            + mPropertyName
                            + " areaId: "
                            + areaId
                            + " valueToSet:"
                            + valueToSet
                            + " because getProperty threw PropertyNotAvailableAndRetryException - "
                            + e);
            return;
        } catch (PropertyNotAvailableException e) {
            verifyPropertyNotAvailableException(e);
            return;
        } catch (CarInternalErrorException e) {
            verifyInternalErrorException(e);
            return;
        }
        CarPropertyValue<T> updatedCarPropertyValue =
                setPropertyAndWaitForChange(
                        mCarPropertyManager,
                        mPropertyId,
                        carPropertyConfig.getPropertyType(),
                        areaId,
                        valueToSet);
        if (updatedCarPropertyValue != null) {
            verifyCarPropertyValue(
                    updatedCarPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
        }
    }

    private void verifySetPropertyWithNullValueThrowsException(int areaId) {
        assertThrows(
                NullPointerException.class,
                () ->
                        mCarPropertyManager.setProperty(
                                mPropertyType, mPropertyId, areaId, /* val= */ null));
    }

    private void verifyHvacTemperatureValueSuggestionSetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        CarPropertyConfig<?> hvacTemperatureSetCarPropertyConfig =
                getCarPropertyConfig(VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        if (hvacTemperatureSetCarPropertyConfig == null) {
            return;
        }
        List<Integer> hvacTemperatureSetConfigArray =
                hvacTemperatureSetCarPropertyConfig.getConfigArray();
        if (hvacTemperatureSetConfigArray.isEmpty()) {
            return;
        }
        float minTempInCelsius = hvacTemperatureSetConfigArray.get(0).floatValue() / 10f;
        float minTempInFahrenheit = hvacTemperatureSetConfigArray.get(3).floatValue() / 10f;

        Float[] temperatureRequest =
                new Float[] {
                    /* requestedValue= */ minTempInCelsius,
                    /* units= */ (float) 0x30, // VehicleUnit#CELSIUS
                    /* suggestedValueInCelsius= */ 0f,
                    /* suggestedValueInFahrenheit= */ 0f
                };
        Float[] expectedTemperatureResponse =
                new Float[] {
                    /* requestedValue= */ minTempInCelsius,
                    /* units= */ (float) 0x30, // VehicleUnit#CELSIUS
                    /* suggestedValueInCelsius= */ minTempInCelsius,
                    /* suggestedValueInFahrenheit= */ minTempInFahrenheit
                };
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<Float[]> updatedCarPropertyValue =
                    setPropertyAndWaitForChange(
                            mCarPropertyManager,
                            mPropertyId,
                            Float[].class,
                            areaId,
                            temperatureRequest,
                            expectedTemperatureResponse);
            if (updatedCarPropertyValue != null) {
                verifyCarPropertyValue(
                        updatedCarPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            }
        }
    }

    private void verifySetPropertyOkayOrThrowExpectedExceptions(int areaId, T valueToSet) {
        spaceOutCarPropertyManagerActions();
        try {
            mCarPropertyManager.setProperty(mPropertyType, mPropertyId, areaId, valueToSet);
        } catch (PropertyNotAvailableAndRetryException e) {
            // Do nothing
        } catch (PropertyNotAvailableException e) {
            verifyPropertyNotAvailableException(e);
        } catch (CarInternalErrorException e) {
            verifyInternalErrorException(e);
        } catch (Exception e) {
            assertWithMessage(
                            "Unexpected exception thrown when trying to setProperty on "
                                    + mPropertyName
                                    + ": "
                                    + e)
                    .fail();
        }
    }

    private void verifyGetNotAvailable(Collection<Integer> areaIdsNotAvailable) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!isAreaIdSupportedInList(areaId, areaIdsNotAvailable)) {
                Log.i(
                        TAG,
                        "Skipping verifyGetNotAvailable because power could not be turned off for"
                                + " power dependent property - "
                                + mPropertyName
                                + " at areaId - "
                                + areaId);
                continue;
            }

            try {
                // getProperty may/may not throw exception when the property is not available.
                CarPropertyValue<T> currentValue =
                        mCarPropertyManager.getProperty(mPropertyId, areaId);
                assertWithMessage(
                                "When the power is turned off getProperty should throw"
                                        + " PropertyNotAvailableException when trying to get a"
                                        + " property with StatusCode.NOT_AVAILABLE or return a"
                                        + " CarPropertyValue with status UNAVAILABLE."
                                        + " Returned CarPropertyValue: "
                                        + currentValue.toString())
                        .that(currentValue.getStatus())
                        .isEqualTo(CarPropertyValue.STATUS_UNAVAILABLE);
            } catch (Exception e) {
                // If the property is read or read-write, then this should throw
                // PropertyNotAvailableException. If the property is write-only, then it will throw
                // IllegalArgumentException.
                assertWithMessage(
                                "Getting property "
                                        + mPropertyName
                                        + " when it's not available"
                                        + " should throw either PropertyNotAvailableException or"
                                        + " IllegalArgumentException.")
                        .that(e.getClass())
                        .isAnyOf(
                                PropertyNotAvailableException.class,
                                IllegalArgumentException.class);
            }
        }
    }

    private void verifySetNotAvailable(Collection<Integer> areaIdsNotAvailable) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG
                && !(canRead(carPropertyConfig.getAccess())
                        && canWrite(carPropertyConfig.getAccess()))) {
            return;
        }

        T valueToSet = getDefaultValue(mPropertyType);
        if (valueToSet == null) {
            assertWithMessage("Testing mixed type property is not supported").fail();
        }
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!(canRead(carPropertyConfig, areaId) && canWrite(carPropertyConfig, areaId))) {
                continue;
            }

            if (!isAreaIdSupportedInList(areaId, areaIdsNotAvailable)) {
                Log.i(
                        TAG,
                        "Skipping verifySetNotAvailable because power could not be turned off for"
                                + " power dependent property - "
                                + mPropertyName
                                + " at areaId - "
                                + areaId);
                continue;
            }

            spaceOutCarPropertyManagerActions();
            SetterCallback setterCallback = new SetterCallback(mPropertyId, areaId, valueToSet);
            assertWithMessage(
                            "Failed to register no change setter callback for "
                                    + VehiclePropertyIds.toString(mPropertyId))
                    .that(
                            subscribePropertyEvents(
                                    mCarPropertyManager,
                                    setterCallback,
                                    mPropertyId,
                                    CarPropertyManager.SENSOR_RATE_FASTEST))
                    .isTrue();

            try {
                mCarPropertyManager.setProperty(mPropertyType, mPropertyId, areaId, valueToSet);
                CarPropertyValue<T> updatedValue =
                        setterCallback.waitForPropertyEvent(SET_PROPERTY_CALLBACK_TIMEOUT_SEC);
                if (updatedValue != null
                        && updatedValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
                    // If the callback receives a new event with the value set before the timeout,
                    // then this check will fail.
                    assertWithMessage(
                                    "Received onChangeEvent(s) for "
                                            + mPropertyName
                                            + " with updated value: "
                                            + valueToSet
                                            + " before 5s timeout. When the power is turned off,"
                                            + " this property must not be available to set.")
                            .that(updatedValue.getValue())
                            .isNotEqualTo(valueToSet);
                }
            } catch (Exception e) {
                // In normal cases, this should throw PropertyNotAvailableException.
                // In rare cases, the value we are setting is the same as the current value,
                // which makes the set operation a no-op. So it is possible that no exception
                // is thrown here.
                // It is also possible that this may throw IllegalArgumentException if the value to
                // set is not valid.
                assertWithMessage(
                                "Setting property "
                                        + mPropertyName
                                        + " when it's not available"
                                        + " should throw either PropertyNotAvailableException or"
                                        + " IllegalArgumentException.")
                        .that(e.getClass())
                        .isAnyOf(
                                PropertyNotAvailableException.class,
                                IllegalArgumentException.class);
            } finally {
                unsubscribePropertyEvents(mCarPropertyManager, setterCallback, mPropertyId);
            }
        }
    }

    private void verifyAdasPropertyErrorState(Collection<Integer> areaIdsDisabled) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG && !canRead(carPropertyConfig.getAccess())) {
            return;
        }

        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!canRead(carPropertyConfig, areaId)) {
                continue;
            }
            if (!isAreaIdSupportedInList(areaId, areaIdsDisabled)) {
                Log.i(
                        TAG,
                        "Skipping verifyAdasPropertyErrorState because could not disable feature"
                                + " for ADAS STATE property - "
                                + mPropertyName
                                + " at areaId - "
                                + areaId);
                continue;
            }

            Integer adasState = mCarPropertyManager.getIntProperty(mPropertyId, areaId);
            assertWithMessage(
                            "When ADAS feature is disabled, "
                                    + mPropertyName
                                    + " must be set to "
                                    + ErrorState.NOT_AVAILABLE_DISABLED
                                    + " (ErrorState.NOT_AVAILABLE_DISABLED).")
                    .that(adasState)
                    .isEqualTo(ErrorState.NOT_AVAILABLE_DISABLED);
        }
    }

    private static int getUpdatesPerAreaId(int changeMode) {
        return changeMode != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS ? 1 : 2;
    }

    private static long getRegisterCallbackTimeoutMillis(int changeMode, float minSampleRate) {
        long timeoutMillis = 1500;
        if (changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            float secondsToMillis = 1_000;
            long bufferMillis = 1_000; // 1 second
            timeoutMillis =
                    ((long)
                                    ((1.0f / minSampleRate)
                                            * secondsToMillis
                                            * getUpdatesPerAreaId(changeMode)))
                            + bufferMillis;
        }
        return timeoutMillis;
    }

    private static boolean subscribePropertyEvents(
            CarPropertyManager carPropertyManager,
            CarPropertyManager.CarPropertyEventCallback callback,
            int propertyId,
            float updateRateHz) {
        if (isAtLeastV() && Flags.variableUpdateRate()) {
            // Use new API if at least V.
            return carPropertyManager.subscribePropertyEvents(
                    List.of(
                            new Subscription.Builder(propertyId)
                                    .setUpdateRateHz(updateRateHz)
                                    .setVariableUpdateRateEnabled(false)
                                    .build()),
                    /* callbackExecutor= */ null,
                    callback);
        } else {
            return carPropertyManager.registerCallback(callback, propertyId, updateRateHz);
        }
    }

    private boolean subscribePropertyEvents(
            CarPropertyManager.CarPropertyEventCallback callback,
            int propertyId,
            float updateRateHz) {
        return subscribePropertyEvents(mCarPropertyManager, callback, propertyId, updateRateHz);
    }

    private static void unsubscribePropertyEvents(
            CarPropertyManager carPropertyManager,
            CarPropertyManager.CarPropertyEventCallback callback,
            int propertyId) {
        if (isAtLeastV() && Flags.variableUpdateRate()) {
            // Use new API if at least V.
            carPropertyManager.unsubscribePropertyEvents(propertyId, callback);
        } else {
            carPropertyManager.unregisterCallback(callback, propertyId);
        }
    }

    private void unsubscribePropertyEvents(
            CarPropertyManager.CarPropertyEventCallback callback, int propertyId) {
        unsubscribePropertyEvents(mCarPropertyManager, callback, propertyId);
    }

    private void verifyCarPropertyValueCallback() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!canReadAllAreaIds(carPropertyConfig)) {
            // This means we specify read permission for one property, but the OEM specify it as
            // write-only. This will only happen for properties that we allow READ_WRITE or WRITE.
            // We currently do not have such system property.
            return;
        }
        int updatesPerAreaId = getUpdatesPerAreaId(mChangeMode);
        long timeoutMillis =
                getRegisterCallbackTimeoutMillis(mChangeMode, carPropertyConfig.getMinSampleRate());

        CarPropertyValueCallback carPropertyValueCallback =
                new CarPropertyValueCallback(
                        mPropertyName,
                        carPropertyConfig.getAreaIds(),
                        updatesPerAreaId,
                        timeoutMillis);
        assertWithMessage("Failed to register callback for " + mPropertyName)
                .that(
                        subscribePropertyEvents(
                                carPropertyValueCallback,
                                mPropertyId,
                                carPropertyConfig.getMaxSampleRate()))
                .isTrue();
        SparseArray<List<CarPropertyValue<?>>> areaIdToCarPropertyValues =
                carPropertyValueCallback.getAreaIdToCarPropertyValues();
        unsubscribePropertyEvents(carPropertyValueCallback, mPropertyId);

        for (int areaId : carPropertyConfig.getAreaIds()) {
            List<CarPropertyValue<?>> carPropertyValues = areaIdToCarPropertyValues.get(areaId);
            assertWithMessage(mPropertyName + " callback value list is null for area ID: " + areaId)
                    .that(carPropertyValues)
                    .isNotNull();
            assertWithMessage(
                            mPropertyName
                                    + " callback values did not receive "
                                    + updatesPerAreaId
                                    + " updates for area ID: "
                                    + areaId)
                    .that(carPropertyValues.size())
                    .isAtLeast(updatesPerAreaId);
            for (CarPropertyValue<?> carPropertyValue : carPropertyValues) {
                verifyCarPropertyValue(
                        carPropertyValue,
                        carPropertyValue.getAreaId(),
                        CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            }
        }
    }

    private void verifyAccess_isSubsetOfOtherAccess(int subAccess, Set<Integer> superAccess) {
        assertWithMessage(
                        "access mode for property: "
                                + mPropertyName
                                + " must be one of: "
                                + accessSetToString(superAccess))
                .that(subAccess)
                .isIn(superAccess);
    }

    private void verifyCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(mPropertyName + " CarPropertyConfig must have correct property ID")
                .that(carPropertyConfig.getPropertyId())
                .isEqualTo(mPropertyId);
        int carPropConfigAccess = carPropertyConfig.getAccess();
        verifyAccess_isSubsetOfOtherAccess(carPropConfigAccess, mAllowedAccessModes);
        if (AREA_ID_CONFIG_ACCESS_FLAG) {
            for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
                int areaAccess = areaIdConfig.getAccess();
                verifyAccess_isSubsetOfOtherAccess(areaAccess, mAllowedAccessModes);
                if (canRead(carPropConfigAccess)) {
                    assertWithMessage(
                                    "area access mode for property:"
                                            + mPropertyName
                                            + ", areaId: "
                                            + areaIdConfig.getAreaId()
                                            + " must be super-set of global access mode, area"
                                            + " access mode: "
                                            + accessToString(areaAccess)
                                            + ", global access: "
                                            + accessToString(carPropConfigAccess))
                            .that(canRead(areaAccess))
                            .isTrue();
                }
                if (canWrite(carPropConfigAccess)) {
                    assertWithMessage(
                                    "area access mode for property:"
                                            + mPropertyName
                                            + ", areaId: "
                                            + areaIdConfig.getAreaId()
                                            + " must be super-set of global access mode, area"
                                            + " access mode: "
                                            + accessToString(areaAccess)
                                            + ", global access: "
                                            + accessToString(carPropConfigAccess))
                            .that(canWrite(areaAccess))
                            .isTrue();
                }
            }
        }
        assertWithMessage(mPropertyName + " must be " + areaTypeToString(mAreaType))
                .that(carPropertyConfig.getAreaType())
                .isEqualTo(mAreaType);
        assertWithMessage(mPropertyName + " must be " + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getChangeMode())
                .isEqualTo(mChangeMode);
        assertWithMessage(mPropertyName + " must be " + mPropertyType + " type property")
                .that(carPropertyConfig.getPropertyType())
                .isEqualTo(mPropertyType);

        int[] areaIds = carPropertyConfig.getAreaIds();
        assertWithMessage(mPropertyName + "'s must have at least 1 area ID defined")
                .that(areaIds.length)
                .isAtLeast(1);
        assertWithMessage(
                        mPropertyName
                                + "'s area IDs must all be unique: "
                                + Arrays.toString(areaIds))
                .that(
                        ImmutableSet.copyOf(
                                                Arrays.stream(areaIds)
                                                        .boxed()
                                                        .collect(Collectors.toList()))
                                        .size()
                                == areaIds.length)
                .isTrue();

        if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) {
            assertWithMessage(
                            mPropertyName
                                    + "'s AreaIds must contain a single 0 since it is "
                                    + areaTypeToString(mAreaType))
                    .that(areaIds)
                    .isEqualTo(new int[] {0});
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_WHEEL_AREA_IDS);
            verifyNoAreaOverlapInAreaIds();
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_WINDOW_AREA_IDS);
            verifyNoAreaOverlapInAreaIds();
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_MIRROR_AREA_IDS);
            verifyNoAreaOverlapInAreaIds();
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_SEAT
                && mPropertyId != VehiclePropertyIds.INFO_DRIVER_SEAT) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_SEAT_AREA_IDS);
            verifyNoAreaOverlapInAreaIds();
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_DOOR) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_DOOR_AREA_IDS);
            verifyNoAreaOverlapInAreaIds();
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR) {
            verifyNoAreaOverlapInAreaIds();
        }

        if (mAreaIdsVerifier.isPresent()) {
            mAreaIdsVerifier.get().verify(mVerifierContext, areaIds);
        }

        if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            verifyContinuousCarPropertyConfig();
        } else {
            verifyNonContinuousCarPropertyConfig();
        }

        mCarPropertyConfigVerifier.ifPresent(
                carPropertyConfigVerifier ->
                        carPropertyConfigVerifier.verify(mVerifierContext, carPropertyConfig));

        if (!mPossibleConfigArrayValues.isEmpty()) {
            assertWithMessage(mPropertyName + " configArray must specify supported values")
                    .that(carPropertyConfig.getConfigArray().size())
                    .isGreaterThan(0);
            for (Integer supportedValue : carPropertyConfig.getConfigArray()) {
                assertWithMessage(
                                mPropertyName
                                        + " configArray value must be a defined "
                                        + "value: "
                                        + supportedValue)
                        .that(supportedValue)
                        .isIn(mPossibleConfigArrayValues);
            }
        }

        mConfigArrayVerifier.ifPresent(
                configArrayVerifier ->
                        configArrayVerifier.verify(
                                mVerifierContext, carPropertyConfig.getConfigArray()));

        if (mPossibleConfigArrayValues.isEmpty()
                && !mConfigArrayVerifier.isPresent()
                && !mCarPropertyConfigVerifier.isPresent()) {
            assertWithMessage(mPropertyName + " configArray is undefined, so it must be empty")
                    .that(carPropertyConfig.getConfigArray().size())
                    .isEqualTo(0);
        }

        for (int areaId : areaIds) {
            T areaIdMinValue = (T) carPropertyConfig.getMinValue(areaId);
            T areaIdMaxValue = (T) carPropertyConfig.getMaxValue(areaId);
            if (mRequireMinMaxValues) {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " must have min value defined")
                        .that(areaIdMinValue)
                        .isNotNull();
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " must have max value defined")
                        .that(areaIdMaxValue)
                        .isNotNull();

                if (CAR_PROPERTY_SUPPORTED_VALUE_FLAG) {
                    assertWithMessage(
                                    mPropertyName
                                            + " - area ID: "
                                            + areaId
                                            + " config must set hasMaxSupportedValue to true")
                            .that(carPropertyConfig.getAreaIdConfig(areaId).hasMaxSupportedValue())
                            .isTrue();
                    assertWithMessage(
                                    mPropertyName
                                            + " - area ID: "
                                            + areaId
                                            + " config must set hasMinSupportedValue to true")
                            .that(carPropertyConfig.getAreaIdConfig(areaId).hasMinSupportedValue())
                            .isTrue();
                }
            }

            if (mRequireMinValuesToBeZero) {
                assertWithMessage(
                                mPropertyName + " - area ID: " + areaId + " min value must be zero")
                        .that(areaIdMinValue)
                        .isEqualTo(0);
            }
            if (mRequireZeroToBeContainedInMinMaxRanges) {
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s max and min range must contain zero")
                        .that(verifyMaxAndMinRangeContainsZero(areaIdMinValue, areaIdMaxValue))
                        .isTrue();
            }
            if (areaIdMinValue != null || areaIdMaxValue != null) {
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s max value must be >= min value")
                        .that(verifyMaxAndMin(areaIdMinValue, areaIdMaxValue))
                        .isTrue();
            }

            if (mRequirePropertyValueToBeInConfigArray && isAtLeastU()) {
                List<?> supportedEnumValues =
                        carPropertyConfig.getAreaIdConfig(areaId).getSupportedEnumValues();
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s supported enum values must match the values in the"
                                        + " config array.")
                        .that(carPropertyConfig.getConfigArray())
                        .containsExactlyElementsIn(supportedEnumValues);
            }

            if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE
                    && !mAllPossibleEnumValues.isEmpty()
                    && isAtLeastU()) {
                List<?> supportedEnumValues =
                        carPropertyConfig.getAreaIdConfig(areaId).getSupportedEnumValues();
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s supported enum values must be defined")
                        .that(supportedEnumValues)
                        .isNotEmpty();
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s supported enum values must not contain any"
                                        + " duplicates")
                        .that(supportedEnumValues)
                        .containsNoDuplicates();
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s supported enum values "
                                        + supportedEnumValues
                                        + " must all exist in all possible enum set "
                                        + mAllPossibleEnumValues)
                        .that(mAllPossibleEnumValues.containsAll(supportedEnumValues))
                        .isTrue();
                if (CAR_PROPERTY_SUPPORTED_VALUE_FLAG) {
                    assertWithMessage(
                                    mPropertyName
                                            + " - areaId: "
                                            + areaId
                                            + " config must set hasSupportedValuesList to true")
                            .that(
                                    carPropertyConfig
                                            .getAreaIdConfig(areaId)
                                            .hasSupportedValuesList())
                            .isTrue();
                }
            } else if (isAtLeastU()) {
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s supported enum values must be empty since property"
                                        + " does not support an enum")
                        .that(carPropertyConfig.getAreaIdConfig(areaId).getSupportedEnumValues())
                        .isEmpty();
            }
        }
    }

    private boolean verifyMaxAndMinRangeContainsZero(T min, T max) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return (Integer) max >= 0 && (Integer) min <= 0;
            case VehiclePropertyType.INT64:
                return (Long) max >= 0 && (Long) min <= 0;
            case VehiclePropertyType.FLOAT:
                return (Float) max >= 0 && (Float) min <= 0;
            default:
                return false;
        }
    }

    private boolean verifyMaxAndMin(T min, T max) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return (Integer) max >= (Integer) min;
            case VehiclePropertyType.INT64:
                return (Long) max >= (Long) min;
            case VehiclePropertyType.FLOAT:
                return (Float) max >= (Float) min;
            default:
                return false;
        }
    }

    private void verifyContinuousCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " must define max sample rate since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate())
                .isGreaterThan(0);
        assertWithMessage(
                        mPropertyName
                                + " must define min sample rate since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate())
                .isGreaterThan(0);
        assertWithMessage(mPropertyName + " max sample rate must be >= min sample rate")
                .that(carPropertyConfig.getMaxSampleRate() >= carPropertyConfig.getMinSampleRate())
                .isTrue();
    }

    private void verifyNonContinuousCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " must define max sample rate as 0 since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate())
                .isEqualTo(0);
        assertWithMessage(
                        mPropertyName
                                + " must define min sample rate as 0 since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate())
                .isEqualTo(0);
    }

    private void handleGetPropertyExceptions(Exception e) {
        if (e instanceof PropertyNotAvailableException) {
            verifyPropertyNotAvailableException((PropertyNotAvailableException) e);
        } else if (e instanceof CarInternalErrorException) {
            verifyInternalErrorException((CarInternalErrorException) e);
        }
        sExceptionClassOnGet = e.getClass();
    }

    private void handleClassSpecificGetPropertyExceptions(
            Exception e, Class<?> expectedClass, int areaId) {
        if (e instanceof IllegalArgumentException) {
            if (mPropertyType.equals(expectedClass)) {
                assertWithMessage(
                                "getProperty for "
                                        + expectedClass
                                        + " class should not throw"
                                        + " IllegalArgumentException for valid propertyId: "
                                        + mPropertyId
                                        + " areaId: "
                                        + areaId
                                        + " of  type: "
                                        + mPropertyType
                                        + " if property is"
                                        + " readable")
                        .fail();
            }
        } else {
            handleGetPropertyExceptions(e);
        }
    }

    private void verifyClassSpecificGetPropertyResults(
            T value, Class<?> expectedClass, int areaId) {
        if (!mPropertyType.equals(expectedClass)) {
            assertWithMessage(
                            "getProperty for "
                                    + expectedClass
                                    + " class should throw"
                                    + " IllegalArgumentException for valid propertyId: "
                                    + mPropertyId
                                    + " areaId: "
                                    + areaId
                                    + " of"
                                    + " type: "
                                    + mPropertyType
                                    + " if property is readable")
                    .fail();
        }
        verifyCarPropertyValue(
                mPropertyId,
                areaId,
                CarPropertyValue.STATUS_AVAILABLE,
                SystemClock.elapsedRealtimeNanos(),
                value,
                areaId,
                CAR_PROPERTY_VALUE_SOURCE_GETTER);
    }

    private void verifyCarPropertyValueGetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG && !canRead(carPropertyConfig.getAccess())) {
            verifyGetPropertyFails(carPropertyConfig.getAreaIds()[0]);
            return;
        }
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!canRead(carPropertyConfig, areaId)) {
                verifyGetPropertyFails(areaId);
                continue;
            }

            CarPropertyValue<?> carPropertyValue = null;
            try {
                carPropertyValue = mCarPropertyManager.getProperty(mPropertyId, areaId);
                verifyCarPropertyValue(carPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_GETTER);
            } catch (PropertyNotAvailableException | CarInternalErrorException e) {
                handleGetPropertyExceptions(e);
            }

            try {
                Boolean value = mCarPropertyManager.getBooleanProperty(mPropertyId, areaId);
                verifyClassSpecificGetPropertyResults((T) value, Boolean.class, areaId);
            } catch (IllegalArgumentException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                handleClassSpecificGetPropertyExceptions(e, Boolean.class, areaId);
            }
            try {
                Integer value = mCarPropertyManager.getIntProperty(mPropertyId, areaId);
                verifyClassSpecificGetPropertyResults((T) value, Integer.class, areaId);
            } catch (IllegalArgumentException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                handleClassSpecificGetPropertyExceptions(e, Integer.class, areaId);
            }
            try {
                Float value = mCarPropertyManager.getFloatProperty(mPropertyId, areaId);
                verifyClassSpecificGetPropertyResults((T) value, Float.class, areaId);
            } catch (IllegalArgumentException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                handleClassSpecificGetPropertyExceptions(e, Float.class, areaId);
            }
            try {
                int[] primitiveArray = mCarPropertyManager.getIntArrayProperty(mPropertyId, areaId);
                Integer[] value = new Integer[primitiveArray.length];
                for (int i = 0; i < primitiveArray.length; i++) {
                    value[i] = (Integer) primitiveArray[i];
                }
                verifyClassSpecificGetPropertyResults((T) value, Integer[].class, areaId);
            } catch (IllegalArgumentException
                    | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                handleClassSpecificGetPropertyExceptions(e, Integer[].class, areaId);
            }
        }
    }

    private void verifyGetPropertyFails(int areaId) {
        assertGetPropertyThrowsException(
                mPropertyName
                        + " is a write_only property so getProperty should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                mPropertyId,
                areaId);
    }

    private static void verifyPropertyNotAvailableException(PropertyNotAvailableException e) {
        if (!isAtLeastU()) {
            return;
        }
        assertThat(((PropertyNotAvailableException) e).getDetailedErrorCode())
                .isIn(PROPERTY_NOT_AVAILABLE_ERROR_CODES);
        int vendorErrorCode = e.getVendorErrorCode();
        assertThat(vendorErrorCode).isAtLeast(VENDOR_ERROR_CODE_MINIMUM_VALUE);
        assertThat(vendorErrorCode).isAtMost(VENDOR_ERROR_CODE_MAXIMUM_VALUE);
    }

    private static void verifyInternalErrorException(CarInternalErrorException e) {
        if (!isAtLeastU()) {
            return;
        }
        int vendorErrorCode = e.getVendorErrorCode();
        assertThat(vendorErrorCode).isAtLeast(VENDOR_ERROR_CODE_MINIMUM_VALUE);
        assertThat(vendorErrorCode).isAtMost(VENDOR_ERROR_CODE_MAXIMUM_VALUE);
    }

    private void verifyCarPropertyValue(
            CarPropertyValue<?> carPropertyValue, int expectedAreaId, String source) {
        verifyCarPropertyValue(
                carPropertyValue.getPropertyId(),
                carPropertyValue.getAreaId(),
                carPropertyValue.getStatus(),
                carPropertyValue.getTimestamp(),
                (T) carPropertyValue.getValue(),
                expectedAreaId,
                source);
    }

    private void verifyCarPropertyValue(
            int propertyId,
            int areaId,
            int status,
            long timestampNanos,
            T value,
            int expectedAreaId,
            String source) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have correct property ID")
                .that(propertyId)
                .isEqualTo(mPropertyId);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have correct area id: "
                                + areaId)
                .that(areaId)
                .isEqualTo(expectedAreaId);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " area ID must be in carPropertyConfig#getAreaIds()")
                .that(
                        Arrays.stream(carPropertyConfig.getAreaIds())
                                .boxed()
                                .collect(Collectors.toList())
                                .contains(areaId))
                .isTrue();
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have a valid status: "
                                + VALID_CAR_PROPERTY_VALUE_STATUSES)
                .that(VALID_CAR_PROPERTY_VALUE_STATUSES)
                .contains(status);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time"
                                + " base")
                .that(timestampNanos)
                .isAtLeast(0);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time"
                                + " base")
                .that(timestampNanos)
                .isLessThan(SystemClock.elapsedRealtimeNanos());
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " must return "
                                + mPropertyType
                                + " type value")
                .that(value.getClass())
                .isEqualTo(mPropertyType);

        // Only validate value if STATUS_AVAILABLE
        if (status != CarPropertyValue.STATUS_AVAILABLE) {
            return;
        }

        mCarPropertyValueVerifier.ifPresent(
                propertyValueVerifier ->
                        propertyValueVerifier.verify(
                                mVerifierContext,
                                carPropertyConfig,
                                propertyId,
                                areaId,
                                timestampNanos,
                                value));

        if (mRequirePropertyValueToBeInConfigArray) {
            assertWithMessage(
                            mPropertyName
                                    + " - areaId: "
                                    + areaId
                                    + " - source: "
                                    + source
                                    + " value must be listed in configArray,"
                                    + " configArray:")
                    .that(carPropertyConfig.getConfigArray())
                    .contains(value);
        }

        if (isAtLeastU()) {
            List<T> supportedEnumValues =
                    carPropertyConfig.getAreaIdConfig(areaId).getSupportedEnumValues();
            if (!supportedEnumValues.isEmpty()) {
                if (mEnumIsBitMap) {
                    int allValidValues = 0;
                    for (T bitEnumValue : supportedEnumValues) {
                        allValidValues |= ((Integer) bitEnumValue).intValue();
                    }
                    assertWithMessage(
                                    mPropertyName
                                            + " - areaId: "
                                            + areaId
                                            + " - source: "
                                            + source
                                            + " value must be a combination of values listed in "
                                            + "getSupportedEnumValues()")
                            .that(((Integer) value).intValue() & allValidValues)
                            .isEqualTo(value);
                } else {
                    assertWithMessage(
                                    mPropertyName
                                            + " - areaId: "
                                            + areaId
                                            + " - source: "
                                            + source
                                            + " value must be listed in getSupportedEnumValues()")
                            .that(value)
                            .isIn(supportedEnumValues);
                }
            }
        }

        T areaIdMinValue = (T) carPropertyConfig.getMinValue(areaId);
        T areaIdMaxValue = (T) carPropertyConfig.getMaxValue(areaId);
        if (areaIdMinValue != null && areaIdMaxValue != null) {
            assertWithMessage(
                            "Property value: "
                                    + value
                                    + " must be between the max: "
                                    + areaIdMaxValue
                                    + " and min: "
                                    + areaIdMinValue
                                    + " values for area ID: "
                                    + Integer.toHexString(areaId))
                    .that(verifyValueInRange(areaIdMinValue, areaIdMaxValue, (T) value))
                    .isTrue();
        }

        if (mVerifyErrorStates) {
            assertWithMessage(
                            "When ADAS feature is enabled, "
                                    + VehiclePropertyIds.toString(mPropertyId)
                                    + " must not be set to "
                                    + ErrorState.NOT_AVAILABLE_DISABLED
                                    + " (ErrorState#NOT_AVAILABLE_DISABLED")
                    .that((Integer) value)
                    .isNotEqualTo(ErrorState.NOT_AVAILABLE_DISABLED);
        }
    }

    private boolean verifyValueInRange(T min, T max, T value) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return ((Integer) value >= (Integer) min && (Integer) value <= (Integer) max);
            case VehiclePropertyType.INT64:
                return ((Long) value >= (Long) min && (Long) value <= (Long) max);
            case VehiclePropertyType.FLOAT:
                return (((Float) value > (Float) min || valueEquals(value, min))
                        && ((Float) value < (Float) max || valueEquals(value, max)));
            default:
                return false;
        }
    }

    private static ImmutableSet<Integer> generateAllPossibleAreaIds(ImmutableSet<Integer> areas) {
        ImmutableSet.Builder<Integer> allPossibleAreaIdsBuilder = ImmutableSet.builder();
        for (int i = 1; i <= areas.size(); i++) {
            allPossibleAreaIdsBuilder.addAll(
                    Sets.combinations(areas, i).stream()
                            .map(
                                    areaCombo -> {
                                        Integer possibleAreaId = 0;
                                        for (Integer area : areaCombo) {
                                            possibleAreaId |= area;
                                        }
                                        return possibleAreaId;
                                    })
                            .collect(Collectors.toList()));
        }
        return allPossibleAreaIdsBuilder.build();
    }

    private void verifyValidAreaIdsForAreaType(ImmutableSet<Integer> allPossibleAreaIds) {
        for (int areaId : getCarPropertyConfig().getAreaIds()) {
            assertWithMessage(
                            mPropertyName
                                    + "'s area ID must be a valid "
                                    + areaTypeToString(mAreaType)
                                    + " area ID")
                    .that(areaId)
                    .isIn(allPossibleAreaIds);
        }
    }

    private void verifyNoAreaOverlapInAreaIds() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAreaIds().length < 2) {
            return;
        }
        ImmutableSet<Integer> areaIds =
                ImmutableSet.copyOf(
                        Arrays.stream(carPropertyConfig.getAreaIds())
                                .boxed()
                                .collect(Collectors.toList()));
        List<Integer> areaIdOverlapCheckResults =
                Sets.combinations(areaIds, 2).stream()
                        .map(
                                areaIdPair -> {
                                    List<Integer> areaIdPairAsList =
                                            areaIdPair.stream().collect(Collectors.toList());
                                    return areaIdPairAsList.get(0) & areaIdPairAsList.get(1);
                                })
                        .collect(Collectors.toList());

        assertWithMessage(
                        mPropertyName
                                + " area IDs: "
                                + Arrays.toString(carPropertyConfig.getAreaIds())
                                + " must contain each area only once (e.g. no bitwise AND overlap)"
                                + " for the area type: "
                                + areaTypeToString(mAreaType))
                .that(
                        Collections.frequency(areaIdOverlapCheckResults, 0)
                                == areaIdOverlapCheckResults.size())
                .isTrue();
    }

    /** Returns whether all AreaIds for the property is readable. */
    private boolean canReadAllAreaIds(CarPropertyConfig<T> carPropertyConfig) {
        if (!AREA_ID_CONFIG_ACCESS_FLAG) {
            return canRead(carPropertyConfig.getAccess());
        }
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (!canRead(carPropertyConfig, areaId)) {
                return false;
            }
        }
        return true;
    }

    /** Verifies that exceptions are thrown when caller does not have read/write permission. */
    private void verifyPermissionNotGrantedException() {
        // If the client itself already has read/write permissions without adopting any permissions
        // from the shell, skip the test.
        if (hasReadPermissions(mReadPermissions) || hasWritePermissions(mWritePermissions)) {
            return;
        }

        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " CarPropertyConfig should not be accessible without"
                                + " permissions.")
                .that(getCarPropertyConfig(/* useCache= */ false))
                .isNull();

        int access = carPropertyConfig.getAccess();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (AREA_ID_CONFIG_ACCESS_FLAG) {
                access = carPropertyConfig.getAreaIdConfig(areaId).getAccess();
            }

            if (canRead(access)) {
                assertGetPropertyThrowsException(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " - area ID: "
                                + areaId
                                + " should not be able to be read without permissions.",
                        SecurityException.class,
                        mPropertyId,
                        areaId);
                assertThrows(
                        SecurityException.class,
                        () -> {
                            mCarPropertyManager.isPropertyAvailable(mPropertyId, areaId);
                        });
            }
            if (canWrite(access)) {
                assertThrows(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " - area ID: "
                                + areaId
                                + " should not be able to be written to without permissions.",
                        SecurityException.class,
                        () ->
                                mCarPropertyManager.setProperty(
                                        mPropertyType,
                                        mPropertyId,
                                        areaId,
                                        getDefaultValue(mPropertyType)));
            }
        }

        if (!canReadAllAreaIds(carPropertyConfig)) {
            return;
        }

        // We expect a return value of false and not a SecurityException thrown.
        // This is because registerCallback first tries to get the CarPropertyConfig for the
        // property, but since no permissions have been granted it can't find the CarPropertyConfig,
        // so it immediately returns false.
        assertWithMessage(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " should not be able to be listened to without permissions.")
                .that(mCarPropertyManager.registerCallback(FAKE_CALLBACK, mPropertyId, 0f))
                .isFalse();

        if (isAtLeastV() && Flags.variableUpdateRate()) {
            // For the new API, if the caller does not have read and write permission, it throws
            // SecurityException.
            assertThrows(
                    mPropertyName
                            + " - property ID: "
                            + mPropertyId
                            + " should not be able to be listened to without permissions.",
                    SecurityException.class,
                    () -> mCarPropertyManager.subscribePropertyEvents(mPropertyId, FAKE_CALLBACK));
        }
    }

    private static boolean isAreaIdSupportedInList(int areaIdToFind, Collection<Integer> areaIds) {
        for (Integer areaId : areaIds) {
            if ((areaIdToFind & areaId) == areaIdToFind) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Integer> getAreaIdAccess(
            CarPropertyConfig<?> carPropertyConfig, int areaId) {
        return AREA_ID_CONFIG_ACCESS_FLAG
                ? Optional.of(carPropertyConfig.getAreaIdConfig(areaId).getAccess())
                : Optional.empty();
    }

    private static Integer getAreaIdAccessOrElseGlobalAccess(
            CarPropertyConfig<?> carPropertyConfig, int areaId) {
        return getAreaIdAccess(carPropertyConfig, areaId).orElse(carPropertyConfig.getAccess());
    }

    private static boolean canRead(int accessMode) {
        return accessMode == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                || accessMode == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE;
    }

    private static boolean canWrite(int accessMode) {
        return accessMode == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                || accessMode == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE;
    }

    private static boolean canRead(CarPropertyConfig<?> carPropertyConfig, int areaId) {
        return doesAreaIdAccessMatch(
                        carPropertyConfig, areaId, CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                || doesAreaIdAccessMatch(
                        carPropertyConfig,
                        areaId,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
    }

    private static boolean canWrite(CarPropertyConfig<?> carPropertyConfig, int areaId) {
        return doesAreaIdAccessMatch(
                        carPropertyConfig, areaId, CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE)
                || doesAreaIdAccessMatch(
                        carPropertyConfig,
                        areaId,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
    }

    private static boolean doesAreaIdAccessMatch(
            CarPropertyConfig<?> carPropertyConfig, int areaId, int expectedAccess) {
        return getAreaIdAccess(carPropertyConfig, areaId)
                .filter(areaIdAccess -> areaIdAccess == expectedAccess)
                .isPresent();
    }

    /** Verifies that hvac temperature is valid. */
    public static void verifyHvacTemperatureIsValid(
            float temp, int minTempTimesTen, int maxTempTimesTen, int incrementTimesTen) {
        int intTempTimesTen = (int) (temp * 10f);
        assertWithMessage(
                        "The temperature value "
                                + intTempTimesTen
                                + " must be at least "
                                + minTempTimesTen
                                + " and at most "
                                + maxTempTimesTen)
                .that(intTempTimesTen >= minTempTimesTen && intTempTimesTen <= maxTempTimesTen)
                .isTrue();

        int remainder = (intTempTimesTen - minTempTimesTen) % incrementTimesTen;
        assertWithMessage(
                        "The temperature value "
                                + intTempTimesTen
                                + " is not a valid temperature value. Valid values start from "
                                + minTempTimesTen
                                + " and increment by "
                                + incrementTimesTen
                                + " until the max temperature setting of "
                                + maxTempTimesTen)
                .that(remainder)
                .isEqualTo(0);
    }

    /**
     * A structure containing verifier context.
     *
     * <p>This contains VehiclePropertyVerifier members that might be useful for the verification.
     */
    public static class VerifierContext {
        private final CarPropertyManager mCarPropertyManager;

        public VerifierContext(CarPropertyManager carPropertyManager) {
            mCarPropertyManager = carPropertyManager;
        }

        public CarPropertyManager getCarPropertyManager() {
            return mCarPropertyManager;
        }
    }

    /** An interface for verifying the config array. */
    public interface ConfigArrayVerifier {
        /** Verifies the config array. Throws exception if not valid. */
        void verify(VerifierContext verifierContext, List<Integer> configArray);
    }

    /** An interface for verifying the property value. */
    public interface CarPropertyValueVerifier<T> {
        /** Verifies the property value. Throws exception if not valid. */
        void verify(
                VerifierContext verifierContext,
                CarPropertyConfig<T> carPropertyConfig,
                int propertyId,
                int areaId,
                long timestampNanos,
                T value);
    }

    /** An interface for verifying the areaIds. */
    public interface AreaIdsVerifier {
        /** Verifies the areaIds. Throws exception if not valid. */
        void verify(VerifierContext verifierContext, int[] areaIds);
    }

    /** An interface for verifying the {@link CarPropertyConfig}. */
    public interface CarPropertyConfigVerifier {
        /** Verifies the property config. Throws exception if not valid. */
        void verify(VerifierContext verifierContext, CarPropertyConfig<?> carPropertyConfig);
    }

    /** The builder class. */
    public static class Builder<T> {
        private final int mPropertyId;
        private final ImmutableSet<Integer> mAllowedAccessModes;
        private final int mAreaType;
        private final int mChangeMode;
        private final Class<T> mPropertyType;
        private CarPropertyManager mCarPropertyManager;
        private boolean mRequiredProperty = false;
        private Optional<ConfigArrayVerifier> mConfigArrayVerifier = Optional.empty();
        private Optional<CarPropertyValueVerifier<T>> mCarPropertyValueVerifier = Optional.empty();
        private Optional<AreaIdsVerifier> mAreaIdsVerifier = Optional.empty();
        private Optional<CarPropertyConfigVerifier> mCarPropertyConfigVerifier = Optional.empty();
        private Optional<Integer> mDependentOnPropertyId = Optional.empty();
        private ImmutableSet<String> mDependentOnPropertyPermissions = ImmutableSet.of();
        private ImmutableSet<Integer> mPossibleConfigArrayValues = ImmutableSet.of();
        private boolean mEnumIsBitMap = false;
        private ImmutableSet<T> mAllPossibleEnumValues = ImmutableSet.of();
        private ImmutableSet<T> mAllPossibleUnwritableValues = ImmutableSet.of();
        private ImmutableSet<T> mAllPossibleUnavailableValues = ImmutableSet.of();
        private boolean mRequirePropertyValueToBeInConfigArray = false;
        private boolean mVerifySetterWithConfigArrayValues = false;
        private boolean mRequireMinMaxValues = false;
        private boolean mRequireMinValuesToBeZero = false;
        private boolean mRequireZeroToBeContainedInMinMaxRanges = false;
        private boolean mPossiblyDependentOnHvacPowerOn = false;
        private boolean mVerifyErrorStates = false;
        private int mPowerPropagationDelayMs = 2000;
        private final ImmutableSet.Builder<String> mReadPermissionsBuilder = ImmutableSet.builder();
        private final ImmutableList.Builder<ImmutableSet<String>> mWritePermissionsBuilder =
                ImmutableList.builder();

        private Builder(
                int propertyId,
                ImmutableSet<Integer> allowedAccessModes,
                int areaType,
                int changeMode,
                Class<T> propertyType) {
            mPropertyId = propertyId;
            mAllowedAccessModes = allowedAccessModes;
            mAreaType = areaType;
            mChangeMode = changeMode;
            mPropertyType = propertyType;
        }

        public int getPropertyId() {
            return mPropertyId;
        }

        public boolean isRequired() {
            return mRequiredProperty;
        }

        /** Sets the car property manager. */
        public Builder<T> setCarPropertyManager(CarPropertyManager carPropertyManager) {
            mCarPropertyManager = carPropertyManager;
            return this;
        }

        /** Sets the property as required. Test will fail if the property is not supported. */
        public Builder<T> requireProperty() {
            mRequiredProperty = true;
            return this;
        }

        /**
         * Sets the property as required if condition is true. If condition is true, test will fail
         * if the property is not supported.
         */
        public Builder<T> requireProperty(boolean condition) {
            mRequiredProperty = condition;
            return this;
        }

        /** Sets the config array verifier. */
        public Builder<T> setConfigArrayVerifier(ConfigArrayVerifier configArrayVerifier) {
            mConfigArrayVerifier = Optional.of(configArrayVerifier);
            return this;
        }

        /** Sets the car property value verifier. */
        public Builder<T> setCarPropertyValueVerifier(
                CarPropertyValueVerifier<T> carPropertyValueVerifier) {
            mCarPropertyValueVerifier = Optional.of(carPropertyValueVerifier);
            return this;
        }

        /** Sets the areaIds verifier. */
        public Builder<T> setAreaIdsVerifier(AreaIdsVerifier areaIdsVerifier) {
            mAreaIdsVerifier = Optional.of(areaIdsVerifier);
            return this;
        }

        /** Sets the car property config verifier. */
        public Builder<T> setCarPropertyConfigVerifier(
                CarPropertyConfigVerifier carPropertyConfigVerifier) {
            mCarPropertyConfigVerifier = Optional.of(carPropertyConfigVerifier);
            return this;
        }

        /** Sets that the property is depending on other properties. */
        public Builder<T> setDependentOnProperty(
                Integer dependentPropertyId, ImmutableSet<String> dependentPropertyPermissions) {
            mDependentOnPropertyId = Optional.of(dependentPropertyId);
            mDependentOnPropertyPermissions = dependentPropertyPermissions;
            return this;
        }

        /** Sets the possible config array values. */
        public Builder<T> setPossibleConfigArrayValues(
                ImmutableSet<Integer> possibleConfigArrayValues) {
            mPossibleConfigArrayValues = possibleConfigArrayValues;
            return this;
        }

        /**
         * Used to assert that supportedEnum values provided in config are a subset of all possible
         * enum values that can be set for the property.
         */
        public Builder<T> setBitMapEnumEnabled(boolean enabled) {
            mEnumIsBitMap = enabled;
            return this;
        }

        /*
         * Used to assert that supportedEnum values provided in config are a subset of all possible
         * enum values that can be set for the property. If enums is defined as a bit map rather
         * than a regular integer, setBitMapEnumEnabled(boolean) should be used as well.
         */
        public Builder<T> setAllPossibleEnumValues(ImmutableSet<T> allPossibleEnumValues) {
            mAllPossibleEnumValues = allPossibleEnumValues;
            return this;
        }

        /**
         * Used to assert that certain values that must not be allowed to be written will throw an
         * IllegalArgumentException when we try to write them using setProperty.
         */
        public Builder<T> setAllPossibleUnwritableValues(
                ImmutableSet<T> allPossibleUnwritableValues) {
            mAllPossibleUnwritableValues = allPossibleUnwritableValues;
            return this;
        }

        /**
         * Used to assert that certain values that are temporarily unavailable to be written will
         * throw a PropertyNotAvailableException when we try to write them using setProperty.
         */
        public Builder<T> setAllPossibleUnavailableValues(
                ImmutableSet<T> allPossibleUnavailableValues) {
            mAllPossibleUnavailableValues = allPossibleUnavailableValues;
            return this;
        }

        /**
         * Requires that the property value must be one of the value defined in the config array.
         */
        public Builder<T> requirePropertyValueTobeInConfigArray() {
            mRequirePropertyValueToBeInConfigArray = true;
            return this;
        }

        /** Uses the config array values to set the property value. */
        public Builder<T> verifySetterWithConfigArrayValues() {
            mVerifySetterWithConfigArrayValues = true;
            return this;
        }

        /** Requires minValue and maxValue to be set. */
        public Builder<T> requireMinMaxValues() {
            mRequireMinMaxValues = true;
            return this;
        }

        /** Requires minValue to be 0. */
        public Builder<T> requireMinValuesToBeZero() {
            mRequireMinValuesToBeZero = true;
            return this;
        }

        /** Requires 0 to be contains within minValue and maxValue. */
        public Builder<T> requireZeroToBeContainedInMinMaxRanges() {
            mRequireZeroToBeContainedInMinMaxRanges = true;
            return this;
        }

        /** Sets that the property might depend on HVAC_POWER_ON. */
        public Builder<T> setPossiblyDependentOnHvacPowerOn() {
            mPossiblyDependentOnHvacPowerOn = true;
            return this;
        }

        /** Verifies if returning error state, the error state is expected. */
        public Builder<T> verifyErrorStates() {
            mVerifyErrorStates = true;
            return this;
        }

        /** Sets the delay to wait for the power state to propagate to dependent properties. */
        public Builder<T> setPowerPropagationDelayMs(int delayMs) {
            mPowerPropagationDelayMs = delayMs;
            return this;
        }

        /** Adds the required read permission. */
        public Builder<T> addReadPermission(String readPermission) {
            mReadPermissionsBuilder.add(readPermission);
            return this;
        }

        /**
         * Adds a single permission that alone can be used to update the property. Any set of
         * permissions in {@code mWritePermissionsBuilder} can be used to set the property.
         *
         * @param writePermission a permission used to update the property
         */
        public Builder<T> addWritePermission(String writePermission) {
            mWritePermissionsBuilder.add(ImmutableSet.of(writePermission));
            return this;
        }

        /**
         * Adds a set of permissions that together can be used to update the property. Any set of
         * permissions in {@code mWritePermissionsBuilder} can be used to set the property.
         *
         * @param writePermissionSet a set of permissions that together can be used to update the
         *     property.
         */
        public Builder<T> addWritePermission(ImmutableSet<String> writePermissionSet) {
            mWritePermissionsBuilder.add(writePermissionSet);
            return this;
        }

        /** Builds the verifier. */
        public VehiclePropertyVerifier<T> build() {
            return new VehiclePropertyVerifier<>(
                    mCarPropertyManager,
                    mPropertyId,
                    mAllowedAccessModes,
                    mAreaType,
                    mChangeMode,
                    mPropertyType,
                    mRequiredProperty,
                    mConfigArrayVerifier,
                    mCarPropertyValueVerifier,
                    mAreaIdsVerifier,
                    mCarPropertyConfigVerifier,
                    mDependentOnPropertyId,
                    mDependentOnPropertyPermissions,
                    mPossibleConfigArrayValues,
                    mEnumIsBitMap,
                    mAllPossibleEnumValues,
                    mAllPossibleUnwritableValues,
                    mAllPossibleUnavailableValues,
                    mRequirePropertyValueToBeInConfigArray,
                    mVerifySetterWithConfigArrayValues,
                    mRequireMinMaxValues,
                    mRequireMinValuesToBeZero,
                    mRequireZeroToBeContainedInMinMaxRanges,
                    mPossiblyDependentOnHvacPowerOn,
                    mVerifyErrorStates,
                    mPowerPropagationDelayMs,
                    mReadPermissionsBuilder.build(),
                    mWritePermissionsBuilder.build());
        }
    }

    private static class CarPropertyValueCallback
            implements CarPropertyManager.CarPropertyEventCallback {
        private final String mPropertyName;
        private final int[] mAreaIds;
        private final int mTotalCarPropertyValuesPerAreaId;
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final SparseArray<List<CarPropertyValue<?>>> mAreaIdToCarPropertyValues =
                new SparseArray<>();

        private final long mTimeoutMillis;

        CarPropertyValueCallback(
                String propertyName,
                int[] areaIds,
                int totalCarPropertyValuesPerAreaId,
                long timeoutMillis) {
            mPropertyName = propertyName;
            mAreaIds = areaIds;
            mTotalCarPropertyValuesPerAreaId = totalCarPropertyValuesPerAreaId;
            mTimeoutMillis = timeoutMillis;
            synchronized (mLock) {
                for (int areaId : mAreaIds) {
                    mAreaIdToCarPropertyValues.put(areaId, new ArrayList<>());
                }
            }
        }

        public SparseArray<List<CarPropertyValue<?>>> getAreaIdToCarPropertyValues() {
            boolean awaitSuccess = false;
            try {
                awaitSuccess = mCountDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                assertWithMessage(
                                "Waiting for onChangeEvent callback(s) for "
                                        + mPropertyName
                                        + " threw an exception: "
                                        + e)
                        .fail();
            }
            synchronized (mLock) {
                assertWithMessage(
                                "Never received "
                                        + mTotalCarPropertyValuesPerAreaId
                                        + "  CarPropertyValues for all "
                                        + mPropertyName
                                        + "'s areaIds: "
                                        + Arrays.toString(mAreaIds)
                                        + " before "
                                        + mTimeoutMillis
                                        + " ms timeout - "
                                        + mAreaIdToCarPropertyValues)
                        .that(awaitSuccess)
                        .isTrue();
                return mAreaIdToCarPropertyValues.clone();
            }
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            synchronized (mLock) {
                if (hasEnoughCarPropertyValuesForEachAreaIdLocked()) {
                    return;
                }
                mAreaIdToCarPropertyValues.get(carPropertyValue.getAreaId()).add(carPropertyValue);
                if (hasEnoughCarPropertyValuesForEachAreaIdLocked()) {
                    mCountDownLatch.countDown();
                }
            }
        }

        @GuardedBy("mLock")
        private boolean hasEnoughCarPropertyValuesForEachAreaIdLocked() {
            for (int areaId : mAreaIds) {
                List<CarPropertyValue<?>> carPropertyValues =
                        mAreaIdToCarPropertyValues.get(areaId);
                if (carPropertyValues == null
                        || carPropertyValues.size() < mTotalCarPropertyValuesPerAreaId) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void onErrorEvent(int propId, int zone) {}

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {}
    }

    private static class SetterCallback<T> implements CarPropertyManager.CarPropertyEventCallback {
        private final int mPropertyId;
        private final String mPropertyName;
        private final int mAreaId;
        private final T mExpectedSetValue;
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private final long mCreationTimeNanos = SystemClock.elapsedRealtimeNanos();
        private CarPropertyValue<?> mUpdatedCarPropertyValue = null;
        private T mReceivedValue = null;
        private Integer mSetErrorCode = null;

        SetterCallback(int propertyId, int areaId, T expectedSetValue) {
            mPropertyId = propertyId;
            mPropertyName = VehiclePropertyIds.toString(propertyId);
            mAreaId = areaId;
            mExpectedSetValue = expectedSetValue;
        }

        private String valueToString(T value) {
            if (value.getClass().isArray()) {
                return Arrays.toString((Object[]) value);
            }
            return value.toString();
        }

        /**
         * Waits at most {@code timeoutInSec} for a property event that is the result of a {@code
         * setProperty} request.
         *
         * <p>If {@link #onChangeEvent(CarPropertyValue)} is called, then this method will return
         * the {@link CarPropertyValue} if:
         *
         * <ul>
         *   <li>The property ID and area ID match {@link #mPropertyId} and {@link #mAreaId}
         *   <li>The event is timestamped after {@link #mCreationTimeNanos} but before {@link
         *       SystemClock#elapsedRealtimeNanos()}
         *   <li>One of the following is true:
         *       <ul>
         *         <li>{@link CarPropertyValue#getStatus()} is NOT {@link
         *             CarPropertyValue#STATUS_AVAILABLE}
         *         <li>{@link CarPropertyValue#getStatus()} is {@link
         *             CarPropertyValue#STATUS_AVAILABLE} and {@link CarPropertyValue#getValue()}
         *             equals {@link #mExpectedSetValue}.
         *       </ul>
         * </ul>
         *
         * <p>If {@link #onErrorEvent(int, int)} is called, then this method will return {@code
         * null} if:
         *
         * <ul>
         *   <li>The property ID and area ID match {@link #mPropertyId} and {@link #mAreaId}
         * </ul>
         *
         * <p>If {@code timeoutInSec} is reached before any of the above conditions are met or if
         * {@link InterruptedException} is thrown, then this method will throw an {@code
         * AssertionError} and fail the test.
         *
         * <p>If the callback receives a valid CarPropertyValue and a valid set property error code,
         * then an {@code AssertionError} will be thrown and the test will fail.
         *
         * @param timeoutInSec maximum time in seconds to wait for an expected event
         * @return a valid {@link CarPropertyValue} if all {@link #onChangeEvent(CarPropertyValue)}
         *     conditions are met, or {@code null} if all {@link #onErrorEvent(int, int)} conditions
         *     are met.
         */
        public CarPropertyValue<?> waitForPropertyEvent(int timeoutInSec) {
            try {
                assertWithMessage(
                                "Never received onChangeEvent(s) or onErrorEvent(s) for "
                                        + mPropertyName
                                        + " new value: "
                                        + valueToString(mExpectedSetValue)
                                        + " before"
                                        + " timeout. Received: "
                                        + (mReceivedValue == null
                                                ? "No value"
                                                : valueToString(mReceivedValue)))
                        .that(mCountDownLatch.await(timeoutInSec, TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                assertWithMessage(
                                "Waiting for onChangeEvent set callback for "
                                        + mPropertyName
                                        + " threw an exception: "
                                        + e)
                        .fail();
            }
            assertWithMessage(
                            "Received both a set error code"
                                    + mSetErrorCode
                                    + " and a new value: "
                                    + mUpdatedCarPropertyValue
                                    + " for propertyId: "
                                    + mPropertyName
                                    + " areaId: "
                                    + mAreaId)
                    .that(mUpdatedCarPropertyValue != null && mSetErrorCode != null)
                    .isFalse();
            return mUpdatedCarPropertyValue;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            // Checking whether the updated carPropertyValue is caused by the setProperty request.
            if (mUpdatedCarPropertyValue != null
                    || carPropertyValue.getPropertyId() != mPropertyId
                    || carPropertyValue.getAreaId() != mAreaId
                    || carPropertyValue.getTimestamp() <= mCreationTimeNanos
                    || carPropertyValue.getTimestamp() >= SystemClock.elapsedRealtimeNanos()) {
                return;
            }
            mReceivedValue = (T) carPropertyValue.getValue();
            if (carPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                    && !valueEquals(mExpectedSetValue, mReceivedValue)) {
                return;
            }
            mUpdatedCarPropertyValue = carPropertyValue;
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int areaId) {
            onErrorEvent(propId, areaId, CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        }

        @Override
        public void onErrorEvent(int propertyId, int areaId, int errorCode) {
            if (mCountDownLatch.getCount() == 0) {
                Log.w(
                        TAG,
                        "SetterCallback - Dropping onErrorEvent. Received an onErrorEvent call "
                                + " after the CountDownLatch finished - "
                                + "propertyId: "
                                + VehiclePropertyIds.toString(propertyId)
                                + " areaId: "
                                + areaId
                                + " errorCode: "
                                + errorCode);
                return;
            }
            if (propertyId != mPropertyId) {
                Log.w(
                        TAG,
                        "SetterCallback - Dropping onErrorEvent. Property ID does not match"
                                + " expected property ID: "
                                + mPropertyName
                                + " - propertyId: "
                                + VehiclePropertyIds.toString(propertyId)
                                + " areaId: "
                                + areaId
                                + " errorCode: "
                                + errorCode);
                return;
            }
            if (areaId != mAreaId) {
                Log.w(
                        TAG,
                        "SetterCallback - Dropping onErrorEvent. Area ID does not match expected"
                                + " area ID: "
                                + mAreaId
                                + " - propertyId: "
                                + mPropertyName
                                + " areaId: "
                                + areaId
                                + " errorCode: "
                                + errorCode);
                return;
            }
            if (!VALID_SET_ERROR_CODES.contains(errorCode)) {
                Log.w(
                        TAG,
                        "SetterCallback - Dropping onErrorEvent. errorCode is not a valid error"
                                + " code: "
                                + VALID_SET_ERROR_CODES
                                + " - propertyId: "
                                + mPropertyName
                                + " areaId: "
                                + areaId
                                + " errorCode: "
                                + errorCode);
                return;
            }
            if (mSetErrorCode != null) {
                Log.w(
                        TAG,
                        "SetterCallback - Dropping onErrorEvent. Already received a valid"
                                + " errorCode: "
                                + mSetErrorCode
                                + " - propertyId: "
                                + mPropertyName
                                + " areaId: "
                                + areaId
                                + " errorCode: "
                                + errorCode);
                return;
            }
            Log.w(
                    TAG,
                    "SetterCallback - Received setProperty error code: "
                            + errorCode
                            + " - propertyId: "
                            + mPropertyName
                            + " - areaId: "
                            + areaId);
            mSetErrorCode = Integer.valueOf(errorCode);
            mCountDownLatch.countDown();
        }
    }

    private static <V> boolean valueEquals(V v1, V v2) {
        return (v1 instanceof Float && floatEquals((Float) v1, (Float) v2))
                || (v1 instanceof Float[] && floatArrayEquals((Float[]) v1, (Float[]) v2))
                || (v1 instanceof Long[] && longArrayEquals((Long[]) v1, (Long[]) v2))
                || (v1 instanceof Integer[] && integerArrayEquals((Integer[]) v1, (Integer[]) v2))
                || v1.equals(v2);
    }

    private static boolean floatEquals(float f1, float f2) {
        return Math.abs(f1 - f2) < FLOAT_INEQUALITY_THRESHOLD;
    }

    private static boolean floatArrayEquals(Float[] f1, Float[] f2) {
        return Arrays.equals(f1, f2);
    }

    private static boolean longArrayEquals(Long[] l1, Long[] l2) {
        return Arrays.equals(l1, l2);
    }

    private static boolean integerArrayEquals(Integer[] i1, Integer[] i2) {
        return Arrays.equals(i1, i2);
    }

    private static class TestGetPropertyCallback implements GetPropertyCallback {
        private final CountDownLatch mCountDownLatch;
        private final int mGetPropertyResultsCount;
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final List<GetPropertyResult<?>> mGetPropertyResults = new ArrayList<>();

        @GuardedBy("mLock")
        private final List<PropertyAsyncError> mPropertyAsyncErrors = new ArrayList<>();

        public void waitForResults() {
            try {
                assertWithMessage(
                                "Received "
                                        + (mGetPropertyResultsCount - mCountDownLatch.getCount())
                                        + " onSuccess(s), expected "
                                        + mGetPropertyResultsCount
                                        + " onSuccess(s)")
                        .that(mCountDownLatch.await(5, TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onSuccess threw an exception: " + e).fail();
            }
        }

        public List<GetPropertyResult<?>> getGetPropertyResults() {
            synchronized (mLock) {
                return mGetPropertyResults;
            }
        }

        public List<PropertyAsyncError> getPropertyAsyncErrors() {
            synchronized (mLock) {
                return mPropertyAsyncErrors;
            }
        }

        @Override
        public void onSuccess(GetPropertyResult getPropertyResult) {
            synchronized (mLock) {
                mGetPropertyResults.add(getPropertyResult);
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onFailure(PropertyAsyncError propertyAsyncError) {
            synchronized (mLock) {
                mPropertyAsyncErrors.add(propertyAsyncError);
                mCountDownLatch.countDown();
            }
        }

        TestGetPropertyCallback(int getPropertyResultsCount) {
            mCountDownLatch = new CountDownLatch(getPropertyResultsCount);
            mGetPropertyResultsCount = getPropertyResultsCount;
        }
    }

    private static class TestSetPropertyCallback implements SetPropertyCallback {
        private final CountDownLatch mCountDownLatch;
        private final int mSetPropertyResultsCount;
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final List<SetPropertyResult> mSetPropertyResults = new ArrayList<>();

        @GuardedBy("mLock")
        private final List<PropertyAsyncError> mPropertyAsyncErrors = new ArrayList<>();

        public void waitForResults() {
            try {
                assertWithMessage(
                                "Received "
                                        + (mSetPropertyResultsCount - mCountDownLatch.getCount())
                                        + " onSuccess(s), expected "
                                        + mSetPropertyResultsCount
                                        + " onSuccess(s)")
                        .that(mCountDownLatch.await(5, TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onSuccess threw an exception: " + e).fail();
            }
        }

        public List<SetPropertyResult> getSetPropertyResults() {
            synchronized (mLock) {
                return mSetPropertyResults;
            }
        }

        public List<PropertyAsyncError> getPropertyAsyncErrors() {
            synchronized (mLock) {
                return mPropertyAsyncErrors;
            }
        }

        @Override
        public void onSuccess(SetPropertyResult setPropertyResult) {
            synchronized (mLock) {
                mSetPropertyResults.add(setPropertyResult);
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onFailure(PropertyAsyncError propertyAsyncError) {
            synchronized (mLock) {
                mPropertyAsyncErrors.add(propertyAsyncError);
                mCountDownLatch.countDown();
            }
        }

        TestSetPropertyCallback(int setPropertyResultsCount) {
            mCountDownLatch = new CountDownLatch(setPropertyResultsCount);
            mSetPropertyResultsCount = setPropertyResultsCount;
        }
    }

    private void verifyGetPropertiesAsync() {
        if (!isAtLeastU()) {
            return;
        }
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG && !canRead(carPropertyConfig.getAccess())) {
            verifyGetPropertiesAsyncFails(carPropertyConfig.getAreaIds()[0]);
            return;
        }

        List<GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        SparseIntArray requestIdToAreaIdMap = new SparseIntArray();
        for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
            int areaId = areaIdConfig.getAreaId();
            if (!canRead(carPropertyConfig, areaId)) {
                verifyGetPropertiesAsyncFails(areaId);
                continue;
            }
            GetPropertyRequest getPropertyRequest =
                    mCarPropertyManager.generateGetPropertyRequest(mPropertyId, areaId);
            int requestId = getPropertyRequest.getRequestId();
            requestIdToAreaIdMap.put(requestId, areaId);
            getPropertyRequests.add(getPropertyRequest);
        }

        TestGetPropertyCallback testGetPropertyCallback =
                new TestGetPropertyCallback(requestIdToAreaIdMap.size());
        mCarPropertyManager.getPropertiesAsync(
                getPropertyRequests, /* cancellationSignal: */
                null,
                /* callbackExecutor: */ null,
                testGetPropertyCallback);
        testGetPropertyCallback.waitForResults();

        for (GetPropertyResult<?> getPropertyResult :
                testGetPropertyCallback.getGetPropertyResults()) {
            int requestId = getPropertyResult.getRequestId();
            int propertyId = getPropertyResult.getPropertyId();
            if (requestIdToAreaIdMap.indexOfKey(requestId) < 0) {
                assertWithMessage(
                                "getPropertiesAsync received GetPropertyResult with unknown"
                                        + " requestId: "
                                        + getPropertyResult)
                        .fail();
            }
            Integer expectedAreaId = requestIdToAreaIdMap.get(requestId);
            verifyCarPropertyValue(
                    propertyId,
                    getPropertyResult.getAreaId(),
                    CarPropertyValue.STATUS_AVAILABLE,
                    getPropertyResult.getTimestampNanos(),
                    (T) getPropertyResult.getValue(),
                    expectedAreaId,
                    CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
        }

        for (PropertyAsyncError propertyAsyncError :
                testGetPropertyCallback.getPropertyAsyncErrors()) {
            int requestId = propertyAsyncError.getRequestId();
            // Async errors are ok as long the requestId is valid
            if (requestIdToAreaIdMap.indexOfKey(requestId) < 0) {
                assertWithMessage(
                                "getPropertiesAsync received PropertyAsyncError with unknown"
                                        + " requestId: "
                                        + propertyAsyncError)
                        .fail();
            }
        }
    }

    private void verifyGetPropertiesAsyncFails(int areaId) {
        if (!isAtLeastU()) {
            return;
        }
        List<GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        GetPropertyRequest getPropertyRequest =
                mCarPropertyManager.generateGetPropertyRequest(mPropertyId, areaId);
        getPropertyRequests.add(getPropertyRequest);
        TestGetPropertyCallback testGetPropertyCallback =
                new TestGetPropertyCallback(/* getPropertyResultsCount: */ 1);
        assertThrows(
                mPropertyName
                        + " is a write_only property so getPropertiesAsync should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () ->
                        mCarPropertyManager.getPropertiesAsync(
                                getPropertyRequests,
                                /* cancellationSignal: */ null, /* callbackExecutor: */
                                null,
                                testGetPropertyCallback));
    }

    private void verifySetPropertiesAsync() {
        if (!isAtLeastU()) {
            return;
        }
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (!AREA_ID_CONFIG_ACCESS_FLAG && !canWrite(carPropertyConfig.getAccess())) {
            verifySetPropertiesAsyncFails(carPropertyConfig.getAreaIds()[0]);
            return;
        }

        ArrayMap<Integer, List<T>> areaIdToPossibleValuesMap = new ArrayMap<>();
        // The maximum possible values count for all areaIds.
        int maxPossibleValuesCount = 0;

        for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
            int areaId = areaIdConfig.getAreaId();
            Collection<T> possibleValues = getPossibleValues(areaId);
            if (possibleValues == null || possibleValues.size() == 0) {
                continue;
            }
            // Convert to a list so that we can access via index later.
            areaIdToPossibleValuesMap.put(areaId, new ArrayList<T>(possibleValues));
            if (possibleValues.size() > maxPossibleValuesCount) {
                maxPossibleValuesCount = possibleValues.size();
            }
        }

        // For each possible value index, generate one async request containing all areaIds that has
        // possible values defined.
        // For example, [value0ForArea1, value0ForArea2], [value1ForArea1, value1ForArea2].
        // If we run out of possible values for one areaId, just use the last possible value for
        // that areaId.
        for (int i = 0; i < maxPossibleValuesCount; i++) {
            SparseIntArray requestIdToAreaIdMap = new SparseIntArray();
            List<SetPropertyRequest<?>> setPropertyRequests = new ArrayList<>();
            for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
                int areaId = areaIdConfig.getAreaId();
                if (!canWrite(carPropertyConfig, areaId)) {
                    verifySetPropertiesAsyncFails(areaId);
                    continue;
                }
                if (!areaIdToPossibleValuesMap.containsKey(areaId)) {
                    continue;
                }
                List<T> possibleValues = areaIdToPossibleValuesMap.get(areaId);
                // Always use the last possible value if we run out of possible values.
                int index = Math.min(i, possibleValues.size() - 1);
                SetPropertyRequest setPropertyRequest =
                        mCarPropertyManager.generateSetPropertyRequest(
                                mPropertyId, areaId, possibleValues.get(index));
                if (!canRead(carPropertyConfig, areaId)) {
                    setPropertyRequest.setWaitForPropertyUpdate(false);
                }
                requestIdToAreaIdMap.put(setPropertyRequest.getRequestId(), areaId);
                setPropertyRequests.add(setPropertyRequest);
            }

            TestSetPropertyCallback testSetPropertyCallback =
                    new TestSetPropertyCallback(requestIdToAreaIdMap.size());
            mCarPropertyManager.setPropertiesAsync(
                    setPropertyRequests,
                    /* cancellationSignal: */ null, /* callbackExecutor: */
                    null,
                    testSetPropertyCallback);
            testSetPropertyCallback.waitForResults();

            for (SetPropertyResult setPropertyResult :
                    testSetPropertyCallback.getSetPropertyResults()) {
                int requestId = setPropertyResult.getRequestId();
                if (requestIdToAreaIdMap.indexOfKey(requestId) < 0) {
                    assertWithMessage(
                                    "setPropertiesAsync received SetPropertyResult with unknown"
                                            + " requestId: "
                                            + setPropertyResult)
                            .fail();
                }
                assertThat(setPropertyResult.getPropertyId()).isEqualTo(mPropertyId);
                assertThat(setPropertyResult.getAreaId())
                        .isEqualTo(requestIdToAreaIdMap.get(requestId));
                assertThat(setPropertyResult.getUpdateTimestampNanos()).isAtLeast(0);
                assertThat(setPropertyResult.getUpdateTimestampNanos())
                        .isLessThan(SystemClock.elapsedRealtimeNanos());
            }

            for (PropertyAsyncError propertyAsyncError :
                    testSetPropertyCallback.getPropertyAsyncErrors()) {
                int requestId = propertyAsyncError.getRequestId();
                // Async errors are ok as long the requestId is valid
                if (requestIdToAreaIdMap.indexOfKey(requestId) < 0) {
                    assertWithMessage(
                                    "setPropertiesAsync received PropertyAsyncError with unknown "
                                            + "requestId: "
                                            + propertyAsyncError)
                            .fail();
                }
            }
        }
    }

    private void verifySetPropertiesAsyncFails(int areaId) {
        if (!isAtLeastU()) {
            return;
        }
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        List<SetPropertyRequest<?>> setPropertyRequests = new ArrayList<>();
        SetPropertyRequest setPropertyRequest =
                mCarPropertyManager.generateSetPropertyRequest(
                        mPropertyId, areaId, getDefaultValue(carPropertyConfig.getPropertyType()));
        setPropertyRequests.add(setPropertyRequest);
        TestSetPropertyCallback testSetPropertyCallback =
                new TestSetPropertyCallback(/* setPropertyResultsCount: */ 1);
        assertThrows(
                mPropertyName
                        + " is a read_only property so setPropertiesAsync should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () ->
                        mCarPropertyManager.setPropertiesAsync(
                                setPropertyRequests,
                                /* cancellationSignal: */ null, /* callbackExecutor: */
                                null,
                                testSetPropertyCallback));
    }

    private void verifyGetMinMaxSupportedValue() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        int[] areaIds = carPropertyConfig.getAreaIds();
        for (int areaId : areaIds) {
            // Because min/max supported value is dynamic, the value we got here is not necessarily
            // the same as the value we got from VehicleAreaConfig.
            MinMaxSupportedValue<T> minMaxSupportedValue =
                    mCarPropertyManager.getMinMaxSupportedValue(mPropertyId, areaId);
            AreaIdConfig areaIdConfig = carPropertyConfig.getAreaIdConfig(areaId);
            T areaIdMinValue = minMaxSupportedValue.getMinValue();
            T areaIdMaxValue = minMaxSupportedValue.getMaxValue();
            if (areaIdConfig.hasMinSupportedValue()) {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " minSupportedValue must not be null if"
                                        + " hasMinSupportedValue is true")
                        .that(areaIdMinValue)
                        .isNotNull();
            } else {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " minSupportedValue must be null if hasMinSupportedValue"
                                        + " is false")
                        .that(areaIdMinValue)
                        .isNull();
            }

            if (areaIdConfig.hasMaxSupportedValue()) {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " maxSupportedValue must not be null if"
                                        + " hasMaxSupportedValue is true")
                        .that(areaIdMaxValue)
                        .isNotNull();
            } else {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " maxSupportedValue must be null if hasMaxSupportedValue"
                                        + " is false")
                        .that(areaIdMaxValue)
                        .isNull();
            }
            if (mRequireMinValuesToBeZero) {
                assertWithMessage(
                                mPropertyName + " - area ID: " + areaId + " min value must be zero")
                        .that(areaIdMinValue)
                        .isEqualTo(0);
            }
            if (mRequireZeroToBeContainedInMinMaxRanges) {
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s max and min range must contain zero")
                        .that(verifyMaxAndMinRangeContainsZero(areaIdMinValue, areaIdMaxValue))
                        .isTrue();
            }
            if (areaIdMinValue != null || areaIdMaxValue != null) {
                assertWithMessage(
                                mPropertyName
                                        + " - areaId: "
                                        + areaId
                                        + "'s max value must be >= min value")
                        .that(verifyMaxAndMin(areaIdMinValue, areaIdMaxValue))
                        .isTrue();
            }
        }
    }

    private void verifyGetSupportedValuesList() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        int[] areaIds = carPropertyConfig.getAreaIds();
        Class propertyType = carPropertyConfig.getPropertyType();
        for (int areaId : areaIds) {
            List<T> supportedValuesList =
                    mCarPropertyManager.getSupportedValuesList(mPropertyId, areaId);
            AreaIdConfig areaIdConfig = carPropertyConfig.getAreaIdConfig(areaId);
            if (areaIdConfig.hasSupportedValuesList()) {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " supportedValuesList must not be null if"
                                        + " hasSupportedValuesList is true")
                        .that(supportedValuesList)
                        .isNotNull();
            } else {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " supportedValuesList must be null if"
                                        + " hasSupportedValuesList is false")
                        .that(supportedValuesList)
                        .isNull();
                continue;
            }

            assertWithMessage(
                            mPropertyName
                                    + " - area ID: "
                                    + areaId
                                    + " supportedValuesList must not "
                                    + "contain duplicate elements")
                    .that(supportedValuesList)
                    .containsNoDuplicates();

            if (propertyType.equals(Integer.class)
                    || propertyType.equals(Float.class)
                    || propertyType.equals(Long.class)) {
                assertWithMessage(
                                mPropertyName
                                        + " - area ID: "
                                        + areaId
                                        + " supportedValuesList must be "
                                        + "in ascending order")
                        .that(supportedValuesList)
                        .isInOrder();
            }

            if (!mAllPossibleEnumValues.isEmpty()) {
                for (int i = 0; i < supportedValuesList.size(); i++) {
                    T supportedValue = supportedValuesList.get(i);
                    assertWithMessage(
                                    mPropertyName
                                            + " - area ID: "
                                            + areaId
                                            + " supported value: "
                                            + supportedValue
                                            + " is not one of the possible enums")
                            .that(mAllPossibleEnumValues)
                            .contains(supportedValue);
                }
            }
        }
    }

    private void verifyRegisterUnregisterSupportedValuesChangeCallback() {
        // Do nothing for the callback.
        SupportedValuesChangeCallback callback = (propertyId, areaId) -> {};
        // This is an executor that runs inside the test thread.
        Executor executor = (r) -> r.run();

        // We are not expecting any supported values change to happen, so here we just call the
        // the API and verify it succeed.
        assertThat(mCarPropertyManager.registerSupportedValuesChangeCallback(mPropertyId, callback))
                .isTrue();

        mCarPropertyManager.unregisterSupportedValuesChangeCallback(mPropertyId);

        assertThat(
                        mCarPropertyManager.registerSupportedValuesChangeCallback(
                                mPropertyId, executor, callback))
                .isTrue();

        mCarPropertyManager.unregisterSupportedValuesChangeCallback(mPropertyId, callback);

        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        int[] areaIds = carPropertyConfig.getAreaIds();
        for (int areaId : areaIds) {
            assertThat(
                            mCarPropertyManager.registerSupportedValuesChangeCallback(
                                    mPropertyId, areaId, callback))
                    .isTrue();

            mCarPropertyManager.unregisterSupportedValuesChangeCallback(
                    mPropertyId, areaId, callback);

            assertThat(
                            mCarPropertyManager.registerSupportedValuesChangeCallback(
                                    mPropertyId, areaId, executor, callback))
                    .isTrue();

            mCarPropertyManager.unregisterSupportedValuesChangeCallback(
                    mPropertyId, areaId, callback);
        }
    }

    private void verifyIsPropertyAvailable() {
        runWithShellPermissionIdentity(
                () -> verifyIsPropertyAvailableWithReadPermission(),
                CHECK_MODE_ASSUME,
                mReadPermissions.toArray(new String[0]));
    }

    private void verifyIsPropertyAvailableWithReadPermission() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        int[] areaIds;
        if (carPropertyConfig == null) {
            areaIds = new int[] {0};
        } else {
            areaIds = carPropertyConfig.getAreaIds();
        }
        for (int areaId : areaIds) {
            // Before Android B, isPropertyAvailable might throw
            // IllegalArgumentException if the property is not supported or the property is
            // not readable.
            if (!isAtLeastB()) {
                boolean noReadAccess = false;
                String reason = "";
                if (carPropertyConfig != null) {
                    int access = getAreaIdAccessOrElseGlobalAccess(carPropertyConfig, areaId);
                    if (access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                            || access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE) {
                        noReadAccess = true;
                        reason = "the property is not readable";
                    }
                } else {
                    reason = "the property is not supported";
                }

                if (carPropertyConfig == null || noReadAccess) {
                    String errorMsg =
                            "carPropertyManager.isPropertyAvailable must return "
                                    + "false or throw IllegalArgumentException if "
                                    + reason
                                    + ", propertyId: "
                                    + mPropertyName
                                    + ", areaId: "
                                    + areaId;
                    try {
                        boolean result =
                                mCarPropertyManager.isPropertyAvailable(mPropertyId, areaId);
                        assertWithMessage(errorMsg).that(result).isFalse();
                    } catch (Exception e) {
                        assertWithMessage(errorMsg)
                                .that(e.getClass())
                                .isEqualTo(IllegalArgumentException.class);
                    }
                    return;
                }
            }

            boolean result = false;
            try {
                result = mCarPropertyManager.isPropertyAvailable(mPropertyId, areaId);
            } catch (Exception e) {
                assertWithMessage(
                                "carPropertyManager.isPropertyAvailable must not throw any"
                                        + " exception, propertyId: "
                                        + mPropertyName
                                        + ", areaId: "
                                        + areaId
                                        + ", exception: "
                                        + e)
                        .fail();
            }
            if (carPropertyConfig == null) {
                assertWithMessage(
                                "carPropertyManager.isPropertyAvailable must return false "
                                        + "if the property is not supported, propertyId: "
                                        + mPropertyName
                                        + ", areaId: "
                                        + areaId)
                        .that(result)
                        .isFalse();
            }
        }
    }

    private static <U> CarPropertyValue<U> setPropertyAndWaitForChange(
            CarPropertyManager carPropertyManager,
            int propertyId,
            Class<U> propertyType,
            int areaId,
            U valueToSet) {
        return setPropertyAndWaitForChange(
                carPropertyManager, propertyId, propertyType, areaId, valueToSet, valueToSet);
    }

    private static <U> CarPropertyValue<U> setPropertyAndWaitForChange(
            CarPropertyManager carPropertyManager,
            int propertyId,
            Class<U> propertyType,
            int areaId,
            U valueToSet,
            U expectedValueToGet) {
        spaceOutCarPropertyManagerActions();
        SetterCallback setterCallback = new SetterCallback(propertyId, areaId, expectedValueToGet);
        assertWithMessage(
                        "Failed to register setter callback for "
                                + VehiclePropertyIds.toString(propertyId))
                .that(
                        subscribePropertyEvents(
                                carPropertyManager,
                                setterCallback,
                                propertyId,
                                CarPropertyManager.SENSOR_RATE_FASTEST))
                .isTrue();
        try {
            carPropertyManager.setProperty(propertyType, propertyId, areaId, valueToSet);
        } catch (PropertyNotAvailableAndRetryException e) {
            return null;
        } catch (PropertyNotAvailableException e) {
            verifyPropertyNotAvailableException(e);
            sExceptionClassOnSet = e.getClass();
            return null;
        } catch (CarInternalErrorException e) {
            verifyInternalErrorException(e);
            sExceptionClassOnSet = e.getClass();
            return null;
        }

        CarPropertyValue<U> carPropertyValue =
                setterCallback.waitForPropertyEvent(SET_PROPERTY_CALLBACK_TIMEOUT_SEC);
        unsubscribePropertyEvents(carPropertyManager, setterCallback, propertyId);
        return carPropertyValue;
    }

    private static void spaceOutCarPropertyManagerActions() {
        synchronized (sLock) {
            do {
                long currentElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
                long remainingDelayMs =
                        CPM_ACTION_DELAY_MS
                                - Duration.ofNanos(
                                                currentElapsedRealtimeNanos
                                                        - sLastActionElapsedRealtimeNanos)
                                        .toMillis();
                if (remainingDelayMs <= 0) {
                    sLastActionElapsedRealtimeNanos = currentElapsedRealtimeNanos;
                    break;
                }
                SystemClock.sleep(remainingDelayMs);
            } while (true);
        }
    }
}
