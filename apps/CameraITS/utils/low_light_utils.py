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
"""Utility functions for low light camera tests."""

import logging
import os.path

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import cv2
import image_processing_utils
import matplotlib.pyplot as plt
import numpy as np
import opencv_processing_utils

_LOW_LIGHT_BOOST_AVG_DELTA_LUMINANCE_THRESH = 18
_LOW_LIGHT_BOOST_AVG_LUMINANCE_THRESH = 90
_BOUNDING_BOX_COLOR = (0, 255, 0)
_BOX_MIN_SIZE_RATIO = 0.08  # 8% of the cropped image width
_BOX_MAX_SIZE_RATIO = 0.5  # 50% of the cropped image width
_BOX_PADDING_RATIO = 0.2
_BOX_PADDING = 6  # 6 pixel padding to bounding box region
_CROP_PADDING = 10
_EXPECTED_NUM_OF_BOXES = 20  # The captured image must result in 20 detected
                             # boxes since the test scene has 20 boxes
_KEY_BOTTOM_LEFT = 'bottom_left'
_KEY_BOTTOM_RIGHT = 'bottom_right'
_KEY_TOP_LEFT = 'top_left'
_KEY_TOP_RIGHT = 'top_right'
_MAX_ASPECT_RATIO = 1.2
_MIN_ASPECT_RATIO = 0.8
_RED_BGR_COLOR = (0, 0, 255)
_NUM_CLUSTERS = 8
_K_MEANS_ITERATIONS = 10
_K_MEANS_EPSILON = 0.5
_TEXT_COLOR = (255, 255, 255)
_FIG_SIZE = (10, 6)
_DILATE_KERNEL_SIZE = (3, 3)
_DILATE_NUM_ITERATIONS = 3

# Allowed tablets for low light scenes
# List entries must be entered in lowercase
TABLET_LOW_LIGHT_SCENES_ALLOWLIST = (
    'hwcmr09',  # Huawei MediaPad M5
    'gta8wifi',  # Samsung Galaxy Tab A8
    'gta8',  # Samsung Galaxy Tab A8 LTE
    'gta9pwifi',  # Samsung Galaxy Tab A9+
    'gta9p',  # Samsung Galaxy Tab A9+ 5G
    'nabu',  # Xiaomi Pad 5
    'nabu_tw',  # Xiaomi Pad 5
    'xun',  # Xiaomi Redmi Pad SE
)

# Tablet brightness mapping strings for (rear, front) facing camera tests
# List entries must be entered in lowercase
TABLET_BRIGHTNESS = {
    'hwcmr09': ('4', '8'),  # Huawei MediaPad M5
    'gta8wifi': ('6', '12'),  # Samsung Galaxy Tab A8
    'gta8': ('6', '12'),  # Samsung Galaxy Tab A8 LTE
    'gta9pwifi': ('6', '12'),  # Samsung Galaxy Tab A9+
    'gta9p': ('6', '12'),  # Samsung Galaxy Tab A9+ 5G
    'nabu': ('8', '14'),  # Xiaomi Pad 5
    'nabu_tw': ('8', '14'),  # Xiaomi Pad 5
    'xun': ('6', '12'),  # Xiaomi Redmi Pad SE
}


