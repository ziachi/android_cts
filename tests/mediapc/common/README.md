# Writing an MPC Test

Using
[this CL](https://android-review.googlesource.com/c/platform/cts/+/3185540) as a
guide focusing on requirement
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media):

-   R: MUST support 6 instances of hardware video decoder sessions (AVC or HEVC)
    in any codec combination running concurrently at 720p resolution@30 fps.
-   S: MUST support 6 instances of hardware video decoder sessions (AVC, HEVC,
    VP9* or later) in any codec combination running concurrently at 720p
    resolution@30 fps. *Only 2 instances are required if VP9 codec is present.
-   Tiramisu: MUST support 6 instances of hardware video decoder sessions (AVC,
    HEVC, VP9, AV1 or later) in any codec combination running concurrently at
    1080p resolution@30 fps.
-   Upside-Down Cake: MUST support 6 instances of 8-bit (SDR) hardware video
    decoder sessions (AVC, HEVC, VP9, AV1 or later) in any codec combination
    running concurrently with 3 sessions at 1080p resolution@30 fps and 3
    sessions at 4k resolution@30fps, unless AV1. AV1 codecs are only required to
    support 1080p resolution, but are still required to support 6 instances at
    1080p30fps.
-   Vanilla Ice Cream: MUST support 6 instances of 8-bit (SDR) hardware video
    decoder sessions (AVC, HEVC, VP9, AV1, or later) in any codec combination
    running concurrently with 3 sessions at 1080p resolution@30 fps and 3
    sessions at 4k resolution@30fps, unless AV1. For all sessions, there MUST
    NOT be more than 1 frame dropped per second. AV1 codecs are only required to
    support 1080p resolution, but are still required to support 6 instances at
    1080p30fps.

## Define Requirements

Each requirement needs to be defined in
[requirements.txtpb](https://cs.android.com/android/platform/superproject/main/+/main:cts/tests/mediapc/requirements/requirements.txtpb).
The information in this file is then used to generate code to be used in tests
for said requirement.

### Give the Requirement a Name

Each requirement needs to be given a human-readable name describing what the
requirement is testing for. Additionally, each requirement name needs to be
unique. This name is then used for the class name when generating code.

For example, we gave
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media)
the name `"Concurrent Video Decoder Sessions"`. This will then generate the
following class:

```
android.mediapc.cts.common.Requirements.ConcurrentVideoDecoderSessionsRequirement
```

### Define Test Configs

A test config describes different set ups for a given requirement that change
which performance classes are being tested for said requirement.

For example: requirement,
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media),
there's 3 test configs: - 720p: which describes tests ran at 720p (makes sense),
these tests only check for performance classes R and S. - 1080p: these tests
only check for performance class Tiramisu. - 4k: these tests only check for
performance classes Upside-down Cake and Vanilla Ice Cream

Additionally each test config needs to be given a proto field number. This
number must be unique for *all* test configs within
[requirements.txtpb](https://cs.android.com/android/platform/superproject/main/+/main:cts/tests/mediapc/requirements/requirements.txtpb).

```
test_configs: {
  key: "720p"
  value: {
    description: "Tests running at 720p"
    proto_field_number: 4
  }
}
test_configs: {
  key: "1080p"
  value: {
    description: "Tests running at 1080p"
    proto_field_number: 5
  }
}
test_configs: {
  key: "4k"
  value: {
    description: "Tests running at 4k"
    proto_field_number: 6
  }
}
```

NOTE: The changelist for
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media)
describes an `id` field. This field has since been deprecated and removed, so
please ignore it.

NOTE: The changelist for
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media)
does not describe proto field numbers. These were yet to be implemented when the
change was created. They are implemented now.

TIP: Most requirements only use one test configuration to describe a singular
test which tests for all performance classes. For these requirements, you only
need to specify a blank default test config. Example:
[cl/666945690](https://android-review.googlesource.com/c/platform/cts/+/3237331)

```
test_configs: {
  key: ""
  value: {
    description: "Default test config"
    proto_field_number: 47
  }
}
```

### Define Variants

In addition to test configs, any variants for a given requirement must also be
defined.

A variant describes a different set of thresholds a requirement must meet
depending on the test setup. The main difference between a variant and a test
config is variants do NOT affect which performance classes are being tested.

Variants do NOT need proto field numbers.

For our requirement,
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media),
when testing with a VP9 codec at 720p, 2 instances are required for S and none
for R, and when testing with an AV1 or other codec, there is no requirement for
R. Therefore, we have created 2 variants:

