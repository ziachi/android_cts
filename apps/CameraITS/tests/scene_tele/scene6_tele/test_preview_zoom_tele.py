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
"""Verify preview zoom ratio scales ArUco marker sizes correctly."""

import logging
import os.path
import subprocess

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import opencv_processing_utils
import preview_processing_utils
import video_processing_utils
import zoom_capture_utils


_CRF = 23  # Constant rate factor for video compression
_CV2_RED = (0, 0, 255)  # color (B, G, R) in cv2 to draw lines
_FPS = 30
_MP4V = 'mp4v'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 50
_NUMBER_OF_CAMERAS_TO_TEST = 0
_WIDE_ZOOM_RATIO_MAX = 2.5


# TODO: b/390003966 - move compress_video() to video_processing_utils.
def compress_video(input_filename, output_filename, crf=_CRF):
  """Compresses the given video using ffmpeg."""

  ffmpeg_cmd = [
      'ffmpeg',
      '-i', input_filename,   # Input file
      '-c:v', 'libx264',      # Use H.264 codec
      '-crf', str(crf),       # Set Constant Rate Factor (adjust for quality)
      '-preset', 'medium',    # Encoding speed/compression balance
      '-c:a', 'copy',         # Copy audio stream without re-encoding
      output_filename         # Output file
  ]

  with open(os.devnull, 'w') as devnull:
    subprocess.run(ffmpeg_cmd, stdout=devnull,
                   stderr=subprocess.STDOUT, check=False)


def _get_test_tols(cam, props, chart_distance, debug):
  """Returns test tolerances for each focal length.

  Args:
    cam: its_session_utils.ItsSession object.
    props: dict, camera properties.
    chart_distance: float, chart distance in cm.
    debug: bool, whether to run in debug mode.
  Returns:
    dict, focal length to (radius tolerance, offset tolerance).
  """
  if camera_properties_utils.logical_multi_camera(props):
    test_tols, _ = zoom_capture_utils.get_test_tols_and_cap_size(
        cam, props, chart_distance, debug)
  else:
    test_tols = {}
    fls = props['android.lens.info.availableFocalLengths']
    for fl in fls:
      test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                       zoom_capture_utils.OFFSET_RTOL)
  logging.debug('Threshold levels to be used for testing: %s', test_tols)
  return test_tols


