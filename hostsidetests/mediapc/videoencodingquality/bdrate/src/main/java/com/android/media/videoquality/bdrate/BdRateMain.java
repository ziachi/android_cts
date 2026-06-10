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

package com.android.media.videoquality.bdrate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Class for calculating BD-RATE as part of the Performance Class - Video Encoding Quality CTS
 * test.
 *
 * verifyBdRate() method returns one of the following exit-codes:
 *
 * <ul>
 *   <li>0 - The VEQ test has passed and the BD-RATE was within the threshold defined by the
 *       reference configuration.
 *   <li>1 - The configuration files could not be loaded and thus, BD-RATE could not be calculated.
 *   <li>2 - BD-RATE could not be calculated because one of the required conditions for calculation
 *       was not met.
 *   <li>3 - The VEQ test has failed due to the calculated BD-RATE being greater than the allowed
 *       threshold defined by the reference configuration.
 * </ul>
 */
public class BdRateMain {
    private static final Logger LOGGER = Logger.getLogger(BdRateMain.class.getName());

    private static final NumberFormat NUMBER_FORMAT = new DecimalFormat("0.00");
    private final Gson mGson;
    private final BdRateCalculator mBdRateCalculator;
    private final BdQualityCalculator mBdQualityCalculator;

    private static Path mRefJsonFile;
    private static Path mTestVmafFile;

    private static Formatter mFormatter = new SimpleFormatter() {
        @Override
        public String format(LogRecord record) {
            return record.getMessage() + "\n";
        }
    };

    private enum Result {
        SUCCESS, INVALID_ARGS, INVALID_DATA, FAILED
    }

    public BdRateMain(
            Gson gson, BdRateCalculator bdRateCalculator, BdQualityCalculator bdQualityCalculator) {
        mGson = gson;
        mBdRateCalculator = bdRateCalculator;
        mBdQualityCalculator = bdQualityCalculator;
    }

    public Result run() {
        LOGGER.info(String.format("Running cts-media-videoquality-bdrate library"));
        LOGGER.info(String.format("Reading reference configuration JSON file: %s", mRefJsonFile));
        ReferenceConfig refConfig = null;
        try {
            refConfig = loadReferenceConfig(mRefJsonFile, mGson);
        } catch (IOException | JsonParseException e) {
            LOGGER.log(Level.SEVERE,
                    "Invalid Arguments: Failed to load reference configuration file!", e);
            return Result.INVALID_ARGS;
        }

        LOGGER.info(String.format("Reading test result text file: %s", mTestVmafFile));
        VeqTestResult veqTestResult = null;
        try {
            veqTestResult = loadTestResult(mTestVmafFile);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Invalid Arguments: Failed to load VEQ Test Result file!", e);
            return Result.INVALID_ARGS;
        }

        if (!veqTestResult.referenceFile().equals(refConfig.referenceFile())) {
            LOGGER.log(Level.SEVERE,
                    "Test Result file and Reference JSON file are not for the same reference file"
                            + ".");
            return Result.INVALID_ARGS;
        }

        logCurves(
                "Successfully loaded rate-distortion data: ",
                refConfig.referenceCurve(),
                veqTestResult.curve());
        LOGGER.info(
                String.format(
                        "Checking Video Encoding Quality (VEQ) for %s", refConfig.referenceFile()));

        return checkVeq(
                mBdRateCalculator,
                mBdQualityCalculator,
                refConfig.referenceCurve(),
                veqTestResult.curve(),
                refConfig.referenceThreshold());
    }

