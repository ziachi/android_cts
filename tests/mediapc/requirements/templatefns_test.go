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

package templatefns

import (
	"errors"
	"testing"

	pb "cts/test/mediapc/requirements/requirements_go_proto"

	"google.golang.org/protobuf/proto"
)

var caseTests = []struct {
	input, kebabCase, snakeCase, titleCase, upperCamelCase, lowerCamelCase, safeTestConfigID string
}{
	{
		input:     "",
		kebabCase: "", snakeCase: "", titleCase: "", upperCamelCase: "", lowerCamelCase: "", safeTestConfigID: ""},
	{
		input:     "f",
		kebabCase: "f", snakeCase: "f", titleCase: "F", upperCamelCase: "F", lowerCamelCase: "f", safeTestConfigID: "__f"},
	{
		input:     "foo",
		kebabCase: "foo", snakeCase: "foo", titleCase: "Foo", upperCamelCase: "Foo", lowerCamelCase: "foo", safeTestConfigID: "__foo"},
	{
		input:     " foo_bar",
		kebabCase: "foo-bar", snakeCase: "foo_bar", titleCase: "Foo Bar", upperCamelCase: "FooBar", lowerCamelCase: "fooBar", safeTestConfigID: "__foo_bar"},
	{
		input:     " foo-bar",
		kebabCase: "foo-bar", snakeCase: "foo_bar", titleCase: "Foo Bar", upperCamelCase: "FooBar", lowerCamelCase: "fooBar", safeTestConfigID: "__foo_bar"},
	{
		input:     " foo bar",
		kebabCase: "foo-bar", snakeCase: "foo_bar", titleCase: "Foo Bar", upperCamelCase: "FooBar", lowerCamelCase: "fooBar", safeTestConfigID: "__foo_bar"},
	{
		input:     " Foo Bar",
		kebabCase: "foo-bar", snakeCase: "foo_bar", titleCase: "Foo Bar", upperCamelCase: "FooBar", lowerCamelCase: "fooBar", safeTestConfigID: "__foo_bar"},
	{
		input:     "HTTP_status_code",
		kebabCase: "http-status-code", snakeCase: "http_status_code", titleCase: "HTTP Status Code", upperCamelCase: "HTTPStatusCode", lowerCamelCase: "httpStatusCode", safeTestConfigID: "__http_status_code"},
	{
		input:     "foo   many spaces",
		kebabCase: "foo-many-spaces", snakeCase: "foo_many_spaces", titleCase: "Foo Many Spaces", upperCamelCase: "FooManySpaces", lowerCamelCase: "fooManySpaces", safeTestConfigID: "__foo_many_spaces"},
	{
		input:     "foo---many-dashes",
		kebabCase: "foo-many-dashes", snakeCase: "foo_many_dashes", titleCase: "Foo Many Dashes", upperCamelCase: "FooManyDashes", lowerCamelCase: "fooManyDashes", safeTestConfigID: "__foo_many_dashes"},
	{
		input:     "foo___many_underline",
		kebabCase: "foo-many-underline", snakeCase: "foo_many_underline", titleCase: "Foo Many Underline", upperCamelCase: "FooManyUnderline", lowerCamelCase: "fooManyUnderline", safeTestConfigID: "__foo_many_underline"},
	{
		input:     "UpperCamelCase",
		kebabCase: "upper-camel-case", snakeCase: "upper_camel_case", titleCase: "Upper Camel Case", upperCamelCase: "UpperCamelCase", lowerCamelCase: "upperCamelCase", safeTestConfigID: "__upper_camel_case"},
	{
		input:     "ACRONYMInUpperCamelCase",
		kebabCase: "acronym-in-upper-camel-case", snakeCase: "acronym_in_upper_camel_case", titleCase: "ACRONYM In Upper Camel Case", upperCamelCase: "ACRONYMInUpperCamelCase", lowerCamelCase: "acronymInUpperCamelCase", safeTestConfigID: "__acronym_in_upper_camel_case"},
	{
		input:     "FooGRPCHandler",
		kebabCase: "foo-grpc-handler", snakeCase: "foo_grpc_handler", titleCase: "Foo GRPC Handler", upperCamelCase: "FooGRPCHandler", lowerCamelCase: "fooGRPCHandler", safeTestConfigID: "__foo_grpc_handler"},
	{
		input:     "GRPC1234Handler",
		kebabCase: "grpc1234-handler", snakeCase: "grpc1234_handler", titleCase: "GRPC1234 Handler", upperCamelCase: "GRPC1234Handler", lowerCamelCase: "grpc1234Handler", safeTestConfigID: "__grpc1234_handler"},
	{
		input:     "tricky4567number",
		kebabCase: "tricky4567-number", snakeCase: "tricky4567_number", titleCase: "Tricky4567 Number", upperCamelCase: "Tricky4567Number", lowerCamelCase: "tricky4567Number", safeTestConfigID: "__tricky4567_number"},
	{
		input:     "tricky 4567number",
		kebabCase: "tricky-4567-number", snakeCase: "tricky_4567_number", titleCase: "Tricky 4567 Number", upperCamelCase: "Tricky4567Number", lowerCamelCase: "tricky4567Number", safeTestConfigID: "__tricky_4567_number"},
	{
		input:     "tricky 4567Number",
		kebabCase: "tricky-4567-number", snakeCase: "tricky_4567_number", titleCase: "Tricky 4567 Number", upperCamelCase: "Tricky4567Number", lowerCamelCase: "tricky4567Number", safeTestConfigID: "__tricky_4567_number"},
	{
		input:     "tricky 4567 Number",
		kebabCase: "tricky-4567-number", snakeCase: "tricky_4567_number", titleCase: "Tricky 4567 Number", upperCamelCase: "Tricky4567Number", lowerCamelCase: "tricky4567Number", safeTestConfigID: "__tricky_4567_number"},
	{
		input:     "lowerCamelCase",
		kebabCase: "lower-camel-case", snakeCase: "lower_camel_case", titleCase: "Lower Camel Case", upperCamelCase: "LowerCamelCase", lowerCamelCase: "lowerCamelCase", safeTestConfigID: "__lower_camel_case"},
	{
		input:     "endInCapitalT",
		kebabCase: "end-in-capital-t", snakeCase: "end_in_capital_t", titleCase: "End In Capital T", upperCamelCase: "EndInCapitalT", lowerCamelCase: "endInCapitalT", safeTestConfigID: "__end_in_capital_t"},
	{
		input:     "snake_case",
		kebabCase: "snake-case", snakeCase: "snake_case", titleCase: "Snake Case", upperCamelCase: "SnakeCase", lowerCamelCase: "snakeCase", safeTestConfigID: "__snake_case"},
	{
		input:     "kebab-case",
		kebabCase: "kebab-case", snakeCase: "kebab_case", titleCase: "Kebab Case", upperCamelCase: "KebabCase", lowerCamelCase: "kebabCase", safeTestConfigID: "__kebab_case"},
	{
		input:     "üñicödeCäse",
		kebabCase: "üñicöde-cäse", snakeCase: "üñicöde_cäse", titleCase: "Üñicöde Cäse", upperCamelCase: "ÜñicödeCäse", lowerCamelCase: "üñicödeCäse", safeTestConfigID: "__üñicöde_cäse"}}

