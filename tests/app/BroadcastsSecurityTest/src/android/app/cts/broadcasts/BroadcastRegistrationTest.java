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

package android.app.cts.broadcasts;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BroadcastRegistrationTest extends StsExtraBusinessLogicTestCase {
    private static final String TEST_ACTION =
            "android.service.autofill.action.DELAYED_FILL";

    @AsbSecurityTest(cveBugId = 310632322)
    @Test
    public void testRegisterWithAndroidPackage() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final IntentFilter filter = new IntentFilter(TEST_ACTION);
        final IIntentReceiver receiver = new IIntentReceiver.Stub() {
            @Override
            public void performReceive(Intent intent, int resultCode,
                    String data, Bundle extras, boolean ordered,
                    boolean sticky, int sendingUser) {
                // Ignore
            }
        };
        assertThrows("Registering a receiver with 'android' package should throw an exception",
                SecurityException.class,
                () -> ActivityManager.getService().registerReceiverWithFeature(
                        context.getIApplicationThread(), "android", "attributionTag",
                        AppOpsManager.toReceiverId(receiver), receiver,
                        filter, null /* broadcastPermission */, context.getUserId(),
                        0 /* flags */));
    }
}
