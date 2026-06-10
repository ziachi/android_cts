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
"""Utility functions to detect a set of patterns in the chart image."""

import enum
import logging
import os

import cv2
import image_processing_utils
import numpy as np

_PATTERN_FILE_PATHS = (
    'qr_code.png',
)
_FLANN_INDEX_KDTREE = 1
_FLANN_COUNT_KDTREE = 10
# We only need 4 feature matches to compute a homography. However, use a larger
# number to avoid calculating unstable homographies.
_MIN_FEATURE_MATCHES_BEFORE_RANSAC = 35
_MIN_FEATURE_MATCHES_AFTER_RANSAC = 25
# This multiplier causes an image with max dimension of 1280 pixels to use a
# reprojection threshold of 5 pixels.
_REPROJECTION_THRESHOLD_MULTIPLIER = 5.0 / 1280
_MIN_REPROJECTION_THRESHOLD = 5.0
# These thresholds are customized for SIFT descriptors.
_DISTANCE_THRESHOLD = 300
_RATIO_THRESHOLD = 0.85
_TEST_IMG_DIR = os.path.join(os.environ['CAMERA_ITS_TOP'], 'test_images')
_TEST_CHART_FILE_PATH = os.path.join(_TEST_IMG_DIR, 'ip-test-chart-gen2.jpg')
_PRECALCULATED_QR_CODE_TO_TEST_CHART_HOMOGRAPHY = np.array([
    [6.779971102933098, 0.013987338118243198, 3607.3750756216737],
    [-0.020449324384403368, 6.811438682306467, 4868.505503109079],
    [-0.0000028107426441194394, 0.0000028798060528285107, 1.0],
])
_MAX_INTENSITY = 255


class _TestChartConstants:
  """Constants related to the test chart original image file at `_TEST_CHART_FILE_PATH`.
  """
  FULL_CHART_WIDTH = 9600
  FULL_CHART_HEIGHT = 12000

  class CenterQrCode:
    SIDE_LENGTH = 2308
    TOP_LEFT_CORNER = (3645, 4824)

  class DynamicRangePatches:
    PATCH_SIDE_LENGTH = 600  # length of each side of a single patch
    TOP_LEFT_CORNER = (3292, 3878)
    # Each group refers to a row or column of adjacent patches in test chart.
    PATCHES_PER_GROUP = 5


@enum.unique
class TestChartFeature(enum.Enum):
  FULL_CHART = 'full_chart'
  CENTER_QR_CODE = 'center_qr_code'
  COLOR_CHECKER_CELLS = 'color_checker_cells'
  DYNAMIC_RANGE_PATCHES = 'dynamic_range_patches'
  DEAD_LEAF_PATCH = 'dead_leaf_patch'


class _TestChartFeatureUnit(object):
  """Base class for information regarding a single unit of some test chart feature (e.g. a single patch of the dynamic range feature).

  Attributes:
    feature_type: The type of the feature.
    image: Optional BGRA color space image in numpy matrix format.
  """

  def __init__(self, feature_type: TestChartFeature, image: np.ndarray = None):
    self.feature_type = feature_type
    self.image = image


class _PolygonalFeatureUnit(_TestChartFeatureUnit):
  """Base class for information regarding a `_TestChartFeatureUnit` which can be enclosed in a polygon.

  For ex: A single square cell of the color checker cells.

  Attributes:
    feature_type: The type of the feature.
    corner_points: Co-ordinates of a polygon that encloses the feature.
    image: Optional BGRA color space image in numpy matrix format.
  """

  def __init__(
      self,
      feature_type: TestChartFeature,
      corner_points: list[list[int, int]],
      image: np.ndarray = None,
  ):
    super().__init__(feature_type=feature_type, image=image)
    self.corner_points = corner_points

  def __repr__(self):
    return 'corner_points: %s' % (str(self.corner_points),)


class _MonoColorPolygonalFeatureUnit(_PolygonalFeatureUnit):
  """A `_PolygonalFeatureUnit` that consists of a single color on test chart.

  Attributes:
    feature_type: The type of the feature.
    corner_points: Co-ordinates of a polygon that encloses the feature.
    test_chart_color_bgr: BGR space color value of the feature unit in
      reference test chart image.
    image: Optional BGRA color space image in numpy matrix format.
  """

  def __init__(
      self,
      feature_type: TestChartFeature,
      corner_points: list[list[int, int]],
      test_chart_color_bgr: np.ndarray,
      image: np.ndarray = None,
  ):
    super().__init__(
        feature_type=feature_type, corner_points=corner_points, image=image
    )
    self.test_chart_color_bgr = test_chart_color_bgr


