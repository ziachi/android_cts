# Copyright 2024 The Android Open Source Project
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
"""Verify that the switch from UW to W has similar RGB values."""


import logging
import os.path
import pathlib

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import multi_camera_switch_utils
import preview_processing_utils


_AE_ATOL = 7.0
_AE_RTOL = 0.04  # 4%
_AWB_ATOL_AB = 10  # ATOL for A and B means in LAB color space
_AWB_ATOL_L = 3  # ATOL for L means in LAB color space
_COLORS = ('r', 'g', 'b', 'gray')
_COLOR_GRAY = _COLORS[3]
_IMG_FORMAT = 'png'
_LENS_SUFFIX_UW = 'uw'
_LENS_SUFFIX_W = 'w'
_MP4_FORMAT = '.mp4'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_MARGIN = 50  # pixels
_RECORDING_DURATION = 400  # milliseconds
_SENSOR_ORIENTATIONS = (90, 270)
_SKIP_INITIAL_FRAMES = 15
_ZOOM_RANGE_UW_W = (0.95, 2.05)  # UW/W crossover range
_ZOOM_STEP = 0.01


class MultiCameraSwitchTest(its_base_test.ItsBaseTest):
  """Test that the switch between cameras has similar RGB values.

  This test uses various zoom ratios within range android.control.zoomRatioRange
  to capture images and find the point when the physical camera changes
  to determine the crossover point of change from UW to W.
  It does preview recording at UW and W crossover point to verify that
  the AE, AWB and AF behavior remains the same.
  """

  def test_multi_camera_switch(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      chart_distance = self.chart_distance
      failed_awb_msg = []
      failed_ae_msg = []
      failed_af_msg = []

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      multi_camera_switch_utils.check_lens_switch_conditions(
          props, first_api_level, _ZOOM_RANGE_UW_W)

      # Set up scene and configure preview size
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, chart_distance)
      preview_test_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id)
      cam.do_3a()

      try:
        # Start dynamic preview recording and collect results
        capture_results, file_list = (
            preview_processing_utils.preview_over_zoom_range(
                self.dut, cam, preview_test_size, _ZOOM_RANGE_UW_W[0],
                _ZOOM_RANGE_UW_W[1], _ZOOM_STEP, self.log_path)
        )

        # Find the crossover point where the camera switches
        lens_changed, counter = (
            multi_camera_switch_utils.find_crossover_point(
                cam, capture_results))

      except Exception as e:
        # Remove all the files except mp4 recording in case of any error
        for filename in os.listdir(self.log_path):
          file_path = os.path.join(self.log_path, filename)
          if os.path.isfile(file_path) and not filename.endswith(_MP4_FORMAT):
            os.remove(file_path)
        raise AssertionError('Error during crossover check') from e

      # Raise error if lens did not switch within the range
      # _ZOOM_RANGE_UW_W
      # TODO(ruchamk): Add lens_changed to the CameraITS metrics
      if not lens_changed:
        e_msg = 'Crossover point not found. Try running the test again!'
        raise AssertionError(e_msg)

      # Process capture results and get camera properties
      img_uw_file, img_w_file, min_focus_distance_w = (
          multi_camera_switch_utils.get_camera_properties_and_log(
              cam, capture_results, file_list, counter,
              _LENS_SUFFIX_UW, _LENS_SUFFIX_W)
      )

      # Remove unwanted frames and only save the UW and
      # W crossover point frames along with mp4 recording
      its_session_utils.remove_frame_files(self.log_path, [
          os.path.join(self.log_path, img_uw_file),
          os.path.join(self.log_path, img_w_file)])

      # Add suffix to the UW and W image files
      uw_path = pathlib.Path(os.path.join(self.log_path, img_uw_file))
      uw_name = uw_path.with_name(f'{uw_path.stem}_uw{uw_path.suffix}')
      os.rename(os.path.join(self.log_path, img_uw_file), uw_name)

      w_path = pathlib.Path(os.path.join(self.log_path, img_w_file))
      w_name = w_path.with_name(f'{w_path.stem}_w{w_path.suffix}')
      os.rename(os.path.join(self.log_path, img_w_file), w_name)

      # Convert UW and W img to numpy array
      uw_img = image_processing_utils.convert_image_to_numpy_array(
          str(uw_name))
      w_img = image_processing_utils.convert_image_to_numpy_array(
          str(w_name))

      # Check the sensor orientation and flip image
      if (props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['FRONT']):
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_uw')
        uw_img = image_processing_utils.check_orientation_and_flip(
            props, uw_img, img_name_stem
        )
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_w')
        w_img = image_processing_utils.check_orientation_and_flip(
            props, w_img, img_name_stem
        )

      # Find ArUco markers in the image with UW lens
      # and extract the outer box patch
      corners, ids = multi_camera_switch_utils.find_aruco_markers(
          uw_img, uw_path, _LENS_SUFFIX_UW)
      uw_chart_patch = multi_camera_switch_utils.extract_main_patch(
          corners, ids, uw_img, uw_path, _LENS_SUFFIX_UW)
      uw_four_patches = image_processing_utils.get_four_quadrant_patches(
          uw_chart_patch, uw_path, _LENS_SUFFIX_UW, _PATCH_MARGIN)

      # Find ArUco markers in the image with W lens
      # and extract the outer box patch
      corners, ids = multi_camera_switch_utils.find_aruco_markers(
          w_img, w_path, _LENS_SUFFIX_W)
      w_chart_patch = multi_camera_switch_utils.extract_main_patch(
          corners, ids, w_img, w_path, _LENS_SUFFIX_W)
      w_four_patches = image_processing_utils.get_four_quadrant_patches(
          w_chart_patch, w_path, _LENS_SUFFIX_W, _PATCH_MARGIN)

      ae_uw_y_avgs = {}
      ae_w_y_avgs = {}

      for uw_patch, w_patch, patch_color in zip(
          uw_four_patches, w_four_patches, _COLORS):
        logging.debug('Checking for quadrant color: %s', patch_color)

        # AE Check: Extract the Y component from rectangle patch
        file_stem = f'{os.path.join(self.log_path, _NAME)}_{patch_color}'
        failed_ae_msg, uw_y_avg, w_y_avg = (
            multi_camera_switch_utils.do_ae_check(
                uw_patch, w_patch, file_stem, patch_color, props,
                _LENS_SUFFIX_UW, _LENS_SUFFIX_W, _AE_RTOL, _AE_ATOL))

        ae_uw_y_avgs.update({patch_color: f'{uw_y_avg:.4f}'})
        ae_w_y_avgs.update({patch_color: f'{w_y_avg:.4f}'})

        # AWB Check : Verify that delta Cab are within the limits
        if camera_properties_utils.awb_regions(props):
          cab_atol = _AWB_ATOL_L if patch_color == _COLOR_GRAY else _AWB_ATOL_AB
          awb_msg = multi_camera_switch_utils.do_awb_check(
              uw_patch, w_patch, cab_atol, patch_color, _LENS_SUFFIX_UW,
              _LENS_SUFFIX_W)
          if awb_msg:
            failed_awb_msg.append(f'{awb_msg}\n')

      # Below print statements are for logging purpose.
      # Do not replace with logging.
      print(f'{_NAME}_ae_uw_y_avgs: ', ae_uw_y_avgs)
      print(f'{_NAME}_ae_w_y_avgs: ', ae_w_y_avgs)

      # Skip the AF check FF->FF
      if min_focus_distance_w == 0:
        logging.debug('AF check skipped for this device.')
      else:
        # AF check using slanted edge
        uw_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            uw_chart_patch, uw_path, _LENS_SUFFIX_UW, _PATCH_MARGIN)
        w_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            w_chart_patch, w_path, _LENS_SUFFIX_W, _PATCH_MARGIN)
        failed_af_msg, sharpness_uw, sharpness_w = (
            multi_camera_switch_utils.do_af_check(
                uw_slanted_edge_patch, w_slanted_edge_patch, _LENS_SUFFIX_UW,
                _LENS_SUFFIX_W))
        print(f'{_NAME}_uw_sharpness: {sharpness_uw:.4f}')
        print(f'{_NAME}_w_sharpness: {sharpness_w:.4f}')
        if failed_af_msg:
          logging.debug(failed_af_msg)

      if failed_awb_msg or failed_ae_msg:
        error_msg = multi_camera_switch_utils.get_error_msg(
            failed_awb_msg, failed_ae_msg)
        raise AssertionError(f'{_NAME} failed with following errors:\n'
                             f'{error_msg}')

if __name__ == '__main__':
  test_runner.main()