def get_metering_region(cam, file_stem):
  """Get the metering region for the given image.

  Detects the chart in the preview image and returns the coordinates of the
  chart in the active array.

  Args:
    cam: ItsSession object to send commands.
    file_stem: File prefix for captured images.
  Returns:
    The metering region sensor coordinates in the active array or None if the
    test chart was not detected.
  """
  req = capture_request_utils.auto_capture_request()
  cap = cam.do_capture(req, cam.CAP_YUV)
  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  region_detection_file = f'{file_stem}_region_detection.jpg'
  image_processing_utils.write_image(img, region_detection_file)
  img = cv2.imread(region_detection_file)

  coords = _find_chart_bounding_region(img)
  if coords is None:
    return None

  # Convert image coordinates to sensor coordinates for metering rectangle
  img_w = img.shape[1]
  img_h = img.shape[0]
  props = cam.get_camera_properties()
  aa = props['android.sensor.info.activeArraySize']
  aa_width, aa_height = aa['right'] - aa['left'], aa['bottom'] - aa['top']
  logging.debug('Active array size: %s', aa)
  coords_tl = (coords[0], coords[1])
  coords_br = (coords[0] + coords[2], coords[1] + coords[3])
  s_coords_tl = image_processing_utils.convert_image_coords_to_sensor_coords(
      aa_width, aa_height, coords_tl, img_w, img_h)
  s_coords_br = image_processing_utils.convert_image_coords_to_sensor_coords(
      aa_width, aa_height, coords_br, img_w, img_h)
  sensor_coords = (s_coords_tl[0], s_coords_tl[1], s_coords_br[0],
                   s_coords_br[1])

  # If testing front camera, mirror coordinates either left/right or up/down
  # Preview are flipped on device's natural orientation
  # For sensor orientation 90 or 270, it is up or down
  # For sensor orientation 0 or 180, it is left or right
  if (props['android.lens.facing'] ==
      camera_properties_utils.LENS_FACING['FRONT']):
    if props['android.sensor.orientation'] in (90, 270):
      tl_coordinates = (sensor_coords[0], aa_height - sensor_coords[3])
      br_coordinates = (sensor_coords[2], aa_height - sensor_coords[1])
      logging.debug('Found sensor orientation %d, flipping up down',
                    props['android.sensor.orientation'])
    else:
      tl_coordinates = (aa_width - sensor_coords[2], sensor_coords[1])
      br_coordinates = (aa_width - sensor_coords[0], sensor_coords[3])
      logging.debug('Found sensor orientation %d, flipping left right',
                    props['android.sensor.orientation'])
    logging.debug('Mirrored top-left coordinates: %s', tl_coordinates)
    logging.debug('Mirrored bottom-right coordinates: %s', br_coordinates)
  else:
    tl_coordinates = (sensor_coords[0], sensor_coords[1])
    br_coordinates = (sensor_coords[2], sensor_coords[3])

  tl_x = int(tl_coordinates[0])
  tl_y = int(tl_coordinates[1])
  br_x = int(br_coordinates[0])
  br_y = int(br_coordinates[1])
  rect_w = br_x - tl_x
  rect_h = br_y - tl_y
  return {'x': tl_x,
          'y': tl_y,
          'width': rect_w,
          'height': rect_h,
          'weight': opencv_processing_utils.AE_AWB_METER_WEIGHT}


def _find_chart_bounding_region(img):
  """Finds the bounding region of the chart.

  Args:
    img: numpy array; captured image from scene_low_light.
  Returns:
    The coordinates of the bounding region relative to the input image. This
    is returned as (left, top, width, height) or None if the test chart was
    not detected.
  """
  # To apply k-means clustering, we need to convert the image in to an array
  # where each row represents a pixel in the image, and each column is a feature
  # In this case, the feature represents the RGB channels of the pixel
  data = img.reshape((-1, 3))
  data = np.float32(data)

  k_means_criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER,
                      _K_MEANS_ITERATIONS, _K_MEANS_EPSILON)
  _, labels, centers = cv2.kmeans(data, _NUM_CLUSTERS, None, k_means_criteria,
                                  _K_MEANS_ITERATIONS,
                                  cv2.KMEANS_RANDOM_CENTERS)
  # Find the cluster closest to red
  min_dist = float('inf')
  closest_cluster_index = -1
  for index, center in enumerate(centers):
    dist = np.linalg.norm(center - np.array(_RED_BGR_COLOR))
    if dist < min_dist:
      min_dist = dist
      closest_cluster_index = index

  target_label = closest_cluster_index

  # create a mask using the data associated with the cluster closest to red
  mask = labels.flatten() == target_label
  mask = mask.reshape((img.shape[0], img.shape[1]))
  mask = mask.astype(np.uint8)
  dilate_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, _DILATE_KERNEL_SIZE)
  mask = cv2.dilate(mask, dilate_kernel, iterations=_DILATE_NUM_ITERATIONS)

  contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL,
                                 cv2.CHAIN_APPROX_SIMPLE)

  max_area = 20
  max_box = None

  # Find the largest box that is closest to square
  for c in contours:
    x, y, w, h = cv2.boundingRect(c)
    aspect_ratio = w / h
    if _MIN_ASPECT_RATIO < aspect_ratio < _MAX_ASPECT_RATIO:
      area = w * h
      if area > max_area:
        max_area = area
        max_box = (
            x + _BOX_PADDING,
            y + _BOX_PADDING,
            w - _BOX_PADDING * 2,
            h - _BOX_PADDING * 2
        )

  return max_box


