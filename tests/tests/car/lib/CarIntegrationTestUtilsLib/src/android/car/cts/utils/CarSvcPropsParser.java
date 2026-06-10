/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.util.ArrayMap;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** A parser for CarSvcProps.json which is the config file for car property service. */
public final class CarSvcPropsParser {
    private static final String CONFIG_RESOURCE_NAME = "CarSvcProps.json";
    private static final String JSON_FIELD_NAME_PROPERTIES = "properties";

    private static final String PERM_TYPE_SINGLE = "single";
    private static final String PERM_TYPE_ANYOF = "anyOf";
    private static final String PERM_TYPE_ALLOF = "allOf";
    private static final String JSON_FIELD_VALUE = "value";
    private static final String JSON_FIELD_TYPE = "type";

    // The CarSvcProps.json file version that we can parse.
    private static final int CAR_SVC_PROP_JSON_VERSION = 1;

    private final SparseArray<VehiclePropertyIdInfo> mVehiclePropertyIdInfoByPropertyId =
            new SparseArray<>();
    private final List<Integer> mAllPropertyIds = new ArrayList<>();
    private final Map<String, List<Integer>> mSystemPropertyIdsByFlag = new ArrayMap<>();

    public static class VehiclePropertyIdInfo {
        // This is one of VehiclePropertyIds.
        public int propertyId;
        public String propertyName;
        // The element should be one of VehiclePropertyAccessType
        public ImmutableSet<Integer> allowedAccessModes;
        // This is one of VehicleAreaTypeValue.
        public int areaType;
        // This is one of VehiclePropertyChangeModeType.
        public int changeMode;
        public Class<?> propertyType;
        // Any one of the read permission in this list is required.
        public ImmutableSet<String> readPermissions;
        // Any one of the condition in this list is required. For each condition, all of the
        // permissions are required.
        public ImmutableList<ImmutableSet<String>> writePermissions;
    }

