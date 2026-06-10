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
"""Utility functions for verifying preview stabilization.
"""

import cv2
import logging
import os
import threading
import time

import camera_properties_utils
import its_session_utils
import sensor_fusion_utils
import video_processing_utils

_AREA_720P_VIDEO = 1280 * 720
_ASPECT_RATIO_16_9 = 16/9  # determine if preview fmt > 16:9
_ASPECT_TOL = 0.01
_GREEN_TOL = 200  # 200 out of 255 Green value in RGB
_GREEN_PERCENT = 95
_HIGH_RES_SIZE = '3840x2160'  # Resolution for 4K quality
_IMG_FORMAT = 'png'
_MIN_PHONE_MOVEMENT_ANGLE = 5  # degrees
_NUM_ROTATIONS = 24
_PREVIEW_DURATION = 400  # milliseconds
_PREVIEW_MAX_TESTED_AREA = 1920 * 1440
_PREVIEW_MIN_TESTED_AREA = 320 * 240
_PREVIEW_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed
_RED_BLUE_TOL = 20  # 20 out of 255 Red or Blue value in RGB
_SKIP_INITIAL_FRAMES = 15
_START_FRAME = 30  # give 3A some frames to warm up
_VIDEO_DELAY_TIME = 5.5  # seconds
_VIDEO_DURATION = 5.5  # seconds


def get_720p_or_above_size(supported_preview_sizes):
  """Returns the smallest size above or equal to 720p in preview and video.

  If the largest preview size is under 720P, returns the largest value.

  Args:
    supported_preview_sizes: list; preview sizes.
      e.g. ['1920x960', '1600x1200', '1920x1080']
  Returns:
    smallest size >= 720p video format
  """

  size_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  smallest_area = float('inf')
  smallest_720p_or_above_size = ''
  largest_supported_preview_size = ''
  largest_area = 0
  for size in supported_preview_sizes:
    area = size_to_area(size)
    if smallest_area > area >= _AREA_720P_VIDEO:
      smallest_area = area
      smallest_720p_or_above_size = size
    else:
      if area > largest_area:
        largest_area = area
        largest_supported_preview_size = size

  if largest_area > _AREA_720P_VIDEO:
    logging.debug('Smallest 720p or above size: %s',
                  smallest_720p_or_above_size)
    return smallest_720p_or_above_size
  else:
    logging.debug('Largest supported preview size: %s',
                  largest_supported_preview_size)
    return largest_supported_preview_size


def collect_data(cam, tablet_device, preview_size, stabilize, rot_rig,
                 zoom_ratio=None, fps_range=None, hlg10=False, ois=False):
  """Capture a new set of data from the device.

  Captures camera preview frames while the user is moving the device in
  the prescribed manner.

  Args:
    cam: camera object.
    tablet_device: boolean; based on config file.
    preview_size: str; preview stream resolution. ex. '1920x1080'
    stabilize: boolean; whether preview stabilization is ON.
    rot_rig: dict with 'cntl' and 'ch' defined.
    zoom_ratio: float; static zoom ratio. None if default zoom.
    fps_range: list; target fps range.
    hlg10: boolean; whether to capture hlg10 output.
    ois: boolean; whether optical image stabilization is ON.
  Returns:
    recording object; a dictionary containing output path, video size, etc.
  """

  output_surfaces = cam.preview_surface(preview_size, hlg10)
  video_stream_index = 0
  stabilize_mode = camera_properties_utils.STABILIZATION_MODE_OFF
  if stabilize:
    stabilize_mode = camera_properties_utils.STABILIZATION_MODE_PREVIEW
  return collect_data_with_surfaces(cam, tablet_device, output_surfaces,
                                    video_stream_index, stabilize_mode, rot_rig,
                                    zoom_ratio, fps_range, ois)