```
variants: {
  key: "VP9"
  value: {
    description: "When one of the codecs is VP9, variant used in 720p tests"
  }
}
variants: {
  key: "AV1"
  value: {
    description: "When one of the codecs is AV1, variant used in 720p tests"
  }
}
```

NOTE: Sometimes, it can be confusing whether to make a variant or a test config
when defining requirements. If unsure, the recommendation is to make another
test config rather than another variant.

NOTE: A variant does not need to be used in every test config.

### Define Measurements

A set of measurements for the requirement must also be defined. Each measurement
needs a name, a measurement_type, a comparison, and a proto field number.

The measurement name is described in the `key` field for each measurement. It
needs to be able to be used as a field for a proto, so lowercase, underscores,
no spaces, etc.

The `measurement_type` describes the data type of the measurement. It can be one
of the following types:

-   MEASUREMENT_TYPE_BOOL
-   MEASUREMENT_TYPE_DOUBLE
-   MEASUREMENT_TYPE_INT
-   MEASUREMENT_TYPE_STRING
-   MEASUREMENT_TYPE_LONG
-   MEASUREMENT_TYPE_FLOAT

The `comparison` describes how the measurement will be tested when evaluating
the performance class. It can be one of the following types:

-   COMPARISON_EQUAL
-   COMPARISON_LESS_THAN
-   COMPARISON_LESS_THAN_OR_EQUAL
-   COMPARISON_GREATER_THAN
-   COMPARISON_GREATER_THAN_OR_EQUAL
-   COMPARISON_INFO_ONLY
-   COMPARISON_CONFIG

NOTE: Config comparison types are measurements that describe how the test was
set up. These values are automatically set, and the thresholds defined later
must all be the same per test config.

Finally, like with test configs, each measurement needs a `proto_field_number`.
For measurements, the number must be greater than or equal to 3, and only needs
to be unique among the other measurements for a given requirement.

For our requirement,
[5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media),
we've defined the following:

```
measurements: {
  key: "concurrent_fps"
  value: {
    description: "The number of frames per second that can be decoded concurrently"
    measurement_type: MEASUREMENT_TYPE_DOUBLE
    comparison: COMPARISON_GREATER_THAN_OR_EQUAL
    proto_field_number: 3
  }
}
measurements: {
  key: "frame_drops_per_sec"
  value: {
    description: "The number of frames dropped per second"
    measurement_type: MEASUREMENT_TYPE_DOUBLE
    comparison: COMPARISON_LESS_THAN_OR_EQUAL
    proto_field_number: 4
  }
}
measurements: {
  key: "resolution"
  value: {
    description: "The resolution the test was run at"
    measurement_type: MEASUREMENT_TYPE_INT
    comparison: COMPARISON_CONFIG
    proto_field_number: 5
  }
}
```

### Define Specs

