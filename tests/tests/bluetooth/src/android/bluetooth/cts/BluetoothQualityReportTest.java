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

package android.bluetooth.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothQualityReport;
import android.bluetooth.BluetoothQualityReport.BqrCommon;
import android.bluetooth.BluetoothQualityReport.BqrConnectFail;
import android.bluetooth.BluetoothQualityReport.BqrEnergyMonitor;
import android.bluetooth.BluetoothQualityReport.BqrRfStats;
import android.bluetooth.BluetoothQualityReport.BqrVsA2dpChoppy;
import android.bluetooth.BluetoothQualityReport.BqrVsLsto;
import android.bluetooth.BluetoothQualityReport.BqrVsScoChoppy;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.bluetooth.flags.Flags;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
public final class BluetoothQualityReportTest {
    private static final String TAG = BluetoothQualityReportTest.class.getSimpleName();

    @Rule public final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static String mRemoteAddress = "01:02:03:04:05:06";
    private static String mDefaultAddress = "00:00:00:00:00:00";
    private static String mRemoteName = "DeviceName";
    private static String mDefaultName = "";
    private static int mLmpVer = 0;
    private static int mLmpSubVer = 1;
    private static int mManufacturerId = 3;
    private static int mRemoteCoD = 4;

    private void assertBqrCommon(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR Common
        BqrCommon bqrCommon = bqr.getBqrCommon();
        assertThat(bqrCommon).isNotNull();
        assertThat(bqr.getQualityReportId()).isEqualTo(bqrp.getQualityReportId());
        if ((bqr.getQualityReportId() == BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR)
                || (bqr.getQualityReportId()
                        == BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS)) {
            return;
        }
        assertThat(bqrCommon.getPacketType()).isEqualTo(bqrp.mPacketType);
        assertThat(BqrCommon.packetTypeToString(bqrCommon.getPacketType())).isEqualTo("TYPE_NULL");
        assertThat(bqrCommon.getConnectionHandle()).isEqualTo(bqrp.mConnectionHandle);
        assertThat(BqrCommon.connectionRoleToString(bqrCommon.getConnectionRole()))
                .isEqualTo(bqrp.mConnectionRoleCentral);
        assertThat(bqrCommon.getConnectionRole()).isEqualTo(bqrp.mConnectionRole);
        assertThat(bqrCommon.getTxPowerLevel()).isEqualTo(bqrp.mTxPowerLevel);
        assertThat(bqrCommon.getRssi()).isEqualTo(bqrp.mRssi);
        assertThat(bqrCommon.getSnr()).isEqualTo(bqrp.mSnr);
        assertThat(bqrCommon.getUnusedAfhChannelCount()).isEqualTo(bqrp.mUnusedAfhChannelCount);
        assertThat(bqrCommon.getAfhSelectUnidealChannelCount())
                .isEqualTo(bqrp.mAfhSelectUnidealChannelCount);
        assertThat(bqrCommon.getLsto()).isEqualTo(bqrp.mLsto);
        assertThat(bqrCommon.getPiconetClock()).isEqualTo(bqrp.mPiconetClock);
        assertThat(bqrCommon.getRetransmissionCount()).isEqualTo(bqrp.mRetransmissionCount);
        assertThat(bqrCommon.getNoRxCount()).isEqualTo(bqrp.mNoRxCount);
        assertThat(bqrCommon.getNakCount()).isEqualTo(bqrp.mNakCount);
        assertThat(bqrCommon.getLastTxAckTimestamp()).isEqualTo(bqrp.mLastTxAckTimestamp);
        assertThat(bqrCommon.getFlowOffCount()).isEqualTo(bqrp.mFlowOffCount);
        assertThat(bqrCommon.getLastFlowOnTimestamp()).isEqualTo(bqrp.mLastFlowOnTimestamp);
        assertThat(bqrCommon.getOverflowCount()).isEqualTo(bqrp.mOverflowCount);
        assertThat(bqrCommon.getUnderflowCount()).isEqualTo(bqrp.mUnderflowCount);
        assertThat(bqrCommon.getCalFailedItemCount()).isEqualTo(bqrp.mCalFailedItemCount);
    }

    private void assertBqrV6Common(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR Common
        BqrCommon bqrCommon = bqr.getBqrCommon();
        expect.that(bqrCommon).isNotNull();
        expect.that(bqr.getQualityReportId()).isEqualTo(bqrp.getQualityReportId());
        if ((bqr.getQualityReportId() == BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR)
                || (bqr.getQualityReportId()
                        == BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS)) {
            return;
        }
        expect.that(bqrCommon.getPacketType()).isEqualTo(bqrp.mPacketType);
        expect.that(BqrCommon.packetTypeToString(bqrCommon.getPacketType())).isEqualTo("TYPE_NULL");
        expect.that(bqrCommon.getConnectionHandle()).isEqualTo(bqrp.mConnectionHandle);
        expect.that(BqrCommon.connectionRoleToString(bqrCommon.getConnectionRole()))
                .isEqualTo(bqrp.mConnectionRoleCentral);
        expect.that(bqrCommon.getConnectionRole()).isEqualTo(bqrp.mConnectionRole);
        expect.that(bqrCommon.getTxPowerLevel()).isEqualTo(bqrp.mTxPowerLevel);
        expect.that(bqrCommon.getRssi()).isEqualTo(bqrp.mRssi);
        expect.that(bqrCommon.getSnr()).isEqualTo(bqrp.mSnr);
        expect.that(bqrCommon.getUnusedAfhChannelCount()).isEqualTo(bqrp.mUnusedAfhChannelCount);
        expect.that(bqrCommon.getAfhSelectUnidealChannelCount())
                .isEqualTo(bqrp.mAfhSelectUnidealChannelCount);
        expect.that(bqrCommon.getLsto()).isEqualTo(bqrp.mLsto);
        expect.that(bqrCommon.getPiconetClock()).isEqualTo(bqrp.mPiconetClock);
        expect.that(bqrCommon.getRetransmissionCount()).isEqualTo(bqrp.mRetransmissionCount);
        expect.that(bqrCommon.getNoRxCount()).isEqualTo(bqrp.mNoRxCount);
        expect.that(bqrCommon.getNakCount()).isEqualTo(bqrp.mNakCount);
        expect.that(bqrCommon.getLastTxAckTimestamp()).isEqualTo(bqrp.mLastTxAckTimestamp);
        expect.that(bqrCommon.getFlowOffCount()).isEqualTo(bqrp.mFlowOffCount);
        expect.that(bqrCommon.getLastFlowOnTimestamp()).isEqualTo(bqrp.mLastFlowOnTimestamp);
        expect.that(bqrCommon.getOverflowCount()).isEqualTo(bqrp.mOverflowCount);
        expect.that(bqrCommon.getUnderflowCount()).isEqualTo(bqrp.mUnderflowCount);
        expect.that(bqrCommon.getCalFailedItemCount()).isEqualTo(bqrp.mCalFailedItemCount);

        expect.that(bqrCommon.getTxTotalPackets()).isEqualTo(bqrp.mTxTotalPackets);
        expect.that(bqrCommon.getTxUnackPackets()).isEqualTo(bqrp.mTxUnackPackets);
        expect.that(bqrCommon.getTxFlushPackets()).isEqualTo(bqrp.mTxFlushPackets);
        expect.that(bqrCommon.getTxLastSubeventPackets()).isEqualTo(bqrp.mTxLastSubeventPackets);
        expect.that(bqrCommon.getCrcErrorPackets()).isEqualTo(bqrp.mCrcErrorPackets);
        expect.that(bqrCommon.getRxDupPackets()).isEqualTo(bqrp.mRxDupPackets);
        expect.that(bqrCommon.getRxUnRecvPackets()).isEqualTo(bqrp.mRxUnRecvPackets);
        expect.that(bqrCommon.getCoexInfoMask()).isEqualTo(bqrp.mCoexInfoMask);
        expect.that(bqrCommon.getCoexInfoMask()).isEqualTo(bqrp.mCoexInfoMask);
    }

