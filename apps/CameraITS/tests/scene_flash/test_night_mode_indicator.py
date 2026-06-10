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
"""Verify Night Mode Indicator works correctly during camera use."""

import logging
import os.path

from mobly import test_runner
import camera_properties_utils
import preview_processing_utils
import its_base_test
import its_session_utils
import lighting_control_utils
import numpy as np
import cv2

_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]
_EXTENSION_NONE = -1
_EXTENSION_NIGHT = 4  # CameraExtensionCharacteristics.EXTENSION_NIGHT
_SESSION_TYPE_CAMERA2 = 'camera2'
_SESSION_TYPE_CAMERA_EXTENSION = 'cameraExtension'
_CAMERA_SESSION_TYPES = (_SESSION_TYPE_CAMERA2, _SESSION_TYPE_CAMERA_EXTENSION)
_NUM_FRAMES_TO_WAIT = 10
_CONTROL_MODE_AUTO = 1
_CONTROL_VIDEO_STABILIZATION_MODE_OFF = 0
_CAPTURE_REQUEST = {
    'android.control.mode': _CONTROL_MODE_AUTO,
    'android.control.videoStabilizationMode':
        _CONTROL_VIDEO_STABILIZATION_MODE_OFF,
}
_CAPTURE_RESULT_KEY_NIGHT_MODE_INDICATOR = 'android.extension.nightModeIndicator'


def _start_preview(cam, file_stem, camera_id, target_preview_size,
                   camera_session_type):
  """Start a preview capture session and verify the Night Mode Indicator state.

  Args:
    cam: ItsSession object
    file_stem: string, prefix for the output image file
    camera_id: string, camera ID to use for capture
    target_preview_size: tuple, (width, height) of the preview size to use
    camera_session_type: string, either 'camera2' or 'cameraExtension'
  """
  extension = _EXTENSION_NONE
  if camera_session_type == _SESSION_TYPE_CAMERA_EXTENSION:
    extension = _EXTENSION_NIGHT

  metadata, frame_bytes = cam.do_capture_preview_frame(
      camera_id, target_preview_size, _NUM_FRAMES_TO_WAIT, extension,
      _CAPTURE_REQUEST
  )
  np_array = np.frombuffer(frame_bytes, dtype=np.uint8)
  img_rgb = cv2.imdecode(np_array, cv2.IMREAD_COLOR)
  cv2.imwrite(f'{file_stem}_capture.jpg', img_rgb)
  if metadata is None:
    raise AssertionError('No metadata returned from capture')
  if metadata[_CAPTURE_RESULT_KEY_NIGHT_MODE_INDICATOR] is None:
    raise AssertionError('No Night Mode Indicator value in metadata')
  result = metadata[_CAPTURE_RESULT_KEY_NIGHT_MODE_INDICATOR]
  logging.debug('Night Mode Indicator value: %s', result)


class NightModeIndicatorTest(its_base_test.ItsBaseTest):
  """Test that Night Mode Indicator works as intended.

  This feature is only available on devices that also support Night Mode Camera
  Extensions. If Night Mode is supported and the feature is present in the
  available capture result keys, then the this test will setup a repeating
  capture request for the preview stream in both a Camera2 session and a Camera
  Extensions session. The test will then verify that the Night Mode Indicator
  changes state as expected during the capture session -- when the light is
  turned off, the indicator should change to the "ON" state; when the light is
  turned back on, the indicator should change to the "OFF" state.
  """

  def test_night_mode_indicator(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID16_API_LEVEL and
          cam.is_night_mode_indicator_supported(self.camera_id)
      )

      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch
      )

      for session_type in _CAMERA_SESSION_TYPES:
        logging.debug('scenario: %s', session_type)
        file_stem = f'{_TEST_NAME}_{session_type}'

        target_preview_size = None
        if session_type == _SESSION_TYPE_CAMERA2:
          target_preview_size = (
              preview_processing_utils.get_max_preview_test_size(
                  cam, self.camera_id
              )
          )
        elif session_type == _SESSION_TYPE_CAMERA_EXTENSION:
          target_preview_size = (
              preview_processing_utils.get_max_extension_preview_test_size(
                  cam, self.camera_id, _EXTENSION_NIGHT
              )
          )
        else:
          logging.debug('Unknown scenario: %s', session_type)
          continue

        lighting_control_utils.set_lighting_state(
            arduino_serial_port, self.lighting_ch, 'ON')
        result = _start_preview(
            cam, file_stem, self.camera_id, target_preview_size, session_type)

        if (result != 'OFF'):
          raise AssertionError('Lighting state ON did not result in Night Mode '
                               'Indicator state OFF.')

        lighting_control_utils.set_lighting_state(
            arduino_serial_port, self.lighting_ch, 'OFF')

        result = _start_preview(
            cam, file_stem, self.camera_id, target_preview_size, session_type)
        if (result != 'ON'):
          raise AssertionError('Lighting state OFF did not result in Night '
                               'Mode Indicator state ON.')


if __name__ == '__main__':
  test_runner.main()