def _crop(img):
  """Crops the captured image according to the red square outline.

  Args:
    img: numpy array; captured image from scene_low_light.
  Returns:
    numpy array of the cropped image or the original image if the crop region
    isn't found.
  """
  max_box = _find_chart_bounding_region(img)

  # If the box is found then return the cropped image
  # otherwise the original image is returned
  if max_box:
    x, y, w, h = max_box
    cropped_img = img[
        y+_CROP_PADDING:y+h-_CROP_PADDING,
        x+_CROP_PADDING:x+w-_CROP_PADDING
    ]
    return cropped_img

  return img


def _find_boxes(image):
  """Finds boxes in the captured image for computing luminance.

  The captured image should be of scene_low_light.png. The boxes are detected
  by finding the contours by applying a threshold followed erosion.

  Args:
    image: numpy array; the captured image.
  Returns:
    array; an array of boxes, where each box is (x, y, w, h).
  """
  gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
  blur = cv2.GaussianBlur(gray, (3, 3), 0)

  thresh = cv2.adaptiveThreshold(
      blur, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 31, -5)

  kernel = np.ones((3, 3), np.uint8)
  eroded = cv2.erode(thresh, kernel, iterations=1)

  contours, _ = cv2.findContours(eroded, cv2.RETR_EXTERNAL,
                                 cv2.CHAIN_APPROX_SIMPLE)
  boxes = []

  # Filter out boxes that are too small or too large
  # and boxes that are not square
  img_hw_size_max = max(image.shape[0], image.shape[1])
  box_min_size = int(round(img_hw_size_max * _BOX_MIN_SIZE_RATIO, 0))
  if box_min_size == 0:
    raise AssertionError('Minimum box size calculated was 0. Check cropped '
                         'image size.')
  box_max_size = int(img_hw_size_max * _BOX_MAX_SIZE_RATIO)
  for c in contours:
    x, y, w, h = cv2.boundingRect(c)
    aspect_ratio = w / h
    if (w > box_min_size and h > box_min_size and
        w < box_max_size and h < box_max_size and
        _MIN_ASPECT_RATIO < aspect_ratio < _MAX_ASPECT_RATIO):
      boxes.append((x, y, w, h))
  return boxes


