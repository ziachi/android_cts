/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.database.cts;

import android.content.Context;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.OpenParams;
import android.test.AndroidTestCase;

import java.io.File;

public class SQLiteCantOpenDatabaseExceptionTest  extends AndroidTestCase {

    private File getDatabaseFile(String name) {
        final Context c = getContext();
        c.deleteDatabase(name);

        // getDatabasePath() doesn't like a filename with a / in it, so we can't use it directly.
        return new File(getContext().getDatabasePath("a").getParentFile(), name);
    }

    private void verifyOpenFailure(File file) {
        try {
            SQLiteDatabase.openDatabase(file, new OpenParams.Builder().build());
            fail("SQLiteCantOpenDatabaseException was not thrown");
        } catch (SQLiteCantOpenDatabaseException e) {
            // Okay - this exception was expected.
        }
    }

    /** DB's directory doesn't exist. */
    public void testDirectoryDoesNotExist() {
        final File file = getDatabaseFile("nonexisitentdir/mydb.db");

        verifyOpenFailure(file);
    }

    /** File doesn't exist */
    public void testFileDoesNotExist() {
        final File file = getDatabaseFile("mydb.db");

        verifyOpenFailure(file);
    }

    /** File exists, but not readable. */
    public void testFileNotReadable() {
        final File file = getDatabaseFile("mydb.db");

        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.close();

        file.setReadable(false);

        verifyOpenFailure(file);
    }

    /** Directory with the given name exists already. */
    public void testPathIsADirectory() {
        final File file = getDatabaseFile("mydb.db");

        file.mkdirs();

        verifyOpenFailure(file);
    }
}
