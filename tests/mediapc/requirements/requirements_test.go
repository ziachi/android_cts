// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package requirements

import (
	"bytes"
	"testing"

	_ "embed"
)

// MPC Requirements data from requirements.txtpb
//
//go:embed requirements.binbp
var reqBinary []byte

func TestGensrc(t *testing.T) {
	reqList, err := UnmarshalRequirementList(reqBinary)
	if err != nil {
		t.Fatalf("Failed to unmarshal reqBinary: %v", err)
	}

	tests := []struct {
		tmpl string
		want string
	}{
		{
			tmpl: "{{- $first := index .ReqList.GetRequirements 0 -}}First requirement is [{{$first.GetId}}].",
			want: "First requirement is [5.1/H-1-1]."},
		{
			tmpl: "UpperCamelCase foo_bar -> {{UpperCamelCase \"foo_bar\"}}",
			want: "UpperCamelCase foo_bar -> FooBar",
		},
	}

	for _, tc := range tests {
		var b bytes.Buffer
		err = Gensrc(tc.tmpl, reqList, &b)
		if err != nil {
			t.Fatalf("Gensrc(%v, ...) failed: %v", tc.tmpl, err)
		}
		got := b.String()
		if got != tc.want {
			t.Errorf("Gensrc(%q, ...) = %q, want %q", tc.tmpl, got, tc.want)
		}
	}
}
