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

package android.location.cts.none;

import static android.location.flags.Flags.FLAG_GNSS_ASSISTANCE_INTERFACE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.AuxiliaryInformation;
import android.location.BeidouAssistance;
import android.location.BeidouSatelliteEphemeris;
import android.location.BeidouSatelliteEphemeris.BeidouSatelliteClockModel;
import android.location.BeidouSatelliteEphemeris.BeidouSatelliteEphemerisTime;
import android.location.BeidouSatelliteEphemeris.BeidouSatelliteHealth;
import android.location.GalileoAssistance;
import android.location.GalileoIonosphericModel;
import android.location.GalileoSatelliteEphemeris;
import android.location.GalileoSatelliteEphemeris.GalileoSatelliteClockModel;
import android.location.GalileoSatelliteEphemeris.GalileoSvHealth;
import android.location.GlonassAlmanac;
import android.location.GlonassAlmanac.GlonassSatelliteAlmanac;
import android.location.GlonassAssistance;
import android.location.GlonassSatelliteEphemeris;
import android.location.GlonassSatelliteEphemeris.GlonassSatelliteClockModel;
import android.location.GlonassSatelliteEphemeris.GlonassSatelliteOrbitModel;
import android.location.GnssAlmanac;
import android.location.GnssAlmanac.GnssSatelliteAlmanac;
import android.location.GnssAssistance;
import android.location.GnssAssistance.GnssSatelliteCorrections;
import android.location.GnssCorrectionComponent;
import android.location.GnssCorrectionComponent.GnssInterval;
import android.location.GnssCorrectionComponent.PseudorangeCorrection;
import android.location.GnssSignalType;
import android.location.GnssStatus;
import android.location.GpsAssistance;
import android.location.GpsSatelliteEphemeris;
import android.location.GpsSatelliteEphemeris.GpsL2Params;
import android.location.GpsSatelliteEphemeris.GpsSatelliteClockModel;
import android.location.GpsSatelliteEphemeris.GpsSatelliteHealth;
import android.location.IonosphericCorrection;
import android.location.KeplerianOrbitModel;
import android.location.KeplerianOrbitModel.SecondOrderHarmonicPerturbation;
import android.location.KlobucharIonosphericModel;
import android.location.LeapSecondsModel;
import android.location.QzssAssistance;
import android.location.QzssSatelliteEphemeris;
import android.location.RealTimeIntegrityModel;
import android.location.SatelliteEphemerisTime;
import android.location.TimeModel;
import android.location.UtcModel;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_GNSS_ASSISTANCE_INTERFACE)
public class GnssAssistanceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testDescribeContents() {
        GnssAssistance gnssAssistance = getTestGnssAssistance();
        assertEquals(0, gnssAssistance.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        GnssAssistance gnssAssistance = getTestGnssAssistance();
        Parcel parcel = Parcel.obtain();
        gnssAssistance.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssAssistance newGnssAssistance = GnssAssistance.CREATOR.createFromParcel(parcel);
        assertTrue(verifyTestGpsAssistance(newGnssAssistance.getGpsAssistance()));
        assertTrue(verifyTestGlonassAssistance(newGnssAssistance.getGlonassAssistance()));
        assertTrue(verifyTestGalileoAssistance(newGnssAssistance.getGalileoAssistance()));
        assertTrue(verifyTestBeidouAssistance(newGnssAssistance.getBeidouAssistance()));
        assertTrue(verifyTestQzssAssistance(newGnssAssistance.getQzssAssistance()));
        parcel.recycle();
    }

    private void assertEqualsWithDelta(final double expected, final double actual) {
        final double delta = 10 * Math.ulp(expected);
        assertEquals(expected, actual, delta);
    }

    private GnssAssistance getTestGnssAssistance() {
        return new GnssAssistance.Builder()
                .setGpsAssistance(getTestGpsAssistance())
                .setGlonassAssistance(getTestGlonassAssistance())
                .setGalileoAssistance(getTestGalileoAssistance())
                .setBeidouAssistance(getTestBeidouAssistance())
                .setQzssAssistance(getTestQzssAssistance())
                .build();
    }

    private boolean verifyTestGpsAssistance(GpsAssistance gpsAssistance) {
        // verify almanac
        GnssAlmanac almanac = gpsAssistance.getAlmanac();
        assertEquals(958, almanac.getWeekNumber());
        assertEquals(503808, almanac.getToaSeconds());
        List<GnssSatelliteAlmanac> satelliteAlmanacList = almanac.getGnssSatelliteAlmanacs();
        GnssSatelliteAlmanac satelliteAlmanac1 = satelliteAlmanacList.get(0);
        GnssSatelliteAlmanac satelliteAlmanac2 = satelliteAlmanacList.get(1);
        assertEquals(1, satelliteAlmanac1.getSvid());
        assertEquals(0, satelliteAlmanac1.getSvHealth());
        assertEqualsWithDelta(7.23e-3, satelliteAlmanac1.getEccentricity());
        assertEqualsWithDelta(8.79e-3, satelliteAlmanac1.getInclination());
        assertEqualsWithDelta(1.97e-1, satelliteAlmanac1.getOmega());
        assertEqualsWithDelta(-3.79e-2, satelliteAlmanac1.getOmega0());
        assertEqualsWithDelta(-2.55e-9, satelliteAlmanac1.getOmegaDot());
        assertEqualsWithDelta(5.15e3, satelliteAlmanac1.getRootA());
        assertEqualsWithDelta(6.14e-1, satelliteAlmanac1.getM0());
        assertEqualsWithDelta(-2.19e-5, satelliteAlmanac1.getAf0());
        assertEqualsWithDelta(-3.63e-12, satelliteAlmanac1.getAf1());
        assertEquals(2, satelliteAlmanac2.getSvid());
        assertEquals(0, satelliteAlmanac2.getSvHealth());
        assertEqualsWithDelta(7.22e-3, satelliteAlmanac2.getEccentricity());
        assertEqualsWithDelta(8.78e-3, satelliteAlmanac2.getInclination());
        assertEqualsWithDelta(1.96e-1, satelliteAlmanac2.getOmega());
        assertEqualsWithDelta(-3.78e-2, satelliteAlmanac2.getOmega0());
        assertEqualsWithDelta(-2.54e-9, satelliteAlmanac2.getOmegaDot());
        assertEqualsWithDelta(5.14e3, satelliteAlmanac2.getRootA());
        assertEqualsWithDelta(6.13e-1, satelliteAlmanac2.getM0());
        assertEqualsWithDelta(-2.18e-5, satelliteAlmanac2.getAf0());
        assertEqualsWithDelta(-3.62e-12, satelliteAlmanac2.getAf1());

        // verify ionospheric model
        assertTrue(verifyTestKlobucharIonosphericModel(gpsAssistance.getIonosphericModel()));
        // verify utc model
        assertTrue(verifyTestUtcModel(gpsAssistance.getUtcModel()));
        // verify leap seconds model
        assertTrue(verifyTestLeapSecondsModel(gpsAssistance.getLeapSecondsModel()));
        // verify time model list
        assertTrue(verifyTestTimeModelList(gpsAssistance.getTimeModels()));

        // verify satellite ephemeris list
        List<GpsSatelliteEphemeris> satelliteEphemerisList = gpsAssistance.getSatelliteEphemeris();
        assertEquals(1, satelliteEphemerisList.size());
        GpsSatelliteEphemeris satelliteEphemeris = satelliteEphemerisList.get(0);

        assertEquals(1, satelliteEphemeris.getSvid());

        GpsL2Params gpsL2Params = satelliteEphemeris.getGpsL2Params();
        assertEquals(0, gpsL2Params.getL2Code());
        assertEquals(0, gpsL2Params.getL2Flag());

        GpsSatelliteClockModel satelliteClockModel = satelliteEphemeris.getSatelliteClockModel();
        assertEqualsWithDelta(-8.39e-4, satelliteClockModel.getAf0());
        assertEqualsWithDelta(-1.65e-11, satelliteClockModel.getAf1());
        assertEqualsWithDelta(0.0, satelliteClockModel.getAf2());
        assertEqualsWithDelta(0.0, satelliteClockModel.getTgd());
        assertEquals(91, satelliteClockModel.getIodc());
        assertTrue(verifyTestTimeOfClockSeconds(satelliteClockModel.getTimeOfClockSeconds()));
        assertTrue(verifyTestKeplerianOrbitModel(satelliteEphemeris.getSatelliteOrbitModel()));

        GpsSatelliteHealth satelliteHealth = satelliteEphemeris.getSatelliteHealth();
        assertEquals(0, satelliteHealth.getSvHealth());
        assertEqualsWithDelta(2.0, satelliteHealth.getSvAccur());
        assertEqualsWithDelta(0.0, satelliteHealth.getFitInt());

        SatelliteEphemerisTime satelliteEphemerisTime =
                satelliteEphemeris.getSatelliteEphemerisTime();
        assertEquals(59, satelliteEphemerisTime.getIode());
        assertEquals(2290, satelliteEphemerisTime.getWeekNumber());
        assertEquals(463472, satelliteEphemerisTime.getToeSeconds());

        // verify real time integrity model list
        assertTrue(
                verifyTestRealTimeIntegrityModelList(gpsAssistance.getRealTimeIntegrityModels()));
        // verify satellite correction list
        assertTrue(verifyTestGnssCorrectionList(gpsAssistance.getSatelliteCorrections()));

        // verify auxiliary information
        assertTrue(verifyTestAuxiliaryInformation(gpsAssistance.getAuxiliaryInformation()));
        return true;
    }

