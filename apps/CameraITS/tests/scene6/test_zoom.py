# Copyright 2020 The Android Open Source Project
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
"""Verify zoom ratio scales ArUco marker sizes correctly."""


import logging
import os.path

import camera_properties_utils
import its_base_test
import its_device_utils
import its_session_utils
import opencv_processing_utils
import ui_interaction_utils
import cv2
from mobly import test_runner
import numpy as np
import zoom_capture_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10
_TEST_FORMATS = ['yuv']  # list so can be appended for newer Android versions
_TEST_REQUIRED_MPC = 33
_SINGLE_CAMERA_NUMBER_OF_CAMERAS_TO_TEST = 1
_ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST = 2  # UW and W
# Wider zoom ratio range will be tested by test_zoom_tele
_WIDE_ZOOM_RATIO_MAX = 2.2
_WIDE_ZOOM_THIRD_CAMERA_CHECK_ZOOM_RATIO = 2.0
_ZOOM_RATIO_REQUEST_RESULT_DIFF_RTOL = 0.1


class ZoomTest(its_base_test.UiAutomatorItsBaseTest):
  """Test the camera zoom behavior using JCA."""

  def setup_class(self):
    super().setup_class()
    self.ui_app = ui_interaction_utils.JETPACK_CAMERA_APP_PACKAGE_NAME
    ui_interaction_utils.restart_cts_verifier(self.dut, self.ui_app)
    # Disable accelerometer rotation to ensure intent result is delivered
    its_device_utils.run_adb_shell_command(
        self.dut.serial, 'settings put system accelerometer_rotation 0')

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(self.dut, self.ui_app)

  def test_zoom(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props) and
          cam.is_primary_camera()
      )

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Determine test zoom range
      z_range = props['android.control.zoomRatioRange']
      debug = self.debug_mode
      camera_facing = props['android.lens.facing']
      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(
          z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH)
      tele_camera_found = cam.has_tele_camera(
          facing=camera_facing)
      # Truncate zoom range if test_zoom_tele will be run
      if tele_camera_found:
        logging.debug('Tele camera found, truncating zoom range max to %.2f',
                      _WIDE_ZOOM_RATIO_MAX)
        z_max = min(z_max, _WIDE_ZOOM_RATIO_MAX)
      z_list = np.arange(z_min, z_max, (z_max - z_min) / (_NUM_STEPS - 1))
      z_list = np.append(z_list, z_max)
      logging.debug('Testing zoom range: %s', str(z_list))

      # Check media performance class
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      ultrawide_camera_found = cam.has_ultrawide_camera(
          facing=camera_facing)
      if (media_performance_class >= _TEST_REQUIRED_MPC and
          ultrawide_camera_found and
          int(z_min) >= 1):
        raise AssertionError(
            f'With primary camera {self.camera_id}, '
            f'MPC >= {_TEST_REQUIRED_MPC}, and '
            'an ultrawide camera facing in the same direction as the primary, '
            'zoom_ratio minimum must be less than 1.0. '
            f'Found media performance class {media_performance_class} '
            f'and minimum zoom {z_min}.')

      # close camera before captures to ensure that JCA can access it
      cam.close_camera()

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, _ = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
      logging.debug('test TOLs: %s', str(test_tols))

      # do captures over zoom range and find ArUco markers with cv2
      img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
      test_failed = False

      test_data = []
      all_aruco_ids = []
      all_aruco_corners = []
      images = []
      physical_ids = set()
      size = None
      captures = cam.do_jca_captures_across_zoom_ratios(
          self.dut,
          self.log_path,
          flash_mode_desc=ui_interaction_utils.FLASH_MODE_OFF_CONTENT_DESC,
          lens_facing=camera_facing,
          zoom_ratios=z_list
      )
      for zoom_ratio, capture in zip(z_list, captures):
        physical_ids.add(capture.physical_id)
        logging.debug('Physical IDs: %s', physical_ids)
        # Ignore captures at higher zooms where smooth zoom can affect results.
        if (tele_camera_found and
            len(physical_ids) > _ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST and
            zoom_ratio > _WIDE_ZOOM_THIRD_CAMERA_CHECK_ZOOM_RATIO):
          logging.debug('Found enough zoom data, given that tele camera found: '
                        '%d physical IDs at zoom ratio %.2f, ignoring '
                        'remaining captures.',
                        len(physical_ids),
                        zoom_ratio)
          break
        bgr_img = cv2.imread(capture.capture_path)
        # Use first image size for all captures
        if not size:
          height, width, _ = bgr_img.shape
          size = (width, height)
          logging.debug('Size: %s', size)
        radius_tol, offset_tol = (
            zoom_capture_utils.RADIUS_RTOL, zoom_capture_utils.OFFSET_RTOL
        )

        # Find ArUco markers
        try:
          corners, ids, _ = opencv_processing_utils.find_aruco_markers(
              bgr_img,
              (f'{img_name_stem}_{zoom_ratio:.2f}_'
               f'ArUco.{zoom_capture_utils.JPEG_STR}'),
              aruco_marker_count=1
          )
        except AssertionError as e:
          logging.debug('Could not find ArUco marker at zoom ratio %.2f: %s',
                        zoom_ratio, e)
          break
        all_aruco_corners.append([corner[0] for corner in corners])
        all_aruco_ids.append([id[0] for id in ids])
        images.append(bgr_img)

        test_data.append(
            zoom_capture_utils.ZoomTestData(
                result_zoom=zoom_ratio,
                radius_tol=radius_tol,
                offset_tol=offset_tol,
                physical_id=capture.physical_id,
            )
        )

      # Find ArUco markers in all captures and update test data
      zoom_capture_utils.update_zoom_test_data_with_shared_aruco_marker(
          test_data, all_aruco_ids, all_aruco_corners, size)
      # Mark ArUco marker center and image center
      opencv_processing_utils.mark_zoom_images(
          images, test_data, img_name_stem)

      number_of_cameras_to_test = (
          _ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST
          if ultrawide_camera_found
          else _SINGLE_CAMERA_NUMBER_OF_CAMERAS_TO_TEST
      )
      # Make reporting active physical IDs optional
      if all(d.physical_id is None for d in test_data):
        number_of_cameras_to_test = 0
      if not zoom_capture_utils.verify_zoom_data(
          test_data, size,
          offset_plot_name_stem=img_name_stem,
          number_of_cameras_to_test=number_of_cameras_to_test):
        test_failed = True

    if test_failed:
      raise AssertionError(f'{_NAME} failed! Check test_log.DEBUG for errors')

if __name__ == '__main__':
  test_runner.main()
