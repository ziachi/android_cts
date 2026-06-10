/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.mediav2.cts;

import android.media.MediaCodec;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class encapsulates information about the availability of a generic media codec resource
 */
public class CodecResource {
    private final String mResourceId;
    private final long mCapacity;
    private long mAvailable;

    public CodecResource(String resourceId, long capacity, long available) {
        this.mResourceId = resourceId;
        this.mCapacity = capacity;
        this.mAvailable = available;
    }

    public String getResourceId() {
        return mResourceId;
    }

    public long getAvailable() {
        return mAvailable;
    }

    public void addAvailable(long amount) {
        this.mAvailable += amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodecResource)) return false;
        CodecResource that = (CodecResource) o;
        return (mResourceId.equals(that.mResourceId) && mCapacity == that.mCapacity
                && mAvailable == that.mAvailable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mResourceId, mCapacity, mAvailable);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "Resource id %s, Capacity 0x%x, Available 0x%x",
                mResourceId, mCapacity, mAvailable);
    }

}

/**
 * This class provides utility functions for managing and comparing codec resources
 */
class CodecResourceUtils {
    private static final long CAPACITY_UNKNOWN = -1L;
    public static final int RESOURCE_EQ = 0;
    public static final int LHS_RESOURCE_GE = 1;
    public static final int RHS_RESOURCE_GE = 2;
    public static final int RESOURCE_COMPARISON_UNKNOWN = -1;

    public enum CodecState {
        UNINITIALIZED,
        CONFIGURED,
        FLUSHED,
        RUNNING,
        EOS,
        STOPPED,
        RELEASED;

        public static String toString(CodecState state) {
            switch (state) {
                case UNINITIALIZED:
                    return "un-initialized";
                case CONFIGURED:
                    return "configured";
                case FLUSHED:
                    return "flushed";
                case RUNNING:
                    return "running";
                case EOS:
                    return "end of stream";
                case STOPPED:
                    return "stopped";
                case RELEASED:
                    return "released";
                default:
                    return "unknown state";
            }
        }
    }

    public static void addResources(List<MediaCodec.InstanceResourceInfo> from,
            List<CodecResource> to, boolean addNewEntry) {
        Map<String, CodecResource> toMap = new HashMap<>();
        for (CodecResource resource : to) {
            toMap.put(resource.getResourceId(), resource);
        }

        for (MediaCodec.InstanceResourceInfo fromResource : from) {
            long amount = fromResource.getStaticCount();
            CodecResource toResource = toMap.get(fromResource.getName());
            if (toResource != null) {
                toResource.addAvailable(amount);
            } else if (addNewEntry) {
                CodecResource entry =
                        new CodecResource(fromResource.getName(), CAPACITY_UNKNOWN, amount);
                to.add(entry);
                toMap.put(entry.getResourceId(), entry);
            }
        }
    }

    public static int compareResources(List<CodecResource> lhs, List<CodecResource> rhs,
            StringBuilder errorLogs) {
        if (lhs.size() != rhs.size()) {
            if (errorLogs != null) {
                errorLogs.append(String.format(Locale.getDefault(),
                        "resources list sizes %d, %d are not identical\n", lhs.size(), rhs.size()));
            }
            return RESOURCE_COMPARISON_UNKNOWN;
        }
        Map<String, CodecResource> lhsMap = new HashMap<>();
        Map<String, CodecResource> rhsMap = new HashMap<>();

        for (CodecResource resource : lhs) {
            lhsMap.put(resource.getResourceId(), resource);
        }
        for (CodecResource resource : rhs) {
            rhsMap.put(resource.getResourceId(), resource);
        }

        int equalCount = 0;
        int lhsGreaterCount = 0;
        int rhsGreaterCount = 0;

        Set<String> allResourceIds = new HashSet<>();
        allResourceIds.addAll(lhsMap.keySet());
        allResourceIds.addAll(rhsMap.keySet());

        for (String resourceId : allResourceIds) {
            CodecResource lhsResource = lhsMap.get(resourceId);
            CodecResource rhsResource = rhsMap.get(resourceId);
            if (lhsResource == null) {
                if (errorLogs != null) {
                    errorLogs.append("lhs resource : empty, rhs resource : ").append(rhsResource)
                            .append("\n");
                }
                return RESOURCE_COMPARISON_UNKNOWN;
            } else if (rhsResource == null) {
                if (errorLogs != null) {
                    errorLogs.append("lhs resource : ").append(lhsResource)
                            .append("rhs resource : empty").append("\n");
                }
                return RESOURCE_COMPARISON_UNKNOWN;
            } else if (lhsResource.getAvailable() == rhsResource.getAvailable()) {
                equalCount++;
            } else if (lhsResource.getAvailable() > rhsResource.getAvailable()) {
                lhsGreaterCount++;
            } else {
                rhsGreaterCount++;
            }
            if (errorLogs != null) {
                errorLogs.append("lhs resource : ").append(lhsResource).append(", rhs resource : ")
                        .append(rhsResource).append("\n");
            }
        }
        if (equalCount == lhs.size()) {
            return RESOURCE_EQ;
        } else if (lhsGreaterCount + equalCount == lhs.size()) {
            return LHS_RESOURCE_GE;
        } else if (rhsGreaterCount + equalCount == lhs.size()) {
            return RHS_RESOURCE_GE;
        } else {
            return RESOURCE_COMPARISON_UNKNOWN;
        }
    }