class CenterQrCode(_PolygonalFeatureUnit):
  """A `_PolygonalFeatureUnit` containing information regarding center QR code test chart feature.

  Attributes:
    corner_points: Co-ordinates of the four corner points.
    image: Optional BGRA color space image in numpy matrix format.
  """

  def __init__(
      self,
      corner_points: list[
          list[int, int], list[int, int], list[int, int], list[int, int]
      ],
      image: np.ndarray = None,
  ):
    super().__init__(
        feature_type=TestChartFeature.CENTER_QR_CODE,
        corner_points=corner_points,
        image=image
    )

  def __repr__(self):
    return 'feature_type: %s, corner_points: %s' % (
        str(self.feature_type),
        str(self.corner_points),
    )


class DynamicRangePatch(_MonoColorPolygonalFeatureUnit):
  """A `_MonoColorPolygonalFeatureUnit` containing information regarding a single cell of the dynamic range patch.

  Attributes:
    corner_points: Co-ordinates of the four corner points in a patch.
    test_chart_color_bgr: BGR space color value of the equivalent patch in
      reference test chart image.
    image: Optional BGRA color space image in numpy matrix format.
  """

  def __init__(
      self,
      corner_points: list[
          list[int, int], list[int, int], list[int, int], list[int, int]
      ],
      test_chart_color_bgr: np.ndarray,
      image: np.ndarray = None,
  ):
    super().__init__(
        feature_type=TestChartFeature.DYNAMIC_RANGE_PATCHES,
        corner_points=corner_points,
        test_chart_color_bgr=test_chart_color_bgr,
        image=image,
    )

  def __repr__(self):
    return 'feature_type: %s, corner_points: %s, test_chart_color_bgr: %s' % (
        str(self.feature_type),
        str(self.corner_points),
        str(self.test_chart_color_bgr),
    )


def _process_input_image(
    image: str | np.ndarray,
) -> (np.ndarray, str):
  """Processes the input image argument and returns numpy array image and original file name (if possible) after verification.
  """
  image_path = None
  if isinstance(image, str):
    if not os.path.exists(image):
      logging.debug('Missing file %s', image)
      return None
    image_path = image
    image = cv2.imread(image, cv2.IMREAD_COLOR)
  _assert_image_type(image, cv2.IMREAD_COLOR)

  return image, image_path


def _assert_image_type(image, colorspace):
  if not isinstance(image, np.ndarray):
    raise AssertionError('Image must be a NumPy array.')

  if colorspace == cv2.IMREAD_COLOR:
    if image.ndim != 3:
      raise AssertionError('Color image must have 3 dimensions.')
    if image.shape[2] != 3:
      raise AssertionError('Color image must have 3 channels.')
  elif colorspace == cv2.IMREAD_GRAYSCALE:
    if image.ndim != 2:
      raise AssertionError('Grayscale image must have 2 dimensions.')


def get_transformed_point(
    image: str | np.ndarray,
    point: tuple[int, int],
    transform_matrix: np.ndarray = None,
) -> tuple[int, int]:
  """Gets the transformed position of a test chart point in the provided image.

  This method transforms test_chart_gen2.jpg to the provided image and finds the
  transformed position of the provided point.

  Args:
    image: The image which test chart is transformed to. Must be a file path
      string or 3 channel BGR color image data in numpy matrix format.
    point: Pixel position of a point in test chart
    transform_matrix: Optional transformation matrix which is used instead of
      the image to transform the provided point. Otherwise,
      `find_test_chart_transformation(image)` is used.

  Returns:
    Pixel position of same point in query image as a tuple of two integers,
    or None if a transformation matrix could not be found.
  """
  image, _ = _process_input_image(image)

  if transform_matrix is None:
    transform_matrix = find_test_chart_transformation(image)
    if transform_matrix is None:
      logging.debug('Center QR code pattern not detected in the image')
      return None

  transformed_homogenous_point = np.dot(
      transform_matrix, [point[0], point[1], 1]
  )
  transformed_cartesian_points = (
      transformed_homogenous_point[:2] / transformed_homogenous_point[2]
  )

  return (
      int(np.round(transformed_cartesian_points[0])),
      int(np.round(transformed_cartesian_points[1])),
  )


