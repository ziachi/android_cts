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
"""Verifies changing AE/AWB regions changes images AE/AWB results."""


import logging
import math
import os.path

from mobly import test_runner
import numpy

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import opencv_processing_utils
import video_processing_utils

_AE_CHANGE_THRESH = 1  # Incorrect behavior is empirically < 0.5 percent
_AWB_CHANGE_THRESH = 2  # Incorrect behavior is empirically < 1.5 percent
_AE_AWB_METER_WEIGHT = 1000  # 1 - 1000 with 1000 as the highest
_ARUCO_MARKERS_COUNT = 4
_AE_AWB_REGIONS_AVAILABLE = 1  # Valid range is >= 0, and unavailable if 0
_IMG_FORMAT = 'png'
_MIRRORED_PREVIEW_SENSOR_ORIENTATIONS = (0, 180)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_AE_AWB_REGIONS = 4
_NUM_FRAMES = 4
_PERCENTAGE = 100
_REGION_DURATION_MS = 1800  # 1.8 seconds


def _do_ae_check(light, dark, file_name_with_path):
  """Checks luma change between two images is above threshold.

  Checks that the Y-average of image with darker metering region
  is higher than the Y-average of image with lighter metering
  region. Y stands for brightness, or "luma".

  Args:
    light: RGB image; metering light region.
    dark: RGB image; metering dark region.
    file_name_with_path: str; path to preview recording.
  """
  # Converts img to YUV and returns Y-average
  light_y = opencv_processing_utils.convert_to_y(light, 'RGB')
  light_y_avg = numpy.average(light_y)
  dark_y = opencv_processing_utils.convert_to_y(dark, 'RGB')
  dark_y_avg = numpy.average(dark_y)
  logging.debug('Light image Y-average: %.4f', light_y_avg)
  logging.debug('Dark image Y-average: %.4f', dark_y_avg)
  # Checks average change in Y-average between two images
  y_avg_change = (
      (dark_y_avg-light_y_avg)/light_y_avg)*_PERCENTAGE
  logging.debug('Y-average percentage change: %.4f', y_avg_change)

  # Don't change print to logging. Used for KPI.
  print(f'{_NAME}_ae_y_change: ', y_avg_change)

  if y_avg_change < _AE_CHANGE_THRESH:
    raise AssertionError(
        f'Luma change {y_avg_change} is less than the threshold: '
        f'{_AE_CHANGE_THRESH}')
  else:
    its_session_utils.remove_mp4_file(file_name_with_path)


def _do_awb_check(blue, yellow):
  """Checks the ratio of red over blue between two RGB images.

  Checks that the R/B of image with blue metering region
  is higher than the R/B of image with yellow metering
  region.

  Args:
    blue: RGB image; metering blue region.
    yellow: RGB image; metering yellow region.
  Returns:
    failure_messages: (list of strings) of error messages.
  """
  # Calculates average red value over average blue value in images
  blue_r_b_ratio = _get_red_blue_ratio(blue)
  yellow_r_b_ratio = _get_red_blue_ratio(yellow)
  logging.debug('Blue image R/B ratio: %s', blue_r_b_ratio)
  logging.debug('Yellow image R/B ratio: %s', yellow_r_b_ratio)
  # Calculates change in red over blue values between two images
  r_b_ratio_change = (
      (blue_r_b_ratio-yellow_r_b_ratio)/yellow_r_b_ratio)*_PERCENTAGE
  logging.debug('R/B ratio change in percentage: %.4f', r_b_ratio_change)

  # Don't change print to logging. Used for KPI.
  print(f'{_NAME}_awb_rb_change: ', r_b_ratio_change)

  if r_b_ratio_change < _AWB_CHANGE_THRESH:
    raise AssertionError(
        f'R/B ratio change {r_b_ratio_change} is less than the'
        f' threshold: {_AWB_CHANGE_THRESH}')


def _extract_and_process_select_frames_from_recording(
    log_path, file_name, video_fps):
  """Extract key frames (1 frame per 2 seconds) from recordings.

  Args:
    log_path: str; file location.
    file_name: str; file name for saved video.
    video_fps: str; numerical value of supported video fps.
  Returns:
    dictionary of images.
  """
  # TODO: b/330382627 - Add function to preview_processing_utils
  # Extract key frames from video
  frames = video_processing_utils.extract_all_frames_from_video(
      log_path, file_name, _IMG_FORMAT, video_fps)
  logging.debug('Number of frames %d', len(frames))
  # Minus one from interval to avoid going out of bounds
  interval = math.floor(len(frames) / _NUM_FRAMES) - 1
  logging.debug('Interval %d', interval)

  # Process select frame files
  select_frames = []
  save_files = [os.path.join(log_path, file_name)]
  for i, frame in enumerate(frames):
    frame_path = os.path.join(log_path, frame)
    if (i % interval == 0) and (i > 0):
      select_frames.append(
          image_processing_utils.convert_image_to_numpy_array(frame_path))
      save_files.append(frame_path)
    else:
      continue
  logging.debug('Frame size %d x %d', select_frames[0].shape[1],
                select_frames[0].shape[0])
  logging.debug('Number of select frames %d', len(select_frames))
  its_session_utils.remove_frame_files(log_path, save_files)
  return select_frames


