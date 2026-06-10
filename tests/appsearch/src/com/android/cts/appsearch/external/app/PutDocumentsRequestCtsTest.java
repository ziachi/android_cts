/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.appsearch.cts.app;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.testutil.AppSearchEmail;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Set;

public class PutDocumentsRequestCtsTest {

    @Test
    public void addGenericDocument_byCollection() {
        Set<AppSearchEmail> emails =
                ImmutableSet.of(
                        new AppSearchEmail.Builder("namespace", "test1").build(),
                        new AppSearchEmail.Builder("namespace", "test2").build());
        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder().addGenericDocuments(emails).build();

        assertThat(request.getGenericDocuments().get(0).getId()).isEqualTo("test1");
        assertThat(request.getGenericDocuments().get(1).getId()).isEqualTo("test2");
    }

    @Test
    public void duplicateIdForNormalAndTakenActionGenericDocumentThrowsException()
            throws Exception {
        GenericDocument normalDocument =
                new GenericDocument.Builder<>("namespace", "id", "builtin:Thing").build();
        GenericDocument takenActionGenericDocument =
                new GenericDocument.Builder<>("namespace", "id", "builtin:ClickAction").build();

        PutDocumentsRequest.Builder builder =
                new PutDocumentsRequest.Builder()
                        .addGenericDocuments(normalDocument)
                        .addTakenActionGenericDocuments(takenActionGenericDocument);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> builder.build());
        assertThat(e.getMessage())
                .isEqualTo(
                        "Document id "
                                + takenActionGenericDocument.getId()
                                + " cannot exist in both taken action and normal document");
    }

    @Test
    public void addTakenActionGenericDocuments() throws Exception {
        GenericDocument searchActionGenericDocument1 =
                new GenericDocument.Builder<>("namespace", "search1", "builtin:SearchAction")
                        .build();
        GenericDocument clickActionGenericDocument1 =
                new GenericDocument.Builder<>("namespace", "click1", "builtin:ClickAction").build();
        GenericDocument clickActionGenericDocument2 =
                new GenericDocument.Builder<>("namespace", "click2", "builtin:ClickAction").build();
        GenericDocument impressionActionGenericDocument1 =
                new GenericDocument.Builder<>(
                                "namespace", "impression1", "builtin:ImpressionAction")
                        .build();
        GenericDocument dismissActionGenericDocument1 =
                new GenericDocument.Builder<>("namespace", "dismiss1", "builtin:DismissAction")
                        .build();
        GenericDocument searchActionGenericDocument2 =
                new GenericDocument.Builder<>("namespace", "search2", "builtin:SearchAction")
                        .build();
        GenericDocument clickActionGenericDocument3 =
                new GenericDocument.Builder<>("namespace", "click3", "builtin:ClickAction").build();
        GenericDocument clickActionGenericDocument4 =
                new GenericDocument.Builder<>("namespace", "click4", "builtin:ClickAction").build();
        GenericDocument clickActionGenericDocument5 =
                new GenericDocument.Builder<>("namespace", "click5", "builtin:ClickAction").build();
        GenericDocument impressionActionGenericDocument2 =
                new GenericDocument.Builder<>(
                                "namespace", "impression2", "builtin:ImpressionAction")
                        .build();
        GenericDocument dismissActionGenericDocument2 =
                new GenericDocument.Builder<>("namespace", "dismiss2", "builtin:DismissAction")
                        .build();

        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder()
                        .addTakenActionGenericDocuments(
                                searchActionGenericDocument1,
                                clickActionGenericDocument1,
                                clickActionGenericDocument2,
                                impressionActionGenericDocument1,
                                dismissActionGenericDocument1,
                                searchActionGenericDocument2,
                                clickActionGenericDocument3,
                                clickActionGenericDocument4,
                                clickActionGenericDocument5,
                                impressionActionGenericDocument2,
                                dismissActionGenericDocument2)
                        .build();

        // Generic documents should contain nothing.
        assertThat(request.getGenericDocuments()).isEmpty();

        // Taken action generic documents should contain correct taken action generic documents.
        assertThat(request.getTakenActionGenericDocuments()).hasSize(11);
        assertThat(request.getTakenActionGenericDocuments().get(0).getId()).isEqualTo("search1");
        assertThat(request.getTakenActionGenericDocuments().get(1).getId()).isEqualTo("click1");
        assertThat(request.getTakenActionGenericDocuments().get(2).getId()).isEqualTo("click2");
        assertThat(request.getTakenActionGenericDocuments().get(3).getId())
                .isEqualTo("impression1");
        assertThat(request.getTakenActionGenericDocuments().get(4).getId()).isEqualTo("dismiss1");
        assertThat(request.getTakenActionGenericDocuments().get(5).getId()).isEqualTo("search2");
        assertThat(request.getTakenActionGenericDocuments().get(6).getId()).isEqualTo("click3");
        assertThat(request.getTakenActionGenericDocuments().get(7).getId()).isEqualTo("click4");
        assertThat(request.getTakenActionGenericDocuments().get(8).getId()).isEqualTo("click5");
        assertThat(request.getTakenActionGenericDocuments().get(9).getId())
                .isEqualTo("impression2");
        assertThat(request.getTakenActionGenericDocuments().get(10).getId()).isEqualTo("dismiss2");
    }

    @Test
    public void addTakenActionGenericDocuments_byCollection() throws Exception {
        Set<GenericDocument> takenActionGenericDocuments =
                ImmutableSet.of(
                        new GenericDocument.Builder<>(
                                        "namespace", "search1", "builtin:SearchAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "click1", "builtin:ClickAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "click2", "builtin:ClickAction")
                                .build(),
                        new GenericDocument.Builder<>(
                                        "namespace", "impression1", "builtin:ImpressionAction")
                                .build(),
                        new GenericDocument.Builder<>(
                                        "namespace", "dismiss1", "builtin:DismissAction")
                                .build(),
                        new GenericDocument.Builder<>(
                                        "namespace", "search2", "builtin:SearchAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "click3", "builtin:ClickAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "click4", "builtin:ClickAction")
                                .build(),
                        new GenericDocument.Builder<>("namespace", "click5", "builtin:ClickAction")
                                .build(),
                        new GenericDocument.Builder<>(
                                        "namespace", "impression2", "builtin:ImpressionAction")
                                .build(),
                        new GenericDocument.Builder<>(
                                        "namespace", "dismiss2", "builtin:DismissAction")
                                .build());

        PutDocumentsRequest request =
                new PutDocumentsRequest.Builder()
                        .addTakenActionGenericDocuments(takenActionGenericDocuments)
                        .build();

        // Generic documents should contain nothing.
        assertThat(request.getGenericDocuments()).isEmpty();

        // Taken action generic documents should contain correct taken action generic documents.
        assertThat(request.getTakenActionGenericDocuments()).hasSize(11);
        assertThat(request.getTakenActionGenericDocuments().get(0).getId()).isEqualTo("search1");
        assertThat(request.getTakenActionGenericDocuments().get(1).getId()).isEqualTo("click1");
        assertThat(request.getTakenActionGenericDocuments().get(2).getId()).isEqualTo("click2");
        assertThat(request.getTakenActionGenericDocuments().get(3).getId())
                .isEqualTo("impression1");
        assertThat(request.getTakenActionGenericDocuments().get(4).getId()).isEqualTo("dismiss1");
        assertThat(request.getTakenActionGenericDocuments().get(5).getId()).isEqualTo("search2");
        assertThat(request.getTakenActionGenericDocuments().get(6).getId()).isEqualTo("click3");
        assertThat(request.getTakenActionGenericDocuments().get(7).getId()).isEqualTo("click4");
        assertThat(request.getTakenActionGenericDocuments().get(8).getId()).isEqualTo("click5");
        assertThat(request.getTakenActionGenericDocuments().get(9).getId())
                .isEqualTo("impression2");
        assertThat(request.getTakenActionGenericDocuments().get(10).getId()).isEqualTo("dismiss2");
    }
}
