/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.BW_STATS_COUNT_THRESHOLD;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_CARRIER_CONFIG_LINK_BANDWIDTHS_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_DEFAULT_NETWORK_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_MODEM_ACTIVITY_RETURNED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_NR_FREQUENCY_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_SCREEN_STATE_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_SIGNAL_STRENGTH_CHANGED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.telephony.CellIdentityLte;
import android.telephony.ModemActivityInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Pair;

import com.android.internal.telephony.TelephonyFacade;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class LinkBandwidthEstimatorTest extends TelephonyTest {
    private LinkBandwidthEstimator mLBE;
    private static final int [] TX_TIME_1_MS = new int[]{0, 0, 0, 0, 0};
    private static final int [] TX_TIME_2_MS = new int[]{100, 0, 0, 0, 100};
    private static final int RX_TIME_1_MS = 100;
    private static final int RX_TIME_2_MS = 200;
    private static final ModemActivityInfo MAI_INIT =
            new ModemActivityInfo(0, 0, 0, TX_TIME_1_MS, RX_TIME_1_MS);
    private static final ModemActivityInfo MAI_TX_RX_TIME_HIGH =
            new ModemActivityInfo(100L, 0, 0, TX_TIME_2_MS, RX_TIME_2_MS);
    private static final ModemActivityInfo MAI_RX_TIME_HIGH =
            new ModemActivityInfo(100L, 0, 0, TX_TIME_1_MS, RX_TIME_2_MS);
    private NetworkCapabilities mNetworkCapabilities;
    private CellIdentityLte mCellIdentity;
    private Pair<Integer, Integer> mDefaultBwKbps;
    private long mElapsedTimeMs = 0;
    private long mTxBytes = 0;
    private long mRxBytes = 0;
    @Mock
    TelephonyFacade mTelephonyFacade;
    @Mock
    DataConnection mDataConnection;
    NetworkRegistrationInfo mNri;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();

        mCellIdentity = new CellIdentityLte(310, 260, 1234, 123456, 366);
        mDefaultBwKbps = new Pair<>(30_000, 15_000);
        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        when(mTelephonyFacade.getElapsedSinceBootMillis()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        when(mDcTracker.getDataConnectionByApnType(anyString())).thenReturn(mDataConnection);
        when(mDcTracker.getLinkBandwidthsFromCarrierConfig(anyString())).thenReturn(mDefaultBwKbps);
        when(mSignalStrength.getDbm()).thenReturn(-100);
        mLBE = new LinkBandwidthEstimator(mPhone, mTelephonyFacade);
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();
    }

    private void addElapsedTime(long timeMs) {
        mElapsedTimeMs += timeMs;
        when(mTelephonyFacade.getElapsedSinceBootMillis()).thenReturn(mElapsedTimeMs);
    }

    private void addTxBytes(long txBytes) {
        mTxBytes += txBytes;
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(mTxBytes);
    }

    private void addRxBytes(long rxBytes) {
        mRxBytes += rxBytes;
        when(mTelephonyFacade.getMobileRxBytes()).thenReturn(mRxBytes);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testScreenOnTxTrafficHighOneModemPoll() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(500_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();

        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnTxRxTrafficHighTwoModemPoll() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(20_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_INIT).sendToTarget();
        processAllMessages();

        addTxBytes(100_000L);
        addRxBytes(200_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_TX_RX_TIME_HIGH).sendToTarget();
        processAllMessages();

        verify(mTelephonyManager, times(2)).requestModemActivityInfo(any(), any());
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
    }

    @Test
    public void testScreenOnRxTrafficHighTwoModemPollRxTimeHigh() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(20_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_INIT).sendToTarget();
        processAllMessages();

        addTxBytes(100_000L);
        addRxBytes(200_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_RX_TIME_HIGH).sendToTarget();
        processAllMessages();

        verify(mTelephonyManager, times(2)).requestModemActivityInfo(any(), any());
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
    }

    @Test
    public void testScreenOnTxRxTrafficLow() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, never()).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnTrafficLowSampleHighAcc() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        for (int i = 0; i < 30; i++) {
            addTxBytes(10_000L);
            addRxBytes(19_000L);
            addElapsedTime(1_100);
            moveTimeForward(1_100);
            processAllMessages();
        }
        verify(mTelephonyManager, times(4)).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnDefaultNetworkToggleNoExtraTrafficPoll() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, null).sendToTarget();
        addElapsedTime(500);
        moveTimeForward(500);
        processAllMessages();
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        for (int i = 0; i < 3; i++) {
            addElapsedTime(1_100);
            moveTimeForward(1_100);
            processAllMessages();
        }

        verify(mTelephonyFacade, times(4)).getMobileTxBytes();
    }

    @Test
    public void testRatChangeTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(19_000L);
        addElapsedTime(2000);
        moveTimeForward(2000);
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(19_000L);
        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        mDefaultBwKbps = new Pair<>(300_000, 150_000);
        when(mDcTracker.getLinkBandwidthsFromCarrierConfig(anyString())).thenReturn(mDefaultBwKbps);
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verify(mTelephonyManager, times(0)).requestModemActivityInfo(any(), any());
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(150_000), eq(300_000));
    }

    @Test
    public void testSignalLevelChangeTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(300_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(17_274));

        addTxBytes(20_000L);
        addRxBytes(50_000L);
        when(mSignalStrength.getDbm()).thenReturn(-110);
        mLBE.obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, mSignalStrength).sendToTarget();
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(25_327));
    }

    @Test
    public void testCarrierConfigChangeTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(19_000L);
        addElapsedTime(2000);
        moveTimeForward(2000);
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(19_000L);
        when(mSignalStrength.getLevel()).thenReturn(2);
        mDefaultBwKbps = new Pair<>(50_000, 20_000);
        when(mDcTracker.getLinkBandwidthsFromCarrierConfig(anyString())).thenReturn(mDefaultBwKbps);
        mLBE.obtainMessage(MSG_CARRIER_CONFIG_LINK_BANDWIDTHS_CHANGED).sendToTarget();
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(20_000), eq(50_000));
    }

    @Test
    public void testSwitchToNrMmwaveTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(19_000L);
        addElapsedTime(2000);
        moveTimeForward(2000);
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(19_000L);
        when(mServiceState.getNrState()).thenReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED);
        when(mServiceState.getNrFrequencyRange()).thenReturn(ServiceState.FREQUENCY_RANGE_MMWAVE);
        when(mSignalStrength.getLevel()).thenReturn(2);
        mDefaultBwKbps = new Pair<>(500_000, 200_000);
        when(mDcTracker.getLinkBandwidthsFromCarrierConfig(anyString())).thenReturn(mDefaultBwKbps);
        mLBE.obtainMessage(MSG_NR_FREQUENCY_CHANGED).sendToTarget();
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(200_000), eq(500_000));
    }

    @Test
    public void testEnoughModemPollTriggerBwUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(300_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mTelephonyManager, times(BW_STATS_COUNT_THRESHOLD + 2))
                .requestModemActivityInfo(any(), any());
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(17_274));
    }

    @Test
    public void testUseCurrentTacStatsWithEnoughData() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(300_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        mCellIdentity = new CellIdentityLte(310, 260, 1235, 123457, 367);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        for (int i = BW_STATS_COUNT_THRESHOLD; i < 3 * BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(400_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(17_300));
    }

    @Test
    public void testUseAllTacStatsIfNoEnoughDataWithCurrentTac() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(300_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        mCellIdentity = new CellIdentityLte(310, 260, 1234, 123456, 367);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        for (int i = BW_STATS_COUNT_THRESHOLD; i < BW_STATS_COUNT_THRESHOLD * 3 / 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(400_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(17_300));
    }

    @Test
    public void testSwitchCarrierFallbackToColdStartValue() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 5; i++) {
            addTxBytes(10_000L);
            addRxBytes(300_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
        verify(mDataConnection, times(1)).updateLinkBandwidthEstimation(eq(15_000), eq(17_274));

        mCellIdentity = new CellIdentityLte(320, 265, 1234, 123456, 366);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);

        addTxBytes(10_000L);
        addRxBytes(10_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        verify(mDataConnection, times(2)).updateLinkBandwidthEstimation(eq(15_000), eq(30_000));
    }
}