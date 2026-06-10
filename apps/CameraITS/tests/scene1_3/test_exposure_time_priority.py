# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Verifies exposure on RAW images for AE exposure time priority mode."""


import logging
import math
import os.path

from matplotlib import pyplot as plt
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils

_BAYER_COLORS = ('R', 'Gr', 'Gb', 'B')
_BLACK_LVL_RTOL = 0.1
_BURST_LEN = 10  # break captures into burst of BURST_LEN requests
_EXP_LONG_THRESH = 1E6  # 1ms
_EXP_MULT_SHORT = pow(2, 1.0/3)  # Test 3 steps per 2x exposure
_EXP_MULT_LONG = pow(10, 1.0/3)  # Test 3 steps per 10x exposure
_IMG_DELTA_THRESH = 0.97  # Each shot must be > 0.97*previous
_IMG_INCREASING_ATOL = 2  # Require images get at least 2x black level
_IMG_SAT_RTOL = 0.01  # 1%
_IMG_STATS_GRID = 9  # Used to find the center 11.11%
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_STEADY_BRIGHTNESS_TOLERANCE = 0.05  # RTOL for steady brightness
_NS_TO_MS_FACTOR = 1.0E-6
_CONTROL_AE_PRIORITY_MODE_EXPOSURE_TIME_PRIORITY = 2  # Cam Metadata enum value


def create_test_exposure_list(e_min, e_max):
  """Create the list of exposure values to test."""
  e_list = []
  multiplier = 1.0
  while e_min*multiplier < e_max:
    e_list.append(int(e_min*multiplier))
    if e_min*multiplier < _EXP_LONG_THRESH:
      multiplier *= _EXP_MULT_SHORT
    else:
      multiplier *= _EXP_MULT_LONG
  if e_list[-1] < e_max*_IMG_DELTA_THRESH:
    e_list.append(int(e_max))
  return e_list


def create_plot(exps, means, log_path):
  """Create plots R, Gr, Gb, B vs exposures.

  Args:
    exps: array of exposure times in ms
    means: array of means for RAW captures
    log_path: path to write plot file
  """
  r = [m[0] for m in means]  # Red channel values
  gr = [m[1] for m in means]  # Green (Gr) channel values
  gb = [m[2] for m in means]  # Green (Gb) channel values
  b = [m[3] for m in means]  # Blue channel values
  plt.figure(f'{_NAME}')
  plt.plot(exps, r, 'r.-', label='R')
  plt.plot(exps, gr, 'g.-', label='Gr')
  plt.plot(exps, gb, 'k.-', label='Gb')
  plt.plot(exps, b, 'b.-', label='B')
  plt.xscale('log')
  plt.yscale('log')
  plt.title(f'{_NAME}')
  plt.xlabel('Exposure time (ms)')
  plt.ylabel('Center patch pixel mean')
  plt.legend(loc='lower right', numpoints=1, fancybox=True)
  plt.savefig(f'{os.path.join(log_path, _NAME)}.png')
  plt.clf()


def assert_increasing_means(means, exps, black_levels, white_level):
  """Assert that each image brightness is increasing as the exposure time increases.

  Args:
    means: BAYER COLORS means for set of images
    exps: exposure times in ms
    black_levels: BAYER COLORS black_level values
    white_level: full scale value
  Returns:
    None
  """
  lower_thresh = np.array(black_levels) * (1 + _BLACK_LVL_RTOL)
  logging.debug('Lower threshold for check: %s', lower_thresh)
  allow_under_saturated = True
  image_increasing = False
  for i in range(1, len(means)):
    prev_mean = means[i-1]
    mean = means[i]

    if max(mean) > min(black_levels) * _IMG_INCREASING_ATOL:
      image_increasing = True

    if math.isclose(max(mean), white_level, rel_tol=_IMG_SAT_RTOL):
      logging.debug('Saturated: white_level %f, max_mean %f',
                    white_level, max(mean))
      break

    if allow_under_saturated and min(mean-lower_thresh) < 0:
      # All channel means are close to black level
      continue
    allow_under_saturated = False

    # Check pixel means are increasing (with small tolerance)
    logging.debug('exp: %.3fms, means: %s', exps[i-1], mean)
    for ch, color in enumerate(_BAYER_COLORS):
      if mean[ch] <= prev_mean[ch] * _IMG_DELTA_THRESH:
        e_msg = (f'{color} not increasing with increased exp time! ')
        if i == 1:
          e_msg += f'black_level: {black_levels[ch]}, '
        else:
          e_msg += (f'exp[i-1]: {exps[i-2]:.3f}ms, '
                    f'mean[i-1]: {prev_mean[ch]:.2f}, ')
        e_msg += (f'exp[i]: {exps[i-1]:.3f}ms, mean[i]: {mean[ch]}, '
                  f'RTOL: {_IMG_DELTA_THRESH}')
        raise AssertionError(e_msg)

  # Check image increases
  if not image_increasing:
    raise AssertionError('Image does not increase with exposure!')


