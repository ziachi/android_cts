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

# This file should be sourced from bash. Sets environment variables for
# running tests, and also checks that a number of dependencies are present
# (indicating that the setup is correct).


[[ "${BASH_SOURCE[0]}" != "${0}" ]] || \
    { echo ">> Script must be sourced with 'source $0'" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || \
    echo ">> Require adb executable to be in path" >&2

command -v python >/dev/null 2>&1 || \
    echo ">> Require python executable to be in path" >&2

python3 -V 2>&1 | grep -q "Python 3.*" || \
    echo ">> Require python version 3" >&2

export PYTHONPATH="$PWD/utils:$PYTHONPATH"
export PYTHONPATH="$PWD/tests:$PYTHONPATH"

echo -e "\n*****Please execute below adb command on your dut before running the tests*****\n"
echo -e "adb -s <device_id> shell am compat enable ALLOW_TEST_API_ACCESS com.android.cts.verifier\n\n"
