#!/bin/bash
#
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

# A tool to generate a dm file from inputs.
# Usage: SCRIPT SOONG_ZIP OUTPUT INPUTS...

soong_zip=$1
output=$2
shift 2

args="${soong_zip} -o ${output}"
for input in $@; do
  if [[ $input =~ \.prof$ ]]; then
    args+=" -e primary.prof -f $input"
  fi
  if [[ $input =~ \.pb$ ]]; then
    args+=" -e config.pb -f $input"
  fi
done

$args
