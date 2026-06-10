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
"""Verify zoom ratio scales ArUco marker sizes correctly for the TELE camera."""


import logging
import math
import os.path

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import opencv_processing_utils
import its_base_test
import its_session_utils
import cv2
from mobly import test_runner
import numpy as np
import zoom_capture_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUMBER_OF_CAMERAS_TO_TEST = 0
_NUM_STEPS_PER_SECTION = 10
# YUV only to improve marker detection, JPEG is tested in test_zoom
_TEST_FORMATS = ('yuv',)
# Empirically found zoom ratio for main cameras without custom offset behavior
_WIDE_ZOOM_RATIO_MIN = 2.2
# Empirically found zoom ratio for TELE cameras
_TELE_TRANSITION_ZOOM_RATIO = 5.0
_ZOOM_RATIO_REQUEST_RESULT_DIFF_RTOL = 0.1


class ZoomTestTELE(its_base_test.ItsBaseTest):
  """Test the camera zoom behavior for the TELE camera, if available."""

  def test_zoom_tele(self):
    # Handle subdirectory
    self.scene = 'scene6_tele'
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        # Use logical camera for captures. Physical ID only for result tracking
        hidden_physical_id=None) as cam:
      camera_properties_utils.skip_unless(self.hidden_physical_id is not None)
      props = cam.get_camera_properties()
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      physical_props = cam.get_camera_properties_by_id(self.hidden_physical_id)
      is_tele = cam.get_camera_type(physical_props) == (
          its_session_utils.CAMERA_TYPE_TELE)
      logging.debug('is_tele: %s', is_tele)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props) and is_tele)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          # Ensure markers are large enough by loading unscaled chart
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Determine test zoom range
      z_range = props['android.control.zoomRatioRange']
      debug = self.debug_mode
      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(
          z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH)
      z_min = max(z_min, _WIDE_ZOOM_RATIO_MIN)  # Force W
      tele_transition_zoom_ratio = min(z_max, _TELE_TRANSITION_ZOOM_RATIO)
      # Increase data near transition ratio
      transition_z_list = np.arange(
          z_min,
          tele_transition_zoom_ratio,
          (tele_transition_zoom_ratio - z_min) / (_NUM_STEPS_PER_SECTION - 1)
      )
      tele_z_list = np.array([])
      if z_max > tele_transition_zoom_ratio:
        tele_z_list = np.arange(
            tele_transition_zoom_ratio,
            z_max,
            (z_max - tele_transition_zoom_ratio) / (_NUM_STEPS_PER_SECTION - 1)
        )
      z_list = np.unique(np.concatenate((transition_z_list, tele_z_list)))
      z_list = np.append(z_list, z_max)
      logging.debug('Testing zoom range: %s', str(z_list))

      # Set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, size = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        focal_lengths = props['android.lens.info.availableFocalLengths']
        logging.debug('focal lengths: %s', focal_lengths)
        for fl in focal_lengths:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
        yuv_size = capture_request_utils.get_largest_format('yuv', props)
        size = [yuv_size['width'], yuv_size['height']]
      logging.debug('capture size: %s', str(size))
      logging.debug('test TOLs: %s', str(test_tols))

      # Do captures over zoom range and find ArUco markers with cv2
      img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
      req = capture_request_utils.auto_capture_request()
      test_failed = False

      for fmt in _TEST_FORMATS:
        logging.debug('testing %s format', fmt)
        test_data = []
        all_aruco_ids = []
        all_aruco_corners = []
        images = []
        found_markers = False
        for z in z_list:
          req['android.control.zoomRatio'] = z
          logging.debug('zoom ratio: %.3f', z)
          cam.do_3a(
              zoom_ratio=z,
              out_surfaces={
                  'format': fmt,
                  'width': size[0],
                  'height': size[1]
              },
              repeat_request=None,
          )
          cap = cam.do_capture(
              req, {'format': fmt, 'width': size[0], 'height': size[1]},
              reuse_session=True)
          cap_physical_id = (
              cap['metadata']['android.logicalMultiCamera.activePhysicalId']
          )
          cap_zoom_ratio = float(cap['metadata']['android.control.zoomRatio'])
          if not math.isclose(cap_zoom_ratio, z,
                              rel_tol=_ZOOM_RATIO_REQUEST_RESULT_DIFF_RTOL):
            raise AssertionError(
                'Request and result zoom ratios too different! '
                f'Request zoom ratio: {z}. '
                f'Result zoom ratio: {cap_zoom_ratio}. ',
                f'RTOL: {_ZOOM_RATIO_REQUEST_RESULT_DIFF_RTOL}'
            )
          img = image_processing_utils.convert_capture_to_rgb_image(
              cap, props=props)
          img_name = (f'{img_name_stem}_{fmt}_{z:.2f}.'
                      f'{zoom_capture_utils.JPEG_STR}')
          image_processing_utils.write_image(img, img_name)

          # Determine radius tolerance of capture
          cap_fl = cap['metadata']['android.lens.focalLength']
          radius_tol, offset_tol = test_tols.get(
              cap_fl,
              (zoom_capture_utils.RADIUS_RTOL, zoom_capture_utils.OFFSET_RTOL)
          )

          # Find ArUco markers
          bgr_img = cv2.cvtColor(
              image_processing_utils.convert_image_to_uint8(img),
              cv2.COLOR_RGB2BGR
          )
          try:
            corners, ids, _ = opencv_processing_utils.find_aruco_markers(
                bgr_img,
                (f'{img_name_stem}_{fmt}_{z:.2f}_'
                 f'ArUco.{zoom_capture_utils.JPEG_STR}'),
                aruco_marker_count=1
            )
            found_markers = True
          except AssertionError as e:
            logging.debug('Could not find ArUco marker at zoom ratio %.2f: %s',
                          z, e)
            if found_markers:
              logging.debug('No more ArUco markers found at zoom %.2f', z)
              break
            else:
              logging.debug('Still no ArUco markers found at zoom %.2f', z)
              continue
          all_aruco_corners.append([corner[0] for corner in corners])
          all_aruco_ids.append([id[0] for id in ids])
          images.append(bgr_img)

          test_data.append(
              zoom_capture_utils.ZoomTestData(
                  result_zoom=cap_zoom_ratio,
                  radius_tol=radius_tol,
                  offset_tol=offset_tol,
                  focal_length=cap_fl,
                  physical_id=cap_physical_id,
              )
          )

        # Find ArUco markers in all captures and update test data
        zoom_capture_utils.update_zoom_test_data_with_shared_aruco_marker(
            test_data, all_aruco_ids, all_aruco_corners, size)
        test_artifacts_name_stem = f'{img_name_stem}_{fmt}'
        # Mark ArUco marker center and image center
        opencv_processing_utils.mark_zoom_images(
            images, test_data, test_artifacts_name_stem)

        if not zoom_capture_utils.verify_zoom_data(
            test_data, size,
            offset_plot_name_stem=test_artifacts_name_stem,
            number_of_cameras_to_test=_NUMBER_OF_CAMERAS_TO_TEST):
          test_failed = True

    if test_failed:
      failure_message = f'{_NAME} failed! Check test_log.DEBUG for errors'
      if first_api_level >= its_session_utils.ANDROID16_API_LEVEL:
        raise AssertionError(failure_message)
      else:
        raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                             f'\n\n{failure_message}')

if __name__ == '__main__':
  test_runner.main()
