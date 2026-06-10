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
"""Verify video is stable during phone movement with JCA."""

import logging
import os
import pathlib
import threading
import time

import camera_properties_utils
import image_processing_utils
import its_base_test
import its_session_utils
from mobly import test_runner
import sensor_fusion_utils
import ui_interaction_utils
import video_processing_utils

_ASPECT_RATIO_16_9 = 16/9  # Determine if video fmt > 16:9.
_IMG_FORMAT = 'png'
_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_MIN_PHONE_MOVEMENT_ANGLE = 5  # Degrees
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_ROTATIONS = 24
_START_FRAME = 30  # Give 3A 1s to warm up.
_VIDEO_DELAY_TIME = 3  # Seconds
_VIDEO_DURATION = 5.5  # Seconds
_VIDEO_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed.


def _collect_data(cam, dut, lens_facing, log_path,
                  aspect_ratio, rot_rig, servo_speed, serial_port):
  """Capture a new set of data from the device.

  Captures camera frames while the device is being rotated in the prescribed
  manner.

  Args:
    cam: Camera object.
    dut: An Android controller device object.
    lens_facing: str; Facing of camera.
    log_path: str; Log path where video will be saved.
    aspect_ratio: str; Key string for video aspect ratio defined by JCA.
    rot_rig: dict with 'cntl' and 'ch' defined.
    servo_speed: int; Speed of servo motor.
    serial_port: str; Serial port for Arduino controller.

  Returns:
    output path: Output path for the recording.
  """
  logging.debug('Starting sensor event collection for %s', aspect_ratio)
  ui_interaction_utils.do_jca_video_setup(
      dut,
      log_path,
      facing=lens_facing,
      aspect_ratio=aspect_ratio,
      stabilization_mode=camera_properties_utils.STABILIZATION_MODE_ON,
  )
  # Start camera movement.
  movement = threading.Thread(
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
  movement.start()
  cam.start_sensor_events()
  logging.debug('Gyro Sensor recording started')
  # Record video with JCA.
  time.sleep(_VIDEO_DELAY_TIME)  # Allow time for rig to start moving.
  recording_path = pathlib.Path(
      cam.do_jca_video_capture(
          dut,
          log_path,
          duration=_VIDEO_DURATION * 1000,  # ms
      )
  )
  prefix_name = str(recording_path).split('.')[0]
  ratio_name = aspect_ratio.replace(' ', '_')
  output_path = (f'{prefix_name}_{ratio_name}.mp4')
  os.rename(recording_path, output_path)
  logging.debug('Output path for recording %s : %s', aspect_ratio, output_path)
  # Wait for movement to stop.
  movement.join()

  return output_path


def _extract_frames_from_video(log_path, recording_path):
  """Extract frames from video.

  Extract frames from video and convert to numpy array.

  Args:
    log_path: str; Directory where video is saved.
    recording_path: str; Path to video file.

  Returns:
    frames: List of numpy arrays.
    frame_shape: Tuple of frame shape.
  """
  file_name = str(recording_path).split('/')[-1]
  logging.debug('file_name: %s', file_name)
  file_list = video_processing_utils.extract_all_frames_from_video(
      log_path, file_name, _IMG_FORMAT)
  frames = []
  logging.debug('Number of frames %d', len(file_list))
  for file in file_list:
    img = image_processing_utils.convert_image_to_numpy_array(
        os.path.join(log_path, file))
    frames.append(img/255)
  frame_shape = frames[0].shape
  logging.debug('Frame size %d x %d', frame_shape[1], frame_shape[0])
  return frames, frame_shape


def _extract_camera_gyro_rotations(
    lens_facing, frames, frame_shape, gyro_events, log_path, ratio_tested):
  """Extract camera and gyro rotations from frames and gyro events.

  Args:
    lens_facing: str; Facing of camera.
    frames: List of numpy arrays.
    frame_shape: Tuple of frame shape.
    gyro_events: List of gyro events.
    log_path: str; Directory where video is saved.
    ratio_tested: str; Key string for video aspect ratio defined by JCA.

  Returns:
    max_gyro_angle: float; Max angle of deflection in gyroscope movement.
    max_camera_angle: float; Max angle of deflection in video movement.
    frame_shape: Tuple of frame shape.
    ratio_name: str; Name of ratio tested for logging.
  """
  ratio_name = ratio_tested.replace(' ', '_')
  file_name_stem = f'{os.path.join(log_path, _NAME)}_{ratio_name}'
  cam_rots = sensor_fusion_utils.get_cam_rotations_from_frames(
      frames[_START_FRAME:], lens_facing, frame_shape[0],
      file_name_stem, _START_FRAME, stabilized_video=True)
  sensor_fusion_utils.plot_camera_rotations(
      cam_rots, _START_FRAME, ratio_name, file_name_stem)
  max_camera_angle = sensor_fusion_utils.calc_max_rotation_angle(
      cam_rots, 'Camera')

  # Extract gyro rotations.
  sensor_fusion_utils.plot_gyro_events(
      gyro_events, f'{_NAME}_{ratio_name}', log_path)
  gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
      gyro_events, _VIDEO_DELAY_TIME)
  max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
      gyro_rots, 'Gyro')
  logging.debug(
      'Max deflection (degrees) %s: video: %.3f, gyro: %.3f, ratio: %.4f',
      ratio_tested, max_camera_angle, max_gyro_angle,
      max_camera_angle / max_gyro_angle)
  return max_gyro_angle, max_camera_angle, frame_shape, ratio_name