def assert_steady_means(means, exps):
  """Assert that image brightness is steady as the exposure time increases (within tolerance).

  Args:
    means: BAYER COLORS means for set of images
    exps: exposure times in ms
  Returns:
    None
  """
  for i in range(1, len(means)):
    prev_mean = means[i-1]
    mean = means[i]

    logging.debug(
        'exp: %.3fms, prev_means: %s, current_means: %s',
        exps[i-1], prev_mean, mean
    )
    for ch, color in enumerate(_BAYER_COLORS):
      # Check current mean is within the steady tolerance of previous mean
      lower_bound = prev_mean[ch] * (1 - _STEADY_BRIGHTNESS_TOLERANCE)
      upper_bound = prev_mean[ch] * (1 + _STEADY_BRIGHTNESS_TOLERANCE)

      if not (lower_bound <= mean[ch] <= upper_bound):
        e_msg = (
            f'{color} not steady! '
            f'Previous Mean: {prev_mean[ch]:.2f}, '
            f'Current Mean: {mean[ch]:.2f}, '
            f'Lower Bound: {lower_bound:.2f}, '
            f'Upper Bound: {upper_bound:.2f}, '
            f'Tolerance: {_STEADY_BRIGHTNESS_TOLERANCE}'
        )
        raise AssertionError(e_msg)


def ae_exposure_time_priority_capture_request(exp_time):
  """Returns a capture request enabling exposure time AE priority mode.

  Args:
   exp_time: The exposure time value to populate the request with.

  Returns:
    The capture request, ready to be passed to the
    its_session_utils.device.do_capture function.
  """
  req = {
      'android.control.mode': 1,  # CONTROL_MODE_AUTO
      'android.control.aeMode': 1,  # CONTROL_AE_MODE_ON
      'android.control.aePriorityMode': 2,
      # CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY
      'android.sensor.exposureTime': exp_time,
  }

  return req


class ExposureTimePriorityTest(its_base_test.ItsBaseTest):
  """Capture RAW images in exp time priority mode & measure pixel values."""

  def test_exposure_time_priority(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw16(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.per_frame_control(props) and
          (_CONTROL_AE_PRIORITY_MODE_EXPOSURE_TIME_PRIORITY in
           camera_properties_utils.ae_priority_mode(props)) and
          not camera_properties_utils.mono_camera(props))
      log_path = self.log_path

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Create list of exposures
      e_min, e_max = props['android.sensor.info.exposureTimeRange']
      logging.debug('exposureTimeRange(ns): %d, %d', e_min, e_max)
      e_test = create_test_exposure_list(e_min, e_max)

      # Capture with rawStats to reduce capture times
      fmt = its_session_utils.define_raw_stats_fmt_exposure(
          props, _IMG_STATS_GRID
      )

      white_level = float(props['android.sensor.info.whiteLevel'])
      black_levels = image_processing_utils.get_black_levels(props)

      # Break caps into bursts and do captures
      burst_len = _BURST_LEN
      caps = []
      reqs = [ae_exposure_time_priority_capture_request(
          e) for e in e_test]

      # Eliminate burst len==1. Error because returns [[]], not [{}, ...]
      while len(reqs) % burst_len == 1:
        burst_len -= 1

      # Break caps into bursts
      for i in range(len(reqs) // burst_len):
        cam.do_3a()
        caps += cam.do_capture(reqs[i*burst_len:(i+1)*burst_len], fmt)
      last_n = len(reqs) % burst_len
      if last_n:
        cam.do_3a()
        caps += cam.do_capture(reqs[-last_n:], fmt)

      sens_min, sens_max = props['android.sensor.info.sensitivityRange']

      # Extract means for each capture
      means_steady = []
      means_increasing = []
      means_total = []  # For plot

      e_test_ms_steady = []
      e_test_ms_increasing = []
      e_test_ms_total = []  # For plot

      for i, cap in enumerate(caps):
        mean_image, _ = image_processing_utils.unpack_rawstats_capture(cap)
        mean = mean_image[_IMG_STATS_GRID // 2, _IMG_STATS_GRID // 2]
        logging.debug(
            'exp_time=%.3fms, mean=%s', (e_test[i] * _NS_TO_MS_FACTOR), mean
        )
        means_total.append(mean)
        e_test_ms_total.append(mean)

        metadata = cap['metadata']
        sens_sensitivity = metadata['android.sensor.sensitivity']

        if sens_min < sens_sensitivity < sens_max:
          # We assume a 'steady brightness' scenario, as the ISO is able to
          # adjust to maintain consistent exposure.
          means_steady.append(mean)
          e_test_ms_steady.append(e_test[i] * _NS_TO_MS_FACTOR)
        else:
          # We assume an 'increasing brightness' scenario, as the exposure
          # time is increasing without available ISO adjustment to help
          # maintain steady brightness, resulting in brighter images.
          means_increasing.append(mean)
          e_test_ms_increasing.append(e_test[i] * _NS_TO_MS_FACTOR)

      # Create plot
      create_plot(e_test_ms_total, means_total, log_path)

      # Each shot not using ISO-based compensation should increase in brightness
      assert_increasing_means(
          means_increasing, e_test_ms_increasing, black_levels, white_level
      )

      # Each shot using ISO-based compensation should be steady in brightness
      assert_steady_means(means_steady, e_test_ms_steady)

if __name__ == '__main__':
  test_runner.main()
