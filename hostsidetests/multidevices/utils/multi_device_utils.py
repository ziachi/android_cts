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
"""Utility functions to interact with devices for Multidevice test."""


import os
import subprocess


def run(cmd):
  """Replacement for os.system, with hiding of stdout+stderr messages.

  Args:
    cmd: Command to be executed in string format.
  """
  with open(os.devnull, 'wb') as devnull:
    subprocess.call(cmd.split(), stdout=devnull, stderr=subprocess.STDOUT)


def install_apk(device_id, package_name):
  """Installs an APK on a given device.

  Args:
    device_id: str; ID of the device.
    package_name: str; name of the package to be installed.
  """
  run(f'adb -s {device_id} install -r -g {package_name}')


def check_apk_installed(device_id, package_name):
  """Verifies that an APK is installed on a given device.

  Args:
    device_id: str; ID of the device.
    package_name: str; name of the package that should be installed.
  """
  verify_cts_cmd = (
      f'adb -s {device_id} shell pm list packages | '
      f'grep {package_name}'
  )
  bytes_output = subprocess.check_output(
      verify_cts_cmd, stderr=subprocess.STDOUT, shell=True
  )
  output = str(bytes_output.decode('utf-8')).strip()
  if package_name not in output:
    raise AssertionError(
        f'{package_name} not installed on device {device_id}!'
    )
