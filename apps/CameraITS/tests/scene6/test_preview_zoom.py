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


_CRF = 23
_CV2_RED = (0, 0, 255)  # color (B, G, R) in cv2 to draw lines
_CV2_FLIP_ACROSS_X_AXIS = 0
_CV2_FLIP_ACROSS_Y_AXIS = 1
_SENSOR_ORIENTATIONS_X_AXIS_MIRRORING = (90, 270)
_FPS = 30
_MINIMUM_ARUCO_MARKERS_TO_DETECT = 1
_MP4V = 'mp4v'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 50
_SINGLE_CAMERA_NUMBER_OF_CAMERAS_TO_TEST = 1
_STANDARD_TEST_MAX_ZOOM_RATIO = 4.0
_ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST = 2  # UW and W
_WIDE_ONLY_ZOOM_RATIO_THRESHOLD = 4.0
_WIDE_ZOOM_THIRD_CAMERA_CHECK_ZOOM_RATIO = 2.0
_WIDE_ZOOM_RATIO_MAX = 2.5

# Note: b/284232490: 1080p could be 1088. 480p could be 704 or 640 too.
#       Use for tests not sensitive to variations of 1080p or 480p.
# TODO: b/370841141 - Remove usage of VIDEO_PREVIEW_QUALITY_SIZE.
#                     Create and use get_supported_video_sizes instead of
#                     get_supported_video_qualities.
_VIDEO_PREVIEW_QUALITY_SIZE = {
    # 'HIGH' and 'LOW' not included as they are DUT-dependent
    '4KDC': '4096x2160',
    '2160P': '3840x2160',
    'QHD': '2560x1440',
    '2k': '2048x1080',
    '1080P': '1920x1080',
    '720P': '1280x720',
    '480P': '720x480',
    'VGA': '640x480',
    'CIF': '352x288',
    'QVGA': '320x240',
    'QCIF': '176x144',
}


def get_largest_video_size(cam, camera_id):
  """Returns the largest supported video size and its area.

  Determine largest supported video size and its area from
  get_supported_video_qualities.

  Args:
    cam: camera object.
    camera_id: str; camera ID.

  Returns:
    max_size: str; largest supported video size in the format 'widthxheight'.
    max_area: int; area of the largest supported video size.
  """
  supported_video_qualities = cam.get_supported_video_qualities(camera_id)
  logging.debug('Supported video profiles & IDs: %s',
                supported_video_qualities)

  quality_keys = [
      quality.split(':')[0]
      for quality in supported_video_qualities
  ]
  logging.debug('Quality keys: %s', quality_keys)

  supported_video_sizes = [
      _VIDEO_PREVIEW_QUALITY_SIZE[key]
      for key in quality_keys
      if key in _VIDEO_PREVIEW_QUALITY_SIZE
  ]
  logging.debug('Supported video sizes: %s', supported_video_sizes)

  if not supported_video_sizes:
    raise AssertionError('No supported video sizes found!')

  size_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  max_size = max(supported_video_sizes, key=size_to_area)

  logging.debug('Largest video size: %s', max_size)
  return size_to_area(max_size)


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


