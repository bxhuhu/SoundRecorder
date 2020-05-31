package com.cree.soundrecorder.util;

import android.util.Log;

/**
 * Title:
 * Description:
 * CreateTime:2020/5/30  10:25
 *
 * @author cree
 * @version 1.0
 */
public class LogUtil {
    private static final String TAG = "cree";

    public static void d(String str) {
        Log.d(TAG, str);
    }


    public static void e(String str) {
        Log.e(TAG, str);
    }

    public static void e(Object o) {
        Log.e(TAG, o.toString()+"");
    }
}