    private boolean verifyTestQzssAssistance(QzssAssistance qzssAssistance) {
        // verify almanac
        GnssAlmanac almanac = qzssAssistance.getAlmanac();
        assertEquals(261, almanac.getWeekNumber());
        assertEquals(176128, almanac.getToaSeconds());
        List<GnssSatelliteAlmanac> satelliteAlmanacList = almanac.getGnssSatelliteAlmanacs();
        assertEquals(1, satelliteAlmanacList.size());
        GnssSatelliteAlmanac satelliteAlmanac = satelliteAlmanacList.get(0);
        assertEquals(194, satelliteAlmanac.getSvid());
        assertEquals(0, satelliteAlmanac.getSvHealth());
        assertEqualsWithDelta(7.428e-2, satelliteAlmanac.getEccentricity());
        assertEqualsWithDelta(0.7101072704 / Math.PI, satelliteAlmanac.getInclination());
        assertEqualsWithDelta(-1.559158521 / Math.PI, satelliteAlmanac.getOmega());
        assertEqualsWithDelta(1.029787968 / Math.PI, satelliteAlmanac.getOmega0());
        assertEqualsWithDelta(-3.120129966e-9 / Math.PI, satelliteAlmanac.getOmegaDot());
        assertEqualsWithDelta(6493.731445, satelliteAlmanac.getRootA());
        assertEqualsWithDelta(-3.041552089 / Math.PI, satelliteAlmanac.getM0());
        assertEqualsWithDelta(-3.814697266e-6, satelliteAlmanac.getAf0());
        assertEqualsWithDelta(0.0, satelliteAlmanac.getAf1());

        // verify ionospheric model
        assertTrue(verifyTestKlobucharIonosphericModel(qzssAssistance.getIonosphericModel()));
        // verify utc model
        assertTrue(verifyTestUtcModel(qzssAssistance.getUtcModel()));
        // verify leap seconds model
        assertTrue(verifyTestLeapSecondsModel(qzssAssistance.getLeapSecondsModel()));
        // verify time model list
        assertTrue(verifyTestTimeModelList(qzssAssistance.getTimeModels()));

        // verify satellite ephemeris list
        List<QzssSatelliteEphemeris> satelliteEphemerisList =
                qzssAssistance.getSatelliteEphemeris();
        assertEquals(1, satelliteEphemerisList.size());
        QzssSatelliteEphemeris satelliteEphemeris = satelliteEphemerisList.get(0);

        assertEquals(194, satelliteEphemeris.getSvid());

        GpsL2Params gpsL2Params = satelliteEphemeris.getGpsL2Params();
        assertEquals(0, gpsL2Params.getL2Code());
        assertEquals(0, gpsL2Params.getL2Flag());

        GpsSatelliteClockModel satelliteClockModel = satelliteEphemeris.getSatelliteClockModel();
        assertEqualsWithDelta(-8.39e-4, satelliteClockModel.getAf0());
        assertEqualsWithDelta(-1.65e-11, satelliteClockModel.getAf1());
        assertEqualsWithDelta(0.0, satelliteClockModel.getAf2());
        assertEqualsWithDelta(0.0, satelliteClockModel.getTgd());
        assertEquals(91, satelliteClockModel.getIodc());
        assertTrue(verifyTestTimeOfClockSeconds(satelliteClockModel.getTimeOfClockSeconds()));
        assertTrue(verifyTestKeplerianOrbitModel(satelliteEphemeris.getSatelliteOrbitModel()));

        GpsSatelliteHealth satelliteHealth = satelliteEphemeris.getSatelliteHealth();
        assertEquals(0, satelliteHealth.getSvHealth());
        assertEqualsWithDelta(2.0, satelliteHealth.getSvAccur());
        assertEqualsWithDelta(0.0, satelliteHealth.getFitInt());

        SatelliteEphemerisTime satelliteEphemerisTime =
                satelliteEphemeris.getSatelliteEphemerisTime();
        assertEquals(59, satelliteEphemerisTime.getIode());
        assertEquals(2290, satelliteEphemerisTime.getWeekNumber());
        assertEquals(463472, satelliteEphemerisTime.getToeSeconds());

        // verify real time integrity model list
        assertTrue(
                verifyTestRealTimeIntegrityModelList(qzssAssistance.getRealTimeIntegrityModels()));
        // verify satellite correction list
        assertTrue(verifyTestGnssCorrectionList(qzssAssistance.getSatelliteCorrections()));

        // verify auxiliary information
        assertTrue(verifyTestAuxiliaryInformation(qzssAssistance.getAuxiliaryInformation()));
        return true;
    }

