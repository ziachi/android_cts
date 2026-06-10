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

// Package templatefns contains functions that are made available in genreqsrc templates.
package templatefns

import (
	"fmt"
	"strings"
	"text/template"
	"unicode"

	pb "cts/test/mediapc/requirements/requirements_go_proto"
)

// Funcs returns a mapping from names of template helper functions to the
// functions themselves.
func Funcs() template.FuncMap {
	// These function are made available in templates by calling their key values, e.g. {{SnakeCase "HelloWorld"}}.
	return template.FuncMap{
		// go/keep-sorted start
		"Dict":             dict,
		"HasConfigVariant": HasConfigVariant,
		"KebabCase":        kebabCase,
		"LowerCamelCase":   lowerCamelCase,
		"LowerCase":        strings.ToLower,
		"SafeReqID":        safeReqID,
		"SafeTestConfigID": safeTestConfigID,
		"SnakeCase":        snakeCase,
		"TitleCase":        titleCase,
		"UpperCamelCase":   upperCamelCase,
		"UpperCase":        strings.ToUpper,
		// go/keep-sorted end
	}
}

// isDelimiter checks if a rune is some kind of whitespace, '_' or '-'.
// helper function
func isDelimiter(r rune) bool {
	return r == '-' || r == '_' || unicode.IsSpace(r)
}

func shouldWriteDelimiter(r, prev, next rune) bool {
	if isDelimiter(prev) {
		// Don't delimit if we just delimited
		return false
	}
	// Delimit before new uppercase letters and after acronyms
	caseDelimit := unicode.IsUpper(r) && (unicode.IsLower(prev) || unicode.IsLower(next))
	// Delimit after digits
	digitDelimit := !unicode.IsDigit(r) && unicode.IsDigit(prev)
	return isDelimiter(r) || caseDelimit || digitDelimit
}

// titleCase converts a string into Title Case.
func titleCase(s string) string {
	runes := []rune(s)
	var out strings.Builder
	for i, r := range runes {
		prev, next := ' ', ' '
		if i > 0 {
			prev = runes[i-1]
			if i+1 < len(runes) {
				next = runes[i+1]
			}
		}

		if shouldWriteDelimiter(r, prev, next) {
			out.WriteRune(' ')
		}

		if !isDelimiter(r) {
			// Output all non-delimiters unchanged
			out.WriteRune(r)
		}
	}
	return strings.Title(out.String())
}

// snakeCase converts a string into snake_case.
func snakeCase(s string) string {
	return strings.ReplaceAll(strings.ToLower(titleCase(s)), " ", "_")
}

// kebabCase converts a string into kebab-case.
func kebabCase(s string) string {
	return strings.ReplaceAll(strings.ToLower(titleCase(s)), " ", "-")
}

// upperCamelCase converts a string into UpperCamelCase.
func upperCamelCase(s string) string {
	return strings.ReplaceAll(titleCase(s), " ", "")
}

// lowerCamelCase converts a string into lowerCamelCase.
func lowerCamelCase(s string) string {
	if len(s) < 2 {
		return strings.ToLower(s)
	}
	runes := []rune(upperCamelCase(s))
	for i, r := range runes {
		// Lowercase leading acronyms
		if i > 1 && unicode.IsLower(r) {
			return strings.ToLower(string(runes[:i-1])) + string(runes[i-1:])
		}
	}
	return string(unicode.ToLower(runes[0])) + string(runes[1:])
}

// safeReqID converts a Media Performance Class (MPC) requirement id to a variable name safe string.
func safeReqID(s string) string {
	f := func(a, b, c string) string {
		return strings.Replace(a, b, c, -1)
	}
	return "r" + strings.ToLower(f(f(f(s, "/", "__"), ".", "_"), "-", "_"))
}

// safeTestConfigID converts a group name to a variable name safe string to append onto a requirement id.
func safeTestConfigID(s string) string {
	if s == "" {
		return ""
	}
	return "__" + snakeCase(s)
}

// dict converts a list of key-value pairs into a map.
// If there is an odd number of values, the last value is nil.
// The last key is preserved so in the template it can be referenced like {{$myDict.key}}.
func dict(v ...any) map[string]any {
	dict := map[string]any{}
	lenv := len(v)
	for i := 0; i < lenv; i += 2 {
		key := toString(v[i])
		if i+1 >= lenv {
			dict[key] = nil
			continue
		}
		dict[key] = v[i+1]
	}
	return dict
}

// HasConfigVariant checks if a requirement has a spec for a given test config and variant.
func HasConfigVariant(r *pb.Requirement, configID string, variantID string) bool {
	for _, spec := range r.GetSpecs() {
		if configID == spec.GetTestConfigId() {
			_, ok := spec.GetVariantSpecs()[variantID]
			if ok {
				return true
			}
		}
	}
	return false
}

// toString converts a value to a string.
func toString(v any) string {
	switch v := v.(type) {
	case string:
		return v
	case []byte:
		return string(v)
	case error:
		return v.Error()
	case fmt.Stringer:
		return v.String()
	default:
		return fmt.Sprintf("%v", v)
	}
}