def _correct_image_rotation(img, regions, max_num_brightest_squares):
  """Corrects the captured image orientation.

  The captured image should be of scene_low_light.png. The darkest square
  must appear in the bottom right and the brightest square must appear in
  the bottom left. This is necessary in order to traverse the hilbert
  ordered squares to return a darkest to brightest ordering.

  Args:
    img: numpy array; the original image captured.
    regions: the tuple of (box, luminance) computed for each square
      in the image.
    max_num_brightest_squares: maximum number of brightest squares present in
      the image.
  Returns:
    numpy array; image in the corrected orientation.
  """
  corner_brightness = {
      _KEY_TOP_LEFT: regions[2][1],
      _KEY_BOTTOM_LEFT: regions[5][1],
      _KEY_TOP_RIGHT: regions[14][1],
      _KEY_BOTTOM_RIGHT: regions[17][1],
  }

  darkest_corner = ('', float('inf'))
  brightest_corner = [('', float('-inf'))]

  for corner, luminance in corner_brightness.items():
    if luminance < darkest_corner[1]:
      darkest_corner = (corner, luminance)
    if luminance > brightest_corner[0][1]:
      brightest_corner = [(corner, luminance)]
    elif luminance == brightest_corner[0][1]:
      brightest_corner.append((corner, luminance))

  if len(brightest_corner) > max_num_brightest_squares:
    raise AssertionError('The captured image is over exposed due to the '
                         'presence of multiple bright squares.')

  if darkest_corner == brightest_corner:
    raise AssertionError('The captured image failed to detect the location '
                         'of the darkest and brightest squares.')

  if darkest_corner[0] == _KEY_TOP_LEFT:
    if any(corner == _KEY_BOTTOM_LEFT for corner, _ in brightest_corner):
      # rotate 90 CW and then flip vertically
      img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
      img = cv2.flip(img, 0)
    elif any(corner == _KEY_TOP_RIGHT for corner, _ in brightest_corner):
      # flip both vertically and horizontally
      img = cv2.flip(img, -1)
    else:
      raise AssertionError('The captured image failed to detect the location '
                           'of the brightest square.')
  elif darkest_corner[0] == _KEY_BOTTOM_LEFT:
    if any(corner == _KEY_TOP_LEFT for corner, _ in brightest_corner):
      # rotate 90 CCW
      img = cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)
    elif any(corner == _KEY_BOTTOM_RIGHT for corner, _ in brightest_corner):
      # flip horizontally
      img = cv2.flip(img, 1)
    else:
      raise AssertionError('The captured image failed to detect the location '
                           'of the brightest square.')
  elif darkest_corner[0] == _KEY_TOP_RIGHT:
    if any(corner == _KEY_TOP_LEFT for corner, _ in brightest_corner):
      # flip vertically
      img = cv2.flip(img, 0)
    elif any(corner == _KEY_BOTTOM_RIGHT for corner, _ in brightest_corner):
      # rotate 90 CW
      img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
    else:
      raise AssertionError('The captured image failed to detect the location '
                           'of the brightest square.')
  elif darkest_corner[0] == _KEY_BOTTOM_RIGHT:
    if any(corner == _KEY_BOTTOM_LEFT for corner, _ in brightest_corner):
      # correct orientation
      pass
    elif any(corner == _KEY_TOP_RIGHT for corner, _ in brightest_corner):
      # rotate 90 and flip horizontally
      img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
      img = cv2.flip(img, 1)
    else:
      raise AssertionError('The captured image failed to detect the location '
                           'of the brightest square.')
  return img


def _compute_luminance_regions(image, boxes):
  """Compute the luminance for each box in scene_low_light.

  Args:
    image: numpy array; captured image.
    boxes: array; array of boxes where each box is (x, y, w, h).
  Returns:
    Array of tuples where each tuple is (box, luminance).
  """
  intensities = []
  for b in boxes:
    x, y, w, h = b
    padding = min(w, h) * _BOX_PADDING_RATIO
    left = int(x + padding)
    top = int(y + padding)
    right = int(x + w - padding)
    bottom = int(y + h - padding)
    box = image[top:bottom, left:right]
    box_xyz = cv2.cvtColor(box, cv2.COLOR_BGR2XYZ)
    intensity = int(np.mean(box_xyz[1]))
    intensities.append((b, intensity))
  return intensities