    private boolean verifyTestGlonassAssistance(GlonassAssistance glonassAssistance) {
        // verify almanac
        GlonassAlmanac almanac = glonassAssistance.getAlmanac();
        assertEquals(1831066775042L, almanac.getIssueDateMillis());
        List<GlonassSatelliteAlmanac> satelliteAlmanacList = almanac.getSatelliteAlmanacs();
        GlonassSatelliteAlmanac satelliteAlmanac = satelliteAlmanacList.get(0);
        assertEquals(1, satelliteAlmanac.getSlotNumber());
        assertEquals(
                GlonassSatelliteEphemeris.HEALTH_STATUS_HEALTHY, satelliteAlmanac.getHealthState());
        assertEquals(1, satelliteAlmanac.getFrequencyChannelNumber());
        assertEqualsWithDelta(-1.9e-5, satelliteAlmanac.getTau());
        assertEqualsWithDelta(0.299, satelliteAlmanac.getTLambda());
        assertEqualsWithDelta(0.0, satelliteAlmanac.getLambda());
        assertEqualsWithDelta(6.42e-3, satelliteAlmanac.getDeltaI());
        assertEqualsWithDelta(-2.65e3, satelliteAlmanac.getDeltaT());
        assertEqualsWithDelta(-6.10e-4, satelliteAlmanac.getDeltaTDot());
        assertEqualsWithDelta(4.21e-4, satelliteAlmanac.getEccentricity());
        assertEqualsWithDelta(0.16, satelliteAlmanac.getOmega());
        assertEqualsWithDelta(100, satelliteAlmanac.getCalendarDayNumber());
        assertTrue(satelliteAlmanac.isGlonassM());

        // verify utc model
        assertTrue(verifyTestUtcModel(glonassAssistance.getUtcModel()));
        // verify time model list
        assertTrue(verifyTestTimeModelList(glonassAssistance.getTimeModels()));

        // verify satellite ephemeris list
        List<GlonassSatelliteEphemeris> satelliteEphemerisList =
                glonassAssistance.getSatelliteEphemeris();
        assertEquals(1, satelliteEphemerisList.size());
        GlonassSatelliteEphemeris satelliteEphemeris = satelliteEphemerisList.get(0);
        assertEquals(1, satelliteEphemeris.getSlotNumber());
        assertEquals(
                GlonassSatelliteEphemeris.HEALTH_STATUS_HEALTHY,
                satelliteEphemeris.getHealthState());
        assertEqualsWithDelta(459030.0, satelliteEphemeris.getFrameTimeSeconds());
        assertEquals(0, satelliteEphemeris.getAgeInDays());
        assertEquals(30, satelliteEphemeris.getUpdateIntervalMinutes());
        assertFalse(satelliteEphemeris.isUpdateIntervalOdd());
        assertTrue(satelliteEphemeris.isGlonassM());
        GlonassSatelliteClockModel satelliteClockModel =
                satelliteEphemeris.getSatelliteClockModel();
        assertTrue(verifyTestTimeOfClockSeconds(satelliteClockModel.getTimeOfClockSeconds()));
        assertEqualsWithDelta(-2.11e-5, satelliteClockModel.getClockBias());
        assertEqualsWithDelta(0.0, satelliteClockModel.getFrequencyBias());
        assertEquals(-1, satelliteClockModel.getFrequencyChannelNumber());
        assertTrue(satelliteClockModel.isGroupDelayDiffSecondsAvailable());
        assertEqualsWithDelta(0.0, satelliteClockModel.getGroupDelayDiffSeconds());
        GlonassSatelliteOrbitModel satelliteOrbitModel =
                satelliteEphemeris.getSatelliteOrbitModel();
        assertEqualsWithDelta(-21248.51806641, satelliteOrbitModel.getX());
        assertEqualsWithDelta(-0.7282361984253, satelliteOrbitModel.getXDot());
        assertEqualsWithDelta(1.862645149231e-9, satelliteOrbitModel.getXAccel());
        assertEqualsWithDelta(-12851.89160156, satelliteOrbitModel.getY());
        assertEqualsWithDelta(-0.3476705551147, satelliteOrbitModel.getYDot());
        assertEqualsWithDelta(-9.313225746155e-10, satelliteOrbitModel.getYAccel());
        assertEqualsWithDelta(5766.135253906, satelliteOrbitModel.getZ());
        assertEqualsWithDelta(-3.464447021484, satelliteOrbitModel.getZDot());
        assertEqualsWithDelta(9.313225746155e-10, satelliteOrbitModel.getZAccel());

        // verify real time integrity model list
        assertTrue(
                verifyTestRealTimeIntegrityModelList(
                        glonassAssistance.getRealTimeIntegrityModels()));

        // verify satellite correction list
        assertEquals(0, glonassAssistance.getSatelliteCorrections().size());

        // verify auxiliary information
        assertTrue(verifyTestAuxiliaryInformation(glonassAssistance.getAuxiliaryInformation()));
        return true;
    }

    private boolean verifyTestGalileoAssistance(GalileoAssistance galileoAssistance) {
        // verify almanac
        GnssAlmanac almanac = galileoAssistance.getAlmanac();
        assertEquals(1831066775042L, almanac.getIssueDateMillis());
        assertEquals(2, almanac.getWeekNumber());
        assertEquals(463200, almanac.getToaSeconds());
        assertEquals(4, almanac.getIoda());
        List<GnssSatelliteAlmanac> satelliteAlmanacList = almanac.getGnssSatelliteAlmanacs();
        assertEquals(1, satelliteAlmanacList.size());
        GnssSatelliteAlmanac gnssSatelliteAlmanac = satelliteAlmanacList.get(0);
        assertEquals(1, gnssSatelliteAlmanac.getSvid());
        assertEquals(0, gnssSatelliteAlmanac.getSvHealth());
        assertEqualsWithDelta(0.00035, gnssSatelliteAlmanac.getEccentricity());
        assertEqualsWithDelta(0.00726, gnssSatelliteAlmanac.getInclination());
        assertEqualsWithDelta(0.0, gnssSatelliteAlmanac.getOmega());
        assertEqualsWithDelta(0.21, gnssSatelliteAlmanac.getOmega0());
        assertEqualsWithDelta(-1.74e-9, gnssSatelliteAlmanac.getOmegaDot());
        assertEqualsWithDelta(0.0, gnssSatelliteAlmanac.getRootA());
        assertEqualsWithDelta(-0.8778, gnssSatelliteAlmanac.getM0());
        assertEqualsWithDelta(1.52e-5, gnssSatelliteAlmanac.getAf0());
        assertEqualsWithDelta(0.0, gnssSatelliteAlmanac.getAf1());

        // verify ionospheric model
        GalileoIonosphericModel galileoIonosphericModel = galileoAssistance.getIonosphericModel();
        assertEqualsWithDelta(121, galileoIonosphericModel.getAi0());
        assertEqualsWithDelta(-0.74609, galileoIonosphericModel.getAi1());
        assertEqualsWithDelta(0.0088196, galileoIonosphericModel.getAi2());
        // verify utc model
        assertTrue(verifyTestUtcModel(galileoAssistance.getUtcModel()));
        // verify leap seconds model
        assertTrue(verifyTestLeapSecondsModel(galileoAssistance.getLeapSecondsModel()));
        // verify time model list
        assertTrue(verifyTestTimeModelList(galileoAssistance.getTimeModels()));

        // verify satellite ephemeris list
        List<GalileoSatelliteEphemeris> satelliteEphemerisList =
                galileoAssistance.getSatelliteEphemeris();
        assertEquals(1, satelliteEphemerisList.size());
        GalileoSatelliteEphemeris satelliteEphemeris = satelliteEphemerisList.get(0);
        assertEquals(1, satelliteEphemeris.getSvid());
        List<GalileoSatelliteClockModel> satelliteClockModelList =
                satelliteEphemeris.getSatelliteClockModels();
        assertEquals(1, satelliteClockModelList.size());
        GalileoSatelliteClockModel satelliteClockModel = satelliteClockModelList.get(0);
        assertTrue(verifyTestTimeOfClockSeconds(satelliteClockModel.getTimeOfClockSeconds()));
        assertEqualsWithDelta(0.0032, satelliteClockModel.getAf0());
        assertEqualsWithDelta(2.278e-11, satelliteClockModel.getAf1());
        assertEqualsWithDelta(-3.469e-18, satelliteClockModel.getAf2());
        assertEqualsWithDelta(-1.490e-8, satelliteClockModel.getBgdSeconds());
        assertEqualsWithDelta(3.119, satelliteClockModel.getSisaMeters());
        assertEquals(
                GalileoSatelliteClockModel.TYPE_FNAV, satelliteClockModel.getSatelliteClockType());
        assertTrue(verifyTestKeplerianOrbitModel(satelliteEphemeris.getSatelliteOrbitModel()));
        GalileoSvHealth satelliteHealth = satelliteEphemeris.getSatelliteHealth();
        assertEquals(
                GalileoSvHealth.DATA_STATUS_DATA_VALID, satelliteHealth.getDataValidityStatusE1b());
        assertEquals(GalileoSvHealth.HEALTH_STATUS_OK, satelliteHealth.getSignalHealthStatusE1b());
        assertEquals(
                GalileoSvHealth.DATA_STATUS_DATA_VALID, satelliteHealth.getDataValidityStatusE5a());
        assertEquals(GalileoSvHealth.HEALTH_STATUS_OK, satelliteHealth.getSignalHealthStatusE5a());
        assertEquals(
                GalileoSvHealth.DATA_STATUS_DATA_VALID, satelliteHealth.getDataValidityStatusE5b());
        assertEquals(GalileoSvHealth.HEALTH_STATUS_OK, satelliteHealth.getSignalHealthStatusE5b());

        // verify real time integrity model list
        assertTrue(
                verifyTestRealTimeIntegrityModelList(
                        galileoAssistance.getRealTimeIntegrityModels()));
        // verify satellite correction list
        assertTrue(verifyTestGnssCorrectionList(galileoAssistance.getSatelliteCorrections()));

        // verify auxiliary information
        assertTrue(verifyTestAuxiliaryInformation(galileoAssistance.getAuxiliaryInformation()));
        return true;
    }

