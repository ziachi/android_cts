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

package com.android.cts.tagging;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assume.assumeThat;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

/* Tests that require MTE to be enabled on device.
 *
 * If MTE is off and bootloader control is supported, use that to enable MTE.
 * If MTE is on, just run the tests.
 * Otherwise skip the tests.
 *
 * This allows to test that devices that offer an MTE toggle have a compliant
 * implementation.
 */

@RunWith(DeviceJUnit4ClassRunner.class)
public class MemtagAppTest extends BaseHostJUnit4Test {
  protected static final String TEST_PKG = "android.cts.tagging.memtagapp";
  protected static final String DEVICE_TEST_CLASS_NAME = "TaggingTest";
  private static String mPreviousState = null;

  @BeforeClassWithInfo
  public static void setUp(TestInformation testInfo) throws Exception {
    var device = testInfo.getDevice();
    assumeThat(device.getProperty("ro.product.cpu.abi"), startsWith("arm64"));
    if (!device.pullFileContents("/proc/cpuinfo").contains("mte") &&
        "1".equals(device.getProperty("ro.arm64.memtag.bootctl_supported"))) {
      mPreviousState = device.getProperty("arm64.memtag.bootctl");
      if (mPreviousState == null) {
        mPreviousState = "";
      }
      device.setProperty("arm64.memtag.bootctl", "memtag");
      device.reboot();
    }
    assumeThat(device.pullFileContents("/proc/cpuinfo"), containsString("mte"));
  }

  @AfterClassWithInfo
  public static void tearDown(TestInformation testInfo) throws Exception {
    if (mPreviousState != null) {
      testInfo.getDevice().setProperty("arm64.memtag.bootctl", mPreviousState);
      testInfo.getDevice().reboot();
    }
  }

  private void runStackMteDeviceTest(String method) throws Exception {
    var options = new DeviceTestRunOptions(TEST_PKG);
    options.setTestClassName(TEST_PKG + "." + DEVICE_TEST_CLASS_NAME);
    options.setTestMethodName(method);
    runDeviceTests(options);
  }
  @Test
  public void testHasMteTls() throws Exception {
    runStackMteDeviceTest("testHasMteTls");
  }
  @Test
  public void testHasMteTlsThread() throws Exception {
    runStackMteDeviceTest("testHasMteTlsThread");
  }
  @Test
  public void testIsStackMteOn() throws Exception {
    runStackMteDeviceTest("testIsStackMteOn");
  }
  @Test
  public void testIsStackMteOnThread() throws Exception {
    runStackMteDeviceTest("testIsStackMteOnThread");
  }
  @Test
  public void testHasMteGlobalTag() throws Exception {
    runStackMteDeviceTest("testHasMteGlobalTag");
  }
  @Test
  public void testMteGlobalTagCorrect() throws Exception {
    runStackMteDeviceTest("testMteGlobalTagCorrect");
  }
}
