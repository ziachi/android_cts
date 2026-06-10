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
package com.android.cts.host.broadcasts;

import static com.android.cts.host.broadcasts.Constants.BROADCAST_PROCESSING_TIME_MS;
import static com.android.cts.host.broadcasts.Constants.RECEIVER_PKG;
import static com.android.cts.host.broadcasts.Constants.TEST_BROADCAST_ACTION;
import static com.android.cts.host.broadcasts.Constants.TEST_CLASS;
import static com.android.cts.host.broadcasts.Constants.TEST_PKG;

import static com.google.common.truth.Truth.assertThat;

import android.app.ProcessStateEnum;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.StatsLog;
import com.android.os.broadcasts.BroadcastProcessed;
import com.android.os.broadcasts.BroadcastSent;
import com.android.os.broadcasts.BroadcastsExtensionAtoms;
import com.android.server.am.Flags;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BroadcastStatsHostTest extends BaseHostJUnit4Test implements IBuildReceiver {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        final int userId = getDevice().getCurrentUser();
        for (String pkg : new String[] {TEST_PKG, RECEIVER_PKG}) {
            runCommand(String.format("cmd package unstop --user %d %s", userId, pkg));
        }
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @RequiresFlagsEnabled(Flags.FLAG_LOG_BROADCAST_SENT_EVENT)
    @Test
    public void testBroadcastSent() throws Exception {
        uploadConfigForBroadcastSentEvent(TEST_PKG, TEST_BROADCAST_ACTION);
        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BroadcastsExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, "testBroadcastSent");

        final List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(1);
        final BroadcastSent broadcastSent = data.get(0).getAtom().getExtension(
                BroadcastsExtensionAtoms.broadcastSent);
        // Hardcoding the intent flags as they cannot be accessed directly on the hostside.
        final int expectedFlags = 0x10000000 /* FLAG_RECEIVER_FOREGROUND */
                | 0x01000000 /* FLAG_RECEIVER_INCLUDE_BACKGROUND */;

        verifyBroadcastSentEvent(broadcastSent, DeviceUtils.getAppUid(getDevice(), TEST_PKG),
                TEST_BROADCAST_ACTION,
                expectedFlags, false /* isPackageTargeted */, false /* isComponentTargeted */,
                2 /* numReceivers */, BroadcastSent.Result.SUCCESS,
                ProcessStateEnum.PROCESS_STATE_FOREGROUND_SERVICE,
                ProcessStateEnum.PROCESS_STATE_FOREGROUND_SERVICE);
    }

    @RequiresFlagsEnabled(Flags.FLAG_LOG_BROADCAST_PROCESSED_EVENT)
    @Test
    public void testBroadcastProcessed() throws Exception {
        uploadConfigForBroadcastProcessedEvent(TEST_PKG, TEST_BROADCAST_ACTION, RECEIVER_PKG);
        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BroadcastsExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, "testBroadcastProcessed");

        final List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(1);
        final BroadcastProcessed broadcastProcessed =
                data.getFirst().getAtom().getExtension(BroadcastsExtensionAtoms.broadcastProcessed);

        verifyBroadcastProcessedEvent(
                broadcastProcessed,
                TEST_BROADCAST_ACTION,
                DeviceUtils.getAppUid(getDevice(), TEST_PKG) /* senderUid */,
                1 /* numReceivers */,
                RECEIVER_PKG,
                BROADCAST_PROCESSING_TIME_MS /* maxTimeMillis */,
                BROADCAST_PROCESSING_TIME_MS /* totalTimeMillis */);
    }

    private void uploadConfigForBroadcastSentEvent(String senderPkg, String action)
            throws Exception {
        final StatsdConfigProto.StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                senderPkg);
        final StatsdConfigProto.FieldValueMatcher.Builder senderMatcher = ConfigUtils.createFvm(
                BroadcastSent.SENDER_UID_FIELD_NUMBER).setEqString(senderPkg);
        final StatsdConfigProto.FieldValueMatcher.Builder actionMatcher = ConfigUtils.createFvm(
                BroadcastSent.INTENT_ACTION_FIELD_NUMBER).setEqString(action);
        ConfigUtils.addEventMetric(config, BroadcastsExtensionAtoms.BROADCAST_SENT_FIELD_NUMBER,
                Arrays.asList(senderMatcher, actionMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);
    }

    private void uploadConfigForBroadcastProcessedEvent(
            String senderPkg, String action, String receiverPkg) throws Exception {
        final StatsdConfigProto.StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(senderPkg);
        final StatsdConfigProto.FieldValueMatcher.Builder actionMatcher =
                ConfigUtils.createFvm(BroadcastProcessed.INTENT_ACTION_FIELD_NUMBER)
                        .setEqString(action);
        final StatsdConfigProto.FieldValueMatcher.Builder senderMatcher =
                ConfigUtils.createFvm(BroadcastProcessed.SENDER_UID_FIELD_NUMBER)
                        .setEqString(senderPkg);
        final StatsdConfigProto.FieldValueMatcher.Builder receiverMatcher =
                ConfigUtils.createFvm(BroadcastProcessed.RECEIVER_UID_FIELD_NUMBER)
                        .setEqString(receiverPkg);
        ConfigUtils.addEventMetric(
                config,
                BroadcastsExtensionAtoms.BROADCAST_PROCESSED_FIELD_NUMBER,
                Arrays.asList(senderMatcher, actionMatcher, receiverMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);
    }

    private void verifyBroadcastSentEvent(BroadcastSent broadcastSent, int senderUid,
            String action, int flags, boolean isPackageTargeted, boolean isComponentTargeted,
            int numReceivers, BroadcastSent.Result result, ProcessStateEnum uidState,
            ProcessStateEnum procState) {
        assertThat(broadcastSent.getSenderUid()).isEqualTo(senderUid);
        assertThat(broadcastSent.getIntentAction()).isEqualTo(action);
        assertThat(broadcastSent.getOriginalIntentFlags()).isEqualTo(flags);
        assertThat(broadcastSent.getPackageTargeted()).isEqualTo(isPackageTargeted);
        assertThat(broadcastSent.getComponentTargeted()).isEqualTo(isComponentTargeted);
        assertThat(broadcastSent.getNumReceivers()).isEqualTo(numReceivers);
        assertThat(broadcastSent.getResult()).isEqualTo(result);
        assertThat(broadcastSent.getSenderUidState()).isEqualTo(uidState);
        assertThat(broadcastSent.getSenderProcState()).isEqualTo(procState);
    }

    private void verifyBroadcastProcessedEvent(
            BroadcastProcessed broadcastProcessed,
            String action,
            int senderUid,
            int numReceivers,
            String receiverProcessName,
            int maxTimeMillis,
            int totalTimeMillis) {
        assertThat(broadcastProcessed).isNotNull();
        assertThat(broadcastProcessed.getIntentAction()).isEqualTo(action);
        assertThat(broadcastProcessed.getNumReceivers()).isEqualTo(numReceivers);
        assertThat(broadcastProcessed.getSenderUid()).isEqualTo(senderUid);
        assertThat(broadcastProcessed.getMaxTimeMillis()).isAtLeast(maxTimeMillis);
        assertThat(broadcastProcessed.getTotalTimeMillis()).isAtLeast(totalTimeMillis);
        assertThat(broadcastProcessed.getReceiverProcessName()).contains(receiverProcessName);
    }

    protected String runCommand(String command) throws Exception {
        final String output = getDevice().executeShellCommand(command);
        CLog.v("Output of cmd '" + command + "': '" + output.trim() + "'");
        return output;
    }
}