    private boolean verifyTestBeidouAssistance(BeidouAssistance beidouAssistance) {
        // verify almanac
        GnssAlmanac almanac = beidouAssistance.getAlmanac();
        assertEquals(782, almanac.getWeekNumber());
        assertEquals(345600, almanac.getToaSeconds());
        List<GnssSatelliteAlmanac> satelliteAlmanacList = almanac.getGnssSatelliteAlmanacs();
        assertEquals(1, satelliteAlmanacList.size());
        GnssSatelliteAlmanac satelliteAlmanac = satelliteAlmanacList.get(0);
        assertEquals(1, satelliteAlmanac.getSvid());
        assertEquals(0, satelliteAlmanac.getSvHealth());
        assertEqualsWithDelta(7.82e-4, satelliteAlmanac.getEccentricity());
        assertEqualsWithDelta(0.0958150411 / Math.PI, satelliteAlmanac.getInclination());
        assertEqualsWithDelta(-0.986917360 / Math.PI, satelliteAlmanac.getOmega());
        assertEqualsWithDelta(-3.02e-1 / Math.PI, satelliteAlmanac.getOmega0());
        assertEqualsWithDelta(-5.014e-10 / Math.PI, satelliteAlmanac.getOmegaDot());
        assertEqualsWithDelta(6493.494226, satelliteAlmanac.getRootA());
        assertEqualsWithDelta(3.15e-1 / Math.PI, satelliteAlmanac.getM0());
        assertEqualsWithDelta(-7.22e-4, satelliteAlmanac.getAf0());
        assertEqualsWithDelta(3.45e-11, satelliteAlmanac.getAf1());

        // verify ionospheric model
        assertTrue(verifyTestKlobucharIonosphericModel(beidouAssistance.getIonosphericModel()));
        // verify utc model
        assertTrue(verifyTestUtcModel(beidouAssistance.getUtcModel()));
        // verify leap seconds model
        assertTrue(verifyTestLeapSecondsModel(beidouAssistance.getLeapSecondsModel()));
        // verify time model list
        assertTrue(verifyTestTimeModelList(beidouAssistance.getTimeModels()));

        // verify satellite ephemeris list
        List<BeidouSatelliteEphemeris> satelliteEphemerisList =
                beidouAssistance.getSatelliteEphemeris();
        assertEquals(1, satelliteEphemerisList.size());
        BeidouSatelliteEphemeris satelliteEphemeris = satelliteEphemerisList.get(0);
        assertEquals(1, satelliteEphemeris.getSvid());
        assertTrue(verifyTestKeplerianOrbitModel(satelliteEphemeris.getSatelliteOrbitModel()));
        BeidouSatelliteClockModel satelliteClockModel = satelliteEphemeris.getSatelliteClockModel();
        assertTrue(verifyTestTimeOfClockSeconds(satelliteClockModel.getTimeOfClockSeconds()));
        assertEqualsWithDelta(0.0006494125118479, satelliteClockModel.getAf0());
        assertEqualsWithDelta(3.720579400124e-11, satelliteClockModel.getAf1());
        assertEqualsWithDelta(0.0, satelliteClockModel.getAf2());
        assertEqualsWithDelta(3.8e-9, satelliteClockModel.getTgd1());
        assertEqualsWithDelta(3.8e-9, satelliteClockModel.getTgd2());
        assertEquals(0, satelliteClockModel.getAodc());
        BeidouSatelliteHealth satelliteHealth = satelliteEphemeris.getSatelliteHealth();
        assertEquals(0, satelliteHealth.getSatH1());
        assertEqualsWithDelta(2.0, satelliteHealth.getSvAccur());
        BeidouSatelliteEphemerisTime satelliteEphemerisTime =
                satelliteEphemeris.getSatelliteEphemerisTime();
        assertEquals(1, satelliteEphemerisTime.getAode());
        assertEquals(934, satelliteEphemerisTime.getBeidouWeekNumber());
        assertEquals(457200, satelliteEphemerisTime.getToeSeconds());

        // verify real time integrity model list
        assertTrue(
                verifyTestRealTimeIntegrityModelList(
                        beidouAssistance.getRealTimeIntegrityModels()));
        // verify satellite correction list
        assertTrue(verifyTestGnssCorrections(beidouAssistance.getSatelliteCorrections()));

        // verify auxiliary information
        assertTrue(verifyTestAuxiliaryInformation(beidouAssistance.getAuxiliaryInformation()));
        return true;
    }

    private boolean verifyTestGnssCorrectionList(
            List<GnssSatelliteCorrections> satelliteCorrectionsList) {
        assertEquals(1, satelliteCorrectionsList.size());
        GnssSatelliteCorrections satelliteCorrections = satelliteCorrectionsList.get(0);
        assertEquals(1, satelliteCorrections.getSvid());
        List<IonosphericCorrection> ionoCorrectionList =
                satelliteCorrections.getIonosphericCorrections();
        assertEquals(1, ionoCorrectionList.size());
        IonosphericCorrection ionoCorrection = ionoCorrectionList.get(0);
        assertEquals(1575420000, ionoCorrection.getCarrierFrequencyHz());
        GnssCorrectionComponent correctionComponent = ionoCorrection.getIonosphericCorrection();
        assertEquals("Klobuchar", correctionComponent.getSourceKey());
        GnssInterval validityInterval = correctionComponent.getValidityInterval();
        assertEquals(1731066775042L, validityInterval.getStartMillisSinceGpsEpoch());
        assertEquals(1731066811805L, validityInterval.getEndMillisSinceGpsEpoch());
        PseudorangeCorrection pseudorangeCorrection =
                correctionComponent.getPseudorangeCorrection();
        assertEqualsWithDelta(100.0, pseudorangeCorrection.getCorrectionMeters());
        assertEqualsWithDelta(10.0, pseudorangeCorrection.getCorrectionUncertaintyMeters());
        assertEqualsWithDelta(1.0, pseudorangeCorrection.getCorrectionRateMetersPerSecond());
        return true;
    }

