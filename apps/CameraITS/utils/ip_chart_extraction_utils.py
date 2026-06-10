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
"""Utility functions for extracting IP chart features."""

import logging
import os

import cv2
import ip_chart_pattern_detector as pd
import numpy as np


# The order of the dynamic range patch cells in the chart based on
# their RGB values and corresponding positions in the chart.
DYNAMIC_RANGE_PATCH_ORDER = [
    2,
    3,
    1,
    4,
    0,
    5,
    19,
    6,
    18,
    7,
    17,
    8,
    16,
    9,
    15,
    10,
    14,
    11,
    13,
    12,
]
SCALE_FACTOR = 2 / 3


def get_feature_from_image(
    img, output_file_name, log_path, feature=pd.TestChartFeature.FULL_CHART
):
  """Get feature extracted from the captured image.

  Args:
    img: path to the captured image.
    output_file_name: output filename.
    log_path: path to save the extracted chart.
    feature: test chart feature to extract from the image.
  Returns:
    feature_image: extracted feature image in BGRA color space image with only
    the test chart features in query image, any pixel not within feature will be
    fully transparent. Returns None in case of any error.
    output_file_path: path to the extracted chart.
  """
  # Convert image to grayscale
  img_bgr = cv2.imread(img)
  feature_image = pd.get_test_chart_features_from_image(
      img_bgr,
      test_chart_features=[feature],
  )
  if (
      pd.detect_pattern(
          query_image=cv2.cvtColor(feature_image, cv2.COLOR_BGRA2BGR)
      )
      is None
  ):
    raise ValueError('Feature not found in the image.')
  else:
    logging.debug('Feature found in the image.')
    cv2.imwrite(
        os.path.join(log_path, output_file_name + '.png'),
        feature_image,
    )
    return feature_image, os.path.join(log_path, output_file_name + '.png')


def get_dynamic_range_patch_cells(img):
  """Get list of each cell from the dynamic range chart.

  Returns the list of each cell from the dynamic range chart.
  Each cell is an instance of `DynamicRangePatch` containing the corner points
  and the cell image. Each cell image is a numpy array in BGRA format.
  The list contains the cell in clockwise order from top-left.

  Args:
    img: path to the full chart captured image.

  Returns:
    dynamic_range_patch_cells: A list of `DynamicRangePatch` where every cell is
    a list containing the four corner positions in query image and the original
    color in test chart.
  """
  img_bgr = cv2.imread(img)
  dynamic_range_patch_cells = pd.find_dynamic_range_patches(img_bgr)
  logging.debug(
      'Dynamic range patch cells length: %s', len(dynamic_range_patch_cells)
  )
  if len(dynamic_range_patch_cells) < 20:
    raise AssertionError('All dynamic range patch cells not found')
  return dynamic_range_patch_cells


def get_cropped_patch(
    patch_image,
    patch_suffix,
    log_path,
    scale_factor=None,
):
  """Returns the cropped patch with scale factor or patch length.

  Args:
    patch_image: numpy array of patch image in BGRA format
    patch_suffix: suffix of the patch
    log_path: path to the log directory
    scale_factor: scale factor of the patch

  Returns:
    cropped_patch: numpy array of cropped patch image in RGB format
  """
  # Extract the alpha channel
  alpha_channel = patch_image[:, :, 3]

  # Find the non-zero (non-transparent) pixels
  y_indices, x_indices = np.where(alpha_channel != 0)

  # Get the bounding box of the non-transparent region
  min_x = np.min(x_indices)
  min_y = np.min(y_indices)
  max_x = np.max(x_indices)
  max_y = np.max(y_indices)

  # Crop the image to the bounding box
  non_tranpsarent_patch = patch_image[min_y:max_y + 1, min_x:max_x + 1]
  height, width = non_tranpsarent_patch.shape[:2]
  target_size = None

  if scale_factor is not None:
    # Determine the target size, ensuring it's even
    target_size = int(min(height, width) * scale_factor)
    target_size = target_size - (target_size % 2)  # Make it even
    logging.debug(
        'Exracted %s patch shape: %s x %s', patch_suffix, height, width
    )

  center_x = height // 2
  center_y = width // 2

  if target_size is None:
    raise ValueError(
        'Target size is None. One of scale_factor or patch_length must be'
        ' specified.'
    )

  start_x = center_x - target_size // 2
  start_y = center_y - target_size // 2

  end_x = start_x + target_size
  end_y = start_y + target_size

  cropped_patch = non_tranpsarent_patch[start_x:end_x, start_y:end_y]
  cv2.imwrite(os.path.join(log_path, patch_suffix + '.png'), cropped_patch)
  height, width = cropped_patch.shape[:2]
  logging.debug('Cropped %s patch shape: %s x %s', patch_suffix, height, width)
  cropped_patch = cv2.cvtColor(cropped_patch, cv2.COLOR_BGRA2RGB)
  return cropped_patch


def get_cropped_dynamic_range_patch_cells(img_path, log_path, chart_suffix):
  """Returns the cropped dynamic range patch cells.

  Args:
    img_path: path to the full chart captured image.
    log_path: path to the log directory
    chart_suffix: suffix of the patch
  Returns:
   cropped_dynamic_range_patch_cells: Cropped dynamic range patch cells
   in the DYNAMIC_RANGE_PATCH_ORDER.
  """
  logging.debug('Getting cropped dynamic range patch cells')
  dynamic_range_patch_cells = get_dynamic_range_patch_cells(img_path)
  reordered_dynamic_range_patch_cells = []
  # Reorder the dynamic range patch cells based on their RGB values and
  # corresponding positions in the chart.
  for i in DYNAMIC_RANGE_PATCH_ORDER:
    reordered_dynamic_range_patch_cells.append(dynamic_range_patch_cells[i])
  cropped_dynamic_range_patch_cells = []
  for i, cell in enumerate(reordered_dynamic_range_patch_cells):
    chart_name = f'{chart_suffix}_dynamic_range_patch_cell_{i}'
    center_patch = get_cropped_patch(
        cell.image,
        chart_name,
        log_path,
        scale_factor=SCALE_FACTOR,
    )
    cv2.imwrite(os.path.join(log_path, chart_name + '.png'), center_patch)
    cropped_dynamic_range_patch_cells.append(center_patch)
  return cropped_dynamic_range_patch_cells
