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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.TestTag
import com.android.bedstead.nene.utils.Tags
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test behavior of BedsteadJUnit4 test when not adding the DeviceState rule manually
 */
@RunWith(BedsteadJUnit4::class)
class BedsteadWithoutDeviceStateTest {

    @Test
    @TestTag(TAG)
    fun testTagAnnotation_testTagIsSet() {
        assertThat(Tags.hasTag(TAG)).isTrue()
    }

    companion object {
        const val TAG = "karzelek"
    }
}
