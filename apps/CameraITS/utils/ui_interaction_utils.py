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
"""Utility functions for interacting with a device via the UI."""

import dataclasses
import datetime
import logging
import math
import os
import re
import subprocess
import time
import xml.etree.ElementTree as et

import camera_properties_utils
import error_util
import its_device_utils


_DIR_EXISTS_TXT = 'Directory exists'
_PERMISSIONS_LIST = ('CAMERA', 'RECORD_AUDIO', 'ACCESS_FINE_LOCATION',
                     'ACCESS_COARSE_LOCATION')

ACTION_ITS_DO_JCA_CAPTURE = (
    'com.android.cts.verifier.camera.its.ACTION_ITS_DO_JCA_CAPTURE'
)
ACTION_ITS_DO_JCA_VIDEO_CAPTURE = (
    'com.android.cts.verifier.camera.its.ACTION_ITS_DO_JCA_VIDEO_CAPTURE'
)
ACTIVITY_WAIT_TIME_SECONDS = 5
AGREE_BUTTON = 'Agree'
AGREE_AND_CONTINUE_BUTTON = 'Agree and continue'
CANCEL_BUTTON_TXT = 'Cancel'
CAMERA_FILES_PATHS = ('/sdcard/DCIM/Camera',
                      '/storage/emulated/0/Pictures',
                      '/sdcard/DCIM',)
CAPTURE_BUTTON_RESOURCE_ID = 'CaptureButton'
DEFAULT_CAMERA_APP_DUMPSYS_PATH = '/sdcard/default_camera_dumpsys.txt'
DEFAULT_CAMERA_CONTENT_DESC_SEPARATOR = ','
DEFAULT_JCA_UI_DUMPSYS_PATH = '/sdcard/jca-ui-dumpsys.txt'
DONE_BUTTON_TXT = 'Done'
EMULATED_STORAGE_PATH = '/storage/emulated/0/Pictures'

# TODO: b/383392277 - use resource IDs instead of content descriptions.
FLASH_MODE_ON_CONTENT_DESC = 'Flash on'
FLASH_MODE_OFF_CONTENT_DESC = 'Flash off'
FLASH_MODE_AUTO_CONTENT_DESC = 'Auto flash'
FLASH_MODE_LOW_LIGHT_BOOST_CONTENT_DESC = 'Low Light Boost on'
FLASH_MODES = (
    FLASH_MODE_ON_CONTENT_DESC,
    FLASH_MODE_OFF_CONTENT_DESC,
    FLASH_MODE_AUTO_CONTENT_DESC,
    FLASH_MODE_LOW_LIGHT_BOOST_CONTENT_DESC
)
IMG_CAPTURE_CMD = 'am start -a android.media.action.IMAGE_CAPTURE'
ITS_ACTIVITY_TEXT = 'Camera ITS Test'
JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
JPG_FORMAT_STR = '.jpg'
LOCATION_ON_TXT = 'Turn on'
OK_BUTTON_TXT = 'OK'
TAKE_PHOTO_CMD = 'input keyevent KEYCODE_CAMERA'
QUICK_SETTINGS_RESOURCE_ID = 'QuickSettingsDropDown'
QUICK_SET_FLASH_RESOURCE_ID = 'QuickSettingsFlashButton'
QUICK_SET_FLIP_CAMERA_RESOURCE_ID = 'QuickSettingsFlipCameraButton'
QUICK_SET_RATIO_RESOURCE_ID = 'QuickSettingsRatioButton'
RATIO_TO_UI_DESCRIPTION = {
    '1 to 1 aspect ratio': 'QuickSettingsRatio1:1Button',
    '3 to 4 aspect ratio': 'QuickSettingsRatio3:4Button',
    '9 to 16 aspect ratio': 'QuickSettingsRatio9:16Button'
}
REMOVE_CAMERA_FILES_CMD = 'rm -rf'
SETTINGS_BACK_BUTTON_RESOURCE_ID = 'BackButton'
SETTINGS_BUTTON_RESOURCE_ID = 'SettingsButton'
SETTINGS_CLOSE_TEXT = 'Close'
SETTINGS_VIDEO_STABILIZATION_AUTO_TEXT = 'Stabilization Auto'
SETTINGS_MENU_STABILIZATION_HIGH_QUALITY_TEXT = 'Stabilization High Quality'
SETTINGS_VIDEO_STABILIZATION_MODE_TEXT = 'Set Video Stabilization'
SETTINGS_MENU_STABILIZATION_OFF_TEXT = 'Stabilization Off'
THREE_TO_FOUR_ASPECT_RATIO_DESC = '3 to 4 aspect ratio'
UI_DESCRIPTION_BACK_CAMERA = 'Back Camera'
UI_DESCRIPTION_FRONT_CAMERA = 'Front Camera'
UI_OBJECT_WAIT_TIME_SECONDS = datetime.timedelta(seconds=3)
UI_PHYSICAL_CAMERA_RESOURCE_ID = 'PhysicalCameraIdTag'
UI_ZOOM_RATIO_TEXT_RESOURCE_ID = 'ZoomRatioTag'
UI_DEBUG_OVERLAY_BUTTON_RESOURCE_ID = 'DebugOverlayButton'
UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_BUTTON_RESOURCE_ID = (
    'DebugOverlaySetZoomRatioButton'
)
UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_TEXT_FIELD_RESOURCE_ID = (
    'DebugOverlaySetZoomRatioTextField'
)
UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_SET_BUTTON_RESOURCE_ID = (
    'DebugOverlaySetZoomRatioSetButton'
)
UI_IMAGE_CAPTURE_SUCCESS_TEXT = 'Image Capture Success'
VIEWFINDER_NOT_VISIBLE_PREFIX = 'viewfinder_not_visible'
VIEWFINDER_VISIBLE_PREFIX = 'viewfinder_visible'
WAIT_INTERVAL_FIVE_SECONDS = datetime.timedelta(seconds=5)
JCA_WATCH_DUMP_FILE = 'jca_watch_dump.txt'
DEFAULT_CAMERA_WATCH_DUMP_FILE = 'default_camera_watch_dump.txt'
WATCH_WAIT_TIME_SECONDS = 2
_CONTROL_ZOOM_RATIO_KEY = 'android.control.zoomRatio'
_REQ_STR_PATTERN = 'REQ'
JCA_VIDEO_STABILIZATION_MODE_OFF = 0
JCA_VIDEO_STABILIZATION_MODE_HIGH_QUALITY = 1
JCA_VIDEO_STABILIZATION_MODE_ON = 2
JCA_VIDEO_STABILIZATION_MODE_OPTICAL = 3
JCA_STABILIZATION_MODES = {
    0: 'Off',
    1: 'High Quality',
    2: 'On',
    3: 'Optical'
}