def _get_dynamic_range_patch_top_right_corner(
    top_left_corner: tuple[int, int],
) -> tuple[int, int]:
  return (
      top_left_corner[0]
      + _TestChartConstants.DynamicRangePatches.PATCH_SIDE_LENGTH,
      top_left_corner[1],
  )


def _get_dynamic_range_patch_bottom_left_corner(
    top_left_corner: tuple[int, int],
) -> tuple[int, int]:
  return (
      top_left_corner[0],
      top_left_corner[1]
      + _TestChartConstants.DynamicRangePatches.PATCH_SIDE_LENGTH,
  )


def _get_dynamic_range_patch_bottom_right_corner(
    top_left_corner: tuple[int, int]
) -> tuple[int, int]:
  return (
      top_left_corner[0]
      + _TestChartConstants.DynamicRangePatches.PATCH_SIDE_LENGTH,
      top_left_corner[1]
      + _TestChartConstants.DynamicRangePatches.PATCH_SIDE_LENGTH,
  )


def _create_dynamic_range_patch(
    top_left_corner: tuple[int, int], test_chart_image: np.ndarray
) -> DynamicRangePatch:
  bottom_right_corner = _get_dynamic_range_patch_bottom_right_corner(
      top_left_corner
  )
  return DynamicRangePatch(
      corner_points=[
          top_left_corner,
          _get_dynamic_range_patch_top_right_corner(top_left_corner),
          bottom_right_corner,
          _get_dynamic_range_patch_bottom_left_corner(top_left_corner),
      ],
      test_chart_color_bgr=test_chart_image[
          int((top_left_corner[1] + bottom_right_corner[1]) / 2),
          int((top_left_corner[0] + bottom_right_corner[0]) / 2),
      ],
  )


def _get_test_chart_dynamic_range_patches() -> list[DynamicRangePatch]:
  """Gets the dynamic range patch positions in test chart.

  The patches are listed in clockwise order starting from the top-left
  one in the test chart.

  Returns:
    A list of the four corners (in serial) of each patch, or None in case of any
      error.
  """
  patches = []
  side_length = _TestChartConstants.DynamicRangePatches.PATCH_SIDE_LENGTH

  # Error if the provided file does not exist.
  test_chart_file_path = _TEST_CHART_FILE_PATH
  if not os.path.exists(test_chart_file_path):
    logging.debug('Missing file %s', test_chart_file_path)
    return None

  test_chart_image = cv2.imread(test_chart_file_path)

  # top row of patches
  top_left_corner = _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER
  for _ in range(_TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP):
    patches.append(
        _create_dynamic_range_patch(top_left_corner, test_chart_image)
    )
    top_left_corner = (top_left_corner[0] + side_length, top_left_corner[1])

  # right column of patches
  top_left_corner = (
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[0]
      + _TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP * side_length,
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[1] + side_length,
  )
  for _ in range(_TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP):
    patches.append(
        _create_dynamic_range_patch(top_left_corner, test_chart_image)
    )
    top_left_corner = (top_left_corner[0], top_left_corner[1] + side_length)

  # bottom row of patches
  top_left_corner = (
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[0],
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[1]
      + (_TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP + 1)
      * side_length,
  )
  rev_patches = []
  for _ in range(_TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP):
    rev_patches.insert(
        0, _create_dynamic_range_patch(top_left_corner, test_chart_image)
    )
    top_left_corner = (top_left_corner[0] + side_length, top_left_corner[1])
  patches.extend(rev_patches)

  # left column of patches
  top_left_corner = (
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[0] - side_length,
      _TestChartConstants.DynamicRangePatches.TOP_LEFT_CORNER[1] + side_length,
  )
  rev_patches = []
  for _ in range(_TestChartConstants.DynamicRangePatches.PATCHES_PER_GROUP):
    rev_patches.insert(
        0,
        _create_dynamic_range_patch(top_left_corner, test_chart_image)
    )
    top_left_corner = (top_left_corner[0], top_left_corner[1] + side_length)
  patches.extend(rev_patches)

  return patches


