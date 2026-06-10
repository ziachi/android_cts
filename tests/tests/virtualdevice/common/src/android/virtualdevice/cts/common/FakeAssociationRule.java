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

package android.virtualdevice.cts.common;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.role.RoleManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A test rule that creates a {@link CompanionDeviceManager} association with the instrumented
 * package for the duration of the test.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class FakeAssociationRule extends ExternalResource {
    private static final String TAG = "FakeAssociationRule";

    private static final String DEVICE_PROFILE = AssociationRequest.DEVICE_PROFILE_APP_STREAMING;
    private static final String DISPLAY_NAME = "CTS CDM VDM Association";
    private static final String FAKE_ASSOCIATION_ADDRESS_FORMAT = "00:00:00:00:00:%02d";

    private static final int TIMEOUT_MS = 10000;

    private final Context mContext = getInstrumentation().getContext();

    private final Executor mCallbackExecutor = Runnable::run;
    private final RoleManager mRoleManager = mContext.getSystemService(RoleManager.class);

    @Mock
    private CompanionDeviceManager.OnAssociationsChangedListener mOnAssociationsChangedListener;

    private int mNextDeviceId = 0;

    private AssociationInfo mAssociationInfo;
    private final CompanionDeviceManager mCompanionDeviceManager =
            mContext.getSystemService(CompanionDeviceManager.class);

    private AssociationInfo createManagedAssociationApi35() {
        String deviceAddress = String.format(Locale.getDefault(Locale.Category.FORMAT),
                FAKE_ASSOCIATION_ADDRESS_FORMAT, ++mNextDeviceId);
        if (mNextDeviceId > 99) {
            throw new IllegalArgumentException("At most 99 associations supported");
        }

        Log.d(TAG, "Associations before shell cmd: "
                + mCompanionDeviceManager.getMyAssociations().size());
        reset(mOnAssociationsChangedListener);
        mRoleManager.setBypassingRoleQualification(true);
        SystemUtil.runShellCommandOrThrow(String.format(Locale.getDefault(Locale.Category.FORMAT),
                "cmd companiondevice associate %d %s %s %s true",
                getInstrumentation().getContext().getUserId(),
                mContext.getPackageName(),
                deviceAddress,
                DEVICE_PROFILE));
        verifyAssociationsChanged();
        mRoleManager.setBypassingRoleQualification(false);

        // Immediately drop the role and rely on Shell
        Consumer<Boolean> callback = mock(Consumer.class);
        mRoleManager.removeRoleHolderAsUser(
                DEVICE_PROFILE, mContext.getPackageName(),
                RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, Process.myUserHandle(),
                mCallbackExecutor, callback);
        verify(callback, timeout(TIMEOUT_MS)).accept(true);

        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();

        final AssociationInfo associationInfo =
                associations.stream()
                        .filter(a -> deviceAddress.equals(a.getDeviceMacAddress().toString()))
                        .findAny()
                        .orElse(null);
        assertThat(associationInfo).isNotNull();
        return associationInfo;
    }

    public AssociationInfo createManagedAssociation(String deviceProfile) {
        final AssociationInfo[] managedAssociation = new AssociationInfo[1];
        AssociationRequest request = new AssociationRequest.Builder()
                .setDeviceProfile(deviceProfile)
                .setDisplayName(DISPLAY_NAME + " - " + mNextDeviceId++)
                .setSelfManaged(true)
                .setSkipRoleGrant(true)
                .build();
        CountDownLatch latch = new CountDownLatch(1);
        CompanionDeviceManager.Callback callback  = new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationCreated(@NonNull AssociationInfo associationInfo) {
                managedAssociation[0] = associationInfo;
                latch.countDown();
            }

            @Override
            public void onFailure(@Nullable CharSequence error) {
                fail(error == null ? "Failed to create CDM association" : error.toString());
            }
        };
        reset(mOnAssociationsChangedListener);
        mCompanionDeviceManager.associate(request, Runnable::run, callback);

        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for CDM association: " + e);
        }

        verifyAssociationsChanged();
        return managedAssociation[0];
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        MockitoAnnotations.initMocks(this);
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        mCompanionDeviceManager.addOnAssociationsChangedListener(
                mCallbackExecutor, mOnAssociationsChangedListener);
        clearExistingAssociations();
        if (isAtLeastB()) {
            mAssociationInfo = createManagedAssociation(DEVICE_PROFILE);
        } else {
            mAssociationInfo = createManagedAssociationApi35();
        }
    }

    @Override
    protected void after() {
        super.after();
        clearExistingAssociations();
        mCompanionDeviceManager.removeOnAssociationsChangedListener(
                mOnAssociationsChangedListener);
    }

    private void clearExistingAssociations() {
        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();
        for (AssociationInfo association : associations) {
            disassociate(association.getId());
        }
        assertThat(mCompanionDeviceManager.getMyAssociations()).isEmpty();
        mAssociationInfo = null;
    }

    public AssociationInfo getAssociationInfo() {
        return mAssociationInfo;
    }

    public void disassociate() {
        clearExistingAssociations();
    }

    private void disassociate(int associationId) {
        reset(mOnAssociationsChangedListener);
        mCompanionDeviceManager.disassociate(associationId);
        verifyAssociationsChanged();
    }

    private void verifyAssociationsChanged() {
        verify(mOnAssociationsChangedListener, timeout(TIMEOUT_MS)
                .description(TAG + ": onAssociationChanged not called, total associations: "
                        + mCompanionDeviceManager.getMyAssociations().size()))
                .onAssociationsChanged(any());
    }

    @ChecksSdkIntAtLeast(api = 36 /* BUILD_VERSION_CODES.Baklava */)
    private static boolean isAtLeastB() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                || Objects.equals(Build.VERSION.CODENAME, "Baklava");
    }
}