@dataclasses.dataclass(frozen=True)
class JcaCapture:
  capture_path: str
  physical_id: int


def _find_ui_object_else_click(object_to_await, object_to_click):
  """Waits for a UI object to be visible. If not, clicks another UI object.

  Args:
    object_to_await: A snippet-uiautomator selector object to be awaited.
    object_to_click: A snippet-uiautomator selector object to be clicked.
  """
  if not object_to_await.wait.exists(UI_OBJECT_WAIT_TIME_SECONDS):
    object_to_click.click()


def verify_ui_object_visible(ui_object, call_on_fail=None):
  """Verifies that a UI object is visible.

  Args:
    ui_object: A snippet-uiautomator selector object.
    call_on_fail: [Optional] Callable; method to call on failure.
  """
  ui_object_visible = ui_object.wait.exists(UI_OBJECT_WAIT_TIME_SECONDS)
  if not ui_object_visible:
    if call_on_fail is not None:
      call_on_fail()
    raise AssertionError('UI object was not visible!')


def open_jca_viewfinder(dut, log_path, request_video_capture=False):
  """Sends an intent to JCA and open its viewfinder.

  Args:
    dut: An Android controller device object.
    log_path: str; Log path to save screenshots.
    request_video_capture: boolean; True if requesting video capture.
  Raises:
    AssertionError: If JCA viewfinder is not visible.
  """
  its_device_utils.start_its_test_activity(dut.serial)
  call_on_fail = lambda: dut.take_screenshot(log_path, prefix='its_not_found')
  verify_ui_object_visible(
      dut.ui(text=ITS_ACTIVITY_TEXT),
      call_on_fail=call_on_fail
  )

  # Send intent to ItsTestActivity, which will start the correct JCA activity.
  if request_video_capture:
    its_device_utils.run(
        f'adb -s {dut.serial} shell am broadcast -a'
        f'{ACTION_ITS_DO_JCA_VIDEO_CAPTURE}'
    )
  else:
    its_device_utils.run(
        f'adb -s {dut.serial} shell am broadcast -a'
        f'{ACTION_ITS_DO_JCA_CAPTURE}'
    )
  jca_capture_button_visible = dut.ui(
      res=CAPTURE_BUTTON_RESOURCE_ID).wait.exists(
          UI_OBJECT_WAIT_TIME_SECONDS)
  if not jca_capture_button_visible:
    dut.take_screenshot(log_path, prefix=VIEWFINDER_NOT_VISIBLE_PREFIX)
    logging.debug('Current UI dump: %s', dut.ui.dump())
    raise AssertionError('JCA was not started successfully!')
  dut.take_screenshot(log_path, prefix=VIEWFINDER_VISIBLE_PREFIX)


def switch_jca_camera(dut, log_path, facing):
  """Interacts with JCA UI to switch camera if necessary.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    facing: str; constant describing the direction the camera lens faces.
  Raises:
    AssertionError: If JCA does not report that camera has been switched.
  """
  if facing == camera_properties_utils.LENS_FACING['BACK']:
    ui_facing_description = UI_DESCRIPTION_BACK_CAMERA
  elif facing == camera_properties_utils.LENS_FACING['FRONT']:
    ui_facing_description = UI_DESCRIPTION_FRONT_CAMERA
  else:
    raise ValueError(f'Unknown facing: {facing}')
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()
  _find_ui_object_else_click(dut.ui(desc=ui_facing_description),
                             dut.ui(res=QUICK_SET_FLIP_CAMERA_RESOURCE_ID))
  if not dut.ui(desc=ui_facing_description).wait.exists(
      UI_OBJECT_WAIT_TIME_SECONDS):
    dut.take_screenshot(log_path, prefix='failed_to_switch_camera')
    logging.debug('JCA UI dump: %s', dut.ui.dump())
    raise AssertionError(f'Failed to switch to {ui_facing_description}!')
  dut.take_screenshot(
      log_path, prefix=f"switched_to_{ui_facing_description.replace(' ', '_')}"
  )
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()


