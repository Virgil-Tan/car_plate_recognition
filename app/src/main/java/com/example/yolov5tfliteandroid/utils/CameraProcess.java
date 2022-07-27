package com.example.yolov5tfliteandroid.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.CameraControl;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.example.yolov5tfliteandroid.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraProcess {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};

    private float zoom_degree = 0;

    /**
     * 判断摄像头权限
     * @param context
     * @return
     */
    public boolean allPermissionsGranted(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 申请摄像头权限
     * @param activity
     */
    public void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    /**
     * 打开摄像头，提供对应的preview, 并且注册analyse事件, analyse就是要对摄像头每一帧进行分析的操作
     */
    public void startCamera(Context context, ImageAnalysis.Analyzer analyzer, PreviewView previewView, Button farther_button, Button closer_button) {

        cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer);

                    Preview previewBuilder = new Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build();

                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                    previewBuilder.setSurfaceProvider(previewView.createSurfaceProvider());

                    // 加多这一步是为了切换不同视图的时候能释放上一视图所有绑定事件
                    cameraProvider.unbindAll();

                    //这里拿到camera对象可以取出两个重要的对象来用
                    Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) context,
                            cameraSelector, imageAnalysis, previewBuilder);

                    //用来调焦操作
                    CameraControl mCameraControl = camera.getCameraControl();

                    Button Farther_button = farther_button.findViewById(R.id.Farther_button);
                    Farther_button.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            zoom_degree = zoom_degree-0.1f;
                            if(zoom_degree<0){
                                zoom_degree = 0;
                            }
                            mCameraControl.setLinearZoom(zoom_degree);
                        }
                    });

                    Button Closer_button = closer_button.findViewById(R.id.Closer_button);
                    Closer_button.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            zoom_degree = zoom_degree+0.1f;
                            if(zoom_degree>1){
                                zoom_degree = 1;
                            }
                            mCameraControl.setLinearZoom(zoom_degree);
                        }
                    });

                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }


    /**
     * 打印输出摄像头支持的宽和高
     * @param activity
     */
    public void showCameraSupportSize(Activity activity) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics cc = manager.getCameraCharacteristics(id);
                if (cc.get(CameraCharacteristics.LENS_FACING) == 1) {
                    Size[] previewSizes = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(SurfaceTexture.class);
                    for (Size s : previewSizes){
                        Log.i("camera", s.getHeight()+"/"+s.getWidth());
                    }
                    break;

                }
            }
        } catch (Exception e) {
            Log.e("image", "can not open camera", e);
        }
    }

}