def _filter_by_ratio_and_distance_test(keypoints_1, keypoints_2, matches):
  """Filters feature matches with a ratio test and a distance test.

  See Lowe's SIFT paper for an explanation of the ratio test. The implementation
  here is adapted from third_party/OpenCVX/v3_4_0/samples/python/find_obj.py

  Args:
    keypoints_1: first set of keypoints
    keypoints_2: second set of keypoints
    matches: matches between the two sets of keypoints

  Returns:
    filtered first set of keypoints
    filtered second key of keypoints
    filtered matching keypoint pairs
  """
  mkeypoints_1, mkeypoints_2 = [], []
  for match in matches:
    if len(match) != 2:
      continue
    if match[0].distance >= _DISTANCE_THRESHOLD:
      continue
    if match[0].distance >= match[1].distance * _RATIO_THRESHOLD:
      continue
    match = match[0]
    mkeypoints_1.append(keypoints_1[match.queryIdx])
    mkeypoints_2.append(keypoints_2[match.trainIdx])

  filtered_keypoints1 = np.float32([kp.pt for kp in mkeypoints_1])
  filtered_keypoints2 = np.float32([kp.pt for kp in mkeypoints_2])
  filtered_pairs = list(zip(mkeypoints_1, mkeypoints_2))
  return filtered_keypoints1, filtered_keypoints2, list(filtered_pairs)


def detect_pattern(query_image: str | np.ndarray):
  """Detects patterns in a query image.

  Searches for a fixed set of patterns in the query image. If one of the
  patterns is found in the query image, a homography between the query image
  and the matching pattern image is returned.

  Args:
    query_image: Query image data, either in the format of a string representing
      the path to the query image file or 3 channel BGR color image data in
      numpy matrix format (same return format as OpenCV `imread(filename)`
      invocation with default parameters).

  Returns:
    (pattern image path, homography as 2-d numpy array, horizontal flip status)
    if the query image matched a pattern image, or None otherwise
  """
  # Initialize SIFT detector and FLANN matcher.
  detector = cv2.SIFT_create(enable_precise_upscale=True)
  flann_params = dict(algorithm=_FLANN_INDEX_KDTREE, trees=_FLANN_COUNT_KDTREE)
  matcher = cv2.FlannBasedMatcher(flann_params, {})

  # Load query image and extract features.
  if isinstance(query_image, str):
    logging.debug('Query image: %s', query_image)
    query_image = cv2.imread(query_image)
  _assert_image_type(query_image, cv2.IMREAD_COLOR)

  query_points, query_descriptors = detector.detectAndCompute(query_image, None)
  logging.debug('%d features in query image', len(query_points))
  if len(query_points) < _MIN_FEATURE_MATCHES_BEFORE_RANSAC:
    logging.debug('Not enough query features.')
    return None

  for pattern_file_path in _PATTERN_FILE_PATHS:
    # Load pattern image.
    path = os.path.join(image_processing_utils.TEST_IMG_DIR, pattern_file_path)
    pattern_image = cv2.imread(path)
    logging.debug('Pattern image: %s', pattern_file_path)

    # Sometimes, the pattern is flipped horizontally in the query image. SIFT
    # features are not flip-invariant. So, try the original pattern image and
    # a horizontally flipped version, and use the version which achieves a
    # larger number of feature matches against the query image.
    max_filtered_match_count = 0
    for flip_horizontal in [False, True]:
      logging.debug('Flip horizontal: %d', flip_horizontal)
      if flip_horizontal:
        transformed_pattern_image = cv2.flip(pattern_image, 1)
      else:
        transformed_pattern_image = pattern_image
      pattern_points, pattern_descriptors = detector.detectAndCompute(
          transformed_pattern_image, None)
      logging.debug('%d features in pattern image', len(pattern_points))

      # Match features.
      raw_matches = matcher.knnMatch(
          pattern_descriptors, trainDescriptors=query_descriptors, k=2)
      filtered_pattern_points, filtered_query_points, _ = (
          _filter_by_ratio_and_distance_test(pattern_points, query_points,
                                             raw_matches))
      filtered_match_count = len(filtered_query_points)
      logging.debug('%d features matches after ratio and distance tests',
                    filtered_match_count)
      if filtered_match_count >= max_filtered_match_count:
        max_filtered_match_count = filtered_match_count
        max_filtered_pattern_points = filtered_pattern_points
        max_filtered_query_points = filtered_query_points
        max_filtered_flip_status = flip_horizontal

    if max_filtered_match_count < _MIN_FEATURE_MATCHES_BEFORE_RANSAC:
      logging.debug('Not enough feature matches before RANSAC.')
      continue

    # Adjust reprojection threshold based on the size of the query image.
    query_height, query_width = query_image.shape[:2]
    query_max_dimension = max(query_height, query_width)
    reprojection_threshold = (
        query_max_dimension * _REPROJECTION_THRESHOLD_MULTIPLIER)
    reprojection_threshold = max(_MIN_REPROJECTION_THRESHOLD,
                                 reprojection_threshold)

    # Compute homography.
    logging.debug('RANSAC reprojection threshold: %f', reprojection_threshold)
    homography, status = cv2.findHomography(max_filtered_pattern_points,
                                            max_filtered_query_points,
                                            cv2.RANSAC, reprojection_threshold)
    ransac_match_count = np.sum(np.ndarray.flatten(status))
    logging.debug('%d feature matches after RANSAC', ransac_match_count)

    if ransac_match_count < _MIN_FEATURE_MATCHES_AFTER_RANSAC:
      logging.debug('Not enough feature matches after RANSAC.')
      continue

    logging.debug('Homography:')
    logging.debug('%.3f, %.3f, %.3f', homography[0][0], homography[0][1],
                  homography[0][2])
    logging.debug('%.3f, %.3f, %.3f', homography[1][0], homography[1][1],
                  homography[1][2])
    logging.debug('%.3f, %.3f, %.3f', homography[2][0], homography[2][1],
                  homography[2][2])
    return (pattern_file_path, homography, max_filtered_flip_status)

  logging.debug('Query image did not match any patterns.')
  return None


