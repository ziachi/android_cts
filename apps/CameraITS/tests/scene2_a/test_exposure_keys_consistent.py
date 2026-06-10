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
"""Validate that exposure values agree when AE is either on or off."""

import logging
import os.path
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import matplotlib.pyplot as plt
from mobly import test_runner
import numpy as np
from scipy.ndimage import gaussian_filter

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SAD_THRESHOLD = 0.04  # Images should not be >= 4% different
_JPG_FORMAT = 'jpg'


def calc_avg_exposure_sad(cap_no_ae, cap_ae, name_with_log_path):
  """Calculate the avg SAD of the AE capture and the non-AE capture.

  Calculates the sum of absolute difference (SAD) for the two images averaged
  by the number of pixels.

  Args:
    cap_no_ae: Camera capture object without auto exposure.
    cap_ae: Camera capture object with auto exposure.
    name_with_log_path: File name with path to write image.

  Returns:
    The average sum of absolute differences for the two images.
  """
  suffix = f'_fmt={_JPG_FORMAT}.{_JPG_FORMAT}'
  img_no_ae = image_processing_utils.decompress_jpeg_to_yuv_image(cap_no_ae)
  image_processing_utils.write_image(
      img_no_ae, f'{name_with_log_path}_no_ae{suffix}', is_yuv=True
  )

  img_ae = image_processing_utils.decompress_jpeg_to_yuv_image(cap_ae)
  image_processing_utils.write_image(
      img_ae, f'{name_with_log_path}{suffix}', is_yuv=True
  )

  luma_no_ae = img_no_ae[:, :, 0:1]
  image_processing_utils.write_image(
      luma_no_ae, f'{name_with_log_path}_luma_no_ae{suffix}'
  )

  luma_ae = img_ae[:, :, 0:1]
  image_processing_utils.write_image(
      luma_ae, f'{name_with_log_path}_luma{suffix}'
  )

  # Blur the captures to remove noise
  luma_no_ae_blur = gaussian_filter(luma_no_ae, sigma=3)
  image_processing_utils.write_image(
      luma_no_ae_blur, f'{name_with_log_path}_luma_no_ae_blur{suffix}'
  )

  luma_ae_blur = gaussian_filter(luma_ae, sigma=3)
  image_processing_utils.write_image(
      luma_ae_blur, f'{name_with_log_path}_luma_blur{suffix}'
  )

  luma_sad = np.abs(np.subtract(luma_no_ae_blur, luma_ae_blur))
  fig, ax = plt.subplots()
  im = ax.imshow(luma_sad, cmap='Reds')
  cbar = fig.colorbar(im, ax=ax)
  cbar.set_label('SAD')
  plt.savefig(f'{name_with_log_path}_sad.png', dpi=300, bbox_inches='tight')

  # Calculate the sum of absolute difference, take the average per pixel.
  avg_sad = np.sum(
      np.abs(np.subtract(luma_ae_blur, luma_no_ae_blur))
  ) / (img_ae.shape[0] * img_ae.shape[1])

  return avg_sad


class ExposureKeysConsistentTest(its_base_test.ItsBaseTest):
  """Test for inconsistencies in exposure metadata and the resulting image.

  Uses JPEG captures as the output format.

  Steps:
  - Takes one capture with auto exposure on.
  - Using values the above CaptureResult, applies the following keys to a
  capture
    request with auto exposure off:
        - Sensor sensitivity
        - Post raw sensitivity boost
        - Exposure time
        - Frame duration
  - Validates that the two images are roughly the same via SAD.
  """

  def test_exposure_keys_consistent(self):
    logging.debug('Starting %s', _NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id,
    ) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance
      )

      # Capture for each available reprocess format
      sizes = capture_request_utils.get_available_output_sizes(
          _JPG_FORMAT, props
      )
      size = sizes[0]

      camera_properties_utils.skip_unless(size is not None)

      logging.info('capture width: %d', size[0])
      logging.info('capture height: %d', size[1])
      out_surface = {'width': size[0], 'height': size[1], 'format': _JPG_FORMAT}

      # Create req, do caps and determine SAD
      req = capture_request_utils.auto_capture_request()
      req['android.control.aeMode'] = 1  # ON
      cam.do_3a()
      cap_ae = cam.do_capture([req], out_surface)[0]
      cr = cap_ae['metadata']
      sensor_sensitivity = cr['android.sensor.sensitivity']
      exposure_time = cr['android.sensor.exposureTime']
      post_raw_sensitivity_boost = cr['android.control.postRawSensitivityBoost']
      frame_duration = cr['android.sensor.frameDuration']

      camera_properties_utils.skip_unless(
          post_raw_sensitivity_boost is not None
      )

      logging.info('sensor_sensitivity: %s', sensor_sensitivity)
      logging.info('exposure_time: %s', exposure_time)
      logging.info('post_raw_sensitivity_boost: %s', post_raw_sensitivity_boost)
      logging.info('frame_duration: %s', frame_duration)

      req['android.control.aeMode'] = 0  # OFF
      req['android.sensor.sensitivity'] = sensor_sensitivity
      req['android.sensor.exposureTime'] = exposure_time
      req['android.control.postRawSensitivityBoost'] = (
          post_raw_sensitivity_boost
      )
      req['android.sensor.frameDuration'] = frame_duration
      cap_no_ae = cam.do_capture([req], out_surface)[0]

      cr_no_ae = cap_no_ae['metadata']
      post_raw_sensitivity_boost_no_ae = cr_no_ae[
          'android.control.postRawSensitivityBoost'
      ]
      logging.info(
          '(no ae) post_raw_sensitivity_boost: %s',
          post_raw_sensitivity_boost_no_ae
      )
      camera_properties_utils.skip_unless(
          post_raw_sensitivity_boost_no_ae is not None
          and post_raw_sensitivity_boost == post_raw_sensitivity_boost_no_ae
      )

      sad = calc_avg_exposure_sad(
          cap_no_ae['data'], cap_ae['data'], name_with_log_path
      )

      logging.info('Avg SAD: %s (threshold: %s)', sad, _SAD_THRESHOLD)
      if sad > _SAD_THRESHOLD:
        raise AssertionError(
            f'Avg SAD greater than threshold: {sad} / {_SAD_THRESHOLD}'
        )


if __name__ == '__main__':
  test_runner.main()
