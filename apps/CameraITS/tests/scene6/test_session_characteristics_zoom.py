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
"""Verify session characteristics zoom."""

import logging
import os

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import zoom_capture_utils

_FPS_30_60 = (30, 60)
_FPS_SELECTION_ATOL = 0.01
_FPS_ATOL = 0.8
_MAX_FPS_INDEX = 1
_MAX_STREAM_COUNT = 3
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SEC_TO_NSEC = 1_000_000_000
_SESSION_CHARACTERISTICS_ZOOM_MAX = 4.0


class SessionCharacteristicsZoomTest(its_base_test.ItsBaseTest):
  """Tests camera capture session specific zoom behavior.

  The combination of camera features tested by this function are:
  - Preview stabilization
  - Target FPS range
  - HLG 10-bit HDR
  """

  def test_session_characteristics_zoom(self):
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
      camera_properties_utils.skip_unless(
          feature_combination_query_version >=
          its_session_utils.ANDROID15_API_LEVEL)

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # set TOLs based on camera and test rig params
      debug = self.debug_mode
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, size = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
        yuv_size = capture_request_utils.get_largest_format('yuv', props)
        size = [yuv_size['width'], yuv_size['height']]
      logging.debug('capture size: %s', size)
      logging.debug('test TOLs: %s', test_tols)

      # List of queryable stream combinations
      combinations_str, combinations = cam.get_queryable_stream_combinations()
      logging.debug('Queryable stream combinations: %s', combinations_str)

      # Stabilization modes. Make sure to test ON first.
      stabilization_params = []
      stabilization_modes = props[
          'android.control.availableVideoStabilizationModes']
      if (camera_properties_utils.STABILIZATION_MODE_PREVIEW in
          stabilization_modes):
        stabilization_params.append(
            camera_properties_utils.STABILIZATION_MODE_PREVIEW)
      stabilization_params.append(
          camera_properties_utils.STABILIZATION_MODE_OFF)
      logging.debug('stabilization modes: %s', stabilization_params)

      configs = props['android.scaler.streamConfigurationMap'][
          'availableStreamConfigurations']
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)

      test_failures = []
      features_tested = {}  # feature combinations already tested
      for stream_combination in combinations:
        combo_version = stream_combination['version']
        streams_name = stream_combination['name']
        min_frame_duration = 0
        configured_streams = []
        skip = False

        # Skip if the combination's version is greater than the device's feature
        # combination query version
        if combo_version > feature_combination_query_version:
          continue

        # Only supports combinations of up to 3 streams
        if len(stream_combination['combination']) > _MAX_STREAM_COUNT:
          raise AssertionError(
              f'stream combination cannot exceed {_MAX_STREAM_COUNT} streams.')

        # Skip if combinations contains only 1 stream, which is preview
        if len(stream_combination['combination']) == 1:
          continue

        for i, stream in enumerate(stream_combination['combination']):
          fmt = None
          size = [int(e) for e in stream['size'].split('x')]
          if stream['format'] == its_session_utils.PRIVATE_FORMAT:
            fmt = capture_request_utils.FMT_CODE_PRIV
          elif stream['format'] == 'jpeg':
            fmt = capture_request_utils.FMT_CODE_JPEG
          elif stream['format'] == its_session_utils.JPEG_R_FMT_STR:
            fmt = capture_request_utils.FMT_CODE_JPEG_R
          elif stream['format'] == 'yuv':
            fmt = capture_request_utils.FMT_CODE_YUV

          # Assume first stream is always a preview stream with priv format
          if i == 0 and fmt != capture_request_utils.FMT_CODE_PRIV:
            raise AssertionError(
                'first stream in the combination must be priv format preview.')

          # Second stream must be jpeg for zoom test. If not, skip
          if (i == 1 and fmt != capture_request_utils.FMT_CODE_JPEG and
              fmt != capture_request_utils.FMT_CODE_JPEG_R):
            logging.debug(
                'second stream format %s is not jpeg/jpeg_r. Skip',
                stream['format'])
            skip = True
            break

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
              {'format': stream['format'], 'width': size[0], 'height': size[1]})

        if skip:
          continue

        # FPS ranges
        max_achievable_fps = _SEC_TO_NSEC / min_frame_duration
        fps_params = [fps for fps in fps_ranges if (
            fps[_MAX_FPS_INDEX] in _FPS_30_60 and
            max_achievable_fps >= fps[_MAX_FPS_INDEX] - _FPS_SELECTION_ATOL)]

        for fps_range in fps_params:
          fps_range_tuple = tuple(fps_range)
          # HLG10. Make sure to test ON first.
          hlg10_params = []
          if camera_properties_utils.dynamic_range_ten_bit(props):
            hlg10_params.append(True)
          hlg10_params.append(False)

          for hlg10 in hlg10_params:
            # Construct output surfaces
            output_surfaces = []
            for configured_stream in configured_streams:
              hlg10_stream = (hlg10 and configured_stream['format'] ==
                              its_session_utils.PRIVATE_FORMAT)
              output_surfaces.append({'format': configured_stream['format'],
                                      'width': configured_stream['width'],
                                      'height': configured_stream['height'],
                                      'hlg10': hlg10_stream})

            for stabilize in stabilization_params:
              settings = {
                  'android.control.videoStabilizationMode': stabilize,
                  'android.control.aeTargetFpsRange': fps_range,
              }
              combination_name = (f'streams_{streams_name}_hlg10_{hlg10}'
                                  f'_stabilization_{stabilize}_fps_range_'
                                  f'_{fps_range[0]}_{fps_range[1]}')
              logging.debug('combination name: %s', combination_name)

              # Is the feature combination supported?
              if not cam.is_stream_combination_supported(
                  output_surfaces, settings):
                logging.debug('%s not supported', combination_name)
                break

              # If a superset of features are already tested, skip.
              # pylint: disable=line-too-long
              is_stabilized = (
                  stabilize == camera_properties_utils.STABILIZATION_MODE_PREVIEW
              )
              skip_test = its_session_utils.check_features_passed(
                  features_tested, streams_name, fps_range_tuple, hlg10, is_stabilized)
              if skip_test:
                continue

              # Get zoom ratio range
              session_props = cam.get_session_properties(
                  output_surfaces, settings)
              z_range = session_props.get('android.control.zoomRatioRange')

              debug = self.debug_mode
              z_min, z_max = float(z_range[0]), float(z_range[1])
              camera_properties_utils.skip_unless(
                  z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH)
              z_max = min(z_max, _SESSION_CHARACTERISTICS_ZOOM_MAX)
              z_list = [z_min, z_max]
              if z_min != 1:
                z_list = np.insert(z_list, 0, 1)  # make reference zoom 1x
              logging.debug('Testing zoom range: %s', z_list)

              # do captures over zoom range and find ArUco markers with cv2
              img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
              req = capture_request_utils.auto_capture_request()

              test_data = []
              all_aruco_ids = []
              all_aruco_corners = []
              images = []
              fmt_str = configured_streams[1]['format']
              for i, z in enumerate(z_list):
                req['android.control.zoomRatio'] = z
                logging.debug('zoom ratio: %.3f', z)
                cam.do_3a(
                    zoom_ratio=z,
                    out_surfaces=output_surfaces,
                    repeat_request=None,
                    first_surface_for_3a=True
                )
                cap = cam.do_capture(
                    req, output_surfaces,
                    reuse_session=True,
                    first_surface_for_3a=True)

                img = image_processing_utils.convert_capture_to_rgb_image(
                    cap, props=props)
                img_name = (f'{img_name_stem}_{combination_name}_{fmt_str}'
                            f'_{z:.2f}.{zoom_capture_utils.JPEG_STR}')
                image_processing_utils.write_image(img, img_name)

                # determine radius tolerance of capture
                cap_fl = cap['metadata']['android.lens.focalLength']
                cap_physical_id = (
                    cap['metadata'][
                        'android.logicalMultiCamera.activePhysicalId']
                )
                result_zoom = float(
                    cap['metadata']['android.control.zoomRatio'])
                radius_tol, offset_tol = test_tols.get(
                    cap_fl,
                    (zoom_capture_utils.RADIUS_RTOL,
                     zoom_capture_utils.OFFSET_RTOL)
                )

                # Find ArUco markers
                bgr_img = cv2.cvtColor(
                    image_processing_utils.convert_image_to_uint8(img),
                    cv2.COLOR_RGB2BGR
                )
                try:
                  corners, ids, _ = opencv_processing_utils.find_aruco_markers(
                      bgr_img,
                      (f'{img_name_stem}_{z:.2f}_'
                       f'ArUco.{zoom_capture_utils.JPEG_STR}'),
                      aruco_marker_count=1
                  )
                except AssertionError as e:
                  logging.debug(
                      'Could not find ArUco marker at zoom ratio %.2f: %s',
                      z, e
                  )
                  break
                all_aruco_corners.append([corner[0] for corner in corners])
                all_aruco_ids.append([id[0] for id in ids])
                images.append(bgr_img)

                test_data.append(
                    zoom_capture_utils.ZoomTestData(
                        result_zoom=result_zoom,
                        radius_tol=radius_tol,
                        offset_tol=offset_tol,
                        focal_length=cap_fl,
                        physical_id=cap_physical_id
                    )
                )

              # Find ArUco markers in all captures and update test data
              zoom_capture_utils.update_zoom_test_data_with_shared_aruco_marker(
                  test_data, all_aruco_ids, all_aruco_corners, size)
              # Mark ArUco marker center and image center
              opencv_processing_utils.mark_zoom_images(
                  images, test_data, img_name_stem)
              if not zoom_capture_utils.verify_zoom_results(
                  test_data, size, z_max, z_min):
                failure_msg = (
                    f'{combination_name}: failed! '
                    'Check test_log.DEBUG for errors')
                test_failures.append(failure_msg)

              its_session_utils.mark_features_passed(
                  features_tested, streams_name, fps_range_tuple,
                  hlg10, is_stabilized)

      if test_failures:
        raise AssertionError(test_failures)

if __name__ == '__main__':
  test_runner.main()
