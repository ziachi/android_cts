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
"""Verifies color temperature and color tint are applied to the image."""


import logging
import os.path

from mobly import test_runner

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import opencv_processing_utils
import target_exposure_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
# The minimum required color temperature range is [2856K, 6500K] in
# This test evaluates three color temperature values within this range.
_COLOR_TEMPERATURE_TEST_VALUES = (2856, 4678, 6500)
# The minimum required color tint range is [-50, 50]. This test
# evaluates four tints within this range.
_COLOR_TINT_TEST_VALUES = (-50, -25, 25, 50)
_RED_CHANNEL = 0
_GREEN_CHANNEL = 1
_BLUE_CHANNEL = 2
_COLOR_CORRECTION_MODE_CCT = 3
_NO_COLOR_TINT = 0
_DEFAULT_COLOR_TEMPERATURE = 0


def _get_regions_of_interest(props, cam, test_name_with_log_path):
  """Captures scene8 image & returns blue, yellow & light regions.

  Args:
    props: camera properties object.
    cam: its_session_utils.ItsSession object
    test_name_with_log_path: name for the test logs and output images

  Returns:
    regions: blue, yellow, light, and dark regions of image
  """
  # Define format
  largest_yuv = capture_request_utils.get_largest_format('yuv', props)
  match_ar = (largest_yuv['width'], largest_yuv['height'])
  fmt = capture_request_utils.get_near_vga_yuv_format(
      props, match_ar=match_ar)

  cam.do_3a()
  req = capture_request_utils.auto_capture_request()
  cap = cam.do_capture(req, fmt)

  # Save image and convert to numpy array
  img = image_processing_utils.convert_capture_to_rgb_image(
      cap, props=props)
  img_path = f'{test_name_with_log_path}_aruco_markers.jpg'
  image_processing_utils.write_image(img, img_path)
  img = image_processing_utils.convert_image_to_uint8(img)

  # Find blue, yellow, and light regions from scene8
  chart_path = f'{test_name_with_log_path}_chart_boundary.jpg'
  regions = opencv_processing_utils.define_regions(
      img, img_path, chart_path, props, match_ar[0], match_ar[1])

  return regions


def _get_ratio(img, region, img_width, img_height, channel_one, channel_two):
  """Gets the requested rgb ratio from the image.

  Args:
    img: RGB image; the image from which the ratio is calculated.
    region: tuple of 4 elements (x, y, w, h); defines the region of interest
        within the image.
    img_width: the width of the image
    img_height: the height of the image
    channel_one: the index of the first channel to compare
    channel_two: the index of the second channel to compare
  Returns:
    ratio: requested ratio
  """
  x = int(region[0] * img_width)
  y = int(region[1] * img_height)
  width = int(region[2] * img_width)
  height = int(region[3] * img_height)

  region_of_interest_from_img = img[y:y + height,
                                    x:x + width]

  img_means = image_processing_utils.compute_image_means(
      region_of_interest_from_img)
  ratio = img_means[channel_one]/img_means[channel_two]

  return ratio


def color_temperature_capture_request(color_temp, color_tint, log_path, cam,
                                      props):
  """Returns capture request with requested color temperature and tint.

  Args:
   color_temp: The color temperature value to populate the request with.
   color_tint: The color tint value to populate the request with.
   log_path: log path to store the captured images.
   cam: its_session_utils.ItsSession object
   props: camera properties object.

  Returns:
    The capture request, ready to be passed to the
    its_session_utils.device.do_capture function.
  """
  # Define baseline request
  e, s = target_exposure_utils.get_target_exposure_combos(
      log_path, cam)['midSensitivity']
  req = capture_request_utils.manual_capture_request(s, e, 0.0, True, props)

  # Add relevant CCT settings
  if color_temp != 0:
    req['android.colorCorrection.colorTemperature'] = color_temp
  req['android.colorCorrection.mode'] = _COLOR_CORRECTION_MODE_CCT
  req['android.colorCorrection.colorTint'] = color_tint

  return req


def validate_r_b_ratios(blue_ratios, yellow_ratios):
  """Validates R/B ratios are decreasing/increasing as expected.

  Args:
    blue_ratios: list R/B ratios for blue region at [2856K, 4678K, 6500K]
    yellow_ratios: list R/B ratios for yellow region at [2856K, 4678K, 6500K]

  Returns:
    bool: True if the ratios are increasing or decreasing as expected
  """
  # Validate blue region: R/B ratios should increase
  for i in range(len(blue_ratios) - 1):
    if blue_ratios[i] >= blue_ratios[i + 1]:
      raise AssertionError(
          f'R/B ratio for blue region did not increase as expected: '
          f'{blue_ratios} ')

  # Validate yellow region: R/B ratios should increase
  for i in range(len(yellow_ratios) - 1):
    if yellow_ratios[i] >= yellow_ratios[i + 1]:
      raise AssertionError(
          f'R/B ratio for yellow region did not increase as expected: '
          f'{yellow_ratios} ')