    private boolean verifyTestKlobucharIonosphericModel(KlobucharIonosphericModel model) {
        assertEqualsWithDelta(2.794e-8, model.getAlpha0());
        assertEqualsWithDelta(7.4506e-9, model.getAlpha1());
        assertEqualsWithDelta(-1.1921e-7, model.getAlpha2());
        assertEqualsWithDelta(1.1921e-7, model.getAlpha3());
        assertEqualsWithDelta(145410, model.getBeta0());
        assertEqualsWithDelta(-180220, model.getBeta1());
        assertEqualsWithDelta(0.0, model.getBeta2());
        assertEqualsWithDelta(131070, model.getBeta3());
        return true;
    }

    private boolean verifyTestLeapSecondsModel(LeapSecondsModel model) {
        assertEquals(18, model.getLeapSeconds());
        assertEquals(19, model.getLeapSecondsFuture());
        assertEquals(1025, model.getWeekNumberLeapSecondsFuture());
        assertEquals(1, model.getDayNumberLeapSecondsFuture());
        return true;
    }

    private boolean verifyTestKeplerianOrbitModel(KeplerianOrbitModel model) {
        assertEqualsWithDelta(5153.63, model.getRootA());
        assertEqualsWithDelta(0.00129, model.getEccentricity());
        assertEqualsWithDelta(0.965, model.getI0());
        assertEqualsWithDelta(1.003e-10, model.getIDot());
        assertEqualsWithDelta(-2.54, model.getOmega());
        assertEqualsWithDelta(-0.95, model.getOmega0());
        assertEqualsWithDelta(-8.35e-9, model.getOmegaDot());
        assertEqualsWithDelta(-1.12, model.getM0());
        assertEqualsWithDelta(4.611e-9, model.getDeltaN());
        SecondOrderHarmonicPerturbation secondOrderHarmonicPerturbation =
                model.getSecondOrderHarmonicPerturbation();
        assertEqualsWithDelta(-3.72e-9, secondOrderHarmonicPerturbation.getCic());
        assertEqualsWithDelta(-1.67e-8, secondOrderHarmonicPerturbation.getCis());
        assertEqualsWithDelta(364.03, secondOrderHarmonicPerturbation.getCrc());
        assertEqualsWithDelta(8.37, secondOrderHarmonicPerturbation.getCrs());
        assertEqualsWithDelta(5.36e-7, secondOrderHarmonicPerturbation.getCuc());
        assertEqualsWithDelta(9.48e-7, secondOrderHarmonicPerturbation.getCus());
        return true;
    }

    private boolean verifyTestGnssCorrections(
            List<GnssSatelliteCorrections> satelliteCorrectionsList) {
        assertEquals(1, satelliteCorrectionsList.size());
        GnssSatelliteCorrections satelliteCorrections = satelliteCorrectionsList.get(0);
        assertEquals(1, satelliteCorrections.getSvid());
        List<IonosphericCorrection> ionoCorrectionList =
                satelliteCorrections.getIonosphericCorrections();
        assertEquals(1, ionoCorrectionList.size());
        IonosphericCorrection ionoCorrection = ionoCorrectionList.get(0);
        assertEquals(1575420000, ionoCorrection.getCarrierFrequencyHz());
        GnssCorrectionComponent correctionComponent = ionoCorrection.getIonosphericCorrection();
        assertEquals("Klobuchar", correctionComponent.getSourceKey());
        GnssInterval validityInterval = correctionComponent.getValidityInterval();
        assertEquals(1731066775042L, validityInterval.getStartMillisSinceGpsEpoch());
        assertEquals(1731066811805L, validityInterval.getEndMillisSinceGpsEpoch());
        PseudorangeCorrection pseudorangeCorrection =
                correctionComponent.getPseudorangeCorrection();
        assertEqualsWithDelta(100.0, pseudorangeCorrection.getCorrectionMeters());
        assertEqualsWithDelta(10.0, pseudorangeCorrection.getCorrectionUncertaintyMeters());
        assertEqualsWithDelta(1.0, pseudorangeCorrection.getCorrectionRateMetersPerSecond());
        return true;
    }

    private boolean verifyTestTimeOfClockSeconds(long timeOfClockSeconds) {
        assertEquals(521330400L, timeOfClockSeconds);
        return true;
    }

    private boolean verifyTestUtcModel(UtcModel model) {
        assertEqualsWithDelta(1.33e-7, model.getA0());
        assertEqualsWithDelta(1.07e-13, model.getA1());
        assertEquals(552960, model.getTimeOfWeek());
        assertEquals(1025, model.getWeekNumber());
        return true;
    }

    private boolean verifyTestTimeModelList(List<TimeModel> timeModelList) {
        assertEquals(1, timeModelList.size());
        TimeModel timeModel = timeModelList.get(0);
        assertEquals(GnssStatus.CONSTELLATION_GPS, timeModelList.get(0).getToGnss());
        assertEqualsWithDelta(-2.1e-9, timeModel.getA0());
        assertEqualsWithDelta(-9.7e-15, timeModel.getA1());
        assertEquals(43200, timeModel.getTimeOfWeek());
        assertEquals(1849, timeModel.getWeekNumber());
        return true;
    }

    private boolean verifyTestRealTimeIntegrityModelList(
            List<RealTimeIntegrityModel> realTimeIntegrityModelList) {
        assertEquals(1, realTimeIntegrityModelList.size());
        RealTimeIntegrityModel realTimeIntegrityModel = realTimeIntegrityModelList.get(0);
        List<GnssSignalType> badSignalTypes = realTimeIntegrityModel.getBadSignalTypes();
        assertEquals(1, badSignalTypes.size());
        GnssSignalType signalType = badSignalTypes.get(0);
        assertEquals(GnssStatus.CONSTELLATION_BEIDOU, signalType.getConstellationType());
        assertEqualsWithDelta(1575420000.0, signalType.getCarrierFrequencyHz());
        assertEquals("BDS_B1C", signalType.getCodeType());
        assertEquals(1, realTimeIntegrityModel.getBadSvid());
        assertEquals(1731065504, realTimeIntegrityModel.getPublishDateSeconds());
        assertEquals(1731065504, realTimeIntegrityModel.getStartDateSeconds());
        assertEquals(1731066504, realTimeIntegrityModel.getEndDateSeconds());
        assertEquals("USABINIT", realTimeIntegrityModel.getAdvisoryType());
        assertEquals("2018001", realTimeIntegrityModel.getAdvisoryNumber());
        return true;
    }

    private boolean verifyTestAuxiliaryInformation(
            List<AuxiliaryInformation> auxiliaryInformationList) {
        assertEquals(1, auxiliaryInformationList.size());
        AuxiliaryInformation auxiliaryInformation = auxiliaryInformationList.get(0);
        assertEquals(1, auxiliaryInformation.getAvailableSignalTypes().size());
        GnssSignalType signalType = auxiliaryInformation.getAvailableSignalTypes().get(0);
        assertEquals(GnssStatus.CONSTELLATION_BEIDOU, signalType.getConstellationType());
        assertEqualsWithDelta(1575420000.0, signalType.getCarrierFrequencyHz());
        assertEquals("BDS_B1C", signalType.getCodeType());
        assertEquals(1, auxiliaryInformation.getSvid());
        assertEquals(-1, auxiliaryInformation.getFrequencyChannelNumber());
        assertEquals(
                AuxiliaryInformation.BDS_B1C_ORBIT_TYPE_GEO, auxiliaryInformation.getSatType());
        return true;
    }

