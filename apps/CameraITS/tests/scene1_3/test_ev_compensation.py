# Copyright 2014 The Android Open Source Project
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
"""Verifies EV compensation is applied."""


import logging
import math
import os.path

from matplotlib import pyplot as plt
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_LINEAR_TONEMAP_CURVE = [0.0, 0.0, 1.0, 1.0]
_LOCKED = 3
_LUMA_DELTA_ATOL = 0.05
_LUMA_DELTA_ATOL_SAT = 0.10
_LUMA_DELTA_ATOL_SAT_UW = 0.15  # higher tol to compensate for less chart area
_LUMA_LOCKED_RTOL_EV_SM = 0.05
_LUMA_LOCKED_RTOL_EV_LG = 0.10
_LUMA_SAT_THRESH = 0.75  # luma value at which ATOL changes from MID to SAT
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_UNSATURATED_EVS = 3
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESH_CONVERGE_FOR_EV = 8  # AE must converge within this num
_UW_FOV_THRESHOLD = 90  # degrees
_VGA_W, _VGA_H = 640, 480
_YUV_FULL_SCALE = 255
_YUV_SAT_MIN = 250


def _assert_correct_advanced_ev_compensation(
    imgs, ev_steps, lumas, expected_lumas, luma_delta_atols, log_path):
  """Assert correct advanced EV compensation behavior.

  Args:
    imgs: list of image arrays from captures.
    ev_steps: list of EV compensation steps.
    lumas: measured luma values over EV steps.
    expected_lumas: expected luma values over EV steps.
    luma_delta_atols: ATOLs for luma change for each EV step.
    log_path: pointer to location to save files.
  """
  failed_test = False
  e_msg = []
  for i, luma in enumerate(lumas):
    luma_delta_atol = luma_delta_atols[i]
    logging.debug('EV step: %3d, luma: %.3f, model: %.3f, ATOL: %.2f',
                  ev_steps[i], luma, expected_lumas[i], luma_delta_atol)
    if not math.isclose(luma, expected_lumas[i], abs_tol=luma_delta_atol):
      failed_test = True
      e_msg.append(f'measured: {lumas[i]}, model: {expected_lumas[i]}, '
                   f'ATOL: {luma_delta_atol}. ')
  if failed_test:
    test_name_w_path = os.path.join(log_path, f'{_NAME}_advanced')
    for i, img in enumerate(imgs):
      image_processing_utils.write_image(
          img, f'{test_name_w_path}_{ev_steps[i]}.jpg')
    raise AssertionError(
        f'Measured/modeled luma deltas too large! {e_msg}')