def _draw_luminance(image, intensities):
  """Draws the luma and noise for each box in scene_low_light for debugging.

  Args:
    image: numpy array; captured image.
    intensities: array; array of tuples (box, luminance intensity).
  """
  for b, intensity in intensities:
    x, y, w, h = b
    padding = min(w, h) * _BOX_PADDING_RATIO
    left = int(x + padding)
    top = int(y + padding)
    right = int(x + w - padding)
    bottom = int(y + h - padding)
    noise_stats = image_processing_utils.compute_patch_noise(
        image, (left, top, (right - left), (bottom - top)))
    cv2.rectangle(image, (left, top), (right, bottom), _BOUNDING_BOX_COLOR, 2)
    # place the luma value above the box offset by 10 pixels
    cv2.putText(img=image, text=f'{intensity}', org=(x, y - 10),
                fontFace=cv2.FONT_HERSHEY_PLAIN, fontScale=1,
                color=_TEXT_COLOR)
    luma = str(round(noise_stats['luma'], 1))
    cu = str(round(noise_stats['chroma_u'], 1))
    cv = str(round(noise_stats['chroma_v'], 1))
    # place the noise (luma, chroma u, chroma v) values above the luma value
    # offset by 30 pixels
    cv2.putText(img=image, text=f'{luma}, {cu}, {cv}', org=(x, y - 30),
                fontFace=cv2.FONT_HERSHEY_PLAIN, fontScale=1,
                color=_TEXT_COLOR)


def _compute_avg(results):
  """Computes the average luminance of the first 6 boxes.

  The boxes are part of scene_low_light.

  Args:
    results: A list of tuples where each tuple is (box, luminance).
  Returns:
    float; The average luminance of the first 6 boxes.
  """
  luminance_values = [luminance for _, luminance in results[:6]]
  avg = sum(luminance_values) / len(luminance_values)
  return avg


def _compute_avg_delta_of_successive_boxes(results):
  """Computes the delta of successive boxes & takes the average of the first 5.

  The boxes are part of scene_low_light.

  Args:
    results: A list of tuples where each tuple is (box, luminance).
  Returns:
    float; The average of the first 5 deltas of successive boxes.
  """
  luminance_values = [luminance for _, luminance in results[:6]]
  delta = [luminance_values[i] - luminance_values[i - 1]
           for i in range(1, len(luminance_values))]
  avg = sum(delta) / len(delta)
  return avg


def _plot_results(results, file_stem):
  """Plots the computed luminance for each box in scene_low_light.

  Args:
    results: A list of tuples where each tuple is (box, luminance).
    file_stem: The output file where the plot is saved.
  """
  luminance_values = [luminance for _, luminance in results]
  box_labels = [f'Box {i + 1}' for i in range(len(results))]

  plt.figure(figsize=_FIG_SIZE)
  plt.plot(box_labels, luminance_values, marker='o', linestyle='-', color='b')
  plt.scatter(box_labels, luminance_values, color='r')

  plt.title('Luminance for each Box')
  plt.xlabel('Boxes')
  plt.ylabel('Luminance (pixel intensity)')
  plt.grid('True')
  plt.xticks(rotation=45)
  plt.savefig(f'{file_stem}_luminance_plot.png', dpi=300)
  plt.close()


def _plot_successive_difference(results, file_stem):
  """Plots the successive difference in luminance between each box.

  The boxes are part of scene_low_light.

  Args:
    results: A list of tuples where each tuple is (box, luminance).
    file_stem: The output file where the plot is saved.
  """
  luminance_values = [luminance for _, luminance in results]
  delta = [luminance_values[i] - luminance_values[i - 1]
           for i in range(1, len(luminance_values))]
  box_labels = [f'Box {i} to Box {i + 1}' for i in range(1, len(results))]

  plt.figure(figsize=_FIG_SIZE)
  plt.plot(box_labels, delta, marker='o', linestyle='-', color='b')
  plt.scatter(box_labels, delta, color='r')

  plt.title('Difference in Luminance Between Successive Boxes')
  plt.xlabel('Box Transition')
  plt.ylabel('Luminance Difference')
  plt.grid('True')
  plt.xticks(rotation=45)
  plt.savefig(
      f'{file_stem}_luminance_difference_between_successive_boxes_plot.png',
      dpi=300)
  plt.close()


