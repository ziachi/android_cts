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

package android.text.style.cts

import android.os.Parcel
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.NoWritingToolsSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NoWritingToolsSpanTest {

    @Test
    fun testNoWritingToolsSpan_parcelable() {
        val start = 5
        val end = 7
        val text = SpannableString("abcdefghijklmnop").apply {
            setSpan(
                NoWritingToolsSpan(),
                start,
                end,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
        }

        val parceledText = parcelUnparcelText(text)

        require(parceledText is Spanned)
        val spans =
            requireNotNull(parceledText.getSpans(start, end, NoWritingToolsSpan::class.java))
        assertThat(spans.size).isEqualTo(1)
        val span = spans[0]
        assertThat(span is NoWritingToolsSpan).isTrue()
        assertThat(span.spanTypeId).isEqualTo(NoWritingToolsSpan().spanTypeId)
    }

    private fun parcelUnparcelText(text: CharSequence): CharSequence {
        val inParcel = Parcel.obtain()
        val outParcel = Parcel.obtain()
        try {
            TextUtils.writeToParcel(text, inParcel, 0)
            val marshalled = inParcel.marshall()
            outParcel.unmarshall(marshalled, 0, marshalled.size)
            outParcel.setDataPosition(0)
            return TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(outParcel)
        } finally {
            outParcel.recycle()
            inParcel.recycle()
        }
    }
}
