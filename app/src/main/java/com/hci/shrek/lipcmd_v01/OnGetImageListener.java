package com.hci.shrek.lipcmd_v01;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.media.Image.Plane;
import android.widget.ImageView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

public class OnGetImageListener implements ImageReader.OnImageAvailableListener {
    private static final String mTAG = "LIP";

    private Activity mainActivity;
    private ImageView imageView;

    private Long lastTimeStamp;
    private Long currentTimeStamp;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mRGBframeBitmap_rotate = null;
    private Bitmap mBlankBitmap = null;
    private Matrix matrix = null;
    private byte[][] mYUVBytes;

    public static boolean hasFace = false;
    private static final float H_PAD_RATIO = 0.15f;
    private double lip_bounding_width = 0;
    private double lip_direction_x = 0, lip_direction_y = 0;
    private double down_direction_x = 0, down_direction_y = 0;
    private double lip_bounding_center_x = 0, lip_bounding_center_y = 0;
    private double mouth_crop_leftup_x = 0, mouth_crop_leftup_y = 0;
    private double mouth_crop_width = 0;
    private double mouth_crop_height = 0;
    public static final int norm_t = 70;
    public static final int norm_width = 100;
    public static final int norm_height = 70;

    public static boolean firstTrigger = false;
    public static boolean firstNotTrigger = false;
    private FaceDet mFaceDet;
    List<VisionDetRet> mFaceRects;
    ArrayList<Point> landmarks;
    private Paint paint;

    private Handler mHandler;

    private int[][][][] images;
    private int image_n = 0;
    private String dataDir;
    private boolean saveData = true;
    private BufferedWriter bw;