    private void assertBqrApproachLsto(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS LSTO
        BqrVsLsto bqrVsLsto = (BqrVsLsto) bqr.getBqrEvent();
        assertThat(bqrVsLsto).isNotNull();
        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Approaching LSTO");
        assertThat(bqrVsLsto.getConnState()).isEqualTo(bqrp.mConnState & 0xFF);
        assertThat(BqrVsLsto.connStateToString(bqrVsLsto.getConnState()))
                .isEqualTo("CONN_UNPARK_ACTIVE");
        assertThat(bqrVsLsto.getBasebandStats()).isEqualTo(bqrp.mBasebandStats);
        assertThat(bqrVsLsto.getSlotsUsed()).isEqualTo(bqrp.mSlotsUsed);
        assertThat(bqrVsLsto.getCxmDenials()).isEqualTo(bqrp.mCxmDenials);
        assertThat(bqrVsLsto.getTxSkipped()).isEqualTo(bqrp.mTxSkipped);
        assertThat(bqrVsLsto.getRfLoss()).isEqualTo(bqrp.mRfLoss);
        assertThat(bqrVsLsto.getNativeClock()).isEqualTo(bqrp.mNativeClock);
        assertThat(bqrVsLsto.getLastTxAckTimestamp()).isEqualTo(bqrp.mLastTxAckTimestampLsto);
        assertThat(bqrVsLsto.describeContents()).isEqualTo(0);
    }

    private void assertBqrA2dpChoppy(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS A2DP Choppy
        BqrVsA2dpChoppy bqrVsA2dpChoppy = (BqrVsA2dpChoppy) bqr.getBqrEvent();
        assertThat(bqrVsA2dpChoppy).isNotNull();
        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("A2DP choppy");
        assertThat(bqrVsA2dpChoppy.getArrivalTime()).isEqualTo(bqrp.mArrivalTime);
        assertThat(bqrVsA2dpChoppy.getScheduleTime()).isEqualTo(bqrp.mScheduleTime);
        assertThat(bqrVsA2dpChoppy.getGlitchCount()).isEqualTo(bqrp.mGlitchCountA2dp);
        assertThat(bqrVsA2dpChoppy.getTxCxmDenials()).isEqualTo(bqrp.mTxCxmDenialsA2dp);
        assertThat(bqrVsA2dpChoppy.getRxCxmDenials()).isEqualTo(bqrp.mRxCxmDenialsA2dp);
        assertThat(bqrVsA2dpChoppy.getAclTxQueueLength()).isEqualTo(bqrp.mAclTxQueueLength);
        assertThat(bqrVsA2dpChoppy.getLinkQuality()).isEqualTo(bqrp.mLinkQuality);
        assertThat(BqrVsA2dpChoppy.linkQualityToString(bqrVsA2dpChoppy.getLinkQuality()))
                .isEqualTo("MEDIUM");
        assertThat(bqrVsA2dpChoppy.describeContents()).isEqualTo(0);
    }

    private void assertBqrScoChoppy(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS SCO Choppy
        BqrVsScoChoppy bqrVsScoChoppy = (BqrVsScoChoppy) bqr.getBqrEvent();
        assertThat(bqrVsScoChoppy).isNotNull();
        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("SCO choppy");
        assertThat(bqrVsScoChoppy.getGlitchCount()).isEqualTo(bqrp.mGlitchCountSco);
        assertThat(bqrVsScoChoppy.getIntervalEsco()).isEqualTo(bqrp.mIntervalEsco);
        assertThat(bqrVsScoChoppy.getWindowEsco()).isEqualTo(bqrp.mWindowEsco);
        assertThat(bqrVsScoChoppy.getAirFormat()).isEqualTo(bqrp.mAirFormat);
        assertThat(BqrVsScoChoppy.airFormatToString(bqrVsScoChoppy.getAirFormat()))
                .isEqualTo("CVSD");
        assertThat(bqrVsScoChoppy.getInstanceCount()).isEqualTo(bqrp.mInstanceCount);
        assertThat(bqrVsScoChoppy.getTxCxmDenials()).isEqualTo(bqrp.mTxCxmDenialsSco);
        assertThat(bqrVsScoChoppy.getRxCxmDenials()).isEqualTo(bqrp.mRxCxmDenialsSco);
        assertThat(bqrVsScoChoppy.getTxAbortCount()).isEqualTo(bqrp.mTxAbortCount);
        assertThat(bqrVsScoChoppy.getLateDispatch()).isEqualTo(bqrp.mLateDispatch);
        assertThat(bqrVsScoChoppy.getMicIntrMiss()).isEqualTo(bqrp.mMicIntrMiss);
        assertThat(bqrVsScoChoppy.getLpaIntrMiss()).isEqualTo(bqrp.mLpaIntrMiss);
        assertThat(bqrVsScoChoppy.getSprIntrMiss()).isEqualTo(bqrp.mSprIntrMiss);
        assertThat(bqrVsScoChoppy.getPlcFillCount()).isEqualTo(bqrp.mPlcFillCount);
        assertThat(bqrVsScoChoppy.getPlcDiscardCount()).isEqualTo(bqrp.mPlcDiscardCount);
        assertThat(bqrVsScoChoppy.getMissedInstanceCount()).isEqualTo(bqrp.mMissedInstanceCount);
        assertThat(bqrVsScoChoppy.getTxRetransmitSlotCount())
                .isEqualTo(bqrp.mTxRetransmitSlotCount);
        assertThat(bqrVsScoChoppy.getRxRetransmitSlotCount())
                .isEqualTo(bqrp.mRxRetransmitSlotCount);
        assertThat(bqrVsScoChoppy.getGoodRxFrameCount()).isEqualTo(bqrp.mGoodRxFrameCount);
        assertThat(bqrVsScoChoppy.describeContents()).isEqualTo(0);
    }

