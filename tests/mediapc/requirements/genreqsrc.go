// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package main generates src to stout from the stdin template using embedded
// Media Performance Class (MPC) requirements data.
package main

import (
  "flag"
  "io"
  "log"
  "os"

  "cts/test/mediapc/requirements/requirements"
)

func main() {

  var f string
  flag.StringVar(&f, "f", "", "The proto binary file containing a RequirementList.")
  flag.Parse()
  reqBinary, err := os.ReadFile(f)
  if err != nil {
    log.Fatalf("Failed to read requirements file %s: %v", f, err)
  }
  req, err := requirements.UnmarshalRequirementList(reqBinary)
  if err != nil {
    log.Fatalf("Failed to unmarshal requirements: %v", err)
  }
  stdin, err := io.ReadAll(os.Stdin)
  str := string(stdin)
  if err != nil {
    log.Fatalf("Failed to read stdin: %v", err)
  }
  err = requirements.Gensrc(str, req, os.Stdout)
  if err != nil {
    log.Fatalf("Failed to generate source: %v", err)
  }
}
