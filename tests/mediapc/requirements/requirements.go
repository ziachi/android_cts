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

// Package requirements generates src  using templates and Media Performance Class (MPC)
// requirements data.
package requirements

import (
	"io"
	"text/template"

	"cts/test/mediapc/requirements/templatefns"
	"google.golang.org/protobuf/proto"

	pb "cts/test/mediapc/requirements/requirements_go_proto"
)

// Gensrc generates source from a template using a list of MPC requirements.
func Gensrc(tmplString string, reqList *pb.RequirementList, w io.Writer) error {
	type Top struct {
		ReqList *pb.RequirementList
	}
	top := Top{ReqList: reqList}
	tmpl, err := template.New("gensrc").Funcs(templatefns.Funcs()).Parse(tmplString)
	if err != nil {
		return err
	}
	err = tmpl.Execute(w, top)
	if err != nil {
		return err
	}
	return nil
}

// UnmarshalRequirementList unmarshals MPC requirements data.
func UnmarshalRequirementList(reqBinary []byte) (*pb.RequirementList, error) {
	req := &pb.RequirementList{}
	err := proto.Unmarshal(reqBinary, req)
	return req, err
}
