package com.hci.shrek.lipcmd_v01;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String mTAG = "LIP";
    private static final int REQUEST_CODE_PERMISSION = 2;
    private static final int REQUEST_CODE_PERMISSION_ONLYCAMERA = 3;
    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    public static boolean triggering = false;

    private TextureView textureView;
    private TextView infoView;
    private ImageView imageView;
    private Button triggerButton;

    private String cameraId;
    private CameraDevice cameraDevice;
    private Size previewSize; // Attention: width 640, height 480

    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private final OnGetImageListener onGetImageListener = new OnGetImageListener();

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     * A Handler for running tasks in the background.
     */
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    /**
     * An additional thread for running inference so as not to block the camera.
     */
    private HandlerThread inferenceThread;
    private Handler inferenceHandler;

    // handles several lifecycle events on a TextureView
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            OpenCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            ConfigureTransform(i, i1);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    // CameraDevice.StateCallback is called when CameraDevice changes its state.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cd) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraDevice = cd;
            CreateCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cd) {
            cd.close();
            cameraDevice = null;

            if (onGetImageListener != null) {
                onGetImageListener.deInitialize();
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cd, int i) {
            cd.close();
            cameraDevice = null;

            if (onGetImageListener != null) {
                onGetImageListener.deInitialize();
            }
        }
    };

    private Paint paint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (verifyPermissions(this))
        {
            Initialize();
        }
    }

    private void Initialize()
    {
        infoView = (TextView) findViewById(R.id.infoTextView);
        textureView = (TextureView) findViewById(R.id.textureView);
        imageView = (ImageView) findViewById(R.id.imageView);
        triggerButton = (Button) findViewById(R.id.triggerBtn);

        triggerButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        MainActivity.triggering = true;
                        OnGetImageListener.firstTrigger = true;
                        Log.v(mTAG, "finger down");
                        break;

                    case MotionEvent.ACTION_UP:
                        MainActivity.triggering = false;
                        OnGetImageListener.firstNotTrigger = true;
                        Log.v(mTAG, "finger down");
                        break;
                }
                return false;
            }
        });
    }

    // Opens the camera
    private void OpenCamera()
    {
        previewSize = new Size(640, 480);
        ConfigureTransform(textureView.getWidth(), textureView.getHeight());

        // get Camera ID of the front-facing camera
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : manager.getCameraIdList())
            {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    this.cameraId = cameraId;
                    if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==PackageManager.PERMISSION_GRANTED)
                    {
                        manager.openCamera(this.cameraId, stateCallback, backgroundHandler);
                        Log.v(mTAG, "open camera");
                    }
                    else
                    {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSION_ONLYCAMERA);
                    }
                    return;
                }
            }
        }catch (final CameraAccessException e) {
            Log.v(mTAG, "Exception! "+e);
        } catch (final NullPointerException e) {
            Log.v(mTAG, "Error! "+e);
        }
    }

    // Close the camera
    private void CloseCamera()
    {
        if (null != captureSession) {
            captureSession.close();
            captureSession = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
        if(null != onGetImageListener){
            onGetImageListener.deInitialize();
        }
    }

    /**
     * Creates a new CameraCaptureSession for camera preview.
     */
    private void CreateCameraPreviewSession()
    {
        onGetImageListener.initialize(this, imageView, inferenceHandler);

        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        // textureViewSurface: for preview on textureView
        Surface textureViewSurface = new Surface(texture);

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequestBuilder.addTarget(textureViewSurface);

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(onGetImageListener, backgroundHandler);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            List<Surface> surfaceList = Arrays.asList(textureViewSurface, imageReader.getSurface());

            cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    captureSession = session;
                    try {
                        captureRequestBuilder.set( CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                        captureRequestBuilder.set( CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        // Finally, we start displaying the camera preview.
                        captureRequest = captureRequestBuilder.build();
                        captureSession.setRepeatingRequest(captureRequest, null, backgroundHandler);
                    } catch (final CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.v(mTAG, "onConfigureFailed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void ConfigureTransform(final int viewWidth, final int viewHeight) {
        if (null == textureView || null == previewSize || null == this) {
            return;
        }
        final int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    // Check member variables related to camera.
    private void CheckCameraOutputSize() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : manager.getCameraIdList())
            {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    Log.v(mTAG, "cameraID: " + cameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizeChoices  = map.getOutputSizes(SurfaceTexture.class);

                    // choose the proper camera size
                    for(Size size : sizeChoices)
                    {
                        Log.v(mTAG, size.getWidth() + " " + size.getHeight());
                    }
                    this.cameraId = cameraId;
                    return;
                }
            }

        }catch (final CameraAccessException e) {
            Log.v(mTAG, "Exception! "+e);
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.v(mTAG, "Error! "+e);
        }
    }

    /**
     * Starts a background thread and its Handler.
     */
    private void StartBackgroundThread()
    {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    /**
     * Stops a background thread and its Handler.
     */
    private void StopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceThread = null;

        } catch (final InterruptedException e) {
            Log.v(mTAG, "error: " + e);
        }
    }

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_persmission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_persmission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Initialize();
        }
    }

    /**
     * Restarts the function.
     */
    @Override
    protected void onResume() {
        super.onResume();
        StartBackgroundThread();

        if(textureView.isAvailable())
        {
            OpenCamera();
        }
        else
        {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    /**
     * Stops the function.
     */
    @Override
    protected void onPause() {
        CloseCamera();
        StopBackgroundThread();
        super.onPause();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
