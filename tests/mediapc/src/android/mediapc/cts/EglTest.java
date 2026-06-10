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

package android.mediapc.cts;

import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.EGLRequirement;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Verify <a href="https://www.khronos.org/egl">Embedded-System Graphics Library (EGL)</a>
 * MPC requirements.
 */
@RunWith(AndroidJUnit4.class)
public class EglTest {

    @Rule
    public final TestName mTestName = new TestName();

    /**
     * <b>7.1.4.1/H-1-2</b> MUST support the {@code EGL_IMG_context_priority} and
     * {@code EGL_EXT_protected_content}
     * extensions.
     */
    @CddTest(requirements = {"7.1.4.1/H-1-2"})
    @Test
    public void requireGraphicsProtectedContent() {
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        EGLRequirement req = Requirements.addR7_1_4_1__H_1_2().to(pce);

        var extensions = getExtensions();
        req.setEglImgContextPriority(extensions.contains("EGL_IMG_context_priority"));
        req.setEglExtProtectedContent(extensions.contains("EGL_EXT_protected_content"));

        pce.submitAndCheck();
    }

    private ImmutableList<String> getExtensions() {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (egl.eglInitialize(display, null)) {
            try {
                String eglExtensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
                return ImmutableList.copyOf(eglExtensions.split(" "));
            } finally {
                egl.eglTerminate(display);
            }
        } else {
            return ImmutableList.of();
        }
    }
}