class PreviewZoomTest(its_base_test.ItsBaseTest):
  """Verify zoom ratio of preview frames matches values in TotalCaptureResult."""

  def test_preview_zoom(self):
    log_path = self.log_path
    video_processing_utils.log_ffmpeg_version()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      debug = self.debug_mode

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      ultrawide_camera_found = cam.has_ultrawide_camera(
          facing=props['android.lens.facing'])
      tele_camera_found = cam.has_tele_camera(
          facing=props['android.lens.facing'])
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      is_front_facing = (
          props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['FRONT']
      )

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

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
      logging.debug('Threshold levels to be used for testing: %s', test_tols)

      largest_area = get_largest_video_size(cam, self.camera_id)

      # get max preview size
      preview_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id, aspect_ratio=None, max_tested_area=largest_area)
      size = [int(x) for x in preview_size.split('x')]
      logging.debug('preview_size = %s', preview_size)
      logging.debug('size = %s', size)

      # Determine test zoom range and step size
      z_range = props['android.control.zoomRatioRange']
      z_range[1] = min(z_range[1], _STANDARD_TEST_MAX_ZOOM_RATIO)
      # Truncate zoom range if test_preview_zoom_tele will be run
      if tele_camera_found:
        logging.debug('Tele camera found, truncating zoom range max to %.2f',
                      _WIDE_ZOOM_RATIO_MAX)
        z_range[1] = _WIDE_ZOOM_RATIO_MAX
      logging.debug('z_range = %s', str(z_range))
      z_min, z_max, z_step_size = zoom_capture_utils.get_preview_zoom_params(
          z_range, _NUM_STEPS)
      camera_properties_utils.skip_unless(
          z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH)

      # recording preview
      capture_results, file_list = (
          preview_processing_utils.preview_over_zoom_range(
              self.dut, cam, preview_size, z_min, z_max, z_step_size, log_path)
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
        # Ignore captures at higher zooms where smooth zoom can affect results.
        if (tele_camera_found and
            len(physical_ids) > _ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST and
            z > _WIDE_ZOOM_THIRD_CAMERA_CHECK_ZOOM_RATIO):
          logging.debug('Found enough zoom data, given that tele camera found: '
                        '%d physical IDs at zoom ratio %.2f, ignoring '
                        'remaining captures.',
                        len(physical_ids), z)
          z_max = z
          break

        # read image
        img_bgr = cv2.imread(os.path.join(log_path, img_name))
        # flip image across correct axis for front camera (preview is flipped)
        if is_front_facing:
          sensor_orientation = props['android.sensor.orientation']
          if sensor_orientation in _SENSOR_ORIENTATIONS_X_AXIS_MIRRORING:
            logging.debug(
                'Found sensor orientation %d, flipping up down',
                sensor_orientation
            )
            img_bgr = cv2.flip(img_bgr, _CV2_FLIP_ACROSS_X_AXIS)
          else:
            logging.debug(
                'Found sensor orientation %d, flipping left right',
                sensor_orientation
            )
            img_bgr = cv2.flip(img_bgr, _CV2_FLIP_ACROSS_Y_AXIS)
          cv2.imwrite(os.path.join(log_path, img_name), img_bgr)

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
              aruco_marker_count=_MINIMUM_ARUCO_MARKERS_TO_DETECT,
              save_images=debug
          )
        except AssertionError as e:
          logging.debug('Could not find ArUco marker at zoom ratio %.2f: %s',
                        z, e)
          if not tele_camera_found and z > _WIDE_ONLY_ZOOM_RATIO_THRESHOLD:
            logging.debug('No Tele camera found and zoom ratio %.2f is greater '
                          'than threshold %.2f, ignoring remaining captures.',
                          z, _WIDE_ONLY_ZOOM_RATIO_THRESHOLD)
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
      # TODO: b/369852004 - decrease TOL for test_preview_zoom
      number_of_cameras_to_test = (
          _ULTRAWIDE_NUMBER_OF_CAMERAS_TO_TEST
          if ultrawide_camera_found and cam.is_primary_camera()
          else _SINGLE_CAMERA_NUMBER_OF_CAMERAS_TO_TEST
      )
      # Make reporting active physical IDs optional
      if all(d.physical_id is None for d in test_data):
        number_of_cameras_to_test = 0
      if not zoom_capture_utils.verify_preview_zoom_results(
          test_data, size, z_max, z_min, z_step_size, plot_name_stem,
          number_of_cameras_to_test=number_of_cameras_to_test):
        first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
        failure_msg = f'{_NAME} failed! Check test_log.DEBUG for errors'
        if first_api_level >= its_session_utils.ANDROID15_API_LEVEL:
          raise AssertionError(failure_msg)
        else:
          raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                               f'\n\n{failure_msg}')

if __name__ == '__main__':
  test_runner.main()