def _extract_capture_luma(cap, ev):
  """Extract and log metadata while calculating luma value.

  Args:
    cap: capture object.
    ev: integer EV value.

  Returns:
    luma: the average luma of the center patch of the capture.
  """
  ev_meta = cap['metadata']['android.control.aeExposureCompensation']
  exp = cap['metadata']['android.sensor.exposureTime']
  iso = cap['metadata']['android.sensor.sensitivity']
  logging.debug('cap EV: %d, exp: %dns, ISO: %d', ev_meta, exp, iso)
  if ev != ev_meta:
    raise AssertionError(
        f'EV compensation cap != req! cap: {ev_meta}, req: {ev}')
  luma = image_processing_utils.extract_luma_from_patch(
      cap, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  return luma


def _create_basic_plot(evs, lumas, log_path):
  """Create plot for basic EV compensation.

  Args:
    evs: list of EV compensation steps.
    lumas: list of measured luma values.
    log_path: string pointer to results area.
  """
  test_name = f'{_NAME}_basic'
  test_name_w_path = os.path.join(log_path, test_name)
  plt.figure(test_name)
  plt.plot(evs, lumas, '-ro')
  plt.title(test_name)
  plt.xlabel('EV Compensation')
  plt.ylabel('Mean Luma (Normalized)')
  plt.savefig(f'{test_name_w_path}_plot.png')


def _create_advanced_plot(ev_steps, lumas, expected_lumas, log_path):
  """Create plot for advanced EV compensation.

  Args:
    ev_steps: list of EV compensation steps.
    lumas: list of measured luma values.
    expected_lumas: list of expected luma values.
    log_path: string pointer to results area.
  """

  test_name = f'{_NAME}_advanced'
  test_name_w_path = os.path.join(log_path, test_name)
  plt.figure(test_name)
  plt.plot(ev_steps, lumas, '-ro', label='measured', alpha=0.7)
  plt.plot(ev_steps, expected_lumas, '-bo', label='expected', alpha=0.7)
  plt.title(test_name)
  plt.xlabel('EV Compensation')
  plt.ylabel('Mean Luma (Normalized)')
  plt.legend(loc='lower right', numpoints=1, fancybox=True)
  plt.savefig(f'{test_name_w_path}_plot.png')


def _create_basic_request_with_ev(ev):
  """Create basic request with EV value.

  Args:
    ev: EV value to set.

  Returns:
    A request object with the given EV value.
  """
  req = capture_request_utils.auto_capture_request()
  req['android.control.aeExposureCompensation'] = ev
  req['android.control.aeLock'] = True
  req['android.control.awbLock'] = True
  return req


def _create_advanced_request_with_ev(ev):
  """Create advanced request with the ev compensation step.

  Args:
    ev: EV value to set.

  Returns:
    A request object with the given EV value.
  """
  req = capture_request_utils.auto_capture_request()
  req['android.control.aeExposureCompensation'] = ev
  req['android.control.aeLock'] = True
  req['android.control.awbLock'] = True
  # Use linear tonemap to avoid brightness being impacted by tone curves.
  req['android.tonemap.mode'] = 0
  req['android.tonemap.curve'] = {'red': _LINEAR_TONEMAP_CURVE,
                                  'green': _LINEAR_TONEMAP_CURVE,
                                  'blue': _LINEAR_TONEMAP_CURVE}
  return req


def _create_basic_ev_comp_changes(props):
  """Create basic ev compensation steps and shifts from control params.

  Args:
    props: camera properties.

  Returns:
    evs: list of EV compensation steps.
    luma_locked_rtols: list of RTOLs for captures with luma locked.
  """
  ev_per_step = capture_request_utils.rational_to_float(
      props['android.control.aeCompensationStep'])
  steps_per_ev = int(1.0 / ev_per_step)
  evs = range(-2 * steps_per_ev, 2 * steps_per_ev + 1, steps_per_ev)
  luma_locked_rtols = [_LUMA_LOCKED_RTOL_EV_LG,
                       _LUMA_LOCKED_RTOL_EV_SM,
                       _LUMA_LOCKED_RTOL_EV_SM,
                       _LUMA_LOCKED_RTOL_EV_SM,
                       _LUMA_LOCKED_RTOL_EV_LG]
  return evs, luma_locked_rtols


def _create_advanced_ev_comp_changes(props):
  """Create advanced ev compensation steps and shifts from control params.

  Args:
    props: camera properties.

  Returns:
    EV steps list and EV shifts list.
  """
  ev_compensation_range = props['android.control.aeCompensationRange']
  range_min = ev_compensation_range[0]
  range_max = ev_compensation_range[1]
  ev_per_step = capture_request_utils.rational_to_float(
      props['android.control.aeCompensationStep'])
  logging.debug('ev_step_size_in_stops: %.3f', ev_per_step)
  steps_per_ev = int(round(1.0 / ev_per_step))
  ev_steps = range(range_min, range_max + 1, steps_per_ev)
  ev_shifts = [pow(2, step * ev_per_step) for step in ev_steps]
  return ev_steps, ev_shifts


class EvCompensationTest(its_base_test.ItsBaseTest):
  """Tests that EV compensation is applied."""

  def test_ev_compensation(self):
    # Basic test code
    logging.debug('Starting %s_basic', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path

      # Check common basic/advanced SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.ev_compensation(props) and
          camera_properties_utils.ae_lock(props) and
          camera_properties_utils.awb_lock(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Create basic EV compensation changes
      evs, luma_locked_rtols = _create_basic_ev_comp_changes(props)

      # Converge 3A, and lock AE once converged. skip AF trigger as
      # dark/bright scene could make AF convergence fail and this test
      # doesn't care the image sharpness.
      mono_camera = camera_properties_utils.mono_camera(props)
      cam.do_3a(ev_comp=0, lock_ae=True, lock_awb=True, do_af=False,
                mono_camera=mono_camera)

      # Do captures and extract information
      largest_yuv = capture_request_utils.get_largest_format('yuv', props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      if fmt['width'] * fmt['height'] > _VGA_W * _VGA_H:
        fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
      logging.debug('YUV size: %d x %d', fmt['width'], fmt['height'])
      lumas = []
      for j, ev in enumerate(evs):
        luma_locked_rtol = luma_locked_rtols[j]
        # Capture a single shot with the same EV comp and locked AE.
        req = _create_basic_request_with_ev(ev)
        caps = cam.do_capture([req]*_THRESH_CONVERGE_FOR_EV, fmt)
        luma_locked = []
        for i, cap in enumerate(caps):
          if cap['metadata']['android.control.aeState'] == _LOCKED:
            luma = _extract_capture_luma(cap, ev)
            luma_locked.append(luma)
            if i == _THRESH_CONVERGE_FOR_EV-1:
              lumas.append(luma)
              if not math.isclose(min(luma_locked), max(luma_locked),
                                  rel_tol=luma_locked_rtol):
                raise AssertionError(f'EV {ev} burst lumas: {luma_locked}, '
                                     f'RTOL: {luma_locked_rtol}')
        logging.debug('lumas per frame ev %d: %s', ev, luma_locked)
      logging.debug('mean lumas in AE locked captures: %s', lumas)
      if caps[_THRESH_CONVERGE_FOR_EV-1]['metadata'][
          'android.control.aeState'] != _LOCKED:
        raise AssertionError(f'No AE lock by {_THRESH_CONVERGE_FOR_EV} frame.')

      # Create basic plot
      _create_basic_plot(evs, lumas, log_path)

      # Trim extra saturated images
      while (lumas[-2] >= _YUV_SAT_MIN/_YUV_FULL_SCALE and
             lumas[-1] >= _YUV_SAT_MIN/_YUV_FULL_SCALE and
             len(lumas) > 2):
        lumas.pop(-1)
        logging.debug('Removed saturated image.')

      # Only allow positive EVs to give saturated image
      if len(lumas) < _NUM_UNSATURATED_EVS:
        raise AssertionError(
            f'>{_NUM_UNSATURATED_EVS-1} unsaturated images needed.')
      min_luma_diffs = min(np.diff(lumas))
      logging.debug(
          'Min of luma value difference between adjacent ev comp: %.3f',
          min_luma_diffs
      )

      # Assert unsaturated lumas increasing with increasing ev comp.
      if min_luma_diffs <= 0:
        raise AssertionError('Lumas not increasing with ev comp! '
                             f'EVs: {list(evs)}, lumas: {lumas}')

      # Advanced test code
      logging.debug('Starting %s_advanced', _NAME)

      # check advanced SKIP conditions
      if not (camera_properties_utils.manual_sensor(props) and
              camera_properties_utils.manual_post_proc(props) and
              camera_properties_utils.per_frame_control(props)
             ):
        return

      # Create advanced EV compensation changes
      ev_steps, ev_shifts = _create_advanced_ev_comp_changes(props)

      # Converge 3A, and lock AE once converged. skip AF trigger as
      # dark/bright scene could make AF convergence fail and this test
      # doesn't care the image sharpness.
      cam.do_3a(ev_comp=0, lock_ae=True, lock_awb=True, do_af=False,
                mono_camera=mono_camera)

      # Create requests and capture
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      imgs = []
      lumas = []
      for ev in ev_steps:
        # Capture a single shot with the same EV comp and locked AE.
        req = _create_advanced_request_with_ev(ev)
        caps = cam.do_capture([req]*_THRESH_CONVERGE_FOR_EV, fmt)
        for cap in caps:
          if cap['metadata']['android.control.aeState'] == _LOCKED:
            ev_meta = cap['metadata']['android.control.aeExposureCompensation']
            if ev_meta != ev:
              raise AssertionError(
                  f'EV comp capture != request! cap: {ev_meta}, req: {ev}')
            imgs.append(
                image_processing_utils.convert_capture_to_rgb_image(cap))
            lumas.append(image_processing_utils.extract_luma_from_patch(
                cap, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H))
            break
        if caps[_THRESH_CONVERGE_FOR_EV-1]['metadata'][
            'android.control.aeState'] != _LOCKED:
          raise AssertionError('AE does not reach locked state in '
                               f'{_THRESH_CONVERGE_FOR_EV} frames.')
        logging.debug('lumas in AE locked captures: %s', str(lumas))

      # Create advanced plot
      i_mid = len(ev_steps) // 2
      luma_normal = lumas[i_mid] / ev_shifts[i_mid]
      expected_lumas = [min(1.0, luma_normal*shift) for shift in ev_shifts]
      _create_advanced_plot(ev_steps, lumas, expected_lumas, log_path)

      # Assert correct behavior for advanced EV compensation
      luma_delta_sat_atol = _LUMA_DELTA_ATOL_SAT_UW if float(
          cam.calc_camera_fov(props)) > _UW_FOV_THRESHOLD else _LUMA_DELTA_ATOL
      luma_delta_atols = [_LUMA_DELTA_ATOL if l < _LUMA_SAT_THRESH
                          else luma_delta_sat_atol for l in expected_lumas]
      _assert_correct_advanced_ev_compensation(
          imgs, ev_steps, lumas, expected_lumas, luma_delta_atols, log_path
      )


if __name__ == '__main__':
  test_runner.main()