def _get_current_flash_mode_desc(dut):
  """Returns the current flash mode description from the JCA UI."""
  dut.ui(res=QUICK_SET_FLASH_RESOURCE_ID).wait.exists(
      UI_OBJECT_WAIT_TIME_SECONDS)
  return dut.ui(res=QUICK_SET_FLASH_RESOURCE_ID).child(depth=1).description


def set_jca_flash_mode(dut, log_path, flash_mode_desc):
  """Interacts with JCA UI to set flash mode if necessary.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    flash_mode_desc: str; flash mode description to set.
      Acceptable values: FLASH_MODES
  Raises:
    AssertionError: If JCA fails to set the desired flash mode.
  """
  if flash_mode_desc not in FLASH_MODES:
    raise ValueError(
        f'Invalid flash mode description: {flash_mode_desc}. '
        f'Valid values: {FLASH_MODES}'
    )
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()
  current_flash_mode_desc = _get_current_flash_mode_desc(dut)
  initial_flash_mode_desc = current_flash_mode_desc
  logging.debug('Initial flash mode description: %s', initial_flash_mode_desc)
  if initial_flash_mode_desc == flash_mode_desc:
    logging.debug('Initial flash mode %s matches desired flash mode %s',
                  initial_flash_mode_desc, flash_mode_desc)
  else:
    while current_flash_mode_desc != flash_mode_desc:
      dut.ui(res=QUICK_SET_FLASH_RESOURCE_ID).click()
      current_flash_mode_desc = _get_current_flash_mode_desc(dut)
      if current_flash_mode_desc == initial_flash_mode_desc:
        raise AssertionError(f'Failed to set flash mode to {flash_mode_desc}!')
  if not dut.ui(desc=flash_mode_desc).wait.exists(UI_OBJECT_WAIT_TIME_SECONDS):
    logging.debug('JCA UI dump: %s', dut.ui.dump())
    dut.take_screenshot(log_path, prefix='cannot_set_flash_mode')
    raise AssertionError(f'Unable to confirm {flash_mode_desc} exists in UI')
  dut.take_screenshot(log_path, prefix='flash_mode_set')
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()


def jca_ui_zoom(dut, zoom_ratio, log_path):
  """Interacts with the debug JCA overlay UI to zoom to the desired zoom ratio.

  Args:
    dut: An Android controller device object.
    zoom_ratio: float; zoom ratio desired. Will be rounded for compatibility.
    log_path: str; log path to save screenshots.
  Raises:
    AssertionError: If desired zoom ratio cannot be reached.
  """
  zoom_ratio = round(zoom_ratio, 2)  # JCA only supports 2 decimal places
  current_zoom_ratio_text = dut.ui(res=UI_ZOOM_RATIO_TEXT_RESOURCE_ID).text
  logging.debug('current zoom ratio text: %s', current_zoom_ratio_text)
  current_zoom_ratio = float(current_zoom_ratio_text[:-1])  # remove `x`
  if math.isclose(zoom_ratio, current_zoom_ratio):
    logging.debug('Desired zoom ratio is %.2f, '
                  'current zoom ratio is %.2f. '
                  'No need to zoom.',
                  zoom_ratio, current_zoom_ratio)
    return
  dut.ui(res=UI_DEBUG_OVERLAY_BUTTON_RESOURCE_ID).click()
  dut.ui(res=UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_BUTTON_RESOURCE_ID).click()
  dut.ui(
      res=UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_TEXT_FIELD_RESOURCE_ID
  ).set_text(str(zoom_ratio))
  dut.ui(res=UI_DEBUG_OVERLAY_SET_ZOOM_RATIO_SET_BUTTON_RESOURCE_ID).click()
  # Ensure that preview is stable by clicking the center of the screen.
  center_x, center_y = (
      dut.ui.info['displayWidth'] // 2,
      dut.ui.info['displayHeight'] // 2
  )
  dut.ui.click(x=center_x, y=center_y)
  time.sleep(UI_OBJECT_WAIT_TIME_SECONDS.total_seconds())
  zoom_ratio_text_after_zoom = dut.ui(res=UI_ZOOM_RATIO_TEXT_RESOURCE_ID).text
  logging.debug('zoom ratio text after zoom: %s', zoom_ratio_text_after_zoom)
  zoom_ratio_after_zoom = float(zoom_ratio_text_after_zoom[:-1])  # remove `x`
  if not math.isclose(zoom_ratio, zoom_ratio_after_zoom):
    dut.take_screenshot(
        log_path, prefix=f'failed_to_zoom_to_{zoom_ratio}'
    )
    raise AssertionError(
        f'Failed to zoom to {zoom_ratio}, '
        f'zoomed to {zoom_ratio_after_zoom} instead.'
    )
  logging.debug('Set zoom ratio to %.2f', zoom_ratio)
  dut.take_screenshot(log_path, prefix=f'zoomed_to_{zoom_ratio}')