class PreviewZoomTestTELE(its_base_test.ItsBaseTest):
  """Verify zoom ratio of preview frames matches values in TotalCaptureResult."""

  def test_preview_zoom_tele(self):
    # Handle subdirectory
    self.scene = 'scene6_tele'
    log_path = self.log_path
    video_processing_utils.log_ffmpeg_version()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        # Use logical camera for captures. Physical ID only for result tracking
        hidden_physical_id=None) as cam:
      camera_properties_utils.skip_unless(self.hidden_physical_id is not None)

      debug = self.debug_mode

      props = cam.get_camera_properties()
      physical_props = cam.get_camera_properties_by_id(self.hidden_physical_id)
      is_tele = cam.get_camera_type(physical_props) == (
          its_session_utils.CAMERA_TYPE_TELE)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props) and is_tele)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          # Ensure markers are large enough by loading unscaled chart
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # set TOLs based on camera and test rig params
      test_tols = _get_test_tols(cam, props, self.chart_distance, debug)

      # get max preview size
      preview_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id)
      size = [int(x) for x in preview_size.split('x')]

      # Determine test zoom range and step size
      z_range = props['android.control.zoomRatioRange']
      logging.debug('z_range = %s', str(z_range))
      z_min, z_max, z_step_size = zoom_capture_utils.get_preview_zoom_params(
          [_WIDE_ZOOM_RATIO_MAX, z_range[1]], _NUM_STEPS)
      camera_properties_utils.skip_unless(
          z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH and
          z_min > z_range[0])

      cam.do_3a(zoom_ratio=z_min)
      capture_results, file_list = (
          preview_processing_utils.preview_over_zoom_range(
              self.dut, cam, preview_size, z_min, z_max,
              z_step_size, log_path)
      )

      test_data = []
      test_data_index = 0
      all_aruco_ids = []
      all_aruco_corners = []
      img_paths = []
      # Initialize video writer
      fourcc = cv2.VideoWriter_fourcc(*_MP4V)
      uncompressed_video = os.path.join(log_path,
                                        'output_frames_uncompressed.mp4')
      out = cv2.VideoWriter(uncompressed_video, fourcc, _FPS,
                            (size[0], size[1]))

      physical_ids = set()
      for capture_result, img_name in zip(capture_results, file_list):
        z = float(capture_result['android.control.zoomRatio'])
        if camera_properties_utils.logical_multi_camera(props):
          phy_id = capture_result['android.logicalMultiCamera.activePhysicalId']
        else:
          phy_id = None
        if phy_id:
          physical_ids.add(phy_id)
        logging.debug('Physical IDs: %s', physical_ids)
        logging.debug('Physical ID: %s, zoom: %s', phy_id, z)

        # read image
        img_bgr = cv2.imread(os.path.join(log_path, img_name))

        # add path to image name
        img_path = f'{os.path.join(self.log_path, img_name)}'

        # determine radius tolerance of capture
        cap_fl = capture_result['android.lens.focalLength']
        radius_tol, offset_tol = test_tols.get(
            cap_fl,
            (zoom_capture_utils.RADIUS_RTOL, zoom_capture_utils.OFFSET_RTOL)
        )

        # Find ArUco markers
        try:
          corners, ids, _ = opencv_processing_utils.find_aruco_markers(
              img_bgr,
              (f'{os.path.join(log_path, img_name)}_{z:.2f}_'
               f'ArUco.{zoom_capture_utils.JPEG_STR}'),
              aruco_marker_count=1,
              save_images=debug
          )
        except AssertionError as e:
          logging.debug('Could not find ArUco marker at zoom ratio %.2f: %s',
                        z, e)
          z_max = z
          break

        all_aruco_corners.append([corner[0] for corner in corners])
        all_aruco_ids.append([id[0] for id in ids])
        img_paths.append(img_path)

        test_data.append(
            zoom_capture_utils.ZoomTestData(
                result_zoom=z,
                radius_tol=radius_tol,
                offset_tol=offset_tol,
                focal_length=cap_fl,
                physical_id=phy_id
            )
        )

        logging.debug('test_data[%d] = %s', test_data_index,
                      test_data[test_data_index])
        test_data_index = test_data_index + 1

      # Find ArUco markers in all captures and update test data
      zoom_capture_utils.update_zoom_test_data_with_shared_aruco_marker(
          test_data, all_aruco_ids, all_aruco_corners, size)
      # Mark ArUco marker center and image center
      opencv_processing_utils.mark_zoom_images_to_video(
          out, img_paths, test_data)

      out.release()
      # Remove png files
      for path in img_paths:
        its_session_utils.remove_file(path)

      # --- Compress Video ---
      compressed_video = os.path.join(log_path, 'output_frames.mp4')
      compress_video(uncompressed_video, compressed_video)

      os.remove(uncompressed_video)

      plot_name_stem = f'{os.path.join(log_path, _NAME)}'
      if not zoom_capture_utils.verify_preview_zoom_results(
          test_data, size, z_max, z_min, z_step_size, plot_name_stem,
          number_of_cameras_to_test=_NUMBER_OF_CAMERAS_TO_TEST):
        first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
        failure_msg = f'{_NAME} failed! Check test_log.DEBUG for errors'
        if first_api_level >= its_session_utils.ANDROID16_API_LEVEL:
          raise AssertionError(failure_msg)
        else:
          raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                               f'\n\n{failure_msg}')

if __name__ == '__main__':
  test_runner.main()