def find_center_qr_code_homography(
    dst_image: str | np.ndarray,
    can_return_precalculated_value: bool = True,
) -> (np.ndarray, bool):
  """Finds the homography matrix transforming the center QR code test chart feature to the provided image.

  Args:
    dst_image:  The image which the center QR code is transformed to. Must be a
      file path string or 3 channel BGR color image data in numpy matrix format.
    can_return_precalculated_value: Whether precalculated value can be returned
      if possible, or need to recalculate again. This is used only when
      `dst_image` is a `str` type (i.e. the path of image is provided).

  Returns:
    A tuple of homography transformation matrix as 2-d numpy array if the query
    image matched a QR code pattern and whether horizontal flip was required for
    the matching. `None` is provided in case of no matching.
  """
  if isinstance(dst_image, str):
    if not os.path.exists(dst_image):
      logging.debug('Missing file %s', dst_image)
      return None

    if (
        can_return_precalculated_value
        and dst_image == _TEST_CHART_FILE_PATH
    ):
      # Use previously calculated homography matrix to save time, must be
      # updated each time the qr_code.png or test_chart_gen2.jpg file is changed
      return (_PRECALCULATED_QR_CODE_TO_TEST_CHART_HOMOGRAPHY, False)

    dst_image = cv2.imread(dst_image, cv2.IMREAD_COLOR)
  _assert_image_type(dst_image, cv2.IMREAD_COLOR)

  _, homography, flip_status = detect_pattern(dst_image)
  return (homography, flip_status)


def _get_test_chart_horizontal_mirror_transformation() -> np.ndarray:
  """Gets the horizontal mirror transformation matrix for test chart image.

  Returns:
    The transformation matrix.
  """
  chart_width = _TestChartConstants.FULL_CHART_WIDTH
  chart_height = _TestChartConstants.FULL_CHART_HEIGHT

  src = np.array(
      [
          [0, 0],
          [chart_width - 1, 0],
          [chart_width - 1, chart_height - 1],
          [0, chart_height - 1],
      ],
      dtype='float32',
  )

  dst = np.array(
      [
          [chart_width - 1, 0],
          [0, 0],
          [0, chart_height - 1],
          [chart_width - 1, chart_height - 1],
      ],
      dtype='float32',
  )

  return cv2.getPerspectiveTransform(src, dst)


