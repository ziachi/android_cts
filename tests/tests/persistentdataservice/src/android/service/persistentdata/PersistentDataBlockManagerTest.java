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

package android.service.persistentdata;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class PersistentDataBlockManagerTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PersistentDataBlockManager sPersistentDataBlockManager =
            sContext.getSystemService(PersistentDataBlockManager.class);
    public static final int FACTORY_RESET_SECRET_SIZE = 32;
    public static final String PERSISTENT_DATA_BLOCK_PROPERTY = "ro.frp.pst";

    @EnsureHasPermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void getPersistentDataPackageName_returnsNonNullResult() {
        if (sPersistentDataBlockManager == null) {
            return;
        }
        assertThat(sPersistentDataBlockManager.getPersistentDataPackageName()).isNotNull();
    }

    @EnsureDoesNotHavePermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void getPersistentDataPackageName_withoutPermission_throwsException() {
        if (sPersistentDataBlockManager == null) {
            return;
        }
        assertThrows(SecurityException.class,
                sPersistentDataBlockManager::getPersistentDataPackageName);
    }

    private static boolean deviceHasPersistentDataBlock() {
        return !SystemProperties.get(PERSISTENT_DATA_BLOCK_PROPERTY).equals("");
    }

    private static boolean shouldSupportFrpActiveApi() {
        return getFirstApiLevel() >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                && deviceHasPersistentDataBlock();
    }

    @EnsureDoesNotHavePermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void checkFactoryResetProtection() {
        assumeTrue(shouldSupportFrpActiveApi());

        assertThat(sPersistentDataBlockManager).isNotNull();
        assertThat(sPersistentDataBlockManager.isFactoryResetProtectionActive()).isFalse();
    }

    @EnsureDoesNotHavePermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void verifyOtherMethodsCannotBeCalledByNonPrivilegedApps() {
        assumeTrue(shouldSupportFrpActiveApi());

        assertThat(sPersistentDataBlockManager).isNotNull();
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.write(new byte[0]));
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.write(new byte[10]));
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.read());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.getDataBlockSize());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.getMaximumDataBlockSize());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.wipe());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.setOemUnlockEnabled(true));
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.setOemUnlockEnabled(false));
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.getOemUnlockEnabled());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.getFlashLockState());
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.deactivateFactoryResetProtection(
                        new byte[FACTORY_RESET_SECRET_SIZE]));
        assertThrows(SecurityException.class,
                () -> sPersistentDataBlockManager.setFactoryResetProtectionSecret(
                        new byte[FACTORY_RESET_SECRET_SIZE]));
    }
}
