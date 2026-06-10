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

package android.autofillservice.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(MockitoJUnitRunner.class)
public class AutofillIdTest {

    @Rule
    public final CheckFlagsRule checkFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private View mHost;

    @Before
    public void setup() {
        mHost = new View(mContext);
        mHost.setAutofillId(new AutofillId(1));
    }

    @Test
    public void testCreateAutofillId() {
        AutofillId vid = AutofillId.create(mHost, /* virtualId= */ 2);

        assertThat(vid).isEqualTo(new AutofillId(/* hostId= */ 1, /* virtualId= */ 2));
    }

    @Test
    public void testCreateAutofillId_invalidHost() {
        assertThrows(NullPointerException.class,
                () -> AutofillId.create(/* hostId= */ null, /* virtualId= */ 2));
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_w_metrics")
    public void testIsVirtual() {
        View view = new View(mContext);

        for (int i = 0; i < 1000; i++) {
            AutofillId vid = AutofillId.create(view, /* virtualId= */ i);
            assertThat(vid.getAutofillVirtualId()).isEqualTo(i);
            assertThat(vid.isVirtual()).isTrue();
        }
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_w_metrics")
    public void testInSession() {
        AutofillId idInSession =
                new AutofillId(new AutofillId(1), /* virtualChildId= */ 10, /* sessionId= */ 12);
        assertThat(idInSession.isInAutofillSession()).isTrue();

        AutofillId idNoSession = new AutofillId(100);
        assertThat(idNoSession.isInAutofillSession()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_w_metrics")
    public void testGetViewId() {
        View view = new View(mContext);

        for (int i = 0; i < 1000; i++) {
            view.setAutofillId(new AutofillId(i));
            AutofillId vid = AutofillId.create(view, /* virtualId= */ 3);
            assertThat(vid.getViewId()).isEqualTo(i);
        }
    }
}