def find_test_chart_transformation(
    dst_image: str | np.ndarray,
) -> np.ndarray:
  """Finds the `TestChartTransformation` that maps test_chart_gen2.jpg positions to the provided destination image.

  The center QR code is used as reference to find the mapping between test chart
  and query image. If the QR code can not be found either image, this method
  will return None.

  Args:
    dst_image: The image which test chart is transformed to. Must be a file path
    string or 3 channel BGR color image data in numpy matrix format.

  Returns:
    Homography transformation matrix as 2-d numpy array if the query image
    matched a QR code pattern, or None otherwise
  """
  dst_image, _ = _process_input_image(dst_image)

  # Find homography matrix mapping center QR code to dst_image
  dst_image_transform_info = find_center_qr_code_homography(dst_image)

  # Error if no QR code pattern is detected.
  if dst_image_transform_info is None:
    logging.debug('No pattern detected for %s', dst_image)
    return None

  qr_to_dst_homography, is_horizontally_flipped = dst_image_transform_info

  # Find homography matrix mapping center QR code to test chart
  qr_to_test_chart_homography, _ = find_center_qr_code_homography(
      _TEST_CHART_FILE_PATH,
  )

  # Error if no QR code pattern is detected.
  if qr_to_test_chart_homography is None:
    logging.debug('No pattern detected for %s', _TEST_CHART_FILE_PATH)
    return None

  # Combine the homography matrices to get a final matrix transforming whole
  # test chart image to dst_image
  test_chart_to_qr_homography = np.linalg.inv(qr_to_test_chart_homography)

  if is_horizontally_flipped:
    # Since dst_image is horizontally flipped, test chart also needs to be
    # flipped first.
    test_chart_to_qr_homography = (
        test_chart_to_qr_homography
        @ _get_test_chart_horizontal_mirror_transformation()
    )

  test_chart_to_dst_transform_matrix = (
      qr_to_dst_homography @ test_chart_to_qr_homography
  )

  return test_chart_to_dst_transform_matrix


def _get_test_chart_center_qr_code() -> CenterQrCode:
  return CenterQrCode(
      corner_points=[
          _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER,
          (
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[0]
              + _TestChartConstants.CenterQrCode.SIDE_LENGTH,
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[1],
          ),
          (
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[0]
              + _TestChartConstants.CenterQrCode.SIDE_LENGTH,
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[1]
              + _TestChartConstants.CenterQrCode.SIDE_LENGTH,
          ),
          (
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[0],
              _TestChartConstants.CenterQrCode.TOP_LEFT_CORNER[1]
              + _TestChartConstants.CenterQrCode.SIDE_LENGTH,
          ),
      ]
  )


def _extract_polygon_from_image(
    image: str | np.ndarray,
    corner_points: list[list[int, int]]
):
  """Extract out the polygon area from an image.

  Args:
    image: Must be a file path string or 3 channel BGR color image data in numpy
      matrix format.
    corner_points: Co-ordinates of a polygon corner points. The polygon
      requirements should be the same as that of `cv2.fillPoly`.

  Returns:
    BGRA color space image of the same size as input image where the polygon
      area in input image is kept intact while any pixel not within the polygon
      will be fully transparent.
  """
  image, _ = _process_input_image(image)

  image_mask = np.zeros(image.shape[0:2]).astype(np.uint8)

  cv2.fillPoly(
      img=image_mask,
      pts=[np.array(corner_points).astype(np.int32)],
      color=(_MAX_INTENSITY),
  )

  image_mask = (image_mask != 0).astype(bool)

  image = cv2.cvtColor(image, cv2.COLOR_BGR2BGRA)
  image[:, :, 3] = np.where(
      image_mask, image[:, :, 3], 0
  )

  return image