    private void assertBqrEnergyMonitor(BQRParameters bqrp, BluetoothQualityReport bqr) {
        BqrEnergyMonitor bqrEnergyMonitor = (BqrEnergyMonitor) bqr.getBqrEvent();
        expect.that(bqrEnergyMonitor).isNotNull();
        expect.that(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Energy Monitor");
        expect.that(bqrp.mAvgCurrentConsume)
                .isEqualTo(bqrEnergyMonitor.getAverageCurrentConsumptionMicroamps());
        expect.that(bqrp.mIdleTotalTime).isEqualTo(bqrEnergyMonitor.getIdleStateTotalTimeMillis());
        expect.that(bqrp.mIdleStateEnterCount).isEqualTo(bqrEnergyMonitor.getIdleStateEnterCount());
        expect.that(bqrp.mActiveTotalTime)
                .isEqualTo(bqrEnergyMonitor.getActiveStateTotalTimeMillis());
        expect.that(bqrp.mActiveStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getActiveStateEnterCount());
        expect.that(bqrp.mBredrTxTotalTime).isEqualTo(bqrEnergyMonitor.getBredrTxTotalTimeMillis());
        expect.that(bqrp.mBredrTxStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getBredrTxStateEnterCount());
        expect.that(bqrp.mBredrTxAvgPowerLevel)
                .isEqualTo(bqrEnergyMonitor.getBredrAverageTxPowerLeveldBm());
        expect.that(bqrp.mBredrRxTotalTime).isEqualTo(bqrEnergyMonitor.getBredrRxTotalTimeMillis());
        expect.that(bqrp.mBredrRxStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getBredrRxStateEnterCount());
        expect.that(bqrp.mLeTxTotalTime).isEqualTo(bqrEnergyMonitor.getLeTsTotalTimeMillis());
        expect.that(bqrp.mLeTxStateEnterCount).isEqualTo(bqrEnergyMonitor.getLeTxStateEnterCount());
        expect.that(bqrp.mLeTxAvgPowerLevel)
                .isEqualTo(bqrEnergyMonitor.getLeAverageTxPowerLeveldBm());
        expect.that(bqrp.mLeRxTotalTime).isEqualTo(bqrEnergyMonitor.getLeRxTotalTimeMillis());
        expect.that(bqrp.mLeRxStateEnterCount).isEqualTo(bqrEnergyMonitor.getLeRxStateEnterCount());
        expect.that(bqrp.mReportTotalTime)
                .isEqualTo(bqrEnergyMonitor.getPowerDataTotalTimeMillis());
        expect.that(bqrp.mRxActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getRxSingleChainActiveDurationMillis());
        expect.that(bqrp.mRxActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getRxDualChainActiveDurationMillis());
        expect.that(bqrp.mTxiPaActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxInternalPaSingleChainActiveDurationMillis());
        expect.that(bqrp.mTxiPaActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxInternalPaDualChainActiveDurationMillis());
        expect.that(bqrp.mTxePaActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxExternalPaSingleChainActiveDurationMillis());
        expect.that(bqrp.mTxePaActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxExternalPaDualChainActiveDurationMillis());
        expect.that(bqrEnergyMonitor.describeContents()).isEqualTo(0);
    }

    private void assertBqrConnectFail(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS Connect Fail
        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        assertThat(bqrConnectFail).isNotNull();
        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Connect fail");
        assertThat(bqrConnectFail.getFailReason()).isEqualTo(bqrp.mFailReason);
        assertThat(bqrConnectFail.describeContents()).isEqualTo(0);
    }

    private void assertBqrRfStats(BQRParameters bqrp, BluetoothQualityReport bqr) {
        BqrRfStats bqrRfStats = (BqrRfStats) bqr.getBqrEvent();
        expect.that(bqrRfStats).isNotNull();
        expect.that(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("RF Stats");
        expect.that(bqrp.mExtensionInfo).isEqualTo(bqrRfStats.getExtensionInfo());
        expect.that(bqrp.mReportTimePeriod).isEqualTo(bqrRfStats.getPerformanceDurationMillis());
        expect.that(bqrp.mTxPoweriPaBf)
                .isEqualTo(bqrRfStats.getTxPowerInternalPaBeamformingCount());
        expect.that(bqrp.mTxPowerePaBf)
                .isEqualTo(bqrRfStats.getTxPowerExternalPaBeamformingCount());
        expect.that(bqrp.mTxPoweriPaDiv).isEqualTo(bqrRfStats.getTxPowerInternalPaDiversityCount());
        expect.that(bqrp.mTxPowerePaDiv).isEqualTo(bqrRfStats.getTxPowerExternalPaDiversityCount());
        expect.that(bqrp.mRssiChainOver50)
                .isEqualTo(bqrRfStats.getPacketsWithRssiAboveMinus50dBm());
        expect.that(bqrp.mRssiChain50To55).isEqualTo(bqrRfStats.getPacketsWithRssi50To55dBm());
        expect.that(bqrp.mRssiChain55To60).isEqualTo(bqrRfStats.getPacketsWithRssi55To60dBm());
        expect.that(bqrp.mRssiChain60To65).isEqualTo(bqrRfStats.getPacketsWithRssi60To65dBm());
        expect.that(bqrp.mRssiChain65To70).isEqualTo(bqrRfStats.getPacketsWithRssi65To70dBm());
        expect.that(bqrp.mRssiChain70To75).isEqualTo(bqrRfStats.getPacketsWithRssi70To75dBm());
        expect.that(bqrp.mRssiChain75To80).isEqualTo(bqrRfStats.getPacketsWithRssi75To80dBm());
        expect.that(bqrp.mRssiChain80To85).isEqualTo(bqrRfStats.getPacketsWithRssi80To85dBm());
        expect.that(bqrp.mRssiChain85To90).isEqualTo(bqrRfStats.getPacketsWithRssi85To90dBm());
        expect.that(bqrp.mRssiChainUnder90)
                .isEqualTo(bqrRfStats.getPacketsWithRssiBelowMinus90dBm());
        expect.that(bqrp.mRssiDeltaUnder2).isEqualTo(bqrRfStats.getPacketsWithRssiDeltaBelow2dBm());
        expect.that(bqrp.mRssiDelta2To5).isEqualTo(bqrRfStats.getPacketsWithRssiDelta2To5dBm());
        expect.that(bqrp.mRssiDelta5To8).isEqualTo(bqrRfStats.getPacketsWithRssiDelta5To8dBm());
        expect.that(bqrp.mRssiDelta8To11).isEqualTo(bqrRfStats.getPacketsWithRssiDelta8To11dBm());
        expect.that(bqrp.mRssiDeltaOver11)
                .isEqualTo(bqrRfStats.getPacketsWithRssiDeltaAbove11dBm());
        expect.that(bqrRfStats.describeContents()).isEqualTo(0);
    }

    private static BluetoothClass getBluetoothClassHelper(int remoteCoD) {
        Parcel p = Parcel.obtain();
        p.writeInt(remoteCoD);
        p.setDataPosition(0);
        BluetoothClass bluetoothClass = BluetoothClass.CREATOR.createFromParcel(p);
        p.recycle();
        return bluetoothClass;
    }

    private BluetoothQualityReport initBqrCommon(
            BQRParameters bqrp,
            String remoteAddr,
            int lmpVer,
            int lmpSubVer,
            int manufacturerId,
            String remoteName,
            int remoteCoD) {

        BluetoothClass bluetoothClass = getBluetoothClassHelper(remoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress(remoteAddr)
                        .setLmpVersion(lmpVer)
                        .setLmpSubVersion(lmpSubVer)
                        .setManufacturerId(manufacturerId)
                        .setRemoteName(remoteName)
                        .setBluetoothClass(bluetoothClass)
                        .build();

        Log.i(TAG, bqr.toString());

        assertThat(remoteAddr).isEqualTo(bqr.getRemoteAddress());
        assertThat(bqr.getLmpVersion()).isEqualTo(lmpVer);
        assertThat(bqr.getLmpSubVersion()).isEqualTo(lmpSubVer);
        assertThat(bqr.getManufacturerId()).isEqualTo(manufacturerId);
        assertThat(remoteName).isEqualTo(bqr.getRemoteName());
        assertThat(bqr.getBluetoothClass()).isEqualTo(bluetoothClass);

        assertBqrCommon(bqrp, bqr);

        return bqr;
    }

    @Test
    public void bqrMonitor() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);
    }

    @Test
    public void bqrApproachLsto() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrApproachLsto(bqrp, bqr);
    }