    private GalileoAssistance getTestGalileoAssistance() {
        return new GalileoAssistance.Builder()
                .setAlmanac(getTestGalileoAlmanac())
                .setIonosphericModel(getTestGalileoIonosphericModel())
                .setUtcModel(getTestUtcModel())
                .setLeapSecondsModel(getTestLeapSecondsModel())
                .setTimeModels(getTestTimeModelList())
                .setSatelliteEphemeris(getTestGalileoSatelliteEphemerisList())
                .setRealTimeIntegrityModels(getTestRealTimeIntegrityModelList())
                .setSatelliteCorrections(getTestSatelliteCorrections())
                .setAuxiliaryInformation(getTestAuxiliaryInformation())
                .build();
    }

    private GnssAlmanac getTestGalileoAlmanac() {
        final List<GnssSatelliteAlmanac> gnssSatelliteAlmanacList = new ArrayList<>();
        final GnssSatelliteAlmanac gnssSatelliteAlmanac =
                new GnssSatelliteAlmanac.Builder()
                        .setSvid(1)
                        .setSvHealth(0)
                        .setEccentricity(0.00035)
                        .setInclination(0.00726)
                        .setOmega(0.0)
                        .setOmega0(0.21)
                        .setOmegaDot(-1.74e-9)
                        .setRootA(0.0)
                        .setM0(-0.8778)
                        .setAf0(1.52e-5)
                        .setAf1(0.0)
                        .build();
        gnssSatelliteAlmanacList.add(gnssSatelliteAlmanac);

        return new GnssAlmanac.Builder()
                .setIssueDateMillis(1831066775042L)
                .setIoda(4)
                .setWeekNumber(2)
                .setToaSeconds(463200)
                .setGnssSatelliteAlmanacs(gnssSatelliteAlmanacList)
                .build();
    }

    private List<GalileoSatelliteEphemeris> getTestGalileoSatelliteEphemerisList() {
        final List<GalileoSatelliteEphemeris> satelliteEphemerisList = new ArrayList<>();
        final List<GalileoSatelliteClockModel> satelliteClockModelList = new ArrayList<>();
        final SatelliteEphemerisTime satelliteEphemerisTime =
                new SatelliteEphemerisTime.Builder()
                        .setIode(125)
                        .setWeekNumber(2290)
                        .setToeSeconds(45900)
                        .build();
        final GalileoSvHealth satelliteHealth =
                new GalileoSvHealth.Builder()
                        .setDataValidityStatusE1b(GalileoSvHealth.DATA_STATUS_DATA_VALID)
                        .setSignalHealthStatusE1b(GalileoSvHealth.HEALTH_STATUS_OK)
                        .setDataValidityStatusE5a(GalileoSvHealth.DATA_STATUS_DATA_VALID)
                        .setSignalHealthStatusE5a(GalileoSvHealth.HEALTH_STATUS_OK)
                        .setDataValidityStatusE5b(GalileoSvHealth.DATA_STATUS_DATA_VALID)
                        .setSignalHealthStatusE5b(GalileoSvHealth.HEALTH_STATUS_OK)
                        .build();
        satelliteClockModelList.add(
                new GalileoSatelliteClockModel.Builder()
                        .setTimeOfClockSeconds(getTestTimeOfClockSeconds())
                        .setAf0(0.0032)
                        .setAf1(2.278e-11)
                        .setAf2(-3.469e-18)
                        .setBgdSeconds(-1.490e-8)
                        .setSisaMeters(3.119)
                        .setSatelliteClockType(GalileoSatelliteClockModel.TYPE_FNAV)
                        .build());
        satelliteEphemerisList.add(
                new GalileoSatelliteEphemeris.Builder()
                        .setSvid(1)
                        .setSatelliteClockModels(satelliteClockModelList)
                        .setSatelliteOrbitModel(getTestKeplerianOrbitModel())
                        .setSatelliteHealth(satelliteHealth)
                        .setSatelliteEphemerisTime(satelliteEphemerisTime)
                        .build());
        return satelliteEphemerisList;
    }

    private GlonassAssistance getTestGlonassAssistance() {
        return new GlonassAssistance.Builder()
                .setAlmanac(getTestGlonassAlmanac())
                .setUtcModel(getTestUtcModel())
                .setTimeModels(getTestTimeModelList())
                .setSatelliteEphemeris(getTestGlonassSatelliteEphemerisList())
                .setRealTimeIntegrityModels(getTestRealTimeIntegrityModelList())
                .setAuxiliaryInformation(getTestAuxiliaryInformation())
                .build();
    }

    private GlonassAlmanac getTestGlonassAlmanac() {
        List<GlonassSatelliteAlmanac> satelliteAlmanacList = new ArrayList<>();
        satelliteAlmanacList.add(
                new GlonassSatelliteAlmanac.Builder()
                        .setSlotNumber(1)
                        .setHealthState(GlonassSatelliteEphemeris.HEALTH_STATUS_HEALTHY)
                        .setFrequencyChannelNumber(1)
                        .setTau(-1.9e-5)
                        .setTLambda(0.299)
                        .setLambda(0.0)
                        .setDeltaI(6.42e-3)
                        .setDeltaT(-2.65e3)
                        .setDeltaTDot(-6.10e-4)
                        .setEccentricity(4.21e-4)
                        .setOmega(0.16)
                        .setCalendarDayNumber(100)
                        .setGlonassM(true)
                        .build());
        return new GlonassAlmanac(1831066775042L, satelliteAlmanacList);
    }

    private List<GlonassSatelliteEphemeris> getTestGlonassSatelliteEphemerisList() {
        final List<GlonassSatelliteEphemeris> satelliteEphemerisList = new ArrayList<>();
        final GlonassSatelliteClockModel satelliteClockModel =
                new GlonassSatelliteClockModel.Builder()
                        .setTimeOfClockSeconds(getTestTimeOfClockSeconds())
                        .setClockBias(-2.11e-5)
                        .setFrequencyBias(0.0)
                        .setFrequencyChannelNumber(-1)
                        .setGroupDelayDiffSeconds(0.0)
                        .setGroupDelayDiffSecondsAvailable(true)
                        .build();
        final GlonassSatelliteOrbitModel satelliteOrbitModel =
                new GlonassSatelliteOrbitModel.Builder()
                        .setX(-21248.51806641)
                        .setXDot(-0.7282361984253)
                        .setXAccel(1.862645149231e-9)
                        .setY(-12851.89160156)
                        .setYDot(-0.3476705551147)
                        .setYAccel(-9.313225746155e-10)
                        .setZ(5766.135253906)
                        .setZDot(-3.464447021484)
                        .setZAccel(9.313225746155e-10)
                        .build();
        final GlonassSatelliteEphemeris satelliteEphemeris =
                new GlonassSatelliteEphemeris.Builder()
                        .setSlotNumber(1)
                        .setHealthState(GlonassSatelliteEphemeris.HEALTH_STATUS_HEALTHY)
                        .setFrameTimeSeconds(459030.0)
                        .setAgeInDays(0)
                        .setUpdateIntervalMinutes(30)
                        .setUpdateIntervalOdd(false)
                        .setGlonassM(true)
                        .setSatelliteClockModel(satelliteClockModel)
                        .setSatelliteOrbitModel(satelliteOrbitModel)
                        .build();
        satelliteEphemerisList.add(satelliteEphemeris);
        return satelliteEphemerisList;
    }

    private GpsAssistance getTestGpsAssistance() {
        return new GpsAssistance.Builder()
                .setAlmanac(getTestGpsAlmance())
                .setIonosphericModel(getTestKlobucharIonosphericModel())
                .setUtcModel(getTestUtcModel())
                .setLeapSecondsModel(getTestLeapSecondsModel())
                .setTimeModels(getTestTimeModelList())
                .setSatelliteEphemeris(getTestGpsSatelliteEphemerisList())
                .setRealTimeIntegrityModels(getTestRealTimeIntegrityModelList())
                .setSatelliteCorrections(getTestSatelliteCorrections())
                .setAuxiliaryInformation(getTestAuxiliaryInformation())
                .build();
    }

