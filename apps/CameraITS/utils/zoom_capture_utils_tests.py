# Copyright 2023 The Android Open Source Project
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
"""Tests for zoom_capture_utils."""


import unittest

import zoom_capture_utils


_ARUCO_MARKER_CENTER = (320, 240)
_FOCAL_LENGTH = 1
_IMG_SIZE = (640, 480)
_OFFSET_RTOL = 0.1
_RADIUS_RTOL = 0.1
_WRONG_ARUCO_OFFSET = 200


def _generate_aruco_markers(center, side_length):
  x, y = center
  return (
      (x - side_length // 2, y - side_length // 2),  # top left
      (x + side_length // 2, y - side_length // 2),  # top right
      (x + side_length // 2, y + side_length // 2),  # bottom right
      (x - side_length // 2, y + side_length // 2),  # bottom left
  )


def _generate_valid_zoom_results():
  return [
      zoom_capture_utils.ZoomTestData(
          result_zoom=1,
          radius_tol=_RADIUS_RTOL,
          offset_tol=_OFFSET_RTOL,
          focal_length=_FOCAL_LENGTH,
          aruco_corners=_generate_aruco_markers(_ARUCO_MARKER_CENTER, 10),
          aruco_offset=10
      ),
      zoom_capture_utils.ZoomTestData(
          result_zoom=2,
          radius_tol=_RADIUS_RTOL,
          offset_tol=_OFFSET_RTOL,
          focal_length=_FOCAL_LENGTH,
          aruco_corners=_generate_aruco_markers(_ARUCO_MARKER_CENTER, 20),
          aruco_offset=20
      ),
      zoom_capture_utils.ZoomTestData(
          result_zoom=3,
          radius_tol=_RADIUS_RTOL,
          offset_tol=_OFFSET_RTOL,
          focal_length=_FOCAL_LENGTH,
          aruco_corners=_generate_aruco_markers(_ARUCO_MARKER_CENTER, 30),
          aruco_offset=30
      ),
      zoom_capture_utils.ZoomTestData(
          result_zoom=4,
          radius_tol=_RADIUS_RTOL,
          offset_tol=_OFFSET_RTOL,
          focal_length=_FOCAL_LENGTH,
          aruco_corners=_generate_aruco_markers(_ARUCO_MARKER_CENTER, 40),
          aruco_offset=40
      ),
  ]


class ZoomCaptureUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def setUp(self):
    super().setUp()
    self.zoom_results = _generate_valid_zoom_results()

  def test_verify_zoom_results_enough_zoom_data(self):
    self.assertTrue(
        zoom_capture_utils.verify_zoom_results(
            self.zoom_results, _IMG_SIZE, 4, 1
        )
    )

  def test_verify_zoom_results_not_enough_zoom(self):
    self.assertFalse(
        zoom_capture_utils.verify_zoom_results(
            self.zoom_results, _IMG_SIZE, 5, 1
        )
    )

  def test_verify_zoom_results_wrong_zoom(self):
    self.zoom_results[-1].result_zoom = 5
    self.assertFalse(
        zoom_capture_utils.verify_zoom_results(
            self.zoom_results, _IMG_SIZE, 4, 1
        )
    )

  def test_verify_zoom_results_wrong_offset(self):
    self.zoom_results[-1].aruco_offset = _WRONG_ARUCO_OFFSET
    self.assertFalse(
        zoom_capture_utils.verify_zoom_results(
            self.zoom_results, _IMG_SIZE, 4, 1
        )
    )


if __name__ == '__main__':
  unittest.main()
