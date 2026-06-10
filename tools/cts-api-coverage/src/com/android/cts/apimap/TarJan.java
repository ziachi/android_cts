/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.cts.apimap;

import com.android.cts.ctsprofiles.MethodProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/** A class to implement Tarjan's strongly connected components algorithm. */
public final class TarJan {

    private int mTime = 0;
    private int mNewNodeIndex = 0;
    private final Map<String, Node> mVertices = new HashMap<>();
    private final Stack<Node> mStack = new Stack<>();
    private final Set<String> mResolvedMethods;
    private final Map<String, Integer> mComponentIDs = new HashMap<>();
    private final Map<Integer, List<MethodProfile>> mComponentNodes = new HashMap<>();

    private static final class Node {
        final int mDfn;
        final MethodProfile mMethod;
        int mLow;
        boolean mInStack;

        Node(int index, MethodProfile method) {
            mDfn = index;
            mMethod = method;
        }
    }

    public TarJan(MethodProfile testMethod, Set<String> resolvedMethods) {
        mResolvedMethods = resolvedMethods;
        tarjan(testMethod);
    }

    /** Gets the strongly connected component the given method belongs to. */
    public int getComponentID(String methodSignature) {
        return mComponentIDs.get(methodSignature);
    }

    /** Gets a list methods belong to the given strongly connected component. */
    public List<MethodProfile> getComponent(int id) {
        return mComponentNodes.get(id);
    }

    private Node tarjan(MethodProfile method) {
        Node v = new Node(mTime++, method);
        v.mLow = v.mDfn;
        String methodSignature = method.getMethodSignatureWithClass();
        mVertices.put(methodSignature, v);
        mStack.add(v);
        v.mInStack = true;

        if (!mResolvedMethods.contains(methodSignature)) {
            for (MethodProfile callee: method.getCommonMethodCalls().values()) {
                String calleeSignature = callee.getMethodSignatureWithClass();
                Node w = mVertices.get(calleeSignature);
                if (w == null) {
                    w = tarjan(callee);
                    v.mLow = Math.min(v.mLow, w.mLow);
                } else if (w.mInStack) {
                    v.mLow = Math.min(v.mLow, w.mDfn);
                }
            }
        }

        if (v.mLow == v.mDfn) {
            Node w;
            mNewNodeIndex++;
            mComponentNodes.put(mNewNodeIndex, new ArrayList<>());
            do {
                w = mStack.pop();
                mComponentIDs.put(w.mMethod.getMethodSignatureWithClass(), mNewNodeIndex);
                mComponentNodes.get(mNewNodeIndex).add(w.mMethod);
                w.mInStack = false;
            } while (!w.equals(v));
        }
        return v;
    }
}
