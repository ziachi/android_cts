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
"""Tests for ip_chart_pattern_detector.py file."""

import os
import unittest

import cv2
import image_processing_utils
import ip_chart_pattern_detector as pattern_detector


_IP_CHART_FILE = 'ip-test-chart-gen2.jpg'
_SLANTED_EDGE_FILE = 'slanted_edge.png'


class IpChartPatternDetectorTest(unittest.TestCase):
  """Unit tests for this module."""

  def test_qr_code_exists(self):
    """Unit test for detect pattern.

    Checks if the qr_code is detected or not in the image.
    """
    query_image = os.path.join(image_processing_utils.TEST_IMG_DIR,
                               _IP_CHART_FILE)
    image_arr = cv2.imread(query_image)

    self.assertIsNotNone(
        pattern_detector.detect_pattern(
            query_image=cv2.cvtColor(image_arr, cv2.COLOR_BGRA2BGR)
        )
    )

  def test_qr_code_does_not_exist(self):
    """Unit test for detect pattern.

    Checks if no qr_code in the image is handled correctly.
    """
    query_image = os.path.join(image_processing_utils.TEST_IMG_DIR,
                               _SLANTED_EDGE_FILE)
    image_arr = cv2.imread(query_image)

    self.assertIsNone(
        pattern_detector.detect_pattern(
            query_image=cv2.cvtColor(image_arr, cv2.COLOR_BGRA2BGR)
        )
    )


if __name__ == '__main__':
  unittest.main()
