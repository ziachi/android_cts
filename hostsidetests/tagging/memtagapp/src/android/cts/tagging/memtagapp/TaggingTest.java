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

package android.cts.tagging.memtagapp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// This is driven by MemtagAppTest from the host, which enables
// MTE, if necessary, and then runs the tests here.
@RunWith(AndroidJUnit4.class)
@SmallTest
public final class TaggingTest {
  static { System.loadLibrary("cts_memtagapp_jni"); }

  @Test
  public void testIsStackMteOn() throws Exception {
    assertThat(isStackMteOn()).isTrue();
  }
  @Test
  public void testIsStackMteOnThread() throws Exception {
    assertThat(isStackMteOnThread()).isTrue();
  }
  @Test
  public void testHasMteTls() throws Exception {
    assertThat(hasMteTls()).isTrue();
  }
  @Test
  public void testHasMteTlsThread() throws Exception {
    assertThat(hasMteTlsThread()).isTrue();
  }
  @Test
  public void testHasMteGlobalTag() throws Exception {
    assertThat(hasMteGlobalTag()).isTrue();
  }
  @Test
  public void testMteGlobalTagCorrect() throws Exception {
    assertThat(mteGlobalTagCorrect()).isTrue();
  }
  public static native boolean isStackMteOn();
  public static native boolean isStackMteOnThread();
  public static native boolean hasMteTls();
  public static native boolean hasMteTlsThread();
  public static native boolean hasMteGlobalTag();
  public static native boolean mteGlobalTagCorrect();
}
