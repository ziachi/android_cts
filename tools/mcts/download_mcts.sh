#!/bin/bash

# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Command tools to download the released MCTS to local.

# Command examples:
# 1) First, you need to download the MCTS test cases corresponding to the
#    Android API level of the DUT. Please use the command below as an example,
#    remember to input the correct device abi and android version.
#    Below is an example when the arm64 device is Android U (34).
#
#    ./download_mcts.sh  --abi arm64 --android_version 34

# 2) Second, you need to download the MCTS test cases corresponding to the
#    preloaded Mainline Train version of the DUT. If you ensure that the DUT
#    doesn't have mainline train prebuilt, you can skip this command.
#    Please use the command below as an example, remember to input the correct
#    device abi and mainline train version. Below is an example when the
#    arm64 device preloaded with Mainline train released in Jan 2024.
#
#    ./download_mcts.sh  --abi arm64 --year 2024 --month 01

# All the files will be downloaded to
# $HOME/xts/mcts_dynamic_download/android/xts/mcts/android_version/abi/

set -e

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) abi="$2";;    # arm64  or x86_64
    --android_version) android_version="$2";;
    --year) year="$2";;
    --month) month="$2";;
    *) echo "Unknown argument $1";
    esac
    shift # skip key
    shift # skip value
  done

path=""

if [[ -n ${year} ]] && [[ -n ${month} ]]; then
  path="${year}-${month}/${abi}"
fi

if [[ -n ${android_version} ]]; then
  path="${android_version}/${abi}"
fi

dir_prefix="$HOME/xts/mcts_dynamic_download/android/xts/mcts"
full_dir_path="$dir_prefix/$path"
mkdir -p $full_dir_path

function download_wget_and_curl_if_needed() {
  if [[ "$OSTYPE" == "linux-gnu" ]]
  then
    [[ -x `which wget` ]] || sudo apt-get install wget
    [[ -x `which curl` ]] || sudo apt-get install curl
  elif [[ "$OSTYPE" == "darwin"* ]]
  then
    [[ -x `which wget` ]] || brew install wget
    [[ -x `which curl` ]] || sudo apt-get install curl
  fi
}

function download_mcts()
  {
    pushd $full_dir_path > /dev/null
    local path=$1
    local file=$2
    local url="https://dl.google.com/android/xts/mcts/${path}/${file}"
    # Download the file if it doesn't exist.
    if [ ! -f ${file} ]; then
      echo "There is no ${file}, trying to download it"
      wget -q ${url} || true
    else
      echo "There is ${file}, checking if it is up to date"
      # %W time of file birth, seconds since Epoch
      # %s seconds since the Epoch (1970-01-01 00:00 UTC)
      file_download_time=$(date -d "@$(stat -c %W ${file})" +%s )
      # The OS Ubuntu below 20.10 version does not support stat command and
      # return "0" by default, so we need to use debugfs to get the file
      # creation time.
      if [[ ${file_download_time} == "0" ]]; then
        file_download_time=$(get_crtime ${file})
      fi
      url_link_last_modified_string=$(curl -sI ${url} | grep -i "last-modified" |  cut -d: -f2- | xargs)
      url_link_time_stamp=$(date -d "${url_link_last_modified_string}" +%s )
      if [[ ${file_download_time} -lt ${url_link_time_stamp} ]]; then
        echo "The file is out of date, trying to download it"
        rm ${file}
        wget -q ${url} || true
      else
        echo "The file is up to date, skip downloading"
      fi
    fi
     echo "Done"
     popd > /dev/null
  }

function get_crtime() {
  local target=$1
  inode=$(stat -c '%i' "${target}")
  fs=$(df  --output=source "${target}"  | tail -1)
  crtime=$(debugfs -R 'stat <'"${inode}"'>' "${fs}" 2>/dev/null | grep -oP 'crtime.*--\s*\K.*')
  file_download_time=$(date -d "${crtime}" +%s)
  echo ${file_download_time}
}

files=(
  "android-mcts-adbd.zip"
  "android-mcts-adservices.zip"
  "android-mcts-appsearch.zip"
  "android-mcts-art.zip"
  "android-mcts-bluetooth.zip"
  "android-mcts-cellbroadcast.zip"
  "android-mcts-configinfrastructure.zip"
  "android-mcts-conscrypt.zip"
  "android-mcts-cronet.zip"
  "android-mcts-dnsresolver.zip"
  "android-mcts-documentsui.zip"
  "android-mcts-extservices.zip"
  "android-mcts-healthfitness.zip"
  "android-mcts-ipsec.zip"
  "android-mcts-media.zip"
  "android-mcts-mediaprovider.zip"
  "android-mcts-networking.zip"
  "android-mcts-neuralnetworks.zip"
  "android-mcts-ondevicepersonalization.zip"
  "android-mcts-permission.zip"
  "android-mcts-rkpd.zip"
  "android-mcts-scheduling.zip"
  "android-mcts-sdkextensions.zip"
  "android-mcts-statsd.zip"
  "android-mcts-tethering.zip"
  "android-mcts-tzdata.zip"
  "android-mcts-uwb.zip"
  "android-mcts-wifi.zip"
)

download_wget_and_curl_if_needed
echo "The files will be download at $full_dir_path"
for file in ${files[@]}; do
  download_mcts $path $file
done
chmod -R 777 $full_dir_path
for file in $full_dir_path/* ; do
  echo "touch $file to update the timestamp"
  touch $file
done

echo "Download all files"
