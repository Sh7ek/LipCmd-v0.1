package com.hci.shrek.lipcmd_v01;

import android.util.Log;

/**
 * Utility class for manipulating images.
 **/

public class ImageUtils {
    private static final String mTAG = "LIP";

    static {
        try {
            System.loadLibrary("image_utils");
            Log.v(mTAG, "load image_utils lib success");
        } catch (UnsatisfiedLinkError e) {
            Log.v(mTAG, "Native library not found, native RGB -> YUV conversion may be unavailable.");
        }
    }

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width
     * and height. The input and output must already be allocated and non-null.
     * For efficiency, no error checking is performed.
     *
     * @param y
     * @param u
     * @param v
     * @param uvPixelStride
     * @param width The width of the input image.
     * @param height The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     * @param output A pre-allocated array for the ARGB 8:8:8:8 output data.
     */
    public static native void convertYUV420ToARGB8888(
            byte[] y,
            byte[] u,
            byte[] v,
            int[] output,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            boolean halfSize);

    public static native String stringFromJNI();
}