    /**
     * Checks the video encoding quality of the target curve against the reference curve using
     * Bjontegaard-Delta (BD) values.
     */
    @VisibleForTesting
    static Result checkVeq(
            BdRateCalculator bdRateCalculator,
            BdQualityCalculator bdQualityCalculator,
            RateDistortionCurve baseline,
            RateDistortionCurve target,
            double threshold) {
        RateDistortionCurvePair curvePair =
                RateDistortionCurvePair.createClusteredPair(baseline, target);

        if (curvePair.canCalculateBdRate()) {
            LOGGER.info("Calculating BD-RATE...");

            double bdRateResult = bdRateCalculator.calculate(curvePair);
            LOGGER.info(
                    String.format("BD-RATE: %.04f (%.02f%%)", bdRateResult, bdRateResult * 100));

            if (bdRateResult > threshold) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Failed Video Encoding Quality (VEQ) test, calculated BD-RATE was (%.04f)"
                                + " which was greater than the test-defined threshold of (%.04f)",
                        bdRateResult, threshold));
                return Result.FAILED;
            }
        } else if (curvePair.canCalculateBdQuality()) {
            LOGGER.warning("Unable to calculate BD-RATE, falling back to checking BD-QUALITY...");

            double bdQualityResult = bdQualityCalculator.calculate(curvePair);
            LOGGER.info(String.format("BD-QUALITY: %.02f", bdQualityResult));

            double percentageQualityChange =
                    bdQualityResult
                            / Arrays.stream(curvePair.baseline().getDistortionsArray())
                            .average()
                            .getAsDouble();

            // Since distortion is measured as a higher == better value, invert
            // the percentage so that it can be compared equivalently with the threshold.
            if (percentageQualityChange * -1 > threshold) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Failed Video Encoding Quality (VEQ) test, calculated BD-Quality was (%"
                                + ".04f) which was greater than the test-defined threshold of (%"
                                + ".04f)",
                        bdQualityResult, threshold));
                return Result.FAILED;
            }
        } else {
            LOGGER.log(Level.SEVERE,
                    "Cannot calculate BD-RATE or BD-QUALITY. Reference configuration likely does "
                            + "not match the test result data.");
            return Result.INVALID_DATA;
        }
        return Result.SUCCESS;
    }

    private static void logCurves(
            String message, RateDistortionCurve referenceCurve, RateDistortionCurve targetCurve) {
        ArrayList<String> rows = new ArrayList<>();
        rows.add(message);
        rows.add(
                String.format(
                        "|%15s|%15s|%15s|%15s|",
                        "Reference Rate", "Reference Dist", "Target Rate", "Target Dist"));
        rows.add("=".repeat(rows.get(1).length()));

        Iterator<RateDistortionPoint> referencePoints = referenceCurve.points().iterator();
        Iterator<RateDistortionPoint> targetPoints = targetCurve.points().iterator();

        while (referencePoints.hasNext() || targetPoints.hasNext()) {
            String refRate = "";
            String refDist = "";
            if (referencePoints.hasNext()) {
                RateDistortionPoint refPoint = referencePoints.next();
                refRate = NUMBER_FORMAT.format(refPoint.rate());
                refDist = NUMBER_FORMAT.format(refPoint.distortion());
            }

            String targetRate = "";
            String targetDist = "";
            if (targetPoints.hasNext()) {
                RateDistortionPoint targetPoint = targetPoints.next();
                targetRate = NUMBER_FORMAT.format(targetPoint.rate());
                targetDist = NUMBER_FORMAT.format(targetPoint.distortion());
            }

            rows.add(
                    String.format(
                            "|%15s|%15s|%15s|%15s|", refRate, refDist, targetRate, targetDist));
        }

        LOGGER.info(String.join("\n", rows));
    }

    private static ReferenceConfig loadReferenceConfig(Path path, Gson gson) throws IOException {
        Preconditions.checkArgument(Files.exists(path));

        // Each config file contains a single ReferenceConfig in a list,
        // the first one is returned here.
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            TypeToken<List<ReferenceConfig>> configsType = new TypeToken<>() {};
            ArrayList<ReferenceConfig> configs = gson.fromJson(reader, configsType.getType());
            return configs.get(0);
        }
    }

    private static VeqTestResult loadTestResult(Path path) throws IOException {
        Preconditions.checkState(Files.exists(path));

        String testResult = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return VeqTestResult.parseFromTestResult(testResult);
    }

    public static int verifyBdRate(String refJsonFilePath, String testVmafFilePath,
            String resultFilePath) throws IOException {
        mRefJsonFile = Paths.get(refJsonFilePath);
        mTestVmafFile = Paths.get(testVmafFilePath);

        // Setup the logger.
        FileHandler fileHandler = new FileHandler(resultFilePath);
        fileHandler.setFormatter(mFormatter);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(fileHandler);
        rootLogger.setLevel(Level.FINEST);

        Result res = new BdRateMain(
                new GsonBuilder()
                        .registerTypeAdapter(
                                ReferenceConfig.class,
                                new ReferenceConfig.Deserializer())
                        .create(),
                BdRateCalculator.create(),
                BdQualityCalculator.create())
                .run();

        LOGGER.info("Passed Video Encoding Quality (VEQ) test.");
        fileHandler.close();
        return res.ordinal();
    }
}