    public void initialize(final Activity mainActivity,final ImageView imageView, final Handler handler)
    {
        this.mainActivity = mainActivity;
        this.imageView = imageView;
        this.mHandler = handler;

        matrix = new Matrix();
        matrix.postRotate(-90);
        matrix.postScale(-1.0f, 1.0f);

        mFaceDet = new FaceDet( Constants.getFaceShapeModelPath());

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(61, 255, 61));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);

        images = new int[norm_t][norm_height][norm_width][3];
        dataDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "dlib/data/";
    }

    public void deInitialize() {
        if (mFaceDet != null) {
            mFaceDet.release();
        }
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return;
        }
        final Plane[] planes = image.getPlanes();
        // Initialize the storage bitmaps once when the resolution is known.
        if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
            mPreviewWidth = image.getWidth();
            mPreviewHeight = image.getHeight();

            mRGBBytes = new int[mPreviewWidth * mPreviewHeight];
            mRGBframeBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888);

            mYUVBytes = new byte[planes.length][];
            for (int i = 0; i < planes.length; ++i) {
                mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
            }
        }

        for (int i = 0; i < planes.length; ++i) {
            planes[i].getBuffer().get(mYUVBytes[i]);
        }

        final int yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();


        if(MainActivity.triggering == true)
        {
            if(OnGetImageListener.firstTrigger == true)  // first click down
            {
                lastTimeStamp = System.currentTimeMillis();
                OnGetImageListener.firstTrigger = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (OnGetImageListener.this)
                        {
                            ImageUtils.convertYUV420ToARGB8888(
                                    mYUVBytes[0],
                                    mYUVBytes[1],
                                    mYUVBytes[2],
                                    mRGBBytes,
                                    mPreviewWidth,
                                    mPreviewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    false);

                            mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);
                            mRGBframeBitmap_rotate = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, true);

                            mFaceRects = mFaceDet.detect(mRGBframeBitmap_rotate);

                            if(mFaceRects.size() > 0)
                            {
                                OnGetImageListener.hasFace = true;
                                image_n = 0;
                                final VisionDetRet rect = mFaceRects.get(0);
                                landmarks = rect.getFaceLandmarks();


                                lip_bounding_width = Math.hypot(landmarks.get(48).x-landmarks.get(54).x, landmarks.get(48).y-landmarks.get(54).y);
                                lip_direction_x = (landmarks.get(54).x-landmarks.get(48).x)/lip_bounding_width;
                                lip_direction_y = (landmarks.get(54).y-landmarks.get(48).y)/lip_bounding_width;
                                lip_bounding_center_x = (landmarks.get(48).x + landmarks.get(54).x)/2.0;
                                lip_bounding_center_y = (landmarks.get(50).y + landmarks.get(51).y + landmarks.get(52).y +
                                                        landmarks.get(56).y + landmarks.get(57).y + landmarks.get(58).y)/6.0;

                                double ba = -lip_direction_y/lip_direction_x;
                                double downd = Math.hypot(ba, 1);
                                down_direction_x = ba/downd;
                                down_direction_y = 1/downd;

                                mouth_crop_width = lip_bounding_width*(1+2*H_PAD_RATIO);
                                mouth_crop_height = mouth_crop_width*0.75;
                                mouth_crop_leftup_x = lip_bounding_center_x-lip_direction_x*(0.5+H_PAD_RATIO)*lip_bounding_width-down_direction_x*mouth_crop_height*0.4;
                                mouth_crop_leftup_y = lip_bounding_center_y-lip_direction_y*(0.5+H_PAD_RATIO)*lip_bounding_width-down_direction_y*mouth_crop_height*0.4;

//                                Log.v(mTAG, mouth_crop_left + " " + mouth_crop_right + " " + mouth_crop_top + " " + mouth_crop_bottom);

                                mBlankBitmap = Bitmap.createBitmap(mPreviewHeight, mPreviewWidth, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(mBlankBitmap);

                                canvas.drawLine(
                                        (float)(mouth_crop_leftup_x),
                                        (float)(mouth_crop_leftup_y),
                                        (float)(mouth_crop_leftup_x + lip_direction_x*mouth_crop_width),
                                        (float)(mouth_crop_leftup_y + lip_direction_y*mouth_crop_width), paint);
                                canvas.drawLine(
                                        (float)(mouth_crop_leftup_x),
                                        (float)(mouth_crop_leftup_y),
                                        (float)(mouth_crop_leftup_x + down_direction_x*mouth_crop_height),
                                        (float)(mouth_crop_leftup_y + down_direction_y*mouth_crop_height), paint);
                                canvas.drawLine(
                                        (float)(mouth_crop_leftup_x + lip_direction_x*mouth_crop_width),
                                        (float)(mouth_crop_leftup_y + lip_direction_y*mouth_crop_width),
                                        (float)(mouth_crop_leftup_x + lip_direction_x*mouth_crop_width + down_direction_x*mouth_crop_height),
                                        (float)(mouth_crop_leftup_y + lip_direction_y*mouth_crop_width + down_direction_y*mouth_crop_height), paint);
                                canvas.drawLine(
                                        (float)(mouth_crop_leftup_x + down_direction_x*mouth_crop_height),
                                        (float)(mouth_crop_leftup_y + down_direction_y*mouth_crop_height),
                                        (float)(mouth_crop_leftup_x + lip_direction_x*mouth_crop_width + down_direction_x*mouth_crop_height),
                                        (float)(mouth_crop_leftup_y + lip_direction_y*mouth_crop_width + down_direction_y*mouth_crop_height), paint);
//                                for (int i = 48; i < 68; i++)
//                                {
//                                    canvas.drawCircle(landmarks.get(i).x, landmarks.get(i).y, 1, paint);
//                                }
                                mainActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        imageView.setImageBitmap(mBlankBitmap);
                                    }
                                });
                            }
                        }

                        lastTimeStamp = System.currentTimeMillis();
                    }
                });
            }
            else if(OnGetImageListener.hasFace == true) // finger still on button, should capture and push_back mouch image
            {
//                mHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        ImageUtils.convertYUV420ToARGB8888(
//                                mYUVBytes[0],
//                                mYUVBytes[1],
//                                mYUVBytes[2],
//                                mRGBBytes,
//                                mPreviewWdith,
//                                mPreviewHeight,
//                                yRowStride,
//                                uvRowStride,
//                                uvPixelStride,
//                                false);
//
//                        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
//                        mRGBframeBitmap_rotate = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWdith, mPreviewHeight, matrix, true);
//
//                    }
//                });
                ImageUtils.convertYUV420ToARGB8888(
                        mYUVBytes[0],
                        mYUVBytes[1],
                        mYUVBytes[2],
                        mRGBBytes,
                        mPreviewWidth,
                        mPreviewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        false);

                mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);
                mRGBframeBitmap_rotate = Bitmap.createBitmap(mRGBframeBitmap, 0, 0, mPreviewWidth, mPreviewHeight, matrix, true);

                if(image_n < norm_t)
                {
                    double h_ratio, w_ratio, f1, f2, f3, f4, x1, x2, y1, y2, r1, r2, g1, g2, b1, b2;
                    int i11, i21, i12, i22, q11r, q11g, q11b, q12r, q12g, q12b, q21r, q21g, q21b, q22r, q22g, q22b;
                    for(int h = 0; h < norm_height; h++)
                    {
                        h_ratio = h/(double)(norm_height-1);
                        for(int w = 0; w < norm_width; w++)
                        {
                            w_ratio = w/(double)(norm_width-1);
                            double y = -(mouth_crop_leftup_x + lip_direction_x * mouth_crop_width * w_ratio + down_direction_x * mouth_crop_height * h_ratio) + mPreviewHeight-1;
                            double x = -(mouth_crop_leftup_y + lip_direction_y * mouth_crop_width * w_ratio + down_direction_y * mouth_crop_height * h_ratio) + mPreviewWidth-1;
                            x1 = Math.floor(x);
                            x2 = Math.ceil(x);
                            y1 = Math.floor(y);
                            y2 = Math.ceil(y);
                            if(x1 == x2) {
                                f1 = 1; f2 = 0;
                            }
                            else {
                                f1 = (x2 - x)/(x2 - x1);
                                f2 = (x - x1)/(x2 - x1);
                            }

                            if(y1 == y2) {
                                f3 = 1; f4 = 0;
                            }
                            else {
                                f3 = (y2-y)/(y2-y1);
                                f4 = (y-y1)/(y2-y1);
                            }
                            i11 = (int)(y1*mPreviewWidth+x1);
                            i21 = (int)(y1*mPreviewWidth+x2);
                            i12 = (int)(y2*mPreviewWidth+x1);
                            i22 = (int)(y2*mPreviewWidth+x2);

                            q11r = (mRGBBytes[i11] & 0x00ff0000) >> 16;
                            q11g = (mRGBBytes[i11] & 0x0000ff00) >> 8;
                            q11b = (mRGBBytes[i11] & 0x000000ff);

                            q12r = (mRGBBytes[i12] & 0x00ff0000) >> 16;
                            q12g = (mRGBBytes[i12] & 0x0000ff00) >> 8;
                            q12b = (mRGBBytes[i12] & 0x000000ff);

                            q21r = (mRGBBytes[i21] & 0x00ff0000) >> 16;
                            q21g = (mRGBBytes[i21] & 0x0000ff00) >> 8;
                            q21b = (mRGBBytes[i21] & 0x000000ff);

                            q22r = (mRGBBytes[i22] & 0x00ff0000) >> 16;
                            q22g = (mRGBBytes[i22] & 0x0000ff00) >> 8;
                            q22b = (mRGBBytes[i22] & 0x000000ff);

                            r1 = f1*q11r + f2*q21r;
                            g1 = f1*q11g + f2*q21g;
                            b1 = f1*q11b + f2*q21b;

                            r2 = f1*q12r + f2*q22r;
                            g2 = f1*q12g + f2*q22g;
                            b2 = f1*q12b + f2*q22b;

                            images[image_n][h][w][0] = (int)(f3*r1 + f4*r2);
                            images[image_n][h][w][1] = (int)(f3*g1 + f4*g2);
                            images[image_n][h][w][2] = (int)(f3*b1 + f4*b2);

//                            if(image_n == 1 && h == 0 && w == 0)
//                            {
//                                Log.v(mTAG, "bitmap: " + mouth_crop_leftup_x + " " + mouth_crop_leftup_y + " " + mouth_crop_width + " " + mouth_crop_height);
//                                Log.v(mTAG, "origin xy: " + x + " " + y);
//                                Log.v(mTAG, "f: " + f1 + " " + f2 + " " + f3 + " " + f4);
//                                Log.v(mTAG, "q11: " + mRGBBytes[i11] + " " + q11r + " " + q11g + " " + q11b);
//                            }
                        }//for w
                    }//for h
                }

                image_n ++;
            }
        }
        else // MainActivity.triggering == false
        {
            if(OnGetImageListener.firstNotTrigger == true && OnGetImageListener.hasFace == true) //lift up finger, should do lip command recognize
            {
                OnGetImageListener.firstNotTrigger = false;
                OnGetImageListener.hasFace = false;
                currentTimeStamp = System.currentTimeMillis();
                float fps = 1000f * image_n/(currentTimeStamp-lastTimeStamp);
                Log.v(mTAG, "fps: " + fps + " " + image_n);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for(int i = image_n; i < norm_t; i++)
                        {
                            for(int h = 0; h < norm_height; h++)
                            {
                                for (int w = 0; w < norm_width; w++)
                                {
                                    Arrays.fill(images[i][h][w], 0);
                                }
                            } // for h
                        } // for i
                        if(saveData) {
                            try {
                                bw = new BufferedWriter(new FileWriter(dataDir + currentTimeStamp + ".txt"));
                                for (int i = 0; i < norm_t; i++) {
                                    for (int h = 0; h < norm_height; h++) {
                                        for (int w = 0; w < norm_width; w++) {
                                            bw.write(images[i][h][w][0] + "," + images[i][h][w][1] + "," + images[i][h][w][2] + " ");
                                        }
                                        bw.write("\n");
                                    } // for h
                                    bw.write("\n\n");
                                } // for i
                                bw.flush();
                                bw.close();
                                Log.v(mTAG, "file is saved");
                            } catch (IOException e) {

                            }//catch
                        }
                    }
                }); // mHandler
            }
        }

        image.close();
    }
}