def change_jca_aspect_ratio(dut, log_path, aspect_ratio):
  """Interacts with JCA UI to change aspect ratio if necessary.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    aspect_ratio: str; Aspect ratio that JCA supports.
      Acceptable values: _RATIO_TO_UI_DESCRIPTION
  Raises:
    ValueError: If ratio is not supported in JCA.
    AssertionError: If JCA does not find the requested ratio.
  """
  if aspect_ratio not in RATIO_TO_UI_DESCRIPTION:
    raise ValueError(f'Testing ratio {aspect_ratio} not supported in JCA!')
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()
  # Change aspect ratio in ratio switching menu if needed
  if not dut.ui(desc=aspect_ratio).wait.exists(UI_OBJECT_WAIT_TIME_SECONDS):
    dut.ui(res=QUICK_SET_RATIO_RESOURCE_ID).click()
    try:
      dut.ui(res=RATIO_TO_UI_DESCRIPTION[aspect_ratio]).click()
    except Exception as e:
      dut.take_screenshot(
          log_path, prefix=f'failed_to_find{aspect_ratio.replace(" ", "_")}'
      )
      raise AssertionError(
          f'Testing ratio {aspect_ratio} not found in JCA app UI!') from e
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()


def do_jca_video_setup(dut, log_path, facing, aspect_ratio, stabilization_mode):
  """Change video capture settings using the UI.

  Selects UI elements to modify settings.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    facing: str; constant describing the direction the camera lens faces.
      Acceptable values: camera_properties_utils.LENS_FACING[BACK, FRONT]
    aspect_ratio: str; Aspect ratios that JCA supports.
      Acceptable values: _RATIO_TO_UI_DESCRIPTION
    stabilization_mode: int; constant describing the video stabilization mode.
      Acceptable values: 0, 1, 2
  """
  open_jca_viewfinder(dut, log_path, request_video_capture=True)
  switch_jca_camera(dut, log_path, facing)
  change_jca_aspect_ratio(dut, log_path, aspect_ratio)
  _set_jca_video_stabilization(dut, log_path, stabilization_mode)


def _set_jca_video_stabilization(dut, log_path, stabilization_mode):
  """Change video stabilization mode using the UI.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    stabilization_mode: int; constant describing the video stabilization mode.
      Acceptable values: JCA_VIDEO_STABILIZATION_MODE_OFF,
                         JCA_VIDEO_STABILIZATION_MODE_HIGH_QUALITY,
                         JCA_VIDEO_STABILIZATION_MODE_ON
                         JCA_VIDEO_STABILIZATION_MODE_OPTICAL
  Mapping of JCA modes:
  ON: corresponds to setting android.control.videoStabilizationMode
    to PREVIEW_STABILIZATION.
  HIGH_QUALITY: corresponds to setting android.control.videoStabilizationMode
    to ON
  AUTO: will set the stabilization mode to PREVIEW_STABILIZATION,
    if the lens supports it, and if not, it will set it to OIS.
    If neither preview stabilization or OIS are supported it will be OFF.
  OPTICAL: optical stabilization is turned on in the default camera app
    when the video stabilization mode is OFF
  """
  dut.ui(res=SETTINGS_BUTTON_RESOURCE_ID).click()
  if not dut.ui(text=SETTINGS_VIDEO_STABILIZATION_MODE_TEXT).wait.exists(
      UI_OBJECT_WAIT_TIME_SECONDS):
    dut.take_screenshot(
        log_path, prefix='failed_to_find_video_stabilization_settings')
    raise AssertionError(
        'Set Video Stabilization settings not found!'
        'Make sure you have the latest JCA app.'
    )
  dut.ui(text=SETTINGS_VIDEO_STABILIZATION_MODE_TEXT).click()

  if not dut.ui(text=JCA_STABILIZATION_MODES[stabilization_mode]).wait.exists(
      UI_OBJECT_WAIT_TIME_SECONDS):
    dut.take_screenshot(
        log_path, prefix='failed_to_find_video_stabilization_mode')
    raise AssertionError(
        'Video Stabilization Mode not found!'
    )

  # Ensure that the stabilzation options are enabled.
  # They will be disabled if the camera does not support stabilization
  if not dut.ui(text=SETTINGS_VIDEO_STABILIZATION_MODE_TEXT).enabled:
    raise AssertionError('Set Video Stabilization not enabled.')

  dut.ui(text=JCA_STABILIZATION_MODES[stabilization_mode]).click()
  time.sleep(ACTIVITY_WAIT_TIME_SECONDS)
  logging.debug('JCA Video Stabilization set to %s successfully.',
                JCA_STABILIZATION_MODES[stabilization_mode])
  screenshot_prefix = (
      f'jca_stabilization_mode_{JCA_STABILIZATION_MODES[stabilization_mode]}_set'
  )
  dut.take_screenshot(log_path, prefix=screenshot_prefix)
  dut.ui(text=SETTINGS_CLOSE_TEXT).click()
  dut.ui(res=SETTINGS_BACK_BUTTON_RESOURCE_ID).click()
  # Verify that the setting was applied
  if stabilization_mode == JCA_VIDEO_STABILIZATION_MODE_ON:
    if not dut.ui(desc='Preview is Stabilized').wait.exists(
        UI_OBJECT_WAIT_TIME_SECONDS):
      raise AssertionError('JCA video stabilization_mode not set to ON.')
  elif stabilization_mode == JCA_VIDEO_STABILIZATION_MODE_HIGH_QUALITY:
    if not dut.ui(desc='Only Video is Stabilized').wait.exists(
        UI_OBJECT_WAIT_TIME_SECONDS):
      raise AssertionError(
          'JCA video stabilization_mode not set to HIGH_QUALITY.')
  elif stabilization_mode == JCA_VIDEO_STABILIZATION_MODE_OPTICAL:
    if not dut.ui(desc='Optical stabilization is Enabled').wait.exists(
        UI_OBJECT_WAIT_TIME_SECONDS):
      raise AssertionError(
          'JCA video stabilization_mode not set to OPTICAL.')
  else:
    if 'stabilize' in dut.ui.dump().lower():
      raise AssertionError('JCA video stabilization_mode not set to OFF.')