def _plot_noise(results, file_stem, img, test_name):
  """Plots the noise in the image.

  The boxes are part of scene_low_light.

  Args:
    results: A list of tuples where each tuple is (box, luminance).
    file_stem: The output file where the plot is saved.
    img: The captured image used to measure patch noise.
    test_name: Name of the test being plotted.
  """
  luma_noise_values = []
  chroma_u_noise_values = []
  chroma_v_noise_values = []
  for region, _ in results:
    x, y, w, h = region
    padding = min(w, h) * _BOX_PADDING_RATIO
    left = int(x + padding)
    top = int(y + padding)
    right = int(x + w - padding)
    bottom = int(y + h - padding)
    noise_stats = image_processing_utils.compute_patch_noise(
        img, (left, top, (right - left), (bottom - top)))
    luma_noise_values.append(noise_stats['luma'])
    chroma_u_noise_values.append(noise_stats['chroma_u'])
    chroma_v_noise_values.append(noise_stats['chroma_v'])

  box_labels = [f'Box {i + 1}' for i in range(len(results))]

  plt.figure(figsize=_FIG_SIZE)
  plt.plot(box_labels, luma_noise_values, marker='o', linestyle='-',
           color='b', label='luma')
  plt.plot(box_labels, chroma_u_noise_values, marker='o', linestyle='-',
           color='r', label='chroma u')
  plt.plot(box_labels, chroma_v_noise_values, marker='o', linestyle='-',
           color='g', label='chroma v')
  plt.legend()

  plt.title('Luma, Chroma U, and Chroma V Noise per Box')
  plt.xlabel('Box')
  plt.ylabel('Noise (std dev)')
  plt.grid('True')
  plt.xticks(rotation=45)
  plt.savefig(f'{file_stem}_noise_per_box_plot.png', dpi=300)
  plt.close()
  # print the chart luma values for telemetry purposes
  # do not convert to logging.debug
  print(f'{test_name}_noise_luma: {luma_noise_values}')
  print(f'{test_name}_noise_chroma_u: {chroma_u_noise_values}')
  print(f'{test_name}_noise_chroma_v: {chroma_v_noise_values}')


def _sort_by_columns(regions):
  """Sort the regions by columns and then by row within each column.

  These regions are part of scene_low_light.

  Args:
    regions: The tuple of (box, luminance) of each square.
  Returns:
    array; an array of tuples of (box, luminance) sorted by columns then by row
      within each column.
  """
  # The input is 20 elements. The first two and last two elements represent the
  # 4 boxes on the outside used for diagnostics. Boxes in indices 2 through 17
  # represent the elements in the 4x4 grid.

  # Sort all elements by column
  col_sorted = sorted(regions, key=lambda r: r[0][0])

  # Sort elements within each column by row
  result = []
  result.extend(sorted(col_sorted[:2], key=lambda r: r[0][1]))

  for i in range(4):
    # take 4 rows per column and then sort the rows
    # skip the first two elements
    offset = i*4+2
    col = col_sorted[offset:(offset+4)]
    result.extend(sorted(col, key=lambda r: r[0][1]))

  result.extend(sorted(col_sorted[-2:], key=lambda r: r[0][1]))
  return result


