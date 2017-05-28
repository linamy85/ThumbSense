package com.example.simon.androidweardatalayer;

import android.util.Log;
import java.nio.ByteBuffer;
import android.support.annotation.Nullable;

/**
 * Created by Amy Lin on 2017/5/22.
 */

public class SyncQueueBuffer {
    private ByteBuffer buffer;
    public int remained = 0;

    public static final int COUNT = 32;
    private static final int FLOAT = Float.SIZE / 8;
    private static final int LONG = Long.SIZE / 8;
    public static final int DATASIZE = FLOAT * 3 + LONG * 1;
    public static final int BUFSIZE = COUNT * DATASIZE;


    public SyncQueueBuffer(int scale) {
        buffer = ByteBuffer.allocate(BUFSIZE * scale);
    }

    public synchronized void putData(long ts, float value[]) {
        buffer.putLong(ts);
        for (float val : value) {
            buffer.putFloat(val);
        }
        remained ++;
    }

    public synchronized @Nullable byte[] getData(int threshold) {
        if (remained == 0 || remained < threshold) {
            return null;
        } else if (threshold == -1) {
            threshold = remained;
        }

        byte[] result = new byte[threshold * DATASIZE];
        buffer.flip();
        //Log.v("Queue", "Get data remaining:" + buffer.remaining());
        buffer.get(result, 0, result.length);
        buffer.compact();
        remained -= threshold;

        return result;
    }
}