def collect_data_with_surfaces(cam, tablet_device, output_surfaces,
                               video_stream_index, stabilize_mode, rot_rig,
                               zoom_ratio=None, fps_range=None, ois=False):
  """Capture a new set of data from the device.

  Captures camera preview frames while the user is moving the device in
  the prescribed manner.

  Args:
    cam: camera object.
    tablet_device: boolean; based on config file.
    output_surfaces: list of dict; The list of output surfaces configured for
      the recording. Only the first surface is used for recording; the rest are
      configured, but not requested.
    video_stream_index: The index of output surface used for recording
    stabilize_mode: int; Video stabilization mode.
    rot_rig: dict with 'cntl' and 'ch' defined.
    zoom_ratio: float; static zoom ratio. None if default zoom.
    fps_range: list; target fps range.
    ois: boolean; whether optical image stabilization is ON.
  Returns:
    recording object; a dictionary containing output path, video size, etc.
  """

  logging.debug('Starting sensor event collection')
  serial_port = None
  if rot_rig['cntl'].lower() == sensor_fusion_utils.ARDUINO_STRING.lower():
    # identify port
    serial_port = sensor_fusion_utils.serial_port_def(
        sensor_fusion_utils.ARDUINO_STRING)
    # send test cmd to Arduino until cmd returns properly
    sensor_fusion_utils.establish_serial_comm(serial_port)
  # Start camera vibration
  if tablet_device:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION_TABLET
  else:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION
  p = threading.Thread(
      target=sensor_fusion_utils.rotation_rig,
      args=(
          rot_rig['cntl'],
          rot_rig['ch'],
          _NUM_ROTATIONS,
          sensor_fusion_utils.ARDUINO_ANGLES_STABILIZATION,
          servo_speed,
          sensor_fusion_utils.ARDUINO_MOVE_TIME_STABILIZATION,
          serial_port,
      ),
  )
  p.start()

  cam.start_sensor_events()
  # Allow time for rig to start moving
  time.sleep(_VIDEO_DELAY_TIME)

  # Record video and return recording object
  min_fps = fps_range[0] if (fps_range is not None) else None
  max_fps = fps_range[1] if (fps_range is not None) else None
  recording_obj = cam.do_preview_recording_multiple_surfaces(
      output_surfaces, video_stream_index, _VIDEO_DURATION, stabilize_mode, ois,
      zoom_ratio=zoom_ratio, ae_target_fps_min=min_fps,
      ae_target_fps_max=max_fps
  )

  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  # Wait for vibration to stop
  p.join()

  return recording_obj