Lastly, the specs for the requirement need to be defined. A spec describes the
required thresholds for a given performance class. It has the following fields:
`mpc`, `specification`, `test_config_id`, and `required_values`. Additionally it
is stored as a map within
[requirements.txtpb](https://cs.android.com/android/platform/superproject/main/+/main:cts/tests/mediapc/requirements/requirements.txtpb),
so it needs a `key` field which corresponds to performance class the spec
describes.

The field `mpc` is an enum that also describes the performance class associated
with the spec. As such, it should correspond to `key` field. -
MEDIA_PERFORMANCE_CLASS_11 corresponds to 30 - MEDIA_PERFORMANCE_CLASS_12
corresponds to 31 - MEDIA_PERFORMANCE_CLASS_13 corresponds to 33 -
MEDIA_PERFORMANCE_CLASS_14 corresponds to 34 - MEDIA_PERFORMANCE_CLASS_15
corresponds to 35

The field `specification` describes in text what the requirement is and the
threshold that must be met. This text is copied directly from the
[Android CDD](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media)
for a given requirement/performance class.

The field `test_config_id` describes the associated `test_config_id` which was
defined previously. If it is the default blank `test_config_id`, the field does
not have to be set.

#### Define Required Values

Finally, `required_values` must be defined for all measurements associated with
the specified performance class. These values are stored as a map where the
`key` corresponds to the measurement name, and the value corresponds to the
required threshold.

NOTE: if a measurement is not needed for a given performance class level it does
not have to be specified

NOTE: config measurements must have the same threshold for all performance class
levels for a given test config

##### Define Variant Required Values

Additionally, `required_values` must be set for variants. A described variant
does not have to correspond to every test config, but if described for given
test config, it must be described for all specs with the same test config.

### Example Generated Class for [5.1/H-1-2](https://source.android.com/docs/compatibility/15/android-15-cdd#2271_media)

```
/**
  * Add a new ConcurrentVideoDecoderSessionsRequirement for requirement 5.1/H-1-2 to a
  * {@code PerformanceClassEvaluator} instance.
  *
  * Concurrent video decoder sessions
  */
public static ConcurrentVideoDecoderSessionsRequirement.With addR5_1__H_1_2() {
    return new ConcurrentVideoDecoderSessionsRequirement.With();
}

/**
  * 5.1/H-1-2 Concurrent Video Decoder Sessions
  *
  * Concurrent video decoder sessions
  */
public static final class ConcurrentVideoDecoderSessionsRequirement extends Requirement {

    public static final class With {
        private With() {}
        public static final class Config1080P {
            private Config1080P() {}
            public ConcurrentVideoDecoderSessionsRequirement to(PerformanceClassEvaluator pce) {
                return pce.addRequirement(ConcurrentVideoDecoderSessionsRequirement.create1080P());
            }
        }
        public static final class Config4K {
            private Config4K() {}
            public ConcurrentVideoDecoderSessionsRequirement to(PerformanceClassEvaluator pce) {
                return pce.addRequirement(ConcurrentVideoDecoderSessionsRequirement.create4K());
            }
        }
        public static final class Config720P {
            private Config720P() {}
            public ConcurrentVideoDecoderSessionsRequirement to(PerformanceClassEvaluator pce) {
                return pce.addRequirement(ConcurrentVideoDecoderSessionsRequirement.create720P());
            }
            public Config720PAndVariantAV1 withVariantAV1() {
                return new Config720PAndVariantAV1();
            }
            public Config720PAndVariantVP9 withVariantVP9() {
                return new Config720PAndVariantVP9();
            }
        }
        public static final class VariantAV1 {
            private VariantAV1() {}
            public Config720PAndVariantAV1 withConfig720P() {
                return new Config720PAndVariantAV1();
            }
        }
        public static final class VariantVP9 {
            private VariantVP9() {}
            public Config720PAndVariantVP9 withConfig720P() {
                return new Config720PAndVariantVP9();
            }
        }
        public static final class Config720PAndVariantAV1 {
            private Config720PAndVariantAV1() {}
            public ConcurrentVideoDecoderSessionsRequirement to(PerformanceClassEvaluator pce) {
                return pce.addRequirement(ConcurrentVideoDecoderSessionsRequirement.create720PAV1());
            }
        }
        public static final class Config720PAndVariantVP9 {
            private Config720PAndVariantVP9() {}
            public ConcurrentVideoDecoderSessionsRequirement to(PerformanceClassEvaluator pce) {
                return pce.addRequirement(ConcurrentVideoDecoderSessionsRequirement.create720PVP9());
            }
        }
        public Config1080P withConfig1080P() {
            return new Config1080P();
        }
        public Config4K withConfig4K() {
            return new Config4K();
        }
        public Config720P withConfig720P() {
            return new Config720P();
        }
        public VariantAV1 withVariantAV1() {
            return new VariantAV1();
        }
        public VariantVP9 withVariantVP9() {
            return new VariantVP9();
        }
    }

    /**
      * 5.1/H-1-2 Concurrent Video Decoder Sessions
      *
      * Concurrent video decoder sessions
      */
    private static ConcurrentVideoDecoderSessionsRequirement create1080P() {
        var concurrentFps =
            RequiredMeasurement.<Double>builder()
                .setId("concurrent_fps")
                .setPredicate(RequirementConstants.DOUBLE_GTE)
                .addRequiredValue(VERSION_CODES.TIRAMISU, 171.000000)
                .build();
        var frameDropsPerSec =
            RequiredMeasurement.<Double>builder()
                .setId("frame_drops_per_sec")
                .setPredicate(RequirementConstants.DOUBLE_LTE)
                .build();
        var resolution =
            RequiredMeasurement.<Integer>builder()
                .setId("resolution")
                .setPredicate(RequirementConstants.INTEGER_INFO)
                .addRequiredValue(VERSION_CODES.TIRAMISU, 1080)
                .build();

        ConcurrentVideoDecoderSessionsRequirement req =
            new ConcurrentVideoDecoderSessionsRequirement(
                "r5_1__h_1_2__1080_p",
                concurrentFps,
                frameDropsPerSec,
                resolution);
        req.setMeasuredValue("resolution",1080);
        return req;
    }

    /**
      * 5.1/H-1-2 Concurrent Video Decoder Sessions
      *
      * Concurrent video decoder sessions
      */
    private static ConcurrentVideoDecoderSessionsRequirement create4K() {
        var concurrentFps =
            RequiredMeasurement.<Double>builder()
                .setId("concurrent_fps")
                .setPredicate(RequirementConstants.DOUBLE_GTE)
                .addRequiredValue(VERSION_CODES.UPSIDE_DOWN_CAKE, 171.000000)
                .addRequiredValue(VERSION_CODES.VANILLA_ICE_CREAM, 171.000000)
                .build();
        var frameDropsPerSec =
            RequiredMeasurement.<Double>builder()
                .setId("frame_drops_per_sec")
                .setPredicate(RequirementConstants.DOUBLE_LTE)
                .addRequiredValue(VERSION_CODES.VANILLA_ICE_CREAM, 1.000000)
                .build();
        var resolution =
            RequiredMeasurement.<Integer>builder()
                .setId("resolution")
                .setPredicate(RequirementConstants.INTEGER_INFO)
                .addRequiredValue(VERSION_CODES.UPSIDE_DOWN_CAKE, 2160)
                .addRequiredValue(VERSION_CODES.VANILLA_ICE_CREAM, 2160)
                .build();

        ConcurrentVideoDecoderSessionsRequirement req =
            new ConcurrentVideoDecoderSessionsRequirement(
                "r5_1__h_1_2__4_k",
                concurrentFps,
                frameDropsPerSec,
                resolution);
        req.setMeasuredValue("resolution",2160);
        return req;
    }

    /**
      * 5.1/H-1-2 Concurrent Video Decoder Sessions
      *
      * Concurrent video decoder sessions
      */
    private static ConcurrentVideoDecoderSessionsRequirement create720P() {
        var concurrentFps =
            RequiredMeasurement.<Double>builder()
                .setId("concurrent_fps")
                .setPredicate(RequirementConstants.DOUBLE_GTE)
                .addRequiredValue(VERSION_CODES.R, 171.000000)
                .addRequiredValue(VERSION_CODES.S, 171.000000)
                .build();
        var frameDropsPerSec =
            RequiredMeasurement.<Double>builder()
                .setId("frame_drops_per_sec")
                .setPredicate(RequirementConstants.DOUBLE_LTE)
                .build();
        var resolution =
            RequiredMeasurement.<Integer>builder()
                .setId("resolution")
                .setPredicate(RequirementConstants.INTEGER_INFO)
                .addRequiredValue(VERSION_CODES.R, 720)
                .addRequiredValue(VERSION_CODES.S, 720)
                .build();

        ConcurrentVideoDecoderSessionsRequirement req =
            new ConcurrentVideoDecoderSessionsRequirement(
                "r5_1__h_1_2__720_p",
                concurrentFps,
                frameDropsPerSec,
                resolution);
        req.setMeasuredValue("resolution",720);
        return req;
    }

    /**
      * 5.1/H-1-2 Concurrent Video Decoder Sessions When one of the codecs is AV1, variant used in 720p tests
      *
      * Concurrent video decoder sessions
      */
    private static ConcurrentVideoDecoderSessionsRequirement create720PAV1() {
        var concurrentFps = RequiredMeasurement
                .<Double>builder()
                .setId("concurrent_fps")
                .setPredicate(RequirementConstants.DOUBLE_GTE)
                .addRequiredValue(VERSION_CODES.S, 171.000000)
                .build();
        var frameDropsPerSec = RequiredMeasurement
                .<Double>builder()
                .setId("frame_drops_per_sec")
                .setPredicate(RequirementConstants.DOUBLE_LTE)
                .build();
        var resolution = RequiredMeasurement
                .<Integer>builder()
                .setId("resolution")
                .setPredicate(RequirementConstants.INTEGER_INFO)
                .addRequiredValue(VERSION_CODES.R, 720)
                .addRequiredValue(VERSION_CODES.S, 720)
                .build();
        ConcurrentVideoDecoderSessionsRequirement req =
            new ConcurrentVideoDecoderSessionsRequirement(
                "r5_1__h_1_2",
                concurrentFps,
                frameDropsPerSec,
                resolution);
        req.setMeasuredValue("resolution",720);
        return req;
    }

    /**
      * 5.1/H-1-2 Concurrent Video Decoder Sessions When one of the codecs is VP9, variant used in 720p tests
      *
      * Concurrent video decoder sessions
      */
    private static ConcurrentVideoDecoderSessionsRequirement create720PVP9() {
        var concurrentFps = RequiredMeasurement
                .<Double>builder()
                .setId("concurrent_fps")
                .setPredicate(RequirementConstants.DOUBLE_GTE)
                .addRequiredValue(VERSION_CODES.S, 57.000000)
                .build();
        var frameDropsPerSec = RequiredMeasurement
                .<Double>builder()
                .setId("frame_drops_per_sec")
                .setPredicate(RequirementConstants.DOUBLE_LTE)
                .build();
        var resolution = RequiredMeasurement
                .<Integer>builder()
                .setId("resolution")
                .setPredicate(RequirementConstants.INTEGER_INFO)
                .addRequiredValue(VERSION_CODES.R, 720)
                .addRequiredValue(VERSION_CODES.S, 720)
                .build();
        ConcurrentVideoDecoderSessionsRequirement req =
            new ConcurrentVideoDecoderSessionsRequirement(
                "r5_1__h_1_2",
                concurrentFps,
                frameDropsPerSec,
                resolution);
        req.setMeasuredValue("resolution",720);
        return req;
    }

    /** The number of frames per second that can be decoded concurrently */
    public void setConcurrentFps(double v) {
        this.setMeasuredValue("concurrent_fps", v);
    }

    /** The number of frames dropped per second */
    public void setFrameDropsPerSec(double v) {
        this.setMeasuredValue("frame_drops_per_sec", v);
    }

    /** The resolution the test was run at */
    public int getResolution() {
        return this.getMeasuredValue("resolution", Integer.class);
    }

    private ConcurrentVideoDecoderSessionsRequirement(String id, RequiredMeasurement<?>... reqs) {
        super(id, reqs);
    }
}
```

## Update Test to Report Data Using PerformanceClassEvaluator

Now that we have a requirement defined we just need to update our test to use
PerformanceClassEvaluator.

First we need to add the following to our test class: @Rule public final
TestName mTestName = new TestName();

### Initializing the Requirement Objects

Next we will create the evaluator and add our newly defined requirement. This
can be done at any point during the test, but typically test writers choose to
do this at the end of the test:

```
PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
ConcurrentVideoDecoderSessionsRequirement r5_1__h_1_2 =
    Requirements.addR5_1__H_1_2().withConfigX().withVariantY().to(pce);
```

NOTE: `withConfigX` should be replaced with the proper config, ex:
`withConfig1080P`. If using the default blank config, this should be left out.

NOTE: `withVariantY` should be replaced with the proper variant, ex:
`withVariantVP9`. If the test is not associated with a variant, this should also
be left out.

NOTE: the order configs and variants are specified does not matter, i.e.
`withVariantY().withConfigX()` is also valid

### Setting the Measured Values

The generated class for the given requirement also generates with set methods
for each measurement.

After the test, once our required measurement(s) have been calculated, we use
the set measurement method(s) generated to report them:

```
r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
r5_1__H_1_2.setFrameDropsPerSec(frameDropsPerSec);
```

NOTE: if a measurement is not associated with the specified test config, it does
not have to be set.

NOTE: config measurement generate get methods instead of set, and do not need to
be set

### Submitting the Test Results

Finally, we just need to submit our results. The submit method should be called
only once at the very end of the test. If we are writing our test CTS, we should
use `submitAndCheck`; if we are writing our test under CTS-Verifier or ITS, we
should use `submitAndVerify`. Ex:

```
pce.submitAndCheck();
```

The test results are then processed and reported creating a file
media_performance_class_test_cases.reportlog.json which will eventually have its
data uploaded and processed.

You can view the file with

```shell
adb root
adb shell cat /storage/emulated/0/report-log-files/MediaPerformanceClassTestCases.reportlog.json
```