def analyze_low_light_scene_capture(
    file_stem,
    img,
    avg_luminance_threshold=_LOW_LIGHT_BOOST_AVG_LUMINANCE_THRESH,
    avg_delta_luminance_threshold=_LOW_LIGHT_BOOST_AVG_DELTA_LUMINANCE_THRESH,
    max_num_brightest_squares=1):
  """Analyze a captured frame to check if it meets low light scene criteria.

  The capture is cropped first, then detects for boxes, and then computes the
  luminance of each box.

  Args:
    file_stem: The file prefix for results saved.
    img: numpy array; The captured image loaded by cv2 as and available for
      analysis.
    avg_luminance_threshold: minimum average luminance of the first 6 boxes.
    avg_delta_luminance_threshold: minimum average difference in luminance
      of the first 5 successive boxes of luminance.
    max_num_brightest_squares: maximum number of brightest squares present in
      the image.
  """
  cv2.imwrite(f'{file_stem}_original.jpg', img)
  img = _crop(img)
  cv2.imwrite(f'{file_stem}_cropped.jpg', img)
  boxes = _find_boxes(img)
  if len(boxes) != _EXPECTED_NUM_OF_BOXES:
    raise AssertionError('The captured image failed to detect the expected '
                         'number of boxes. '
                         'Check the captured image to see if the image was '
                         'correctly captured and try again. '
                         f'Actual: {len(boxes)}, '
                         f'Expected: {_EXPECTED_NUM_OF_BOXES}')

  regions = _compute_luminance_regions(img, boxes)

  # Sorted so each column is read left to right
  sorted_regions = _sort_by_columns(regions)
  img = _correct_image_rotation(img, sorted_regions, max_num_brightest_squares)
  cv2.imwrite(f'{file_stem}_rotated.jpg', img)

  # The orientation of the image may have changed which will affect the
  # coordinates of the squares. Therefore, locate the squares, recompute the
  # regions, and sort again
  boxes = _find_boxes(img)
  regions = _compute_luminance_regions(img, boxes)
  sorted_regions = _sort_by_columns(regions)

  # Reorder this so the regions are increasing in luminance according to the
  # Hilbert curve arrangement pattern of the grid
  # See scene_low_light_reference.png which indicates the order of each
  # box
  hilbert_ordered = [
      sorted_regions[17],
      sorted_regions[13],
      sorted_regions[12],
      sorted_regions[16],
      sorted_regions[15],
      sorted_regions[14],
      sorted_regions[10],
      sorted_regions[11],
      sorted_regions[7],
      sorted_regions[6],
      sorted_regions[2],
      sorted_regions[3],
      sorted_regions[4],
      sorted_regions[8],
      sorted_regions[9],
      sorted_regions[5],
  ]

  test_name = os.path.basename(file_stem)

  _plot_results(hilbert_ordered, file_stem)
  _plot_successive_difference(hilbert_ordered, file_stem)
  _plot_noise(hilbert_ordered, file_stem, img, test_name)

  _draw_luminance(img, regions)
  cv2.imwrite(f'{file_stem}_result.jpg', img)

  avg = _compute_avg(hilbert_ordered)
  delta_avg = _compute_avg_delta_of_successive_boxes(hilbert_ordered)

  # the following print statements are necessary for telemetry
  # do not convert to logging.debug
  print(f'{test_name}_avg_luma: {avg:.2f}')
  print(f'{test_name}_delta_avg_luma: {delta_avg:.2f}')
  chart_luma_values = [v[1] for v in hilbert_ordered]
  print(f'{test_name}_chart_luma: {chart_luma_values}')

  logging.debug('average luminance of the 6 boxes: %.2f', avg)
  logging.debug('average difference in luminance of 5 successive boxes: %.2f',
                delta_avg)
  if avg < float(avg_luminance_threshold):
    raise AssertionError('Average luminance of the first 6 boxes did not '
                         'meet minimum requirements for low light scene '
                         'criteria. '
                         f'Actual: {avg:.2f}, '
                         f'Expected: {avg_luminance_threshold}')
  if delta_avg < float(avg_delta_luminance_threshold):
    raise AssertionError('The average difference in luminance of the first 5 '
                         'successive boxes did not meet minimum requirements '
                         'for low light scene criteria. '
                         f'Actual: {delta_avg:.2f}, '
                         f'Expected: {avg_delta_luminance_threshold}')