def _initialize_rotation_rig(rot_rig, rotator_cntl, rotator_ch):
  """Initialize rotation rig.

  Args:
    rot_rig: dict with 'cntl' and 'ch' defined
    rotator_cntl: str; controller type
    rotator_ch: str; controller channel

  Returns:
    rot_rig: updated dict with 'cntl' and 'ch' defined
  """
  rot_rig['cntl'] = rotator_cntl
  rot_rig['ch'] = rotator_ch
  if rot_rig['cntl'].lower() != sensor_fusion_utils.ARDUINO_STRING.lower():
    raise AssertionError(f'You must use an arduino controller for {_NAME}.')
  logging.debug('video qualities tested: %s', str(
      ui_interaction_utils.RATIO_TO_UI_DESCRIPTION.keys()))
  return rot_rig


def _initialize_servo_controller(tablet_device, rot_rig):
  """Initialize controller.

  Args:
    tablet_device: bool; True if tablet device is connected.
    rot_rig: dict with 'cntl' and 'ch' defined.

  Returns:
    serial_port: str; Serial port for Arduino controller.
    servo_speed: int; Speed of servo motor.
  """
  serial_port = None
  if rot_rig['cntl'].lower() == sensor_fusion_utils.ARDUINO_STRING.lower():
    # Identify port.
    serial_port = sensor_fusion_utils.serial_port_def(
        sensor_fusion_utils.ARDUINO_STRING)
    # Send test cmd to arduino until cmd returns properly.
    sensor_fusion_utils.establish_serial_comm(serial_port)
  if tablet_device:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION_TABLET
  else:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION
  return serial_port, servo_speed


class VideoStabilizationJCATest(its_base_test.UiAutomatorItsBaseTest):
  """Tests if video is stabilized.

  Camera is moved in sensor fusion rig on an arc of 15 degrees.
  Speed is set to mimic hand movement and not be too fast.
  Video is captured after rotation rig starts moving, and the
  gyroscope data is dumped.

  Video is processed to dump all of the frames to PNG files.
  Camera movement is extracted from frames by determining max
  angle of deflection in video movement vs max angle of deflection
  in gyroscope movement. Test is a PASS if rotation is reduced in video.
  """

  def setup_class(self):
    super().setup_class()
    self.ui_app = _JETPACK_CAMERA_APP_PACKAGE_NAME
    # Restart CtsVerifier to ensure that correct flags are set.
    ui_interaction_utils.force_stop_app(
        self.dut, its_base_test.CTS_VERIFIER_PKG)
    self.dut.adb.shell(
        'am start -n com.android.cts.verifier/.CtsVerifierActivity')

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(self.dut, self.ui_app)

  def test_video_stabilization_jca(self):
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Close camera after props retrieved so that ItsTestActivity can open it.
      cam.close_camera()

      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      supported_stabilization_modes = props[
          'android.control.availableVideoStabilizationModes']

      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID16_API_LEVEL and
          camera_properties_utils.STABILIZATION_MODE_ON
          in supported_stabilization_modes)

      # Log ffmpeg version being used.
      video_processing_utils.log_ffmpeg_version()

      # Raise error if not FRONT or REAR facing camera.
      lens_facing = props['android.lens.facing']
      camera_properties_utils.check_front_or_rear_camera(props)

      # Initialize rotation rig.
      rot_rig = _initialize_rotation_rig(
          rot_rig, self.rotator_cntl, self.rotator_ch)
      # Initialize connection with controller.
      serial_port, servo_speed = _initialize_servo_controller(
          self.tablet_device, rot_rig)
      max_cam_gyro_angles = {}

      for ratio_tested in ui_interaction_utils.RATIO_TO_UI_DESCRIPTION.keys():
        # Record video.
        recording_path = _collect_data(
            cam, self.dut, lens_facing, log_path, ratio_tested,
            rot_rig, servo_speed, serial_port
        )

        # Get gyro events.
        logging.debug('Reading out inertial sensor events')
        gyro_events = cam.get_sensor_events()['gyro']
        logging.debug('Number of gyro samples %d', len(gyro_events))

        # Extract frames from video.
        frames, frame_shape = _extract_frames_from_video(
            log_path, recording_path)
        # Extract camera and gyro rotations.
        max_gyro_angle, max_camera_angle, frame_shape, ratio_name = (
            _extract_camera_gyro_rotations(lens_facing, frames, frame_shape,
                                           gyro_events, log_path, ratio_tested))
        max_cam_gyro_angles[ratio_name] = {'gyro': max_gyro_angle,
                                           'cam': max_camera_angle,
                                           'frame_shape': frame_shape}
        # Assert phone is moved enough during test.
        if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
          raise AssertionError(
              f'Phone not moved enough! Movement: {max_gyro_angle}, '
              f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

      # Assert PASS/FAIL criteria.
      test_failures = []
      for ratio_name, max_angles in max_cam_gyro_angles.items():
        aspect_ratio = (max_angles['frame_shape'][1] /
                        max_angles['frame_shape'][0])
        if aspect_ratio > _ASPECT_RATIO_16_9:
          video_stabilization_factor = _VIDEO_STABILIZATION_FACTOR * 1.1
        else:
          video_stabilization_factor = _VIDEO_STABILIZATION_FACTOR
        if max_angles['cam'] >= max_angles['gyro']*video_stabilization_factor:
          test_failures.append(
              f'{ratio_name} video not stabilized enough! '
              f"Max video angle:  {max_angles['cam']:.3f}, "
              f"Max gyro angle: {max_angles['gyro']:.3f}, "
              f"ratio: {max_angles['cam']/max_angles['gyro']:.3f} "
              f'THRESH: {video_stabilization_factor}.')
        else:  # Remove frames if PASS.
          its_session_utils.remove_tmp_files(log_path, 'ITS_JCA_*')
      if test_failures:
        raise AssertionError(test_failures)


if __name__ == '__main__':
  test_runner.main()
