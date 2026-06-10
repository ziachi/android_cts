package com.android.cts.verifier.nfc.hce;
public final class HceUtils {
    public static final String TRANSACTION_EVENT_AID = "A000000476416E64726F696443545341";
    public static final String HCI_CMD = "0025000000";


    public static String getHexBytes(String header, byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (header != null) {
            sb.append(header + ": ");
        }
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    public static byte[] hexStringToBytes(String s) {
        if (s == null || s.length() == 0) return null;
        int len = s.length();
        if (len % 2 != 0) {
            s = '0' + s;
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte)
                            ((Character.digit(s.charAt(i), 16) << 4)
                                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static final CommandApdu buildCommandApdu(String apdu, boolean reachable) {
        return new CommandApdu(apdu, reachable);
    }

    public static final CommandApdu buildSelectApdu(String aid, boolean reachable) {
        StringBuilder sb = new StringBuilder();
        sb.append("00A40400");
        sb.append(String.format("%02X", aid.length() / 2));
        sb.append(aid);
        return new CommandApdu(sb.toString(), reachable);
    }
}
