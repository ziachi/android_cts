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

import argparse
import json
import logging
import multi_device_utils
import os
import os.path
from pathlib import Path
import re
import subprocess
import tempfile
import time
import yaml


RESULT_KEY = 'result'
RESULT_PASS = 'PASS'
RESULT_FAIL = 'FAIL'
CONFIG_FILE = os.path.join(os.getcwd(), 'config.yml')
TESTS_DIR = os.path.join(os.getcwd(), 'tests')
CTS_VERIFIER_PACKAGE_NAME = 'com.android.cts.verifier'
MOBLY_TEST_SUMMARY_TXT_FILE = 'test_mobly_summary.txt'
MULTI_DEVICE_TEST_ACTIVITY = (
    'com.android.cts.verifier/.multidevice.MultiDeviceTestsActivity'
)
ACTION_HOST_TEST_RESULT = 'com.android.cts.verifier.ACTION_HOST_TEST_RESULT'
EXTRA_VERSION = 'com.android.cts.verifier.extra.HOST_TEST_RESULT'
ACTIVITY_START_WAIT = 2  # seconds


def get_config_file_contents():
  """Read the config file contents from a YML file.

  Args: None

  Returns:
    config_file_contents: a dict read from config.yml
  """
  with open(CONFIG_FILE) as file:
    config_file_contents = yaml.safe_load(file)
  return config_file_contents


def get_device_serial_number(config_file_contents):
  """Returns the serial number of the dut devices.

  Args:
      config_file_contents: dict read from config.yml file.

  Returns:
      The serial numbers (str) or None if the device is not found.
  """

  device_serial_numbers = []
  for _, testbed_data in config_file_contents.items():
    for data_dict in testbed_data:
      android_devices = data_dict.get('Controllers', {}).get(
          'AndroidDevice', []
      )

      for device_dict in android_devices:
        device_serial_numbers.append(device_dict.get('serial'))
  return device_serial_numbers


def report_result(device_id, results):
  """Sends a pass/fail result to the device, via an intent.

  Args:
   device_id: the serial number of the device.
   results: a dictionary contains all multi-device test names as key and
     result/summary of current test run.
  """
  adb = f'adb -s {device_id}'

  # Start MultiDeviceTestsActivity to receive test results
  cmd = (
      f'{adb} shell am start'
      f' {MULTI_DEVICE_TEST_ACTIVITY} --activity-brought-to-front'
  )
  multi_device_utils.run(cmd)
  time.sleep(ACTIVITY_START_WAIT)

  json_results = json.dumps(results)
  cmd = (
      f'{adb} shell am broadcast -a {ACTION_HOST_TEST_RESULT} --es'
      f" {EXTRA_VERSION} '{json_results}'"
  )
  if len(cmd) > 4095:
    logging.info('Command string might be too long! len:%s', len(cmd))
  multi_device_utils.run(cmd)


def main():
  """Run all Multi-device Mobly tests and collect results."""

  logging.basicConfig(level=logging.INFO)
  topdir = tempfile.mkdtemp(prefix='MultiDevice_')
  subprocess.call(['chmod', 'g+rx', topdir])  # Add permissions

  # Parse command-line arguments
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--test_cases',
      nargs='+',
      help='Specific test cases to run (space-separated)')
  parser.add_argument(
      '--test_files',
      nargs='+',
      help='Filter test files by name (substring match, space-separated)')
  args = parser.parse_args()

  config_file_contents = get_config_file_contents()
  device_ids = get_device_serial_number(config_file_contents)

  test_results = {}
  test_summary_file_list = []

  # Run tests
  for root, _, files in os.walk(TESTS_DIR):
    for test_file in files:
      if test_file.endswith('-py-ctsv') and (
          args.test_files is None or
          test_file in args.test_files
      ):
        test_file_path = os.path.join(root, test_file)
        logging.info('Start running test: %s', test_file)
        cmd = [
            test_file_path,  # Use the full path to the test file
            '-c',
            CONFIG_FILE,
            '--testbed',
            test_file,
        ]

        if args.test_cases:
          cmd.extend(['--tests'])
          for test_case in args.test_cases:
            cmd.extend([test_case])

        summary_file_path = os.path.join(topdir, MOBLY_TEST_SUMMARY_TXT_FILE)

        test_completed = False
        with open(summary_file_path, 'w+') as fp:
          subprocess.run(cmd, stdout=fp, check=False)
          fp.seek(0)
          for line in fp:
            if line.startswith('Test summary saved in'):
              match = re.search(r'"(.*?)"', line)  # Get test artifacts file path
              if match:
                test_summary = Path(match.group(1))
                test_artifact = test_summary.parent
                logging.info(
                    'Please check the test artifacts of %s under: %s', test_file, test_artifact
                )
                if test_summary.exists():
                  test_summary_file_list.append(test_summary)
                  test_completed = True
                  break
        if not test_completed:
          logging.error('Failed to get test summary file path')
        os.remove(os.path.join(topdir, MOBLY_TEST_SUMMARY_TXT_FILE))

  # Parse test summary files
  for test_summary_file in test_summary_file_list:
    with open(test_summary_file) as file:
      test_summary_content = yaml.safe_load_all(file)
      for doc in test_summary_content:
        if doc['Type'] == 'Record':
          test_key = f"{doc['Test Class']}#{doc['Test Name']}"
          result = (
              RESULT_PASS if doc['Result'] in ('PASS', 'SKIP') else RESULT_FAIL
          )
          test_results.setdefault(test_key, {RESULT_KEY: result})

  for device_id in device_ids:
    report_result(device_id, test_results)

  logging.info('Test execution completed. Results: %s', test_results)


if __name__ == '__main__':
  main()