    private List<GpsSatelliteEphemeris> getTestGpsSatelliteEphemerisList() {
        final List<GpsSatelliteEphemeris> satelliteEphemerisList = new ArrayList<>();

        final GpsL2Params gpsL2Params = new GpsL2Params.Builder().setL2Code(0).setL2Flag(0).build();

        final GpsSatelliteClockModel satelliteClockModel =
                new GpsSatelliteClockModel.Builder()
                        .setTimeOfClockSeconds(getTestTimeOfClockSeconds())
                        .setAf0(-8.39e-4)
                        .setAf1(-1.65e-11)
                        .setAf2(0)
                        .setTgd(0)
                        .setIodc(91)
                        .build();

        final GpsSatelliteHealth satelliteHealth =
                new GpsSatelliteHealth.Builder()
                        .setSvHealth(0)
                        .setSvAccur(2.0)
                        .setFitInt(0.0)
                        .build();

        final SatelliteEphemerisTime satelliteEphemerisTime =
                new SatelliteEphemerisTime.Builder()
                        .setIode(59)
                        .setWeekNumber(2290)
                        .setToeSeconds(463472)
                        .build();

        satelliteEphemerisList.add(
                new GpsSatelliteEphemeris.Builder()
                        .setSvid(1)
                        .setGpsL2Params(gpsL2Params)
                        .setSatelliteClockModel(satelliteClockModel)
                        .setSatelliteOrbitModel(getTestKeplerianOrbitModel())
                        .setSatelliteHealth(satelliteHealth)
                        .setSatelliteEphemerisTime(satelliteEphemerisTime)
                        .build());
        return satelliteEphemerisList;
    }

    private GnssAlmanac getTestGpsAlmance() {
        List<GnssSatelliteAlmanac> satelliteAlmanacList = new ArrayList<>();
        satelliteAlmanacList.add(
                new GnssSatelliteAlmanac.Builder()
                        .setSvid(1)
                        .setSvHealth(0)
                        .setEccentricity(7.23e-3)
                        .setInclination(8.79e-3)
                        .setOmega(1.97e-1)
                        .setOmega0(-3.79e-2)
                        .setOmegaDot(-2.55e-9)
                        .setRootA(5.15e3)
                        .setM0(6.14e-1)
                        .setAf0(-2.19e-5)
                        .setAf1(-3.63e-12)
                        .build());
        satelliteAlmanacList.add(
                new GnssSatelliteAlmanac.Builder()
                        .setSvid(2)
                        .setSvHealth(0)
                        .setEccentricity(7.22e-3)
                        .setInclination(8.78e-3)
                        .setOmega(1.96e-1)
                        .setOmega0(-3.78e-2)
                        .setOmegaDot(-2.54e-9)
                        .setRootA(5.14e+3)
                        .setM0(6.13e-1)
                        .setAf0(-2.18e-5)
                        .setAf1(-3.62e-12)
                        .build());

        return new GnssAlmanac.Builder()
                .setWeekNumber(958)
                .setToaSeconds(503808)
                .setGnssSatelliteAlmanacs(satelliteAlmanacList)
                .build();
    }

    private BeidouAssistance getTestBeidouAssistance() {
        return new BeidouAssistance.Builder()
                .setAlmanac(getTestBeidouAlmanac())
                .setIonosphericModel(getTestKlobucharIonosphericModel())
                .setUtcModel(getTestUtcModel())
                .setLeapSecondsModel(getTestLeapSecondsModel())
                .setTimeModels(getTestTimeModelList())
                .setSatelliteEphemeris(getTestBeidouSatelliteEphemerisList())
                .setRealTimeIntegrityModels(getTestRealTimeIntegrityModelList())
                .setSatelliteCorrections(getTestSatelliteCorrections())
                .setAuxiliaryInformation(getTestAuxiliaryInformation())
                .build();
    }

    private GnssAlmanac getTestBeidouAlmanac() {
        List<GnssSatelliteAlmanac> satelliteAlmanacList = new ArrayList<>();
        satelliteAlmanacList.add(
                new GnssSatelliteAlmanac.Builder()
                        .setSvid(1)
                        .setSvHealth(0)
                        .setEccentricity(7.82e-4)
                        .setInclination(0.0958150411 / Math.PI)
                        .setOmega(-0.986917360 / Math.PI)
                        .setOmega0(-3.02e-1 / Math.PI)
                        .setOmegaDot(-5.014e-10 / Math.PI)
                        .setRootA(6493.494226)
                        .setM0(3.15e-1 / Math.PI)
                        .setAf0(-7.22e-4)
                        .setAf1(3.45e-11)
                        .build());
        return new GnssAlmanac.Builder()
                .setWeekNumber(782)
                .setToaSeconds(345600)
                .setGnssSatelliteAlmanacs(satelliteAlmanacList)
                .build();
    }

    private List<BeidouSatelliteEphemeris> getTestBeidouSatelliteEphemerisList() {
        final List<BeidouSatelliteEphemeris> satelliteEphemerisList = new ArrayList<>();
        final BeidouSatelliteClockModel satelliteClockModel =
                new BeidouSatelliteClockModel.Builder()
                        .setTimeOfClockSeconds(getTestTimeOfClockSeconds())
                        .setAf0(0.0006494125118479)
                        .setAf1(3.720579400124e-11)
                        .setAf2(0.0)
                        .setTgd1(3.8e-9)
                        .setTgd2(3.8e-9)
                        .setAodc(0)
                        .build();
        final BeidouSatelliteHealth beidouSatelliteHealth =
                new BeidouSatelliteHealth.Builder().setSatH1(0).setSvAccur(2.0).build();
        final BeidouSatelliteEphemerisTime beidouSatelliteEphemerisTime =
                new BeidouSatelliteEphemerisTime.Builder()
                        .setAode(1)
                        .setBeidouWeekNumber(934)
                        .setToeSeconds(457200)
                        .build();
        satelliteEphemerisList.add(
                new BeidouSatelliteEphemeris.Builder()
                        .setSvid(1)
                        .setSatelliteClockModel(satelliteClockModel)
                        .setSatelliteOrbitModel(getTestKeplerianOrbitModel())
                        .setSatelliteHealth(beidouSatelliteHealth)
                        .setSatelliteEphemerisTime(beidouSatelliteEphemerisTime)
                        .build());
        return satelliteEphemerisList;
    }

    private QzssAssistance getTestQzssAssistance() {
        return new QzssAssistance.Builder()
                .setAlmanac(getTestQzssAlmanac())
                .setIonosphericModel(getTestKlobucharIonosphericModel())
                .setUtcModel(getTestUtcModel())
                .setLeapSecondsModel(getTestLeapSecondsModel())
                .setTimeModels(getTestTimeModelList())
                .setSatelliteEphemeris(getTestQzssSatelliteEphemerisList())
                .setRealTimeIntegrityModels(getTestRealTimeIntegrityModelList())
                .setSatelliteCorrections(getTestSatelliteCorrections())
                .setAuxiliaryInformation(getTestAuxiliaryInformation())
                .build();
    }