def get_test_chart_features_aligned_to_image(
    image: str | np.ndarray,
    transform_matrix: np.ndarray = None,
    test_chart_features: list[TestChartFeature] = None,
) -> np.ndarray:
  """Gets the test chart features aligned to the provided image.

  Args:
    image: Must be a file path string or 3 channel BGR color image data in numpy
      matrix format.
    transform_matrix: Used to transform the points in test chart to query image,
      `find_transform_matrix_mapping_from_test_chart` method will be used to
      calculate it if None provided.
    test_chart_features: List of `TestChartFeature`, will be replaced with
      [TestChartFeature.FULL_CHART] as default if None provided.

  Returns:
    BGRA color space image with only the test chart features in query image, any
      pixel not within feature will be fully transparent. Returns None in case
      of any error.
  """
  logging.debug('Finding feature %s', test_chart_features)
  image, image_path = _process_input_image(image)

  if test_chart_features is None:
    test_chart_features = [TestChartFeature.FULL_CHART]

  if transform_matrix is None:
    transform_matrix = find_test_chart_transformation(image)
    if transform_matrix is None:
      if image_path is not None:
        logging.debug('No pattern detected for %s', image_path)
      else:
        logging.debug('No pattern detected')
      return None

  test_chart_image = cv2.imread(
      _TEST_CHART_FILE_PATH
  )

  test_chart_mask = np.zeros(test_chart_image.shape[0:2]).astype(np.uint8)

  for feature in test_chart_features:
    match feature:
      case TestChartFeature.FULL_CHART:
        test_chart_mask.fill(_MAX_INTENSITY)

      case TestChartFeature.CENTER_QR_CODE:
        cv2.fillPoly(
            img=test_chart_mask,
            pts=[
                np.array(_get_test_chart_center_qr_code().corner_points).astype(
                    np.int32
                )
            ],
            color=(_MAX_INTENSITY),
        )

  test_chart_mask = (test_chart_mask != 0).astype(bool)

  test_chart_image = cv2.cvtColor(test_chart_image, cv2.COLOR_BGR2BGRA)
  test_chart_image[:, :, 3] = np.where(
      test_chart_mask, test_chart_image[:, :, 3], 0
  )

  aligned_test_chart_image = cv2.warpPerspective(
      src=test_chart_image,
      M=transform_matrix,
      dsize=(image.shape[1], image.shape[0]),
      flags=cv2.INTER_CUBIC,
      borderMode=cv2.BORDER_CONSTANT,
      borderValue=(0, 0, 0, 0),
  )

  return aligned_test_chart_image


def get_test_chart_features_from_image(
    image: str | np.ndarray,
    transform_matrix: np.ndarray = None,
    test_chart_features: list[TestChartFeature] = None,
    feature_area_mask: np.ndarray = None,
) -> np.ndarray:
  """Gets test chart features from the provided image.

  Args:
    image: Must be a file path string or 3 channel BGR color image data in numpy
      matrix format.
    transform_matrix: Used to transform the points in test chart to query image,
      `find_transform_matrix_mapping_from_test_chart` method will be used to
      calculate it if None provided.
    test_chart_features: List of chart_features. TestChartFeature.FULL_CHART as
      default if None provided.
    feature_area_mask: Boolean mask of feature area, will be calculated based on
      test_chart_features if None.

  Returns:
    BGRA color space image with only the test chart features in query image, any
      pixel not within feature will be fully transparent. Returns None in case
      of any error.
  """
  image, _ = _process_input_image(image)

  if test_chart_features is None:
    test_chart_features = [TestChartFeature.FULL_CHART]

  if feature_area_mask is None:
    aligned_test_chart_image = get_test_chart_features_aligned_to_image(
        image, transform_matrix, test_chart_features
    )
    feature_area_mask = aligned_test_chart_image[:, :, 3] != 0

  image = cv2.cvtColor(image, cv2.COLOR_BGR2BGRA)
  image[:, :, 3] = np.where(feature_area_mask, image[:, :, 3], 0)

  return image


def find_dynamic_range_patches(
    image: str | np.ndarray,
    transform_matrix: np.ndarray = None,
) -> list[DynamicRangePatch]:
  """Finds the dynamic range patch positions of given image.

  The patches are listed in clockwise order starting from the top-left
  one in the test chart.

  Args:
    image: Must be a file path string or 3 channel BGR color image data in numpy
      matrix format.
    transform_matrix: Used to transform the points in test chart to query image,
      `find_transform_matrix_mapping_from_test_chart` method will be used to
      calculate it if None provided.

  Returns:
    A list of `DynamicRangePatch` where every patch is a list containing the
    four corner positions in query image and the original color in test chart.
  """
  image, image_path = _process_input_image(image)

  if transform_matrix is None:
    transform_matrix = find_test_chart_transformation(image)
    if transform_matrix is None:
      if image_path is not None:
        logging.debug('No pattern detected for %s', image_path)
      else:
        logging.debug('No pattern detected')
      return None

  transformed_dynamic_range_patches = []
  for patch in _get_test_chart_dynamic_range_patches():
    transformed_points = [
        get_transformed_point(image, point, transform_matrix)
        for point in patch.corner_points
    ]
    transformed_patch = DynamicRangePatch(
        corner_points=transformed_points,
        test_chart_color_bgr=patch.test_chart_color_bgr,
        image=_extract_polygon_from_image(image, transformed_points)
    )
    transformed_dynamic_range_patches.append(transformed_patch)

  return transformed_dynamic_range_patches