def verify_preview_stabilization(recording_obj, gyro_events, test_name,
                                 log_path, facing, zoom_ratio=None,
                                 stabilization_mode=True):
  """Verify the returned recording is properly stabilized.

  Args:
    recording_obj: Camcorder recording object.
    gyro_events: Gyroscope events collected while recording.
    test_name: Name of the test.
    log_path: Path for the log file.
    facing: Facing of the camera device.
    zoom_ratio: Static zoom ratio. None if default zoom.
    stabilization_mode: boolean; Whether stabilization mode is ON.

  Returns:
    A dictionary containing the maximum gyro angle, the maximum camera angle,
    and a failure message if the recorded video isn't properly stablilized.
  """

  file_name = recording_obj['recordedOutputPath'].split('/')[-1]
  logging.debug('recorded file name: %s', file_name)
  video_size = recording_obj['videoSize']
  logging.debug('video size: %s', video_size)

  # Get all frames from the video
  file_list = video_processing_utils.extract_all_frames_from_video(
      log_path, file_name, _IMG_FORMAT
  )

  logging.debug('Number of frames %d', len(file_list))
  # Extract camera rotations
  if zoom_ratio:
    zoom_ratio_suffix = f'{zoom_ratio:.1f}'
  else:
    zoom_ratio_suffix = '1'
  file_name_stem = (
      f'{os.path.join(log_path, test_name)}_{video_size}_{zoom_ratio_suffix}x'
      f'_stabilization={stabilization_mode}')
  cam_rots = sensor_fusion_utils.get_cam_rotations_from_files(
      file_list[_START_FRAME:],
      facing,
      file_name_stem,
      log_path,
      _START_FRAME,
      stabilized_video=stabilization_mode
  )
  sensor_fusion_utils.plot_camera_rotations(cam_rots, _START_FRAME,
                                            video_size, file_name_stem)
  max_camera_angle = sensor_fusion_utils.calc_max_rotation_angle(
      cam_rots, 'Camera')

  # Extract gyro rotations
  sensor_fusion_utils.plot_gyro_events(
      gyro_events,
      f'{test_name}_{video_size}_{zoom_ratio_suffix}x'
      f'_stabilization={stabilization_mode}',
      log_path
  )
  gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
      gyro_events, _VIDEO_DELAY_TIME)
  max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
      gyro_rots, 'Gyro')
  logging.debug('Stabilization mode: %s', stabilization_mode)
  logging.debug(
      'Max deflection (degrees) %s: video: %.3f, gyro: %.3f ratio: %.4f',
      video_size, max_camera_angle, max_gyro_angle,
      max_camera_angle / max_gyro_angle)

  # Assert phone is moved enough during test
  if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
    raise AssertionError(
        f'Phone not moved enough! Movement: {max_gyro_angle}, '
        f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

  w_x_h = video_size.split('x')
  if int(w_x_h[0])/int(w_x_h[1]) > _ASPECT_RATIO_16_9:
    preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR * 1.1
  else:
    preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR

  failure_msg = None
  if max_camera_angle >= max_gyro_angle * preview_stabilization_factor:
    # Fail if stabilization mode is on
    if stabilization_mode:
      failure_msg = (
          f'{video_size} preview not stabilized enough! '
          f'Max preview angle: {max_camera_angle:.3f}, '
          f'Max gyro angle: {max_gyro_angle:.3f}, '
          f'ratio: {max_camera_angle/max_gyro_angle:.3f} '
          f'THRESH: {preview_stabilization_factor}.')
  else:
    # Fail if stabilization mode is off
    if not stabilization_mode:
      failure_msg = (
          f'{video_size} preview is stabilized when testing stabilization=OFF! '
          f'Max preview angle: {max_camera_angle:.3f}, '
          f'Max gyro angle: {max_gyro_angle:.3f}, '
          f'ratio: {max_camera_angle/max_gyro_angle:.3f} '
          f'THRESH: {preview_stabilization_factor}.')

  # Delete saved frames if the format is a PASS
  if not failure_msg:
    for file in file_list:
      try:
        os.remove(os.path.join(log_path, file))
      except FileNotFoundError:
        logging.debug('File Not Found: %s', str(file))
    logging.debug('Format %s passes, frame images removed', video_size)

  return {'gyro': max_gyro_angle, 'cam': max_camera_angle,
          'failure': failure_msg}


def collect_preview_data_with_zoom(cam, preview_size, zoom_start,
                                   zoom_end, step_size, recording_duration_ms,
                                   padded_frames=False):
  """Captures a preview video from the device.

  Captures camera preview frames from the passed device.

  Args:
    cam: camera object.
    preview_size: str; preview resolution. ex. '1920x1080'.
    zoom_start: (float) is the starting zoom ratio during recording.
    zoom_end: (float) is the ending zoom ratio during recording.
    step_size: (float) is the step for zoom ratio during recording.
    recording_duration_ms: preview recording duration in ms.
    padded_frames: boolean; Whether to add additional frames at the beginning
      and end of recording to workaround issue with MediaRecorder.

  Returns:
    recording object as described by cam.do_preview_recording_with_dynamic_zoom.
  """
  recording_obj = cam.do_preview_recording_with_dynamic_zoom(
      preview_size,
      stabilize=False,
      sweep_zoom=(zoom_start, zoom_end, step_size, recording_duration_ms),
      padded_frames=padded_frames
  )
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])
  return recording_obj


def is_aspect_ratio_match(size_str, target_ratio):
  """Checks if a resolution string matches the target aspect ratio."""
  width, height = map(int, size_str.split('x'))
  return abs(width / height - target_ratio) < _ASPECT_TOL


