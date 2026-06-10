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
"""Utility functions for Default camera app and JCA image Parity metrics."""

import logging
import math

import cv2
import numpy as np

_DYNAMIC_PATCH_MID_TONE_START_IDX = 5
_DYNAMIC_PATCH_MID_TONE_END_IDX = 15
AR_REL_TOL = 0.1
EXPECTED_BRIGHTNESS_50 = 50.0
MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR = 10.0
MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR = 8.0
MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR = 6.0
MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR = 3.0
# This is the height of center QR code on feature chart in cm
CENTER_QR_CODE_CM = 5
FOV_REL_TOL = 0.1


def check_if_qr_code_size_match(img1, img2):
  """Checks if the size of two images are the same or not.

  Args:
    img1: first image array in BGRA format
    img2: second image array in BGRA format
  Returns:
    True if the size of two images are the same, False otherwise
  """
  # Extract the alpha channel
  alpha_channel_1 = img1[:, :, 3]
  alpha_channel_2 = img2[:, :, 3]

  # Find the non-zero (non-transparent) pixels
  y1_indices, x1_indices = np.where(alpha_channel_1 != 0)
  y2_indices, x2_indices = np.where(alpha_channel_2 != 0)

  # Get the bounding box of the non-transparent region
  min_x1 = np.min(x1_indices)
  min_y1 = np.min(y1_indices)
  max_x1 = np.max(x1_indices)
  max_y1 = np.max(y1_indices)

  min_x2 = np.min(x2_indices)
  min_y2 = np.min(y2_indices)
  max_x2 = np.max(x2_indices)
  max_y2 = np.max(y2_indices)

  # Crop the image to the bounding box
  non_tranpsarent_patch_1 = img1[min_y1:max_y1 + 1, min_x1:max_x1 + 1]
  non_tranpsarent_patch_2 = img2[min_y2:max_y2 + 1, min_x2:max_x2 + 1]

  height1, width1 = non_tranpsarent_patch_1.shape[:2]
  logging.debug('Height 1: %s, Width 1: %s', height1, width1)
  ar_1 = width1 / height1
  logging.debug('Aspect ratio 1: %.2f', ar_1)
  if not math.isclose(ar_1, 1, rel_tol=AR_REL_TOL):
    raise ValueError(
        'Aspect ratio of the non-transparent region of the image 1 is not 1:1.'
    )
  height2, width2 = non_tranpsarent_patch_2.shape[:2]
  logging.debug('Height 2: %s, Width 2: %s', height2, width2)
  ar_2 = width2 / height2
  logging.debug('Aspect ratio 2: %.2f', ar_2)
  if not math.isclose(ar_2, 1, rel_tol=AR_REL_TOL):
    raise ValueError(
        'Aspect ratio of the non-transparent region of the image 2 is not 1:1.'
    )
  return math.isclose(height1, height2, rel_tol=AR_REL_TOL)


def get_lab_mean_values(img):
  """Computes the mean values of the 'L', 'A', and 'B' channels.

  Converts the img from RGB to CIELAB color space and calculates the mean values
  of L, A and B channels only for the non-transparent regions of the image

  Args:
    img: img array in RGB colorspace.
  Returns:
    mean_l, mean_a, mean_b: mean value of l, a, b channels
  """
  img_lab = cv2.cvtColor(img, cv2.COLOR_RGB2LAB)
  img_lab = img_lab.astype(np.uint32)
  mean_l = np.mean(img_lab[:, :, 0]) * 100 / 255
  mean_a = np.mean(img_lab[:, :, 1]) - 128
  mean_b = np.mean(img_lab[:, :, 2]) - 128
  logging.debug('L, A, B values: %.2f %.2f %.2f', mean_l, mean_a, mean_b)
  return mean_l, mean_a, mean_b