def _get_red_blue_ratio(img):
  """Computes the ratios of average red over blue in img.

  Args:
    img: numpy array; RGB image.
  Returns:
    r_b_ratio: float; ratio of R and B channel means.
  """
  img_means = image_processing_utils.compute_image_means(img)
  r_b_ratio = img_means[0]/img_means[2]
  return r_b_ratio


class AeAwbRegions(its_base_test.ItsBaseTest):
  """Tests that changing AE and AWB regions changes image's RGB values.

  Test records an 8 seconds preview recording, and meters a different
  AE/AWB region (blue, light, dark, yellow) for every 2 seconds.
  Extracts a frame from each second of recording with a total of 8 frames
  (2 from each region). For AE check, a frame from light is compared to the
  dark region. For AWB check, a frame from blue is compared to the yellow
  region.

  """

  def test_ae_awb_regions(self):
    """Test AE and AWB regions."""

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      test_name_with_log_path = os.path.join(log_path, _NAME)

      # Check skip conditions
      max_ae_regions = props['android.control.maxRegionsAe']
      max_awb_regions = props['android.control.maxRegionsAwb']
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          camera_properties_utils.ae_regions(props) and
          (max_awb_regions >= _AE_AWB_REGIONS_AVAILABLE or
           max_ae_regions >= _AE_AWB_REGIONS_AVAILABLE))
      logging.debug('maximum AE regions: %d', max_ae_regions)
      logging.debug('maximum AWB regions: %d', max_awb_regions)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          log_path)

      # Find largest preview size to define capture size to find aruco markers
      common_preview_size_info = (
          video_processing_utils.get_preview_video_sizes_union(
              cam, self.camera_id))
      preview_size = common_preview_size_info.largest_size
      width = int(preview_size.split('x')[0])
      height = int(preview_size.split('x')[1])
      req = capture_request_utils.auto_capture_request()
      fmt = {'format': 'yuv', 'width': width, 'height': height}
      cam.do_3a()
      cap = cam.do_capture(req, fmt)

      # Save image and convert to numpy array
      img = image_processing_utils.convert_capture_to_rgb_image(
          cap, props=props)
      img_path = f'{test_name_with_log_path}_aruco_markers.jpg'
      image_processing_utils.write_image(img, img_path)
      img = image_processing_utils.convert_image_to_uint8(img)

      # Define AE/AWB metering regions
      chart_path = f'{test_name_with_log_path}_chart_boundary.jpg'
      ae_awb_regions = opencv_processing_utils.define_regions(
          img, img_path, chart_path, props, width, height)

      # Do preview recording with pre-defined AE/AWB regions
      recording_obj = cam.do_preview_recording_with_dynamic_ae_awb_region(
          preview_size, ae_awb_regions, _REGION_DURATION_MS)
      logging.debug('Tested quality: %s', recording_obj['quality'])

      # Grab the video from the save location on DUT
      self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
      file_name = recording_obj['recordedOutputPath'].split('/')[-1]
      file_name_with_path = os.path.join(log_path, file_name)
      logging.debug('file_name: %s', file_name)

      # Determine acceptable ranges
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)
      ae_target_fps_range = camera_properties_utils.get_fps_range_to_test(
          fps_ranges)
      video_fps = str(ae_target_fps_range[0])

      # Extract 1 frames per 2 seconds of preview recording
      # Meters each region of 4 (blue, light, dark, yellow) for 2 seconds
      # Unpack frames based on metering region's color
      # If testing front camera with preview mirrored, reverse order.
      # pylint: disable=unbalanced-tuple-unpacking
      if ((props['android.lens.facing'] ==
           camera_properties_utils.LENS_FACING['FRONT']) and
          props['android.sensor.orientation'] in
          _MIRRORED_PREVIEW_SENSOR_ORIENTATIONS):
        yellow, dark, light, blue = (
            _extract_and_process_select_frames_from_recording(
                log_path, file_name, video_fps))
      else:
        blue, light, dark, yellow = (
            _extract_and_process_select_frames_from_recording(
                log_path, file_name, video_fps))

      # AWB Check : Verify R/B ratio change is greater than threshold
      if max_awb_regions >= _AE_AWB_REGIONS_AVAILABLE:
        _do_awb_check(blue, yellow)

      # AE Check: Extract the Y component from rectangle patch
      if max_ae_regions >= _AE_AWB_REGIONS_AVAILABLE:
        _do_ae_check(light, dark, file_name_with_path)

if __name__ == '__main__':
  test_runner.main()