def default_camera_app_setup(device_id, pkg_name):
  """Setup Camera app by providing required permissions.

  Args:
    device_id: serial id of device.
    pkg_name: pkg name of the app to setup.
  Returns:
    Runtime exception from called function or None.
  """
  logging.debug('Setting up the app with permission.')
  for permission in _PERMISSIONS_LIST:
    cmd = f'pm grant {pkg_name} android.permission.{permission}'
    its_device_utils.run_adb_shell_command(device_id, cmd)
  allow_manage_storage_cmd = (
      f'appops set {pkg_name} MANAGE_EXTERNAL_STORAGE allow'
  )
  its_device_utils.run_adb_shell_command(device_id, allow_manage_storage_cmd)


def _get_current_camera_facing(content_desc, resource_id):
  """Returns the current camera facing based on UI elements."""
  # If separator is present, the last element is the current camera facing.
  if DEFAULT_CAMERA_CONTENT_DESC_SEPARATOR in content_desc:
    current_facing = content_desc.split(
        DEFAULT_CAMERA_CONTENT_DESC_SEPARATOR)[-1]
    if 'rear' in current_facing.lower() or 'back' in current_facing.lower():
      return 'rear'
    elif 'front' in current_facing.lower():
      return 'front'

  # If separator is not present, the element describes the other camera facing.
  if ('rear' in content_desc.lower() or 'rear' in resource_id.lower()
      or 'back' in content_desc.lower() or 'back' in resource_id.lower()):
    return 'front'
  elif 'front' in content_desc.lower() or 'front' in resource_id.lower():
    return 'rear'
  else:
    raise ValueError('Failed to determine current camera facing.')


def switch_default_camera(dut, facing, log_path):
  """Interacts with default camera app UI to switch camera.

  Args:
    dut: An Android controller device object.
    facing: str; constant describing the direction the camera lens faces.
    log_path: str; log path to save screenshots.
  Raises:
    AssertionError: If default camera app does not report that
      camera has been switched.
  """
  flip_camera_pattern = (
      r'(switch to|flip camera|switch camera|camera switch|'
      r'toggle_button|front_back_switcher|switch_camera_button|camera_switch_button)'
    )
  flash_pattern = 'flash'
  default_ui_dump = dut.ui.dump()
  logging.debug('Default camera UI dump: %s', default_ui_dump)
  root = et.fromstring(default_ui_dump)
  for node in root.iter('node'):
    resource_id = node.get('resource-id')
    content_desc = node.get('content-desc')
    # Ignore resource ids for flash on/off
    if (re.search(flash_pattern, content_desc, re.IGNORECASE) or
        re.search(flash_pattern, resource_id, re.IGNORECASE)):
      continue
    if content_desc:
      if re.search(
          flip_camera_pattern, content_desc, re.IGNORECASE
      ):
        logging.debug('Pattern matches')
        logging.debug('Resource id: %s', resource_id)
        logging.debug('Flip camera content-desc: %s', content_desc)
        break
    else:
      if re.search(
          flip_camera_pattern, resource_id, re.IGNORECASE
      ):
        logging.debug('Pattern matches')
        logging.debug('Resource id: %s', resource_id)
        logging.debug('Flip camera content-desc: %s', content_desc)
        break
  else:
    raise AssertionError('Flip camera resource not found.')
  if facing == _get_current_camera_facing(content_desc, resource_id):
    logging.debug('Pattern found but camera is already switched.')
  else:
    if content_desc:
      dut.ui(desc=content_desc).click.wait()
    else:
      dut.ui(res=resource_id).click.wait()

  dut.take_screenshot(
      log_path, prefix=f'switched_to_{facing}_default_camera'
  )