    public static List<CodecResource> getCurrentGlobalCodecResources() {
        List<CodecResource> currentGlobalResources = new ArrayList<>();
        List<MediaCodec.GlobalResourceInfo> globalResources =
                MediaCodec.getGloballyAvailableResources();
        for (MediaCodec.GlobalResourceInfo resource : globalResources) {
            CodecResource res = new CodecResource(resource.getName(), resource.getCapacity(),
                    resource.getAvailable());
            currentGlobalResources.add(res);
        }
        return currentGlobalResources;
    }

    /**
     * This function computes the sum of current globally available resources and current active
     * codec instance(s) consumed resources and matches them with system's globally available
     * resources. The caller is responsible for passing all active media codec instances and
     * system's global media codec resources.
     *
     * @param codecsAndStates list of media codec instance and its state.
     * @param refResources    expected global resources
     * @param msg             diagnostics to print on failure
     */
    public static void validateGetCodecResources(List<Pair<MediaCodec, CodecState>> codecsAndStates,
            List<CodecResource> refResources, String msg) {
        boolean shouldThrowException = false;
        for (Pair<MediaCodec, CodecState> codecAndState : codecsAndStates) {
            boolean seenException;
            try {
                codecAndState.first.getRequiredResources();
                seenException = false;
            } catch (IllegalStateException ignored) {
                seenException = true;
            }
            shouldThrowException = (codecAndState.second == CodecState.UNINITIALIZED
                    || codecAndState.second == CodecState.STOPPED
                    || codecAndState.second == CodecState.RELEASED);
            Assert.assertEquals(msg, shouldThrowException, seenException);
        }
        if (!shouldThrowException) {
            List<CodecResource> currAvblResources = getCurrentGlobalCodecResources();
            StringBuilder logs = new StringBuilder();
            for (Pair<MediaCodec, CodecState> codecAndState : codecsAndStates) {
                if (codecAndState.second != CodecState.CONFIGURED) {
                    List<MediaCodec.InstanceResourceInfo> instanceResources =
                            codecAndState.first.getRequiredResources();
                    addResources(instanceResources, currAvblResources, false);
                }
            }
            int result = compareResources(refResources, currAvblResources, logs);
            Assert.assertEquals(logs.toString(), RESOURCE_EQ, result);
        }
    }

    /**
     * Determines the maximum percentage of resource consumption across all resources.
     * <p>
     * This method compares the total available resources before usage to the remaining resources
     * afterward and calculates the percentage of each resource that has been consumed. It then
     * returns the highest observed consumption percentage among all resources.
     *
     * @param globalResources list of CodecResource representing the total available resources.
     * @param usedResources   list of CodecResource representing the remaining resources after
     *                        usage.
     * @return The highest percentage of resource consumption among all resources.
     */
    public static double computeConsumption(List<CodecResource> globalResources,
            List<CodecResource> usedResources) {
        Map<String, CodecResource> globalResourcesMap = new HashMap<>();
        Map<String, CodecResource> usedResourcesMap = new HashMap<>();

        for (CodecResource resource : globalResources) {
            globalResourcesMap.put(resource.getResourceId(), resource);
        }
        for (CodecResource resource : usedResources) {
            usedResourcesMap.put(resource.getResourceId(), resource);
        }

        double max = 0;
        for (Map.Entry<String, CodecResource> global : globalResourcesMap.entrySet()) {
            CodecResource used = usedResourcesMap.get(global.getKey());
            if (used != null) {
                double result = (double) (global.getValue().getAvailable() - used.getAvailable())
                        / global.getValue().getAvailable() * 100;
                max = Math.max(max, result);
            }
        }
        return max;
    }
}