def get_brightness_variation(
    default_brightness_values, jca_brightness_values
):
  """Gets the brightness variation between default and jca color cells.

  Args:
    default_brightness_values: The default brightness values of the greyscale
      cells
    jca_brightness_values: The jca brightness values of the greyscale cells

  Returns:
    mean_delta_ab_diff: mean delta ab diff between default and jca rounded
      upto 2 places
  """
  default_brightness = np.mean(default_brightness_values)
  jca_brightness = np.mean(jca_brightness_values)

  default_ref_brightness_diff = default_brightness - EXPECTED_BRIGHTNESS_50
  jca_ref_brightness_diff = jca_brightness - EXPECTED_BRIGHTNESS_50
  default_jca_brightness_diff = jca_brightness - default_brightness
  logging.debug('default_ref_brightness_diff: %.2f',
                default_ref_brightness_diff)
  logging.debug('jca_ref_brightness_diff: %.2f',
                jca_ref_brightness_diff)
  logging.debug('default_jca_brightness_diff: %.2f',
                default_jca_brightness_diff)

  # Check that the brightness difference default and jca to the reference do not
  # exceed the max absolute error
  if (default_ref_brightness_diff > MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR) or (
      jca_ref_brightness_diff > MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR
  ):
    e_msg = (
        f'The brightness of default and jca for greyscale cells exceeds the'
        f' threshold. Actual default: {default_ref_brightness_diff:.2f}, Actual'
        f' jca: {default_jca_brightness_diff:.2f}, Expected:'
        f' {MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  # Check that the brightness between default and jca does not exceed the
  # max relative error
  if (default_jca_brightness_diff > MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR):
    e_msg = (
        f'The brightness difference between default and jca for greyscale cells'
        f' exceeds the threshold. Actual: {default_jca_brightness_diff:.2f}, '
        f'Expected: {MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  return default_jca_brightness_diff


def do_brightness_check(default_patch_list, jca_patch_list):
  """Computes brightness diff between default and jca capture images.

  Args:
    default_patch_list: default camera dynamic range patch cells
    jca_patch_list: jca camera dynamic range patch cells

  Returns:
    mean_brightness_diff: mean brightness diff between default and jca
  """
  default_brightness_values = []
  for patch in default_patch_list:
    mean_l, _, _ = get_lab_mean_values(patch)
    default_brightness_values.append(mean_l)
  jca_brightness_values = []
  for patch in jca_patch_list:
    mean_l, _, _ = get_lab_mean_values(patch)
    jca_brightness_values.append(mean_l)

  default_rounded_values = [round(float(x), 2)
                            for x in default_brightness_values]
  jca_rounded_values = [round(float(x), 2) for x in jca_brightness_values]

  logging.debug('default_brightness_values: %s', default_rounded_values)
  logging.debug('jca_brightness_values: %s', jca_rounded_values)

  mean_brightness_diff = get_brightness_variation(
      default_brightness_values[
          _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
      ],
      jca_brightness_values[
          _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
      ],
  )
  logging.debug(
      'Brightness difference between default and jca: %.2f',
      mean_brightness_diff,
  )
  return round(float(mean_brightness_diff), 2)


def get_neutral_delta_ab(greyscale_cells):
  """Returns the delta ab value for grey scale cells compared to reference.

  Args:
    greyscale_cells: list of grey scale cells

  Returns:
    neutral_delta_ab_values: list of neutral delta ab values for each color cell
  """
  neutral_delta_ab_values = []
  for i, greyscale_cell in enumerate(greyscale_cells):
    _, mean_a, mean_b = get_lab_mean_values(greyscale_cell)
    neutral_delta_ab = np.sqrt(mean_a**2 + mean_b**2)
    logging.debug(
        'Reference delta AB value for greyscale cell %d: %.2f',
        i + 1,
        neutral_delta_ab,
    )
    neutral_delta_ab_values.append(neutral_delta_ab)
  return neutral_delta_ab_values


def get_delta_ab(color_cells_1, color_cells_2):
  """Computes the delta ab value between two color cells.

  Args:
    color_cells_1: first color cells array
    color_cells_2: second color cells array

  Returns:
    delta_ab_values: list of delta ab values for each color cell
  """
  delta_ab_values = []
  for i, (color_cell_1, color_cell_2) in enumerate(
      zip(color_cells_1, color_cells_2)
  ):
    _, mean_a_1, mean_b_1 = get_lab_mean_values(color_cell_1)
    _, mean_a_2, mean_b_2 = get_lab_mean_values(color_cell_2)
    delta_ab = np.sqrt((mean_a_1 - mean_a_2) ** 2 + (mean_b_1 - mean_b_2) ** 2)
    logging.debug('Delta AB value for color cell %d: %.2f', i + 1, delta_ab)
    delta_ab_values.append(delta_ab)
  return delta_ab_values


def get_white_balance_variation(
    default_greyscale_cells, jca_greyscale_cells
):
  """Gets the white balance variation between default and jca color cells.

  Args:
    default_greyscale_cells: list of default greyscale cells
    jca_greyscale_cells: list of jca greyscale cells

  Returns:
    mean_delta_ab_diff: mean delta ab diff between default and jca
  """
  default_neutral_delta_ab = np.mean(
      get_neutral_delta_ab(default_greyscale_cells)
  )
  jca_neutral_delta_ab = np.mean(get_neutral_delta_ab(jca_greyscale_cells))
  default_jca_neutral_delta_ab = np.mean(
      get_delta_ab(default_greyscale_cells, jca_greyscale_cells)
  )
  logging.debug('default_neutral_delta_ab_rounded_values: %.2f',
                default_neutral_delta_ab)
  logging.debug('jca_neutral_delta_ab_rounded_values: %.2f',
                jca_neutral_delta_ab)
  logging.debug('default_jca_neutral_delta_ab_rounded_values: %.2f',
                default_jca_neutral_delta_ab)

  # Check that the white balance between default and jca does not exceed the
  # max absolute error.
  if (default_neutral_delta_ab > MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR) or (
      jca_neutral_delta_ab > MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR
  ):
    e_msg = (
        f'White balance of default and jca images exceeds the threshold.'
        f'Actual default value: {default_neutral_delta_ab:.2f},'
        f'Actual jca value: {jca_neutral_delta_ab:.2f}, '
        f'Expected maximum: {MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  # Check that the white balance between default and jca does not exceed the
  # max relative error.
  if (default_jca_neutral_delta_ab > MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR):
    e_msg = (
        f'White balance between default and jca for greyscale cells exceeds the'
        f' threshold. Actual default: {default_jca_neutral_delta_ab:.2f}, '
        f'Expected: {MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  return default_jca_neutral_delta_ab


def do_white_balance_check(default_patch_list, jca_patch_list):
  """Computes white balance diff between default and jca images.

  Args:
    default_patch_list: default camera dynamic range patch cells
    jca_patch_list: jca camera dynamic range patch cells

  Returns:
    mean_neutral_delta_ab: mean neutral delta ab between default and jca
      rounded to 2 places
  """
  default_a_values = []
  default_b_values = []
  default_middle_tone_patch_list = default_patch_list[
      _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
  ]
  for patch in default_middle_tone_patch_list:
    _, mean_a, mean_b = get_lab_mean_values(patch)
    default_a_values.append(mean_a)
    default_b_values.append(mean_b)
  jca_a_values = []
  jca_b_values = []
  jca_middle_tone_patch_list = jca_patch_list[
      _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
  ]
  for patch in jca_middle_tone_patch_list:
    _, mean_a, mean_b = get_lab_mean_values(patch)
    jca_a_values.append(mean_a)
    jca_b_values.append(mean_b)

  default_rounded_a_values = [round(float(x), 2)
                              for x in default_a_values]
  default_rounded_b_values = [round(float(x), 2)
                              for x in default_b_values]
  jca_rounded_a_values = [round(float(x), 2)
                          for x in jca_a_values]
  jca_rounded_b_values = [round(float(x), 2)
                          for x in jca_b_values]
  logging.debug('default_rounded_a_values: %s', default_rounded_a_values)
  logging.debug('default_rounded_b_values: %s', default_rounded_b_values)
  logging.debug('jca_rounded_a_values: %s', jca_rounded_a_values)
  logging.debug('jca_rounded_b_values: %s', jca_rounded_b_values)

  mean_neutral_delta_ab = get_white_balance_variation(
      default_middle_tone_patch_list,
      jca_middle_tone_patch_list,
  )
  logging.debug(
      'White balance difference between default and jca: %.2f',
      mean_neutral_delta_ab,
  )
  return round(float(mean_neutral_delta_ab), 2)


def _get_non_transparent_pixels(img):
  """Returns the non transparent pixels from BGRA image.
  """
  alpha_channel = img[:, :, 3]

  # Find the non-zero (non-transparent) pixels
  y_indices, x_indices = np.where(alpha_channel != 0)

  # Get the bounding box of the non-transparent region
  min_x = np.min(x_indices)
  min_y = np.min(y_indices)
  max_x = np.max(x_indices)
  max_y = np.max(y_indices)

  # Crop the image to the bounding box
  non_tranpsarent_patch = img[min_y:max_y + 1, min_x:max_x + 1]
  return non_tranpsarent_patch


def get_fov_in_degrees(img_path, qr_code_img, chart_distance):
  """Returns fov measurement in degrees.

  Args:
    img_path: captured img path
    qr_code_img: Extracted center QR code img
    chart_distance: distance between phone and chart in cm
  Returns:
    fov_degrees: FoV measurement in degrees
  """
  img = cv2.imread(img_path)
  img_height, _ = img.shape[:2]
  logging.debug('Height of captured img in pixels: %d', img_height)

  nt_qr_code_img = _get_non_transparent_pixels(qr_code_img)
  qr_code_height, _ = nt_qr_code_img.shape[:2]
  logging.debug('Height of QR code in pixels: %d', qr_code_height)

  # Get captured image height in cm
  height_in_cm = (img_height / qr_code_height) * CENTER_QR_CODE_CM
  logging.debug('Height of captured img in cm: %d', height_in_cm)
  angle_radians = 2 * math.atan(height_in_cm / (2 * chart_distance))
  fov_degrees = math.degrees(angle_radians)
  return fov_degrees