class ColorCorrectionModeCct(its_base_test.ItsBaseTest):
  """Tests that color temperature and color tint settings are applied.

  This test verifies that the camera's color temperature and color tint
  adjustments are correctly applied to captured images. It ensures that
  changes in color temperature and color tint are reflected in the image's
  RGB ratios by validating expected shifts in the red, green, and blue
  channels.
  """

  def test_color_correction_mode_cct(self):
    """Test CCT color correction mode."""

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      test_name_with_log_path = os.path.join(log_path, _NAME)

      camera_properties_utils.skip_unless(
          (_COLOR_CORRECTION_MODE_CCT in
           camera_properties_utils.color_correction_modes(props)))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          log_path)

      regions = _get_regions_of_interest(props, cam, test_name_with_log_path)

      capture_requests = []
      for test_color_temp_val in _COLOR_TEMPERATURE_TEST_VALUES:
        # Test with varying color temp values and no color tint
        req = color_temperature_capture_request(test_color_temp_val,
                                                _NO_COLOR_TINT,
                                                log_path, cam, props)
        capture_requests.append(req)

      for test_color_tint_val in _COLOR_TINT_TEST_VALUES:
        # Test with default color temp and vary color tint
        req = color_temperature_capture_request(
            _DEFAULT_COLOR_TEMPERATURE, test_color_tint_val,
            log_path, cam, props)
        capture_requests.append(req)

      # Define format
      largest_yuv = capture_request_utils.get_largest_format('yuv', props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)

      caps = cam.do_capture(capture_requests, fmt)

      blue_ratios = []  # Ratios from blue region
      yellow_ratios = []  # Ratio from yellow region
      # First three images were captures with varying color temperatures
      for i in range(len(_COLOR_TEMPERATURE_TEST_VALUES)):
        # Save image and convert to numpy array
        img = image_processing_utils.convert_capture_to_rgb_image(
            caps[i], props=props)
        img_path = f'{test_name_with_log_path}_scene8_{i}.jpg'
        image_processing_utils.write_image(img, img_path)
        img = image_processing_utils.convert_image_to_uint8(img)

        blue_ratio = _get_ratio(
            img, regions['regionBlue'], match_ar[0], match_ar[1],
            _RED_CHANNEL, _BLUE_CHANNEL)
        blue_ratios.append(blue_ratio)
        yellow_ratio = _get_ratio(
            img, regions['regionYellow'], match_ar[0], match_ar[1],
            _RED_CHANNEL, _BLUE_CHANNEL)
        yellow_ratios.append(yellow_ratio)

    validate_r_b_ratios(blue_ratios, yellow_ratios)

    b_g_ratios = []  # Ratios for light region towards magenta
    r_g_ratios = []  # Ratios for light region towards green
    # Last 4 images were captured with varying color tints
    for i in range(len(caps) - len(_COLOR_TINT_TEST_VALUES), len(caps)):
      # Save image and convert to numpy array
      img = image_processing_utils.convert_capture_to_rgb_image(
          caps[i], props=props)
      img_path = f'{test_name_with_log_path}_scene8_{i}.jpg'
      image_processing_utils.write_image(img, img_path)
      img = image_processing_utils.convert_image_to_uint8(img)

      # Captures at idx 0-2 were for testing color temp, therefore captures
      # at idx 3 and 4 are the first two images for testing color tint
      if i <= 4:
        # First two images were captured with color tint -50 and -25,
        # towards the magenta on the magenta-green spectrum.
        # Check b/g ratio for increases in green in the overall image
        ratio = _get_ratio(
            img, regions['regionLight'], match_ar[0],
            match_ar[1], _BLUE_CHANNEL, _GREEN_CHANNEL)
        b_g_ratios.append(ratio)

      else:
        # Last two images were captured with color tint 25 and 50
        # towards the green on the magenta-green spectrum.
        # Check r/g ratio for increases in magenta in  overall image
        ratio = _get_ratio(
            img, regions['regionLight'], match_ar[0],
            match_ar[1], _RED_CHANNEL, _GREEN_CHANNEL)
        r_g_ratios.append(ratio)

    if b_g_ratios[0] > b_g_ratios[1]:
      raise AssertionError(
          f'B/G ratio did not increase as expected. First ratio: '
          f'{b_g_ratios[0]}, Second ratio: {b_g_ratios[1]}')

    if r_g_ratios[0] > r_g_ratios[1]:
      raise AssertionError(
          f'R/G ratio did not increase as expected. First ratio: '
          f'{r_g_ratios[0]}, Second ratio: {r_g_ratios[1]}')


if __name__ == '__main__':
  test_runner.main()