def get_max_preview_test_size(cam, camera_id, aspect_ratio=None,
                              max_tested_area=_PREVIEW_MAX_TESTED_AREA):
  """Finds the max preview size to be tested.

  If the device supports the _HIGH_RES_SIZE preview size then
  it uses that for testing, otherwise uses the max supported
  preview size capped at max_tested_area.

  Args:
    cam: camera object
    camera_id: str; camera device id under test
    aspect_ratio: preferred aspect_ratio For example: '4/3'
    max_tested_area: area of max preview resolution

  Returns:
    preview_test_size: str; wxh resolution of the size to be tested
  """
  resolution_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  supported_preview_sizes = cam.get_all_supported_preview_sizes(
      camera_id, filter_recordable=True)
  logging.debug('Resolutions supported by preview and MediaRecorder: %s',
                supported_preview_sizes)

  if aspect_ratio is None:
    supported_preview_sizes = [size for size in supported_preview_sizes
                               if resolution_to_area(size)
                               >= video_processing_utils.LOWEST_RES_TESTED_AREA]
  else:
    supported_preview_sizes = [size for size in supported_preview_sizes
                               if resolution_to_area(size)
                               >= video_processing_utils.LOWEST_RES_TESTED_AREA
                               and is_aspect_ratio_match(size, aspect_ratio)]

  logging.debug('Supported preview resolutions: %s', supported_preview_sizes)

  if _HIGH_RES_SIZE in supported_preview_sizes:
    preview_test_size = _HIGH_RES_SIZE
  else:
    capped_supported_preview_sizes = [
        size
        for size in supported_preview_sizes
        if (
            resolution_to_area(size) <= max_tested_area
            and resolution_to_area(size) >= _PREVIEW_MIN_TESTED_AREA
        )
    ]
    logging.debug('Capped preview resolutions: %s',
                  capped_supported_preview_sizes)
    preview_test_size = capped_supported_preview_sizes[-1]

  logging.debug('Selected preview resolution: %s', preview_test_size)

  return preview_test_size


def get_max_extension_preview_test_size(cam, camera_id, extension):
  """Finds the max preview size for an extension to be tested.

  If the device supports the _HIGH_RES_SIZE preview size then
  it uses that for testing, otherwise uses the max supported
  preview size capped at _PREVIEW_MAX_TESTED_AREA.

  Args:
    cam: camera object
    camera_id: str; camera device id under test
    extension: int; camera extension mode under test

  Returns:
    preview_test_size: str; wxh resolution of the size to be tested
  """
  resolution_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  supported_preview_sizes = (
      cam.get_supported_extension_preview_sizes(camera_id, extension))
  supported_preview_sizes = [size for size in supported_preview_sizes
                             if resolution_to_area(size)
                             >= video_processing_utils.LOWEST_RES_TESTED_AREA]
  logging.debug('Supported preview resolutions for extension %d: %s',
                extension, supported_preview_sizes)

  if _HIGH_RES_SIZE in supported_preview_sizes:
    preview_test_size = _HIGH_RES_SIZE
  else:
    capped_supported_preview_sizes = [
        size
        for size in supported_preview_sizes
        if (
            resolution_to_area(size) <= _PREVIEW_MAX_TESTED_AREA
            and resolution_to_area(size) >= _PREVIEW_MIN_TESTED_AREA
        )
    ]
    preview_test_size = capped_supported_preview_sizes[-1]

  logging.debug('Selected preview resolution: %s', preview_test_size)

  return preview_test_size


def is_image_green(image_path):
  """Checks if an image is mostly green.

  Checks if an image is mostly green by ensuring green is dominant
  and red/blue values are low.

  Args:
    image_path: str; The path to the image file.

  Returns:
    bool: True if mostly green, False otherwise.
  """

  image = cv2.imread(image_path)

  green_pixels = ((image[:, :, 1] > _GREEN_TOL) &
                  (image[:, :, 0] < _RED_BLUE_TOL) &
                  (image[:, :, 2] < _RED_BLUE_TOL)).sum()

  green_percentage = (green_pixels / (image.shape[0] * image.shape[1])) * 100

  if green_percentage >= _GREEN_PERCENT:
    return True
  else:
    return False


