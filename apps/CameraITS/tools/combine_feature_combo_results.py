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
"""Combine separate feature combinations results (.pb) into one."""

import os
import re
import sys

from google.protobuf import text_format
import feature_combination_info_pb2

_DEBUG = False


def main():
  """Combine the feature combination verification tests into a single file.

  Command line arguments:
    pb_file_0: the relative path of the first pb file (camera 0, for example)
               to be combined
    pb_file_1: the relative path of the scond pb file (camera 1, for example)
               to be combined
  """
  # Validate input file paths
  if len(sys.argv) != 3:
    raise ValueError(f'{sys.argv[0]} pb_file_0 pb_file_1')
  for file_path in sys.argv[1:]:
    if not os.path.isfile(file_path):
      raise ValueError(f'{file_path} does not exist')

  # Combine the input proto files
  pb_file_0_name = sys.argv[1]
  pb_file_1_name = sys.argv[2]
  with open(pb_file_0_name, 'rb') as file_0:
    msg_0 = feature_combination_info_pb2.FeatureCombinationDatabase()
    msg_0.ParseFromString(file_0.read())
    with open(pb_file_1_name, 'rb') as file_1:
      msg_1 = feature_combination_info_pb2.FeatureCombinationDatabase()
      msg_1.ParseFromString(file_1.read())

      # Validate protos in the 2 input files
      if msg_0.build_fingerprint != msg_1.build_fingerprint:
        raise ValueError('Build fingerprint not matching:'
                         f'{msg_0.build_fingerprint} vs '
                         f'{msg_1.build_fingerprint}')
      if len(msg_0.feature_combination_for_camera) != 1:
        raise ValueError(f'{msg_0.build_fingerprint} '
                         'must contain 1 camera, but contains '
                         f'{len(msg_0.feature_combination_for_camera)}')
      if len(msg_1.feature_combination_for_camera) != 1:
        raise ValueError(f'{msg_1.build_fingerprint} '
                         'must contain 1 camera, but contains '
                         f'{len(msg_1.feature_combination_for_camera)}')
      if (msg_0.feature_combination_for_camera[0].camera_id ==
          msg_1.feature_combination_for_camera[0].camera_id):
        raise ValueError(f'proto files have duplicate camera id: '
                         f'{msg_0.feature_combination_for_camera[0].camera_id}')

      if msg_1.timestamp_in_sec > msg_0.timestamp_in_sec:
        msg_0.timestamp_in_sec = msg_1.timestamp_in_sec

      msg_0.feature_combination_for_camera.append(
          msg_1.feature_combination_for_camera[0]
      )

      build_id = re.sub(r'[/:]', '_', msg_0.build_fingerprint)
      file_name = f'{build_id}.pb'
      with open(file_name, 'wb') as f:
        f.write(msg_0.SerializeToString())

      if _DEBUG:
        txtpb_file_name = f'{build_id}.txtpb'
        with open(txtpb_file_name, 'w') as tf:
          tf.write(text_format.MessageToString(msg_0))

if __name__ == '__main__':
  main()