func TestKebabCase(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := kebabCase(tt.input); got != tt.kebabCase {
				t.Fatalf("KebabCase(%q) = %q, want %q", tt.input, got, tt.kebabCase)
			}
		})
	}
}

func TestSnakeCase(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := snakeCase(tt.input); got != tt.snakeCase {
				t.Fatalf("SnakeCase(%q) = %q, want %q", tt.input, got, tt.snakeCase)
			}
		})
	}
}

func TestTitleCase(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := titleCase(tt.input); got != tt.titleCase {
				t.Fatalf("TitleCase(%q) = %q, want %q", tt.input, got, tt.titleCase)
			}
		})
	}
}

func TestUpperCamelCase(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := upperCamelCase(tt.input); got != tt.upperCamelCase {
				t.Fatalf("upperCamelCase(%q) = %q, want %q", tt.input, got, tt.upperCamelCase)
			}
		})
	}
}

func TestLowerCamelCase(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := lowerCamelCase(tt.input); got != tt.lowerCamelCase {
				t.Fatalf("lowerCamelCase(%q) = %q, want %q", tt.input, got, tt.lowerCamelCase)
			}
		})
	}
}

func TestSafeTestConfigID(t *testing.T) {
	for _, tt := range caseTests {
		t.Run(tt.input, func(t *testing.T) {
			if got := safeTestConfigID(tt.input); got != tt.safeTestConfigID {
				t.Fatalf("safeTestConfigID(%q) = %q, want %q", tt.input, got, tt.safeTestConfigID)
			}
		})
	}
}

func TestDict(t *testing.T) {
	errorValue := errors.New("error_value")
	var dictTest = []struct {
		values []any
		want   map[string]any
	}{
		{
			values: []any{1, 2, 3},
			want: map[string]any{
				"1": 2,
				"3": nil,
			},
		},
		{
			values: []any{"foo", "bar"},
			want:   map[string]any{"foo": "bar"},
		},
		{
			values: []any{errors.New("error_key"), errorValue},
			want:   map[string]any{"error_key": errorValue},
		},
	}
	for _, tt := range dictTest {
		got := dict(tt.values...)
		for k, v := range tt.want {
			if got[k] != v {
				t.Fatalf("dict(%v)[%q] = %q, want %q", tt.values, k, got[k], v)
			}
		}
	}
}

func TestHasConfigVariant(t *testing.T) {
	testReq := pb.Requirement_builder{
		Specs: map[int64]*pb.RequirementSpec{
			30: pb.RequirementSpec_builder{
				TestConfigId: proto.String("720p"),
				VariantSpecs: map[string]*pb.VariantSpec{
					"VP9": pb.VariantSpec_builder{}.Build(),
				},
			}.Build(),
			34: pb.RequirementSpec_builder{
				TestConfigId: proto.String("4k"),
				VariantSpecs: make(map[string]*pb.VariantSpec),
			}.Build(),
		},
	}.Build()

	tests := []struct {
		r         *pb.Requirement
		configID  string
		variantID string
		want      bool
	}{
		{
			r:         testReq,
			configID:  "720p",
			variantID: "VP9",
			want:      true,
		},
		{
			r:         testReq,
			configID:  "4k",
			variantID: "VP9",
			want:      false,
		},
	}

	for _, tc := range tests {
		got := HasConfigVariant(tc.r, tc.configID, tc.variantID)
		if got != tc.want {
			t.Errorf("HasConfigVariant(%v, %q, %q) = %v, want: %v", tc.r, tc.configID, tc.variantID, got, tc.want)
		}
	}
}