def pull_img_files(device_id, input_path, output_path):
  """Pulls files from the input_path on the device to output_path.

  Args:
    device_id: serial id of device.
    input_path: File location on device.
    output_path: Location to save the file on the host.
  """
  logging.debug('Pulling files from the device')
  pull_cmd = f'adb -s {device_id} pull {input_path} {output_path}'
  its_device_utils.run(pull_cmd)


def launch_and_take_capture(dut, pkg_name, camera_facing, log_path,
                            dumpsys_path=DEFAULT_CAMERA_APP_DUMPSYS_PATH):
  """Launches the camera app and takes still capture.

  Args:
    dut: An Android controller device object.
    pkg_name: pkg_name of the default camera app to
      be used for captures.
    camera_facing: camera lens facing orientation
    log_path: str; log path to save screenshots.
    dumpsys_path: path of the file on device to store the report

  Returns:
    img_path_on_dut: Path of the captured image on the device
  """
  device_id = dut.serial
  # start cameraservice watch command to monitor default camera pkg
  watch_dump_path = os.path.join(log_path, DEFAULT_CAMERA_WATCH_DUMP_FILE)
  watch_process = start_cameraservice_watch(device_id, watch_dump_path,
                                            pkg_name)
  try:
    logging.debug('Launching app: %s', pkg_name)
    launch_cmd = f'monkey -p {pkg_name} 1'
    its_device_utils.run_adb_shell_command(device_id, launch_cmd)

    # Click OK/Done button on initial pop up windows
    if dut.ui(text=AGREE_BUTTON).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS):
      dut.ui(text=AGREE_BUTTON).click.wait()
    if dut.ui(text=AGREE_AND_CONTINUE_BUTTON).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS):
      dut.ui(text=AGREE_AND_CONTINUE_BUTTON).click.wait()
    if dut.ui(text=OK_BUTTON_TXT).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS):
      dut.ui(text=OK_BUTTON_TXT).click.wait()
    if dut.ui(text=DONE_BUTTON_TXT).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS):
      dut.ui(text=DONE_BUTTON_TXT).click.wait()
    if dut.ui(text=CANCEL_BUTTON_TXT).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS):
      dut.ui(text=CANCEL_BUTTON_TXT).click.wait()
    if dut.ui(text=LOCATION_ON_TXT).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS
    ):
      dut.ui(text=LOCATION_ON_TXT).click.wait()
    switch_default_camera(dut, camera_facing, log_path)
    take_dumpsys_report(dut, dumpsys_path)
    time.sleep(ACTIVITY_WAIT_TIME_SECONDS)
    logging.debug('Taking photo')
    its_device_utils.run_adb_shell_command(device_id, TAKE_PHOTO_CMD)

    # pull the dumpsys output
    dut.adb.pull([dumpsys_path, log_path])
    time.sleep(ACTIVITY_WAIT_TIME_SECONDS)
    # stop cameraservice watch immediately after capturing image
    stop_cameraservice_watch(watch_process)
    img_path_on_dut = ''
    photo_storage_path = ''
    for path in CAMERA_FILES_PATHS:
      check_path_cmd = (
          f'ls {path} && echo "Directory exists" || '
          'echo "Directory does not exist"'
      )
      cmd_output = dut.adb.shell(check_path_cmd).decode('utf-8').strip()
      if _DIR_EXISTS_TXT in cmd_output:
        photo_storage_path = path
        break
    find_file_path = (
        f'find {photo_storage_path} ! -empty -a ! -name \'.pending*\''
        ' -a -type f -iname "*.jpg" -o -iname "*.jpeg"'
    )
    img_path_on_dut = (
        dut.adb.shell(find_file_path).decode('utf-8').strip().lower()
    )
    logging.debug('Image path on DUT: %s', img_path_on_dut)
    if JPG_FORMAT_STR not in img_path_on_dut:
      raise AssertionError('Failed to find jpg files!')
  finally:
    force_stop_app(dut, pkg_name)
  return img_path_on_dut


def restart_cts_verifier(dut, package_name):
  """Sends ADB commands to restart CtsVerifier app."""
  # Set correct intent flags so that JCA finishes successfully (b/353830655)
  force_stop_app(dut, package_name)
  dut.adb.shell('am start -n com.android.cts.verifier/.CtsVerifierActivity')


def force_stop_app(dut, pkg_name):
  """Force stops an app with given pkg_name.

  Args:
    dut: An Android controller device object.
    pkg_name: pkg_name of the app to be stopped.
  """
  logging.debug('Closing app: %s', pkg_name)
  force_stop_cmd = f'am force-stop {pkg_name}'
  dut.adb.shell(force_stop_cmd)


def default_camera_app_dut_setup(device_id, pkg_name):
  """Setup the device for testing default camera app.

  Args:
    device_id: serial id of device.
    pkg_name: pkg_name of the app.
  Returns:
    Runtime exception from called function or None.
  """
  default_camera_app_setup(device_id, pkg_name)
  for path in CAMERA_FILES_PATHS:
    its_device_utils.run_adb_shell_command(
        device_id, f'{REMOVE_CAMERA_FILES_CMD} {path}/*')


