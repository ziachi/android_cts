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
"""Verify feature combinations for stabilization, 10-bit, and frame rate."""

import concurrent.futures
from datetime import datetime  # pylint: disable=g-importing-member
from google.protobuf import text_format
import gc
import logging
import os
import threading
import time

from mobly import test_runner

import its_base_test
import error_util
import camera_properties_utils
import capture_request_utils
import its_session_utils
import preview_processing_utils
import video_processing_utils
import feature_combination_info_pb2

_BIT_HLG10 = 0x01  # bit 1 for feature mask
_BIT_STABILIZATION = 0x02  # bit 2 for feature mask
_FPS_30_60 = (30, 60)
_FPS_SELECTION_ATOL = 0.01
_FPS_ATOL_CODEC = 1.2
_FPS_ATOL_METADATA = 1.1

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SEC_TO_NSEC = 1_000_000_000


class FeatureCombinationTest(its_base_test.ItsBaseTest):
  """Tests camera feature combinations.

  The combination of camera features tested by this function are:
  - Preview stabilization
  - Target FPS range
  - HLG 10-bit HDR

  Camera is moved in sensor fusion rig on an arc of 15 degrees.
  Speed is set to mimic hand movement (and not be too fast).
  Preview is captured after rotation rig starts moving and the
  gyroscope data is dumped.

  Preview stabilization:
  The recorded preview is processed to dump all of the frames to
  PNG files. Camera movement is extracted from frames by determining
  max angle of deflection in video movement vs max angle of deflection
  in gyroscope movement. Test is a PASS if rotation is reduced in video.

  Target FPS range:
  The recorded preview has the expected fps range. For example,
  if [60, 60] is set as targetFpsRange, the camera device is expected to
  produce 60fps preview/video.

  HLG 10-bit HDR:
  The recorded preview has valid 10-bit HLG outputs.
  """

  features_passed_lock = threading.Lock()

  def test_feature_combination(self):
    # Use a pool of threads to execute calls asynchronously
    with concurrent.futures.ThreadPoolExecutor() as executor:
      self._test_feature_combination(executor)

  def _create_feature_combo_proto(self):
    """Start logging feature combination info for camera in proto."""
    feature_combo_for_camera = (
        feature_combination_info_pb2.FeatureCombinationForCamera())
    feature_combo_for_camera.camera_id = self.camera_id

    return feature_combo_for_camera

  def _add_feature_combo_entry_to_proto(self, feature_combo_for_camera,
                                        output_surfaces,
                                        support_claimed,
                                        is_supported,
                                        fps_range,
                                        stabilization):
    """Log whether a feature combination is supported."""
    entry = feature_combination_info_pb2.FeatureCombinationEntry()
    entry.is_supported = is_supported
    entry.support_claimed = support_claimed
    for surface in output_surfaces:
      config_entry = feature_combination_info_pb2.OutputConfiguration()
      config_entry.image_format = surface['format_code']
      config_entry.size.width = surface['width']
      config_entry.size.height = surface['height']
      config_entry.stream_usecase = feature_combination_info_pb2.USECASE_DEFAULT
      config_entry.dynamic_range_profile = (
          feature_combination_info_pb2.PROFILE_HLG10 if surface['hlg10']
          else feature_combination_info_pb2.PROFILE_STANDARD)
      entry.session_configuration.output_configurations.append(config_entry)
    entry.session_configuration.stabilization = (
        feature_combination_info_pb2.STABILIZATION_PREVIEW if stabilization
        else feature_combination_info_pb2.STABILIZATION_OFF)
    entry.session_configuration.frame_rate_range.max = fps_range[1]
    entry.session_configuration.frame_rate_range.min = fps_range[0]

    feature_combo_for_camera.entries.append(entry)

  def _output_feature_combo_proto(self, feature_combo_for_camera):
    """Finish logging feature combination info and write to ReportLogFiles."""
    database = feature_combination_info_pb2.FeatureCombinationDatabase()
    database.build_fingerprint = (
        its_session_utils.get_build_fingerprint(self.dut.serial))
    database.timestamp_in_sec = int(time.time())
    database.feature_combination_for_camera.append(feature_combo_for_camera)

    current_time = datetime.now().strftime('%Y_%m_%d_%H_%M_%S')
    proto_file_name = (
        f'{self.dut.serial}_camera_{self.camera_id}_{current_time}.pb'
    )
    logging.debug('proto_file_name %s', proto_file_name)
    logging.debug('root_output_path %s', self.root_output_path)

    proto_file_path = os.path.join(self.root_output_path, proto_file_name)
    with open(proto_file_path, 'wb') as f:
      f.write(database.SerializeToString())

    txtpb_file_name = proto_file_name.replace('.pb', '.txtpb')
    txtpb_file_path = os.path.join(self.root_output_path, txtpb_file_name)
    with open(txtpb_file_path, 'w') as tf:
      database_str = text_format.MessageToString(database)
      tf.write(database_str)

    print(f'feature_query_proto:{txtpb_file_name}')

  def _finish_combination(
      self, combination_name, is_stabilized, support_claimed,
      passed, recording_obj, gyro_events, test_name, log_path,
      facing, output_surfaces, fps_range, hlg10,
      features_passed, streams_name, fps_range_tuple
  ):
    """Finish verifying a feature combo & preview stabilization if necessary."""
    result = {'name': combination_name,
              'output_surfaces': output_surfaces,
              'fps_range': fps_range,
              'is_stabilized': is_stabilized,
              'support_claimed': support_claimed,
              'passed': passed}
    if is_stabilized:
      stabilization_result = (
          preview_processing_utils.verify_preview_stabilization(
              recording_obj, gyro_events, test_name, log_path, facing
          )
      )
      if stabilization_result['failure']:
        result['stabilization_failure'] = stabilization_result['failure']
        result['passed'] = False

    if result['passed']:
      with self.features_passed_lock:
        its_session_utils.mark_features_passed(
            features_passed, streams_name, fps_range_tuple,
            hlg10, is_stabilized)
      # Remove video clip if test passes to save space
      try:
        file_name = recording_obj['recordedOutputPath'].split('/')[-1]
        os.remove(os.path.join(log_path, file_name))
      except FileNotFoundError:
        logging.debug('File Not Found: %s', str(file_name))

    return result

  def _append_test_failure(self, failures, support_claimed, msg):
    if (support_claimed == feature_combination_info_pb2.SUPPORT_YES):
      failures['required'].append(msg)
    else:
      failures['optional'].append(msg)

  def _handle_one_completed_future(
      self, pending_futures, future, test_failures, database):
    result = future.result()
    pending_futures.remove(future)
    logging.debug('Verification result: %s', result)
    if 'stabilization_failure' in result:
      failure_msg = f"{result['name']}: {result['stabilization_failure']}"
      self._append_test_failure(
          test_failures, result['support_claimed'], failure_msg
      )

    self._add_feature_combo_entry_to_proto(
        database, result['output_surfaces'], result['support_claimed'],
        result['passed'], result['fps_range'], result['is_stabilized']
    )
    gc.collect()

  def _handle_completed_futures(self, verifications, test_failures, database):
    for future in verifications[:]:
      if future.done():
        self._handle_one_completed_future(
            verifications, future, test_failures, database)

  def _drain_feature_verification_futures(
      self, verifications, test_failures, database):
    for future in concurrent.futures.as_completed(verifications):
      self._handle_one_completed_future(
          verifications, future, test_failures, database)

  def _test_feature_combination(self, executor):
    """Tests features using an injected ThreadPoolExecutor for analysis.

    Args:
      executor: a ThreadPoolExecutor to analyze recordings asynchronously.
    """
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id) as cam:

      # Skip if the device doesn't support feature combination query
      props = cam.get_camera_properties()
      feature_combination_query_version = props.get(
          'android.info.sessionConfigurationQueryVersion')
      if not feature_combination_query_version:
        feature_combination_query_version = (
            its_session_utils.ANDROID14_API_LEVEL
        )
      support_query = (feature_combination_query_version >=
                       its_session_utils.ANDROID15_API_LEVEL)

      # Log ffmpeg version being used
      video_processing_utils.log_ffmpeg_version()

      # Raise error if not FRONT or REAR facing camera
      facing = props['android.lens.facing']
      camera_properties_utils.check_front_or_rear_camera(props)

      # Initialize rotation rig
      rot_rig['cntl'] = self.rotator_cntl
      rot_rig['ch'] = self.rotator_ch
      if rot_rig['cntl'].lower() != 'arduino':
        raise AssertionError(
            f'You must use the arduino controller for {_NAME}.')

      # List of queryable stream combinations
      combinations_str, combinations = cam.get_queryable_stream_combinations()
      logging.debug('Queryable stream combinations: %s', combinations_str)

      # Stabilization: Make sure to test ON first in order to be able to skip
      # OFF
      stabilization_params = [True, False]
      stabilization_modes = props[
          'android.control.availableVideoStabilizationModes']

      logging.debug('stabilization modes: %s', stabilization_params)

      configs = props['android.scaler.streamConfigurationMap'][
          'availableStreamConfigurations']
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)
      fps_params = [fps for fps in fps_ranges if (
          fps[1] in _FPS_30_60)]
      hlg10_params = [True, False]

      # 'required': failed combinations that are required to pass
      # 'optional': failed combinations that are used for analytics only
      test_failures = {'required': [], 'optional': []}
      feature_verification_futures = []
      database = self._create_feature_combo_proto()
      features_passed = {}

      for fps_range in fps_params:
        fps_range_tuple = tuple(fps_range)
        for hlg10 in hlg10_params:
          for is_stabilized in stabilization_params:
            for stream_combination in combinations:
              streams_name = stream_combination['name']
              combo_version = stream_combination['version']
              combination_name = (f'(streams: {streams_name}, hlg10: {hlg10}, '
                                  f'stabilization: {is_stabilized}, fps_range: '
                                  f'[{fps_range[0]}, {fps_range[1]}])')

              min_frame_duration = 0
              configured_streams = []
              if (stream_combination['combination'][0]['format'] !=
                  its_session_utils.PRIVATE_FORMAT):
                raise AssertionError(
                    f'First stream for {streams_name} must be PRIV')
              video_stream_index = 0
              for index, stream in enumerate(stream_combination['combination']):
                fmt = None
                size = [int(e) for e in stream['size'].split('x')]
                match stream['format']:
                  case its_session_utils.PRIVATE_FORMAT:
                    fmt = capture_request_utils.FMT_CODE_PRIV
                    video_stream_index = index
                  case 'jpeg':
                    fmt = capture_request_utils.FMT_CODE_JPEG
                  case its_session_utils.JPEG_R_FMT_STR:
                    fmt = capture_request_utils.FMT_CODE_JPEG_R
                  case 'yuv':
                    fmt = capture_request_utils.FMT_CODE_YUV
                  case _:
                    raise AssertionError(
                        f'Unsupported stream format {stream["format"]}')
                config = [x for x in configs if
                          x['format'] == fmt and
                          x['width'] == size[0] and
                          x['height'] == size[1]]
                min_frame_duration = max(
                    config[0]['minFrameDuration'], min_frame_duration)
                logging.debug(
                    'format is %s, min_frame_duration is %d}',
                    stream['format'], config[0]['minFrameDuration'])
                configured_streams.append(
                    {'formatStr': stream['format'], 'format': fmt,
                     'width': size[0], 'height': size[1]})

              # Check if the FPS range is supported
              max_achievable_fps = _SEC_TO_NSEC / min_frame_duration
              if max_achievable_fps < fps_range[1] - _FPS_SELECTION_ATOL:
                continue

              # Check if the hlg10 is supported for the video size and fps
              video_size = (
                  stream_combination['combination'][video_stream_index]['size']
              )
              if (hlg10 and
                  not cam.is_hlg10_recording_supported_for_size_and_fps(
                      video_size, fps_range[1])
                  ):
                continue

              # Check if stabilization is supported: Use video stabilization
              # if there is a dedicated video stream
              if not is_stabilized:
                stabilize_mode = camera_properties_utils.STABILIZATION_MODE_OFF
              elif video_stream_index == 0:
                stabilize_mode = (
                    camera_properties_utils.STABILIZATION_MODE_PREVIEW
                )
              else:
                stabilize_mode = camera_properties_utils.STABILIZATION_MODE_ON
              if stabilize_mode not in stabilization_modes:
                continue

              logging.debug('combination name: %s', combination_name)

              # Construct output surfaces
              output_surfaces = []
              for index, configured_stream in enumerate(configured_streams):
                hlg10_stream = (configured_stream['formatStr'] ==
                                its_session_utils.PRIVATE_FORMAT and hlg10)
                is_video = index == video_stream_index
                output_surfaces.append(
                    {'format': configured_stream['formatStr'],
                     'format_code': configured_stream['format'],
                     'width': configured_stream['width'],
                     'height': configured_stream['height'],
                     'hlg10': hlg10_stream,
                     'is_video': is_video}
                )

              settings = {
                  'android.control.videoStabilizationMode': stabilize_mode,
                  'android.control.aeTargetFpsRange': fps_range,
              }

              # UNKNOWN means that the device doesn't support
              # is_stream_combination_supported (with session parameters) for
              # this combination of features.
              support_claimed = feature_combination_info_pb2.SUPPORT_UNKNOWN
              if feature_combination_query_version >= combo_version:
                # Is the feature combination supported?
                if cam.is_stream_combination_supported(
                    output_surfaces, settings):
                  support_claimed = feature_combination_info_pb2.SUPPORT_YES
                else:
                  support_claimed = feature_combination_info_pb2.SUPPORT_NO
                  logging.debug('%s not supported', combination_name)

              passed = True
              # If a superset of features are already tested, skip and assuming
              # the subset of those features are supported. Do not skip [60, *]
              # even if its superset feature passes (b/385753212).
              with self.features_passed_lock:
                skip_test = (
                    streams_name in features_passed and
                    fps_range_tuple in features_passed[streams_name] and
                    fps_range[0] < 60 and
                    its_session_utils.check_features_passed(
                        features_passed, streams_name, fps_range_tuple,
                        hlg10, is_stabilized)
                )
                if skip_test:
                  self._add_feature_combo_entry_to_proto(
                      database, output_surfaces,
                      feature_combination_info_pb2.SUPPORT_YES,
                      passed, fps_range, is_stabilized)
                  continue

              # In case collect_data_with_surfaces throws an exception, treat it
              # as an unsupported combination. (b/385753212#2)
              try:
                recording_obj = (
                    preview_processing_utils.collect_data_with_surfaces(
                        cam, self.tablet_device, output_surfaces,
                        video_stream_index, stabilize_mode, rot_rig=rot_rig,
                        fps_range=fps_range
                    )
                )
              except error_util.CameraItsError as e:
                if (support_query and
                    support_claimed == feature_combination_info_pb2.SUPPORT_YES
                   ):
                  raise e
                failure_msg = (
                    f'{combination_name}: collect_data_with_surfaces throws '
                    f'exception: {e}')
                logging.debug(failure_msg)
                self._append_test_failure(
                    test_failures, support_claimed, failure_msg
                )
                passed = False
                self._add_feature_combo_entry_to_proto(
                    database, output_surfaces, support_claimed,
                    passed, fps_range, is_stabilized)
                cam.reset_socket_and_camera()
                continue

              gyro_events = None
              if is_stabilized:
                # Get gyro events
                logging.debug('Reading out inertial sensor events')
                gyro_events = cam.get_sensor_events()['gyro']
                logging.debug('Number of gyro samples %d', len(gyro_events))

              # Grab the video from the file location on DUT
              self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
              # Delete the video file from the DUT
              self.dut.adb.shell('rm %s' % recording_obj['recordedOutputPath'])

              # Verify FPS by inspecting the video clip
              preview_file_name = (
                  recording_obj['recordedOutputPath'].split('/')[-1])
              preview_file_name_with_path = os.path.join(
                  self.log_path, preview_file_name)
              avg_frame_rate_codec = (
                  video_processing_utils.get_avg_frame_rate(
                      preview_file_name_with_path))
              logging.debug('Average codec frame rate for %s is %f',
                            combination_name, avg_frame_rate_codec)
              if (avg_frame_rate_codec > fps_range[1] + _FPS_ATOL_CODEC or
                  avg_frame_rate_codec < fps_range[0] - _FPS_ATOL_CODEC):
                failure_msg = (
                    f'{combination_name}: Average video clip frame rate '
                    f'{avg_frame_rate_codec} exceeding the allowed range of '
                    f'({fps_range[0]}-{_FPS_ATOL_CODEC}, '
                    f'{fps_range[1]}+{_FPS_ATOL_CODEC})')
                self._append_test_failure(
                    test_failures, support_claimed, failure_msg
                )
                passed = False

              # Verify FPS by inspecting the result metadata
              capture_results = recording_obj['captureMetadata']
              if len(capture_results) <= 1:
                raise AssertionError(
                    f'{combination_name}: captureMetadata has only '
                    f'{len(capture_results)} frames')
              last_t = capture_results[-1]['android.sensor.timestamp']
              first_t = capture_results[0]['android.sensor.timestamp']
              avg_frame_duration = (
                  (last_t - first_t) / (len(capture_results) - 1))
              avg_frame_rate_metadata = _SEC_TO_NSEC / avg_frame_duration
              logging.debug('Average metadata frame rate for %s is %f',
                            combination_name, avg_frame_rate_metadata)
              if (avg_frame_rate_metadata > fps_range[1] + _FPS_ATOL_METADATA or
                  avg_frame_rate_metadata < fps_range[0] - _FPS_ATOL_METADATA):
                failure_msg = (
                    f'{combination_name}: Average frame rate '
                    f'{avg_frame_rate_metadata} exceeding the allowed range of '
                    f'({fps_range[0]}-{_FPS_ATOL_METADATA}, '
                    f'{fps_range[1]}+{_FPS_ATOL_METADATA})')
                self._append_test_failure(
                    test_failures, support_claimed, failure_msg
                )
                passed = False

              # Verify color space
              color_space = video_processing_utils.get_video_colorspace(
                  self.log_path, preview_file_name_with_path)
              if (hlg10 and
                  video_processing_utils.COLORSPACE_HDR not in color_space):
                failure_msg = (
                    f'{combination_name}: video color space {color_space} '
                    'is missing COLORSPACE_HDR')
                self._append_test_failure(
                    test_failures, support_claimed, failure_msg
                )
                passed = False

              if not self.parallel_execution:
                self._finish_combination(
                    combination_name, is_stabilized,
                    support_claimed, passed, recording_obj, gyro_events, _NAME,
                    log_path, facing, output_surfaces, fps_range, hlg10,
                    features_passed, streams_name, fps_range_tuple
                )
              else:
                future = executor.submit(
                    self._finish_combination, combination_name, is_stabilized,
                    support_claimed, passed, recording_obj, gyro_events, _NAME,
                    log_path, facing, output_surfaces, fps_range, hlg10,
                    features_passed, streams_name, fps_range_tuple
                )
                # Handle completed feature verification futures
                self._handle_completed_futures(
                    feature_verification_futures, test_failures, database)
                feature_verification_futures.append(future)

      # Drain the remaining feature combination results
      self._drain_feature_verification_futures(
          feature_verification_futures, test_failures, database)

      # Output the feature combination proto to ItsService and optionally to
      # file
      self._output_feature_combo_proto(database)

      # Assert PASS/FAIL criteria
      if test_failures:
        logging.debug(test_failures)
      if test_failures['required']:
        raise AssertionError(test_failures['required'])

if __name__ == '__main__':
  test_runner.main()
