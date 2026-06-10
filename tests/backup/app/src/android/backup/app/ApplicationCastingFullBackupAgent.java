/*
 * Copyright 2024 The Android Open Source Project
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

package android.backup.app;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Full Backup agent that tries to cast its {@link android.app.Application} object during backup.
 *
 * <p>This is used to check whether the app is in restricted mode during backup since casting the
 * application will throw an exception and fail the backup when in restricted mode.
 */
public class ApplicationCastingFullBackupAgent extends BackupAgent {
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        throw new IllegalStateException("unexpected onBackup");
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        throw new IllegalStateException("unexpected onRestore");
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        CustomApplication customApplication = (CustomApplication) getApplicationContext();

        // Write a file so that the backup doesn't get rejected for being empty.
        try (FileWriter fileWriter = new FileWriter(new File(getFilesDir(), "testfile"))) {
            fileWriter.write("meow");
        }

        super.onFullBackup(data);
    }
}