def launch_jca_and_capture(dut, log_path, camera_facing, zoom_ratio=None,
                           video_stabilization=None):
  """Launches the jetpack camera app and takes still capture.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    camera_facing: camera lens facing orientation
    zoom_ratio: optional; zoom_ratio to be set while taking the JCA capture.
    By default it will be set to 1 if the value is None.
    video_stabilization: optional; video stabilization mode to be set while
    taking the JCA capture. By default, JCA uses AUTO mode.

    AUTO in JCA will set the stabilization mode to PREVIEW_STABILIZATION,
    if the lens supports it, and if not, it will set it to OIS. If neither
    preview stabilization or OIS are supported it will be OFF.

  Returns:
    img_path_on_dut: Path of the captured image on the device
  """
  device_id = dut.serial
  remove_command = f'rm -rf {EMULATED_STORAGE_PATH}/*'
  its_device_utils.run_adb_shell_command(device_id, remove_command)
  watch_dump_path = os.path.join(log_path, JCA_WATCH_DUMP_FILE)
  watch_process = start_cameraservice_watch(device_id, watch_dump_path,
                                            JETPACK_CAMERA_APP_PACKAGE_NAME)
  try:
    logging.debug('Launching JCA app')
    launch_cmd = (
        'am start -n '
        f'{JETPACK_CAMERA_APP_PACKAGE_NAME}/{JETPACK_CAMERA_APP_PACKAGE_NAME}.MainActivity '
        '--ez "KEY_DEBUG_MODE" true'
    )
    its_device_utils.run_adb_shell_command(device_id, launch_cmd)
    switch_jca_camera(dut, log_path, camera_facing)
    change_jca_aspect_ratio(dut, log_path,
                            aspect_ratio=THREE_TO_FOUR_ASPECT_RATIO_DESC)
    if video_stabilization is not None:
      _set_jca_video_stabilization(dut, log_path, video_stabilization)
    # Set zoom_ratio after setting video stabilization to avoid reset to default
    if zoom_ratio is not None:
      jca_ui_zoom(dut, zoom_ratio, log_path)
    # Take dumpsys before capturing the image
    take_dumpsys_report(dut, file_path=DEFAULT_JCA_UI_DUMPSYS_PATH)
    if dut.ui(res=CAPTURE_BUTTON_RESOURCE_ID).wait.exists(
        timeout=WAIT_INTERVAL_FIVE_SECONDS
    ):
      dut.ui(res=CAPTURE_BUTTON_RESOURCE_ID).click.wait()
    time.sleep(ACTIVITY_WAIT_TIME_SECONDS)
    stop_cameraservice_watch(watch_process)
    # pull the dumpsys output
    dut.adb.pull([DEFAULT_JCA_UI_DUMPSYS_PATH, log_path])
    img_path_on_dut = (
        dut.adb.shell(
            "find {} ! -empty -a ! -name '.pending*' -a -type f".format(
                EMULATED_STORAGE_PATH
            )
        )
        .decode('utf-8')
        .strip()
    )
    logging.debug('Image path on DUT: %s', img_path_on_dut)
    if JPG_FORMAT_STR not in img_path_on_dut:
      raise AssertionError('Failed to find jpg files!')
  finally:
    force_stop_app(dut, JETPACK_CAMERA_APP_PACKAGE_NAME)
  return img_path_on_dut


def take_dumpsys_report(dut, file_path):
  """Takes dumpsys report of camera service and stores the report in the file.

  Args:
    dut: An Android controller device object.
    file_path: Path of the file on device to store the report.
  """
  dut.adb.shell(['dumpsys', 'media.camera', '>', file_path])


def _watch_clear(device_id):
  """Clears cameraservice watch cache.

  Args:
    device_id: serial id of device.
  """
  cmd = f'adb -s {device_id} shell cmd media.camera watch clear'.split(' ')
  subprocess.run(cmd, check=True)
  logging.debug('Cleared watch cache')


def _watch_start(device_id, pkg_name):
  """Starts cameraservice watch command.

  Args:
    device_id: serial id of device.
    pkg_name: pkg_name of the app.
  """
  cmd = [
      'adb',
      '-s',
      device_id,
      'shell',
      'cmd',
      'media.camera',
      'watch',
      'start',
      '-m',
      (
          'android.control.captureIntent,android.jpeg.quality,'
          'android.control.zoomRatio,'
          'android.scaler.cropRegion,'
          'android.control.zoomMethod,'
          '3a'
      ),
      '-c',
      pkg_name,
  ]
  subprocess.run(cmd, check=True)
  logging.debug('Started watching 3a for %s', pkg_name)


def _watch_live(device_id, file_path):
  """Starts cameraservice watch live command.

  Args:
    device_id: serial id of device.
    file_path: Path of the file to store the report.

  Returns:
    watch_process: subprocess.Popen object for the watch live command.
  """
  cmd = f'adb -s {device_id} shell cmd media.camera watch live'.split(' ')
  with open(file_path, 'w') as f:
    logging.debug('Starting watch live')
    watch_process = subprocess.Popen(
        cmd, stdout=f, stdin=subprocess.PIPE
    )
  logging.debug('watch live output written to the file_path: %s', file_path)
  return watch_process