    @Test
    public void bqrA2dpChoppy() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrA2dpChoppy(bqrp, bqr);
    }

    @Test
    public void bqrScoChoppy() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrScoChoppy(bqrp, bqr);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void bqrEnergyMonitor() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrEnergyMonitor(bqrp, bqr);
    }

    @Test
    public void bqrConnectFail() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrConnectFail(bqrp, bqr);

        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        assertThat(bqrConnectFail).isNotNull();

        assertThat(BqrConnectFail.connectFailIdToString(BqrConnectFail.CONNECT_FAIL_ID_NO_ERROR))
                .isEqualTo("No error");
        assertThat(
                        BqrConnectFail.connectFailIdToString(
                                BqrConnectFail.CONNECT_FAIL_ID_PAGE_TIMEOUT))
                .isEqualTo("Page Timeout");
        assertThat(
                        BqrConnectFail.connectFailIdToString(
                                BqrConnectFail.CONNECT_FAIL_ID_CONNECTION_TIMEOUT))
                .isEqualTo("Connection Timeout");
        assertThat(
                        BqrConnectFail.connectFailIdToString(
                                BqrConnectFail.CONNECT_FAIL_ID_ACL_ALREADY_EXIST))
                .isEqualTo("ACL already exists");
        assertThat(
                        BqrConnectFail.connectFailIdToString(
                                BqrConnectFail.CONNECT_FAIL_ID_CONTROLLER_BUSY))
                .isEqualTo("Controller busy");
        assertThat(BqrConnectFail.connectFailIdToString(0xFF)).isEqualTo("INVALID");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void bqrRfStats() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrRfStats(bqrp, bqr);
    }

    @Test
    public void defaultNameAddress() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress("123456123456")
                        .setLmpVersion(mLmpVer)
                        .setLmpSubVersion(mLmpSubVer)
                        .setManufacturerId(mManufacturerId)
                        .setBluetoothClass(bluetoothClass)
                        .build();

        assertThat(bqr.getRemoteAddress()).isEqualTo(mDefaultAddress);
        assertThat(bqr.getRemoteName()).isEqualTo(mDefaultName);
    }

    @Test
    public void invalidQualityReportId() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) 123);
        assertThat(bqrp.getQualityReportId()).isEqualTo(123);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        initBqrCommon(
                                bqrp,
                                mRemoteAddress,
                                mLmpVer,
                                mLmpSubVer,
                                mManufacturerId,
                                mRemoteName,
                                mRemoteCoD));
    }

    @Test
    public void rawDataNull() {
        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        assertThrows(
                NullPointerException.class,
                () ->
                        new BluetoothQualityReport.Builder(null)
                                .setRemoteAddress(mRemoteAddress)
                                .setLmpVersion(mLmpVer)
                                .setLmpSubVersion(mLmpSubVer)
                                .setManufacturerId(mManufacturerId)
                                .setRemoteName(mRemoteName)
                                .setBluetoothClass(bluetoothClass)
                                .build());
    }

    @Test
    public void invalidRawData() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        for (int id : BQRParameters.QualityReportId) {
            bqrp.setQualityReportId((byte) id);
            assertThat(bqrp.getQualityReportId()).isEqualTo(id);

            byte[] rawData = {0};

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    rawData = ByteBuffer.allocate(BQRParameters.mBqrCommonSize - 1).array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsLstoSize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsA2dpChoppySize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsScoChoppySize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsScoChoppySize
                                                    - 1)
                                    .array();
                    break;
            }

            final byte[] data = rawData;

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new BluetoothQualityReport.Builder(data)
                                    .setRemoteAddress(mRemoteAddress)
                                    .setLmpVersion(mLmpVer)
                                    .setLmpSubVersion(mLmpSubVer)
                                    .setManufacturerId(mManufacturerId)
                                    .setRemoteName(mRemoteName)
                                    .setBluetoothClass(bluetoothClass)
                                    .build());
        }
    }

    @Test
    public void readWriteBqrParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        for (int id : BQRParameters.QualityReportId) {
            if ((id == BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR)
                    || (id == BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS)) {
                continue;
            }
            bqrp.setQualityReportId((byte) id);
            assertThat(bqrp.getQualityReportId()).isEqualTo(id);

            BluetoothQualityReport bqr =
                    initBqrCommon(
                            bqrp,
                            mRemoteAddress,
                            mLmpVer,
                            mLmpSubVer,
                            mManufacturerId,
                            mRemoteName,
                            mRemoteCoD);

            Parcel parcel = Parcel.obtain();
            bqr.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BluetoothQualityReport bqrFromParcel =
                    BluetoothQualityReport.CREATOR.createFromParcel(parcel);

            assertBqrCommon(bqrp, bqrFromParcel);

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    assertBqrApproachLsto(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    assertBqrA2dpChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    assertBqrScoChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    assertBqrConnectFail(bqrp, bqr);
                    break;
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void readWriteBqrParcelV6Flag() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        for (int id : BQRParameters.QualityReportId) {
            bqrp.setQualityReportId((byte) id);
            assertThat(bqrp.getQualityReportId()).isEqualTo(id);

            BluetoothQualityReport bqr =
                    initBqrCommon(
                            bqrp,
                            mRemoteAddress,
                            mLmpVer,
                            mLmpSubVer,
                            mManufacturerId,
                            mRemoteName,
                            mRemoteCoD);

            Parcel parcel = Parcel.obtain();
            bqr.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BluetoothQualityReport bqrFromParcel =
                    BluetoothQualityReport.CREATOR.createFromParcel(parcel);

            assertBqrV6Common(bqrp, bqrFromParcel);

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    assertBqrApproachLsto(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    assertBqrA2dpChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    assertBqrScoChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    assertBqrConnectFail(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR:
                    assertBqrEnergyMonitor(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS:
                    assertBqrRfStats(bqrp, bqr);
                    break;
            }
        }
    }

    @Test
    public void readWriteBqrCommonParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Quality monitor");

        Parcel parcel = Parcel.obtain();
        bqr.getBqrCommon().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrCommon bqrCommonFromParcel = BqrCommon.CREATOR.createFromParcel(parcel);

        // BQR Common
        assertThat(bqrCommonFromParcel).isNotNull();
        assertThat(bqrCommonFromParcel.getPacketType()).isEqualTo(bqrp.mPacketType);
        assertThat(BqrCommon.packetTypeToString(bqrCommonFromParcel.getPacketType()))
                .isEqualTo("TYPE_NULL");
        assertThat(bqrCommonFromParcel.getConnectionHandle()).isEqualTo(bqrp.mConnectionHandle);
        assertThat(BqrCommon.connectionRoleToString(bqrCommonFromParcel.getConnectionRole()))
                .isEqualTo(bqrp.mConnectionRoleCentral);
        assertThat(bqrCommonFromParcel.getTxPowerLevel()).isEqualTo(bqrp.mTxPowerLevel);
        assertThat(bqrCommonFromParcel.getRssi()).isEqualTo(bqrp.mRssi);
        assertThat(bqrCommonFromParcel.getSnr()).isEqualTo(bqrp.mSnr);
        assertThat(bqrCommonFromParcel.getUnusedAfhChannelCount())
                .isEqualTo(bqrp.mUnusedAfhChannelCount);
        assertThat(bqrCommonFromParcel.getAfhSelectUnidealChannelCount())
                .isEqualTo(bqrp.mAfhSelectUnidealChannelCount);
        assertThat(bqrCommonFromParcel.getLsto()).isEqualTo(bqrp.mLsto);
        assertThat(bqrCommonFromParcel.getPiconetClock()).isEqualTo(bqrp.mPiconetClock);
        assertThat(bqrCommonFromParcel.getRetransmissionCount())
                .isEqualTo(bqrp.mRetransmissionCount);
        assertThat(bqrCommonFromParcel.getNoRxCount()).isEqualTo(bqrp.mNoRxCount);
        assertThat(bqrCommonFromParcel.getNakCount()).isEqualTo(bqrp.mNakCount);
        assertThat(bqrCommonFromParcel.getLastTxAckTimestamp()).isEqualTo(bqrp.mLastTxAckTimestamp);
        assertThat(bqrCommonFromParcel.getFlowOffCount()).isEqualTo(bqrp.mFlowOffCount);
        assertThat(bqrCommonFromParcel.getLastFlowOnTimestamp())
                .isEqualTo(bqrp.mLastFlowOnTimestamp);
        assertThat(bqrCommonFromParcel.getOverflowCount()).isEqualTo(bqrp.mOverflowCount);
        assertThat(bqrCommonFromParcel.getUnderflowCount()).isEqualTo(bqrp.mUnderflowCount);
    }

    @Test
    public void readWriteBqrVsApproachLstoParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Approaching LSTO");

        BqrVsLsto bqrVsLsto = (BqrVsLsto) bqr.getBqrEvent();
        assertThat(bqrVsLsto).isNotNull();
        Parcel parcel = Parcel.obtain();
        bqrVsLsto.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsLsto bqrVsLstoFromParcel = BqrVsLsto.CREATOR.createFromParcel(parcel);

        // BQR VS LSTO
        assertThat(bqrVsLstoFromParcel).isNotNull();
        assertThat(bqrVsLstoFromParcel.getConnState()).isEqualTo(bqrp.mConnState & 0xFF);
        assertThat(BqrVsLsto.connStateToString(bqrVsLstoFromParcel.getConnState()))
                .isEqualTo("CONN_UNPARK_ACTIVE");
        assertThat(bqrVsLstoFromParcel.getBasebandStats()).isEqualTo(bqrp.mBasebandStats);
        assertThat(bqrVsLstoFromParcel.getSlotsUsed()).isEqualTo(bqrp.mSlotsUsed);
        assertThat(bqrVsLstoFromParcel.getCxmDenials()).isEqualTo(bqrp.mCxmDenials);
        assertThat(bqrVsLstoFromParcel.getTxSkipped()).isEqualTo(bqrp.mTxSkipped);
        assertThat(bqrVsLstoFromParcel.getRfLoss()).isEqualTo(bqrp.mRfLoss);
        assertThat(bqrVsLstoFromParcel.getNativeClock()).isEqualTo(bqrp.mNativeClock);
        assertThat(bqrVsLstoFromParcel.getLastTxAckTimestamp())
                .isEqualTo(bqrp.mLastTxAckTimestampLsto);
        assertThat(bqrVsLstoFromParcel.describeContents()).isEqualTo(0);
    }

    @Test
    public void readWriteBqrVsA2dpChoppyParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);
        assertThat(bqrp.getQualityReportId())
                .isEqualTo(BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("A2DP choppy");

        BqrVsA2dpChoppy bqrVsA2dpChoppy = (BqrVsA2dpChoppy) bqr.getBqrEvent();
        assertThat(bqrVsA2dpChoppy).isNotNull();
        Parcel parcel = Parcel.obtain();
        bqrVsA2dpChoppy.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsA2dpChoppy bqrVsA2dpChoppyFromParcel =
                BqrVsA2dpChoppy.CREATOR.createFromParcel(parcel);

        // BQR VS A2DP Choppy
        assertThat(bqrVsA2dpChoppyFromParcel).isNotNull();
        assertThat(bqrVsA2dpChoppyFromParcel.getArrivalTime()).isEqualTo(bqrp.mArrivalTime);
        assertThat(bqrVsA2dpChoppyFromParcel.getScheduleTime()).isEqualTo(bqrp.mScheduleTime);
        assertThat(bqrVsA2dpChoppyFromParcel.getGlitchCount()).isEqualTo(bqrp.mGlitchCountA2dp);
        assertThat(bqrVsA2dpChoppyFromParcel.getTxCxmDenials()).isEqualTo(bqrp.mTxCxmDenialsA2dp);
        assertThat(bqrVsA2dpChoppyFromParcel.getRxCxmDenials()).isEqualTo(bqrp.mRxCxmDenialsA2dp);
        assertThat(bqrVsA2dpChoppyFromParcel.getAclTxQueueLength())
                .isEqualTo(bqrp.mAclTxQueueLength);
        assertThat(bqrVsA2dpChoppyFromParcel.getLinkQuality()).isEqualTo(bqrp.mLinkQuality);
        assertThat(BqrVsA2dpChoppy.linkQualityToString(bqrVsA2dpChoppyFromParcel.getLinkQuality()))
                .isEqualTo("MEDIUM");
        assertThat(bqrVsA2dpChoppyFromParcel.describeContents()).isEqualTo(0);
    }

    @Test
    public void readWriteBqrVsScoChoppyParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);
        assertThat(BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY)
                .isEqualTo(bqrp.getQualityReportId());

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("SCO choppy");

        BqrVsScoChoppy bqrVsScoChoppy = (BqrVsScoChoppy) bqr.getBqrEvent();
        assertThat(bqrVsScoChoppy).isNotNull();
        Parcel parcel = Parcel.obtain();
        bqrVsScoChoppy.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsScoChoppy bqrVsScoChoppyFromParcel = BqrVsScoChoppy.CREATOR.createFromParcel(parcel);

        // BQR VS SCO Choppy
        assertThat(bqrVsScoChoppyFromParcel).isNotNull();
        assertThat(bqrVsScoChoppyFromParcel.getGlitchCount()).isEqualTo(bqrp.mGlitchCountSco);
        assertThat(bqrVsScoChoppyFromParcel.getIntervalEsco()).isEqualTo(bqrp.mIntervalEsco);
        assertThat(bqrVsScoChoppyFromParcel.getWindowEsco()).isEqualTo(bqrp.mWindowEsco);
        assertThat(bqrVsScoChoppyFromParcel.getAirFormat()).isEqualTo(bqrp.mAirFormat);
        assertThat(BqrVsScoChoppy.airFormatToString(bqrVsScoChoppyFromParcel.getAirFormat()))
                .isEqualTo("CVSD");
        assertThat(bqrVsScoChoppyFromParcel.getInstanceCount()).isEqualTo(bqrp.mInstanceCount);
        assertThat(bqrVsScoChoppyFromParcel.getTxCxmDenials()).isEqualTo(bqrp.mTxCxmDenialsSco);
        assertThat(bqrVsScoChoppyFromParcel.getRxCxmDenials()).isEqualTo(bqrp.mRxCxmDenialsSco);
        assertThat(bqrVsScoChoppyFromParcel.getTxAbortCount()).isEqualTo(bqrp.mTxAbortCount);
        assertThat(bqrVsScoChoppyFromParcel.getLateDispatch()).isEqualTo(bqrp.mLateDispatch);
        assertThat(bqrVsScoChoppyFromParcel.getMicIntrMiss()).isEqualTo(bqrp.mMicIntrMiss);
        assertThat(bqrVsScoChoppyFromParcel.getLpaIntrMiss()).isEqualTo(bqrp.mLpaIntrMiss);
        assertThat(bqrVsScoChoppyFromParcel.getSprIntrMiss()).isEqualTo(bqrp.mSprIntrMiss);
        assertThat(bqrVsScoChoppyFromParcel.getPlcFillCount()).isEqualTo(bqrp.mPlcFillCount);
        assertThat(bqrVsScoChoppyFromParcel.getPlcDiscardCount()).isEqualTo(bqrp.mPlcDiscardCount);

        assertThat(bqrVsScoChoppyFromParcel.getMissedInstanceCount())
                .isEqualTo(bqrp.mMissedInstanceCount);
        assertThat(bqrVsScoChoppyFromParcel.getTxRetransmitSlotCount())
                .isEqualTo(bqrp.mTxRetransmitSlotCount);
        assertThat(bqrVsScoChoppyFromParcel.getRxRetransmitSlotCount())
                .isEqualTo(bqrp.mRxRetransmitSlotCount);
        assertThat(bqrVsScoChoppyFromParcel.getGoodRxFrameCount())
                .isEqualTo(bqrp.mGoodRxFrameCount);
        assertThat(bqrVsScoChoppyFromParcel.describeContents()).isEqualTo(0);
    }

    @Test
    public void readWriteBqrConnectFailParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);
        assertThat(BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL)
                .isEqualTo(bqrp.getQualityReportId());

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertThat(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Connect fail");

        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        assertThat(bqrConnectFail).isNotNull();
        Parcel parcel = Parcel.obtain();
        bqrConnectFail.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrConnectFail bqrConnFailFromParcel = BqrConnectFail.CREATOR.createFromParcel(parcel);

        // BQR VS Connect Fail
        assertThat(bqrConnFailFromParcel).isNotNull();
        assertThat(bqrConnFailFromParcel.getFailReason()).isEqualTo(bqrp.mFailReason);
        assertThat(bqrConnFailFromParcel.describeContents()).isEqualTo(0);
    }

    /**
     * Get the test object of BluetoothQualityReport based on given Quality Report Id.
     *
     * @param qualityReportId Quality Report Id
     * @return Bluetooth Quality Report object
     */
    public static BluetoothQualityReport getBqr(int qualityReportId) {
        BQRParameters bqrp = BQRParameters.getInstance();
        assertThat(bqrp).isNotNull();

        bqrp.setQualityReportId((byte) qualityReportId);
        assertThat(bqrp.getQualityReportId()).isEqualTo(qualityReportId);
        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress(mRemoteAddress)
                        .setLmpVersion(mLmpVer)
                        .setLmpSubVersion(mLmpSubVer)
                        .setManufacturerId(mManufacturerId)
                        .setRemoteName(mRemoteName)
                        .setBluetoothClass(bluetoothClass)
                        .build();
        return bqr;
    }

    private static final class BQRParameters {
        private static BQRParameters INSTANCE;
        private static String TAG = "BQRParameters";

        public static int mBqrCommonSize = 85;
        public static int mBqrVsLstoSize = 23;
        public static int mBqrVsA2dpChoppySize = 16;
        public static int mBqrVsScoChoppySize = 33;
        public static int mBqrConnectFailSize = 1;
        public static int mBqrEnergyMonitorSize = 81;
        public static int mBqrRfStatsSize = 82;

        // BQR Common
        public byte mQualityReportId = 1;
        public byte mPacketType = 2;
        public short mConnectionHandle = 3;
        public byte mConnectionRole = 0; // Central
        public String mConnectionRoleCentral = "Central";
        public byte mTxPowerLevel = 5;
        public byte mRssi = 6;
        public byte mSnr = 7;
        public byte mUnusedAfhChannelCount = 8;
        public byte mAfhSelectUnidealChannelCount = 9;
        public short mLsto = 10;
        public int mPiconetClock = 11;
        public int mRetransmissionCount = 12;
        public int mNoRxCount = 13;
        public int mNakCount = 14;
        public int mLastTxAckTimestamp = 15;
        public int mFlowOffCount = 16;
        public int mLastFlowOnTimestamp = 17;
        public int mOverflowCount = 18;
        public int mUnderflowCount = 19;
        public String mAddressStr = "01:02:03:04:05:06";
        public byte[] mAddress = {6, 5, 4, 3, 2, 1};
        public byte mCalFailedItemCount = 50;

        public int mTxTotalPackets = 20;
        public int mTxUnackPackets = 21;
        public int mTxFlushPackets = 22;
        public int mTxLastSubeventPackets = 23;
        public int mCrcErrorPackets = 24;
        public int mRxDupPackets = 25;
        public int mRxUnRecvPackets = 26;
        public short mCoexInfoMask = 1;

        // BQR VS LSTO
        public byte mConnState = (byte) 0x89;
        public int mBasebandStats = 21;
        public int mSlotsUsed = 22;
        public short mCxmDenials = 23;
        public short mTxSkipped = 24;
        public short mRfLoss = 25;
        public int mNativeClock = 26;
        public int mLastTxAckTimestampLsto = 27;

        // BQR VS A2DP Choppy
        public int mArrivalTime = 28;
        public int mScheduleTime = 29;
        public short mGlitchCountA2dp = 30;
        public short mTxCxmDenialsA2dp = 31;
        public short mRxCxmDenialsA2dp = 32;
        public byte mAclTxQueueLength = 33;
        public byte mLinkQuality = 3;

        // BQR VS SCO Choppy
        public short mGlitchCountSco = 35;
        public byte mIntervalEsco = 36;
        public byte mWindowEsco = 37;
        public byte mAirFormat = 2;
        public short mInstanceCount = 39;
        public short mTxCxmDenialsSco = 40;
        public short mRxCxmDenialsSco = 41;
        public short mTxAbortCount = 42;
        public short mLateDispatch = 43;
        public short mMicIntrMiss = 44;
        public short mLpaIntrMiss = 45;
        public short mSprIntrMiss = 46;
        public short mPlcFillCount = 47;
        public short mPlcDiscardCount = 51;
        public short mMissedInstanceCount = 52;
        public short mTxRetransmitSlotCount = 53;
        public short mRxRetransmitSlotCount = 54;
        public short mGoodRxFrameCount = 55;

        // BQR VS Connect Fail
        public byte mFailReason = 0x3a;

        // BQR Energy Monitor
        public short mAvgCurrentConsume = 56;
        public int mIdleTotalTime = 57;
        public int mIdleStateEnterCount = 58;
        public int mActiveTotalTime = 59;
        public int mActiveStateEnterCount = 60;
        public int mBredrTxTotalTime = 61;
        public int mBredrTxStateEnterCount = 62;
        public byte mBredrTxAvgPowerLevel = 63;
        public int mBredrRxTotalTime = 64;
        public int mBredrRxStateEnterCount = 65;
        public int mLeTxTotalTime = 66;
        public int mLeTxStateEnterCount = 67;
        public byte mLeTxAvgPowerLevel = 68;
        public int mLeRxTotalTime = 69;
        public int mLeRxStateEnterCount = 70;
        public int mReportTotalTime = 71;
        public int mRxActiveOneChainTime = 72;
        public int mRxActiveTwoChainTime = 73;
        public int mTxiPaActiveOneChainTime = 74;
        public int mTxiPaActiveTwoChainTime = 75;
        public int mTxePaActiveOneChainTime = 76;
        public int mTxePaActiveTwoChainTime = 77;

        // BQR Rf Stats
        public byte mExtensionInfo = 78;
        public int mReportTimePeriod = 79;
        public int mTxPoweriPaBf = 80;
        public int mTxPowerePaBf = 81;
        public int mTxPoweriPaDiv = 82;
        public int mTxPowerePaDiv = 83;
        public int mRssiChainOver50 = 84;
        public int mRssiChain50To55 = 85;
        public int mRssiChain55To60 = 86;
        public int mRssiChain60To65 = 87;
        public int mRssiChain65To70 = 88;
        public int mRssiChain70To75 = 89;
        public int mRssiChain75To80 = 90;
        public int mRssiChain80To85 = 91;
        public int mRssiChain85To90 = 92;
        public int mRssiChainUnder90 = 93;
        public int mRssiDeltaUnder2 = 94;
        public int mRssiDelta2To5 = 95;
        public int mRssiDelta5To8 = 96;
        public int mRssiDelta8To11 = 97;
        public int mRssiDeltaOver11 = 98;

        public static int[] QualityReportId = {
            BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR,
            BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO,
            BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY,
            BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY,
            BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL,
            BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR,
            BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS,
        };

        private BQRParameters() {}

        public static BQRParameters getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new BQRParameters();
            }
            return INSTANCE;
        }

        public void setQualityReportId(byte id) {
            mQualityReportId = id;
        }

        public int getQualityReportId() {
            return (int) mQualityReportId;
        }

        public byte[] getByteArray() {
            ByteBuffer ba;
            ByteBuffer addrBuff = ByteBuffer.wrap(mAddress, 0, mAddress.length);

            switch ((int) mQualityReportId) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    ba = ByteBuffer.allocate(mBqrCommonSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsLstoSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsA2dpChoppySize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsScoChoppySize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrConnectFailSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR:
                    ba = ByteBuffer.allocate(1 + mBqrEnergyMonitorSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS:
                    ba = ByteBuffer.allocate(1 + mBqrRfStatsSize);
                    break;
                default:
                    ba = ByteBuffer.allocate(mBqrCommonSize);
                    break;
            }

            ba.order(ByteOrder.LITTLE_ENDIAN);

            ba.put(mQualityReportId);

            if (mQualityReportId
                    == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR) {
                ba.putShort(mAvgCurrentConsume);
                ba.putInt(mIdleTotalTime);
                ba.putInt(mIdleStateEnterCount);
                ba.putInt(mActiveTotalTime);
                ba.putInt(mActiveStateEnterCount);
                ba.putInt(mBredrTxTotalTime);
                ba.putInt(mBredrTxStateEnterCount);
                ba.put(mBredrTxAvgPowerLevel);
                ba.putInt(mBredrRxTotalTime);
                ba.putInt(mBredrRxStateEnterCount);
                ba.putInt(mLeTxTotalTime);
                ba.putInt(mLeTxStateEnterCount);
                ba.put(mLeTxAvgPowerLevel);
                ba.putInt(mLeRxTotalTime);
                ba.putInt(mLeRxStateEnterCount);
                ba.putInt(mReportTotalTime);
                ba.putInt(mRxActiveOneChainTime);
                ba.putInt(mRxActiveTwoChainTime);
                ba.putInt(mTxiPaActiveOneChainTime);
                ba.putInt(mTxiPaActiveTwoChainTime);
                ba.putInt(mTxePaActiveOneChainTime);
                ba.putInt(mTxePaActiveTwoChainTime);
            } else if (mQualityReportId
                    == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS) {
                ba.put(mExtensionInfo);
                ba.putInt(mReportTimePeriod);
                ba.putInt(mTxPoweriPaBf);
                ba.putInt(mTxPowerePaBf);
                ba.putInt(mTxPoweriPaDiv);
                ba.putInt(mTxPowerePaDiv);
                ba.putInt(mRssiChainOver50);
                ba.putInt(mRssiChain50To55);
                ba.putInt(mRssiChain55To60);
                ba.putInt(mRssiChain60To65);
                ba.putInt(mRssiChain65To70);
                ba.putInt(mRssiChain70To75);
                ba.putInt(mRssiChain75To80);
                ba.putInt(mRssiChain80To85);
                ba.putInt(mRssiChain85To90);
                ba.putInt(mRssiChainUnder90);
                ba.putInt(mRssiDeltaUnder2);
                ba.putInt(mRssiDelta2To5);
                ba.putInt(mRssiDelta5To8);
                ba.putInt(mRssiDelta8To11);
                ba.putInt(mRssiDeltaOver11);
            } else {
                ba.put(mPacketType);
                ba.putShort(mConnectionHandle);
                ba.put(mConnectionRole);
                ba.put(mTxPowerLevel);
                ba.put(mRssi);
                ba.put(mSnr);
                ba.put(mUnusedAfhChannelCount);
                ba.put(mAfhSelectUnidealChannelCount);
                ba.putShort(mLsto);
                ba.putInt(mPiconetClock);
                ba.putInt(mRetransmissionCount);
                ba.putInt(mNoRxCount);
                ba.putInt(mNakCount);
                ba.putInt(mLastTxAckTimestamp);
                ba.putInt(mFlowOffCount);
                ba.putInt(mLastFlowOnTimestamp);
                ba.putInt(mOverflowCount);
                ba.putInt(mUnderflowCount);
                ba.put(addrBuff);
                ba.put(mCalFailedItemCount);
                ba.putInt(mTxTotalPackets);
                ba.putInt(mTxUnackPackets);
                ba.putInt(mTxFlushPackets);
                ba.putInt(mTxLastSubeventPackets);
                ba.putInt(mCrcErrorPackets);
                ba.putInt(mRxDupPackets);
                ba.putInt(mRxUnRecvPackets);
                ba.putShort(mCoexInfoMask);

                if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO) {
                    ba.put(mConnState);
                    ba.putInt(mBasebandStats);
                    ba.putInt(mSlotsUsed);
                    ba.putShort(mCxmDenials);
                    ba.putShort(mTxSkipped);
                    ba.putShort(mRfLoss);
                    ba.putInt(mNativeClock);
                    ba.putInt(mLastTxAckTimestampLsto);
                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY) {
                    ba.putInt(mArrivalTime);
                    ba.putInt(mScheduleTime);
                    ba.putShort(mGlitchCountA2dp);
                    ba.putShort(mTxCxmDenialsA2dp);
                    ba.putShort(mRxCxmDenialsA2dp);
                    ba.put(mAclTxQueueLength);
                    ba.put(mLinkQuality);
                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY) {
                    ba.putShort(mGlitchCountSco);
                    ba.put(mIntervalEsco);
                    ba.put(mWindowEsco);
                    ba.put(mAirFormat);
                    ba.putShort(mInstanceCount);
                    ba.putShort(mTxCxmDenialsSco);
                    ba.putShort(mRxCxmDenialsSco);
                    ba.putShort(mTxAbortCount);
                    ba.putShort(mLateDispatch);
                    ba.putShort(mMicIntrMiss);
                    ba.putShort(mLpaIntrMiss);
                    ba.putShort(mSprIntrMiss);
                    ba.putShort(mPlcFillCount);
                    ba.putShort(mPlcDiscardCount);
                    ba.putShort(mMissedInstanceCount);
                    ba.putShort(mTxRetransmitSlotCount);
                    ba.putShort(mRxRetransmitSlotCount);
                    ba.putShort(mGoodRxFrameCount);

                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL) {
                    ba.put(mFailReason);
                }
            }
            return ba.array();
        }
    }
}