    private static ImmutableSet<Integer> parseAllowedAccessModes(
            JSONArray jsonArray, String propertyName) throws JSONException {
        ImmutableSet.Builder<Integer> allowedAccessModes = ImmutableSet.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            String accessModeStr = jsonArray.getString(i);
            switch (accessModeStr) {
                case "READ":
                    allowedAccessModes.add(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ);
                    break;
                case "WRITE":
                    allowedAccessModes.add(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE);
                    break;
                case "READ_WRITE":
                    allowedAccessModes.add(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid access mode: "
                                    + accessModeStr
                                    + " for property: "
                                    + propertyName);
            }
        }
        return allowedAccessModes.build();
    }

    private static int parseChangeMode(String changeModeStr, String propertyName) {
        switch (changeModeStr) {
            case "STATIC":
                return CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC;
            case "ONCHANGE":
                return CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE;
            case "CONTINUOUS":
                return CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS;
            default:
                throw new IllegalArgumentException(
                        "Invalid change mode: " + changeModeStr + " for property: " + propertyName);
        }
    }

    private static int parseAreaType(String areaTypeStr, String propertyName) {
        switch (areaTypeStr) {
            case "GLOBAL":
                return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
            case "WINDOW":
                return VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW;
            case "SEAT":
                return VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
            case "DOOR":
                return VehicleAreaType.VEHICLE_AREA_TYPE_DOOR;
            case "MIRROR":
                return VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR;
            case "WHEEL":
                return VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL;
            case "VENDOR":
                return VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR;
            default:
                throw new IllegalArgumentException(
                        "Invalid area type: " + areaTypeStr + " for property: " + propertyName);
        }
    }

    private static Class<?> parsePropertyType(String propertyTypeStr, String propertyName) {
        switch (propertyTypeStr) {
            case "Integer":
                return Integer.class;
            case "Integer[]":
                return Integer[].class;
            case "Long":
                return Long.class;
            case "Long[]":
                return Long[].class;
            case "Float":
                return Float.class;
            case "Float[]":
                return Float[].class;
            case "String":
                return String.class;
            case "Boolean":
                return Boolean.class;
            case "byte[]":
                return byte[].class;
            case "Object[]":
                return Object[].class;
            default:
                throw new IllegalArgumentException(
                        "Invalid property type: "
                                + propertyTypeStr
                                + " for property: "
                                + propertyName);
        }
    }

    private static ImmutableSet<String> getSubPermissions(JSONObject permissionObj, int propertyId)
            throws JSONException {
        ImmutableSet.Builder<String> subPermissions = ImmutableSet.builder();
        var subFields = permissionObj.getJSONArray(JSON_FIELD_VALUE);
        for (int i = 0; i < subFields.length(); i++) {
            var subPermissionObj = subFields.getJSONObject(i);
            if (!subPermissionObj.getString(JSON_FIELD_TYPE).equals(PERM_TYPE_SINGLE)) {
                throw new IllegalStateException(
                        "sub permission type must be single for property: "
                                + VehiclePropertyIds.toString(propertyId));
            }
            subPermissions.add(subPermissionObj.getString(JSON_FIELD_VALUE));
        }
        return subPermissions.build();
    }

    private static ImmutableSet<String> parseReadPermission(
            @Nullable JSONObject permissionObj, int propertyId) throws JSONException {
        if (permissionObj == null) {
            return ImmutableSet.of();
        }
        var type = permissionObj.getString(JSON_FIELD_TYPE);
        switch (type) {
            case PERM_TYPE_SINGLE:
                return ImmutableSet.of(permissionObj.getString(JSON_FIELD_VALUE));
            case PERM_TYPE_ANYOF:
                return getSubPermissions(permissionObj, propertyId);
                // "allOf" is not supported for read.
            default:
                throw new IllegalStateException(
                        "Invalid read permission type: "
                                + type
                                + " for property: "
                                + VehiclePropertyIds.toString(propertyId));
        }
    }

    private static ImmutableList<ImmutableSet<String>> parseWritePermission(
            @Nullable JSONObject permissionObj, int propertyId) throws JSONException {
        if (permissionObj == null) {
            return ImmutableList.of();
        }
        var type = permissionObj.getString(JSON_FIELD_TYPE);
        var permBuilder = ImmutableList.<ImmutableSet<String>>builder();
        switch (type) {
            case PERM_TYPE_SINGLE:
                return ImmutableList.of(ImmutableSet.of(permissionObj.getString(JSON_FIELD_VALUE)));
            case PERM_TYPE_ANYOF:
                for (String subPermission : getSubPermissions(permissionObj, propertyId)) {
                    permBuilder.add(ImmutableSet.of(subPermission));
                }
                return permBuilder.build();
            case PERM_TYPE_ALLOF:
                return permBuilder.add(getSubPermissions(permissionObj, propertyId)).build();
            default:
                throw new IllegalStateException(
                        "Invalid write permission type: "
                                + type
                                + " for property: "
                                + VehiclePropertyIds.toString(propertyId));
        }
    }

    public CarSvcPropsParser() {
        String configString;
        try (InputStream configFile =
                this.getClass().getClassLoader().getResourceAsStream(CONFIG_RESOURCE_NAME)) {
            try {
                configString = new String(configFile.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot read from config file: " + CONFIG_RESOURCE_NAME, e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close config resource stream", e);
        }

        JSONObject configJsonObject;
        try {
            configJsonObject = new JSONObject(configString);
        } catch (JSONException e) {
            throw new IllegalStateException(
                    "Config file: "
                            + CONFIG_RESOURCE_NAME
                            + " does not contain a valid JSONObject.",
                    e);
        }

        int version = configJsonObject.optInt("version");
        if (version > CAR_SVC_PROP_JSON_VERSION) {
            throw new IllegalStateException(
                    "Incompatible Car service property config JSON file version, only support: "
                            + CAR_SVC_PROP_JSON_VERSION
                            + ", actually: "
                            + version);
        }
        try {
            JSONObject properties = configJsonObject.getJSONObject(JSON_FIELD_NAME_PROPERTIES);
            Iterator<String> keysIt = properties.keys();
            while (keysIt.hasNext()) {
                String propertyName = keysIt.next();
                JSONObject propertyObj = properties.getJSONObject(propertyName);
                int propertyId = propertyObj.getInt("propertyId");
                // TURN_SIGNAL_STATE is deprecated but still exposed through CarPropertyManager.
                if (propertyId != VehiclePropertyIds.TURN_SIGNAL_STATE
                        && propertyObj.optBoolean("deprecated")) {
                    continue;
                }

                String featureFlag = propertyObj.optString("featureFlag");
                if (!featureFlag.isEmpty()) {
                    if (mSystemPropertyIdsByFlag.get(featureFlag) == null) {
                        mSystemPropertyIdsByFlag.put(featureFlag, new ArrayList<>());
                    }
                    mSystemPropertyIdsByFlag.get(featureFlag).add(propertyId);
                }

                VehiclePropertyIdInfo vehiclePropertyIdInfo = new VehiclePropertyIdInfo();
                vehiclePropertyIdInfo.propertyId = propertyId;
                vehiclePropertyIdInfo.propertyName = propertyName;
                vehiclePropertyIdInfo.allowedAccessModes =
                        parseAllowedAccessModes(
                                propertyObj.getJSONArray("allowedAccessModes"), propertyName);
                vehiclePropertyIdInfo.changeMode =
                        parseChangeMode(propertyObj.getString("changeMode"), propertyName);
                vehiclePropertyIdInfo.propertyType =
                        parsePropertyType(propertyObj.getString("propertyType"), propertyName);
                vehiclePropertyIdInfo.areaType =
                        parseAreaType(propertyObj.getString("areaType"), propertyName);
                vehiclePropertyIdInfo.readPermissions =
                        parseReadPermission(
                                propertyObj.optJSONObject("readPermission"), propertyId);
                vehiclePropertyIdInfo.writePermissions =
                        parseWritePermission(
                                propertyObj.optJSONObject("writePermission"), propertyId);
                mVehiclePropertyIdInfoByPropertyId.put(propertyId, vehiclePropertyIdInfo);
                mAllPropertyIds.add(propertyId);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(
                    "Config file: " + CONFIG_RESOURCE_NAME + " has invalid JSON format.", e);
        }
    }

    /** Gets the VehiclePropertyId information. */
    public @Nullable VehiclePropertyIdInfo getVehiclePropertyIdInfo(int propertyId) {
        return mVehiclePropertyIdInfoByPropertyId.get(propertyId);
    }

    /** Gets all the defined system property IDs. */
    public List<Integer> getAllSystemPropertyIds() {
        return new ArrayList<>(mAllPropertyIds);
    }

    /** Gets the defined system property IDs under the given flag. */
    public List<Integer> getSystemPropertyIdsForFlag(String flag) {
        List<Integer> ids = mSystemPropertyIdsByFlag.get(flag);
        if (ids == null) {
            return new ArrayList<Integer>();
        }
        return new ArrayList<Integer>(ids);
    }
}