def start_cameraservice_watch(device_id, file_path, pkg_name):
  """Starts cameraservice watch command.

  Args:
    device_id: serial id of device.
    file_path: Path of the file to store the report.
    pkg_name: pkg_name of the app.

  Returns:
    watch_process: subprocess.Popen object for the watch live command.
  """
  _watch_start(device_id, pkg_name)
  watch_process = _watch_live(device_id, file_path)
  watch_process.its_watch_process_device_id = device_id
  return watch_process


def stop_cameraservice_watch(watch_process):
  """Stops cameraservice watch command.

  Args:
    watch_process: subprocess.Popen object returned by start_cameraservice_watch
  Raises:
    CameraItsError: If watch_process not created by start_cameraservice_watch
  """
  if not hasattr(watch_process, 'its_watch_process_device_id'):
    raise error_util.CameraItsError(
        'watch_process was not created by start_cameraservice_watch'
    )
  device_id = watch_process.its_watch_process_device_id
  watch_process.stdin.write(b'\n')
  watch_process.stdin.flush()
  watch_process.wait()
  logging.debug('Stopping watch live')
  cmd = f'adb -s {device_id} shell cmd media.camera watch stop'.split(' ')
  subprocess.run(cmd, check=True)
  logging.debug('Stopped watching 3a')


def get_default_camera_zoom_ratio(file_name):
  """Returns the zoom_ratio used by default camera capture.

  Args:
    file_name: str; file name storing default camera pkg watch
    cameraservice dump output.
  Returns:
    zoom_ratio: zoom_ratio rounded up to 2 decimal places
  Raises:
    FileNotFoundError: If file_name does not exist
  """
  zoom_ratio_values = []
  if not os.path.exists(file_name):
    raise FileNotFoundError(f'File not found: {file_name}')
  with open(file_name, 'r') as file:
    for line in file:
      if _CONTROL_ZOOM_RATIO_KEY in line:
        if _REQ_STR_PATTERN not in line:
          continue
        logging.debug('zoomRatio line: %s', line)
        values = line.split(':')
        value_str = values[-1]
        match = re.search(r'[\d.]+', value_str)
        if match:
          value = float(match.group())
          rounded_value = round(value, 2)
          logging.debug('zoomRatio found: %s', rounded_value)
          zoom_ratio_values.append(rounded_value)

  if zoom_ratio_values:
    logging.debug('zoom_ratio_values: %s', zoom_ratio_values)
    return zoom_ratio_values[-1]
  return None


def get_default_camera_video_stabilization(file_name):
  """Returns the video stabilization mode used by default camera capture.

  Args:
    file_name: str; file name storing default camera pkg watch
    cameraservice dump output.
  Returns:
    video_stabilization_mode: str; video stabilization mode used by
    default camera app during the capture
  Raises:
    FileNotFoundError: If file_name does not exist
  """
  video_stabilization_modes = []
  if not os.path.exists(file_name):
    raise FileNotFoundError(f'File not found: {file_name}')
  with open(file_name, 'r') as file:
    for line in file:
      if 'videoStabilizationMode' in line:
        logging.debug('videoStabilizationMode line: %s', line)
        values = line.split(':')
        value_str = values[-1]
        match = re.search(r'[a-zA-Z]+', value_str)
        if match:
          value = str(match.group())
          logging.debug('videoStabilizationMode found: %s', value)
          video_stabilization_modes.append(value)
  if video_stabilization_modes:
    logging.debug('video_stabilization_modes: %s', video_stabilization_modes)
    logging.debug('videoStabilizationMode used for default captures: %s',
                  video_stabilization_modes[-1])
    return video_stabilization_modes[-1].strip()
  return None


def get_default_camera_ois_mode(file_name):
  """Returns the optical stabilization mode used by default camera capture.

  Args:
    file_name: str; file name storing default camera pkg watch
    cameraservice dump output.
  Returns:
    optical_stabilization_mode: str; optical stabilization mode used by
    default camera app during the capture
  Raises:
    FileNotFoundError: If file_name does not exist
  """
  optical_stabilization_modes = []
  if not os.path.exists(file_name):
    raise FileNotFoundError(f'File not found: {file_name}')
  with open(file_name, 'r') as file:
    for line in file:
      if 'opticalStabilizationMode' in line:
        if _REQ_STR_PATTERN not in line:
          continue
        logging.debug('opticalStabilizationMode line: %s', line)
        values = line.split(':')
        value_str = values[-1]
        match = re.search(r'[a-zA-Z]+', value_str)
        if match:
          value = str(match.group())
          logging.debug('opticalStabilizationMode found: %s', value)
          optical_stabilization_modes.append(value)
  if optical_stabilization_modes:
    logging.debug('optical_stabilization_modes: %s',
                  optical_stabilization_modes)
    logging.debug('opticalStabilizationMode used for default captures: %s',
                  optical_stabilization_modes[-1])
    return optical_stabilization_modes[-1].strip()
  return None