def preview_over_zoom_range(dut, cam, preview_size, z_min, z_max, z_step_size,
                            log_path):
  """Captures a preview video from the device over zoom range.

  Captures camera preview frames at various zoom level in zoom range.

  Args:
    dut: device under test
    cam: camera object
    preview_size: str; preview resolution. ex. '1920x1080'
    z_min: minimum zoom for preview capture
    z_max: maximum zoom for preview capture
    z_step_size: zoom step size from min to max
    log_path: str; path for video file directory

  Returns:
    capture_results: total capture results of each frame
    file_list: file name for each frame
  """
  logging.debug('z_min : %.2f, z_max = %.2f, z_step_size = %.2f',
                z_min, z_max, z_step_size)

  # Converge 3A
  cam.do_3a()

  # recording preview
  # TODO: b/350821827 - encode time stamps in camera frames instead of
  #                     padded green frams
  # MediaRecorder on some devices drop last few frames. To solve this issue
  # add green frames as padding at the end of recorded camera frames. This way
  # green buffer frames would be droped by MediaRecorder instead of actual
  # frames. Later these green padded frames are removed.
  preview_rec_obj = collect_preview_data_with_zoom(
      cam, preview_size, z_min, z_max, z_step_size,
      _PREVIEW_DURATION, padded_frames=True)

  preview_file_name = its_session_utils.pull_file_from_dut(
      dut, preview_rec_obj['recordedOutputPath'], log_path)

  logging.debug('recorded video size : %s',
                str(preview_rec_obj['videoSize']))

  # Extract frames as png from mp4 preview recording
  file_list = video_processing_utils.extract_all_frames_from_video(
      log_path, preview_file_name, _IMG_FORMAT
  )

  first_camera_frame_idx = 0
  last_camera_frame_idx = len(file_list)

  # Find index of the first-non green frame
  for (idx, file_name) in enumerate(file_list):
    file_path = os.path.join(log_path, file_name)
    if is_image_green(file_path):
      its_session_utils.remove_file(file_path)
      logging.debug('Removed green file %s', file_name)
    else:
      logging.debug('First camera frame: %s', file_name)
      first_camera_frame_idx = idx
      break

  # Find index of last non-green frame
  for (idx, file_name) in reversed(list(enumerate(file_list))):
    file_path = os.path.join(log_path, file_name)
    if is_image_green(file_path):
      its_session_utils.remove_file(file_path)
      logging.debug('Removed green file %s', file_name)
    else:
      logging.debug('Last camera frame: %s', file_name)
      last_camera_frame_idx = idx
      break

  logging.debug('start idx = %d -- end idx = %d', first_camera_frame_idx,
                last_camera_frame_idx)
  file_list = file_list[first_camera_frame_idx:last_camera_frame_idx+1]

  # Raise error if capture result and frame count doesn't match
  capture_results = preview_rec_obj['captureMetadata']
  extra_capture_result_count = len(capture_results) - len(file_list)
  logging.debug('Number of frames %d', len(file_list))
  if extra_capture_result_count != 0:
    its_session_utils.remove_frame_files(log_path)
    e_msg = (f'Number of CaptureResult ({len(capture_results)}) '
             f'vs number of Frames ({len(file_list)}) count mismatch.'
             ' Retry Test.')
    raise AssertionError(e_msg)

  # skip frames which might not have 3A converged
  capture_results = capture_results[_SKIP_INITIAL_FRAMES:]
  skipped_files = file_list[:_SKIP_INITIAL_FRAMES]
  file_list = file_list[_SKIP_INITIAL_FRAMES:]

  # delete skipped files
  for file_name in skipped_files:
    its_session_utils.remove_file(os.path.join(log_path, file_name))

  return capture_results, file_list