    private GnssAlmanac getTestQzssAlmanac() {
        List<GnssSatelliteAlmanac> satelliteAlmanacList = new ArrayList<>();
        satelliteAlmanacList.add(
                new GnssSatelliteAlmanac.Builder()
                        .setSvid(194)
                        .setSvHealth(0)
                        .setEccentricity(7.428e-2)
                        .setInclination(0.7101072704 / Math.PI)
                        .setOmega(-1.559158521 / Math.PI)
                        .setOmega0(1.029787968 / Math.PI)
                        .setOmegaDot(-3.120129966e-9 / Math.PI)
                        .setRootA(6493.731445)
                        .setM0(-3.041552089 / Math.PI)
                        .setAf0(-3.814697266e-6)
                        .setAf1(0.0)
                        .build());
        return new GnssAlmanac.Builder()
                .setWeekNumber(261)
                .setToaSeconds(176128)
                .setGnssSatelliteAlmanacs(satelliteAlmanacList)
                .build();
    }

    private List<QzssSatelliteEphemeris> getTestQzssSatelliteEphemerisList() {
        final List<QzssSatelliteEphemeris> satelliteEphemerisList = new ArrayList<>();

        final GpsL2Params gpsL2Params = new GpsL2Params.Builder().setL2Code(0).setL2Flag(0).build();

        final GpsSatelliteClockModel satelliteClockModel =
                new GpsSatelliteClockModel.Builder()
                        .setTimeOfClockSeconds(getTestTimeOfClockSeconds())
                        .setAf0(-8.39e-4)
                        .setAf1(-1.65e-11)
                        .setAf2(0)
                        .setTgd(0)
                        .setIodc(91)
                        .build();

        final GpsSatelliteHealth satelliteHealth =
                new GpsSatelliteHealth.Builder()
                        .setSvHealth(0)
                        .setSvAccur(2.0)
                        .setFitInt(0.0)
                        .build();

        final SatelliteEphemerisTime satelliteEphemerisTime =
                new SatelliteEphemerisTime.Builder()
                        .setIode(59)
                        .setWeekNumber(2290)
                        .setToeSeconds(463472)
                        .build();

        satelliteEphemerisList.add(
                new QzssSatelliteEphemeris.Builder()
                        .setSvid(194)
                        .setGpsL2Params(gpsL2Params)
                        .setSatelliteClockModel(satelliteClockModel)
                        .setSatelliteOrbitModel(getTestKeplerianOrbitModel())
                        .setSatelliteHealth(satelliteHealth)
                        .setSatelliteEphemerisTime(satelliteEphemerisTime)
                        .build());
        return satelliteEphemerisList;
    }

    private List<RealTimeIntegrityModel> getTestRealTimeIntegrityModelList() {
        final List<RealTimeIntegrityModel> realTimeIntegrityModelList = new ArrayList<>();
        List<GnssSignalType> signalTypes = new ArrayList<>();
        signalTypes.add(
                GnssSignalType.create(GnssStatus.CONSTELLATION_BEIDOU, 1575420000.0, "BDS_B1C"));
        realTimeIntegrityModelList.add(
                new RealTimeIntegrityModel.Builder()
                        .setBadSvid(1)
                        .setBadSignalTypes(signalTypes)
                        .setPublishDateSeconds(1731065504)
                        .setStartDateSeconds(1731065504)
                        .setEndDateSeconds(1731066504)
                        .setAdvisoryType("USABINIT")
                        .setAdvisoryNumber("2018001")
                        .build());
        return realTimeIntegrityModelList;
    }

    private List<GnssSatelliteCorrections> getTestSatelliteCorrections() {
        final List<GnssSatelliteCorrections> satelliteCorrectionsList = new ArrayList<>();
        final List<IonosphericCorrection> ionoCorrectionList = new ArrayList<>();
        final GnssInterval validityInterval = new GnssInterval(1731066775042L, 1731066811805L);
        final PseudorangeCorrection pseudorangeCorrection = new PseudorangeCorrection(100, 10, 1);
        ionoCorrectionList.add(
                new IonosphericCorrection(
                        1575420000,
                        new GnssCorrectionComponent(
                                "Klobuchar", validityInterval, pseudorangeCorrection)));

        final GnssSatelliteCorrections satelliteCorrections =
                new GnssSatelliteCorrections(1, ionoCorrectionList);
        satelliteCorrectionsList.add(satelliteCorrections);
        return satelliteCorrectionsList;
    }

    private KlobucharIonosphericModel getTestKlobucharIonosphericModel() {
        return new KlobucharIonosphericModel.Builder()
                .setAlpha0(2.794e-8)
                .setAlpha1(7.4506e-9)
                .setAlpha2(-1.1921e-7)
                .setAlpha3(1.1921e-7)
                .setBeta0(145410)
                .setBeta1(-180220)
                .setBeta2(0.0)
                .setBeta3(131070)
                .build();
    }

    private GalileoIonosphericModel getTestGalileoIonosphericModel() {
        return new GalileoIonosphericModel.Builder()
                .setAi0(121)
                .setAi1(-0.74609)
                .setAi2(0.0088196)
                .build();
    }

    private UtcModel getTestUtcModel() {
        return new UtcModel.Builder()
                .setA0(1.33e-7)
                .setA1(1.07e-13)
                .setTimeOfWeek(552960)
                .setWeekNumber(1025)
                .build();
    }

    private LeapSecondsModel getTestLeapSecondsModel() {
        return new LeapSecondsModel.Builder()
                .setLeapSeconds(18)
                .setLeapSecondsFuture(19)
                .setWeekNumberLeapSecondsFuture(1025)
                .setDayNumberLeapSecondsFuture(1)
                .build();
    }

    private List<TimeModel> getTestTimeModelList() {
        final List<TimeModel> timeModelList = new ArrayList<>();
        timeModelList.add(
                new TimeModel.Builder()
                        .setToGnss(GnssStatus.CONSTELLATION_GPS)
                        .setA0(-2.1e-9)
                        .setA1(-9.7e-15)
                        .setTimeOfWeek(43200)
                        .setWeekNumber(1849)
                        .build());
        return timeModelList;
    }

    private long getTestTimeOfClockSeconds() {
        return 521330400L;
    }

    private KeplerianOrbitModel getTestKeplerianOrbitModel() {
        final SecondOrderHarmonicPerturbation secondOrderHarmonicPerturbation =
                new SecondOrderHarmonicPerturbation.Builder()
                        .setCic(-3.72e-9)
                        .setCis(-1.67e-8)
                        .setCrc(364.03)
                        .setCrs(8.37)
                        .setCuc(5.36e-7)
                        .setCus(9.48e-7)
                        .build();

        return new KeplerianOrbitModel.Builder()
                .setRootA(5153.63)
                .setEccentricity(0.00129)
                .setI0(0.965)
                .setIDot(1.003e-10)
                .setOmega(-2.54)
                .setOmega0(-0.95)
                .setOmegaDot(-8.35e-9)
                .setM0(-1.12)
                .setDeltaN(4.611e-9)
                .setSecondOrderHarmonicPerturbation(secondOrderHarmonicPerturbation)
                .build();
    }

    private List<AuxiliaryInformation> getTestAuxiliaryInformation() {
        final List<AuxiliaryInformation> auxiliaryInformationList = new ArrayList<>();
        List<GnssSignalType> signalTypes = new ArrayList<>();
        signalTypes.add(
                GnssSignalType.create(GnssStatus.CONSTELLATION_BEIDOU, 1575420000.0, "BDS_B1C"));
        auxiliaryInformationList.add(
                new AuxiliaryInformation.Builder()
                        .setSvid(1)
                        .setAvailableSignalTypes(signalTypes)
                        .setFrequencyChannelNumber(-1)
                        .setSatType(AuxiliaryInformation.BDS_B1C_ORBIT_TYPE_GEO)
                        .build());
        return auxiliaryInformationList;
    }
}
