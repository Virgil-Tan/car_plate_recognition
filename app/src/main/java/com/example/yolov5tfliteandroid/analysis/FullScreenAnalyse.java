package com.example.yolov5tfliteandroid.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.example.yolov5tfliteandroid.detector.Yolov5TFLiteDetector;
import com.example.yolov5tfliteandroid.utils.ImageProcess;
import com.example.yolov5tfliteandroid.utils.Recognition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.tensorflow.lite.support.image.TensorImage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;



public class FullScreenAnalyse implements ImageAnalysis.Analyzer {

    public static class Result{

        public Result(long costTime, Bitmap bitmap) {
            this.costTime = costTime;
            this.bitmap = bitmap;
        }
        long costTime;
        Bitmap bitmap;
    }

    String plate_num=null;

    Context FScontect;
    ImageView boxLabelCanvas;
    PreviewView previewView;
    int rotation;
    ImageProcess imageProcess;
    private Yolov5TFLiteDetector yolov5TFLiteDetector;
    TextToSpeech textToSpeech;

    String targetPlate;

    public FullScreenAnalyse(Context context,
                             PreviewView previewView,
                             ImageView boxLabelCanvas,
                             int rotation,
                             Yolov5TFLiteDetector yolov5TFLiteDetector,
                             TextToSpeech textToSpeech,
                             String targetPlate) {
        this.FScontect = context;
        this.previewView = previewView;
        this.boxLabelCanvas = boxLabelCanvas;
        this.rotation = rotation;
        this.imageProcess = new ImageProcess();
        this.yolov5TFLiteDetector = yolov5TFLiteDetector;
        this.textToSpeech = textToSpeech;
        this.targetPlate = targetPlate;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        int previewHeight = previewView.getHeight();
        int previewWidth = previewView.getWidth();

        // ??????Observable???image analyse??????????????????????????????, ??????UI????????????????????????????????????, ????????????UI??????
        Observable.create( (ObservableEmitter<Result> emitter) -> {
            long start = System.currentTimeMillis();
            Log.i("image",""+previewWidth+'/'+previewHeight);

            byte[][] yuvBytes = new byte[3][];
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            int imageHeight = image.getHeight();
            int imagewWidth = image.getWidth();

            imageProcess.fillBytes(planes, yuvBytes);
            int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            int[] rgbBytes = new int[imageHeight * imagewWidth];
            imageProcess.YUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    imagewWidth,
                    imageHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);

            // ??????bitmap
            Bitmap imageBitmap = Bitmap.createBitmap(imagewWidth, imageHeight, Bitmap.Config.ARGB_8888);
            imageBitmap.setPixels(rgbBytes, 0, imagewWidth, 0, 0, imagewWidth, imageHeight);

            // ??????????????????fill_start?????????bitmap
            double scale = Math.max(
                    previewHeight / (double) (rotation % 180 == 0 ? imagewWidth : imageHeight),
                    previewWidth / (double) (rotation % 180 == 0 ? imageHeight : imagewWidth)
            );
            Matrix fullScreenTransform = imageProcess.getTransformationMatrix(
                    imagewWidth, imageHeight,
                    (int) (scale * imageHeight), (int) (scale * imagewWidth),
                    rotation % 180 == 0 ? 90 : 0, false
            );

            // ??????preview????????????bitmap
            Bitmap fullImageBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, imagewWidth, imageHeight, fullScreenTransform, false);
            // ????????????preview???????????????????????????bitmap
            Bitmap cropImageBitmap = Bitmap.createBitmap(
                    fullImageBitmap, 0, 0,
                    previewWidth, previewHeight
            );

            // ???????????????bitmap
            Matrix previewToModelTransform =
                    imageProcess.getTransformationMatrix(
                            cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                            yolov5TFLiteDetector.getInputSize().getWidth(),
                            yolov5TFLiteDetector.getInputSize().getHeight(),
                            0, false);
            Bitmap modelInputBitmap = Bitmap.createBitmap(cropImageBitmap, 0, 0,
                    cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                    previewToModelTransform, false);

            Matrix modelToPreviewTransform = new Matrix();
            previewToModelTransform.invert(modelToPreviewTransform);

            //??????
            ArrayList<Recognition> recognitions = yolov5TFLiteDetector.detect(modelInputBitmap);

//            textRecogni(recognitions,fullImageBitmap);


            Bitmap emptyCropSizeBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
            Canvas cropCanvas = new Canvas(emptyCropSizeBitmap);
            // ????????????
            Paint boxPaint = new Paint();
            boxPaint.setStrokeWidth(5);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setColor(Color.RED);
            // ????????????
            Paint textPain = new Paint();
            textPain.setTextSize(50);
            textPain.setColor(Color.RED);
            textPain.setStyle(Paint.Style.FILL);

            for (Recognition res : recognitions) {
                RectF location = res.getLocation();
                String label = res.getLabelName();
                float confidence = res.getConfidence();
                modelToPreviewTransform.mapRect(location);

//                String rec = textRecogni(res,fullImageBitmap,location);
                Task<Text> te = textRecogni(res,fullImageBitmap,location);

                te.addOnSuccessListener(new OnSuccessListener<Text>() {
//                            String resultText = null;
                            @Override
                            public void onSuccess(Text visionText) {
                                String temptext = visionText.getText();
//                                resultText = temptext;
                                plate_num = temptext;
                                String plate = temptext.replace(" ","");

                                Log.e("",plate);
                                Log.e("","a: "+textToSpeech.isSpeaking());
                                Log.e("","b: "+targetPlate);

                                plate = plate.toLowerCase();
                                targetPlate = targetPlate.toLowerCase();

                                if(!textToSpeech.isSpeaking() && plate.equals(targetPlate)){
                                    startAuto("found the car");
                                }
                                if(!textToSpeech.isSpeaking() && plate.equals("")){
                                    startAuto("too far can not detect");
                                }
                            }

                            public void startAuto(String data) {
                                textToSpeech.setPitch(0.5f);
                                // ????????????
                                textToSpeech.setSpeechRate(1f);
                                textToSpeech.speak(data,
                                        TextToSpeech.QUEUE_FLUSH, null);
                            }

                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(e.toString(),".");
                            }
                        });
                cropCanvas.drawRect(location, boxPaint);
                cropCanvas.drawText(label + ":" + String.format("%.2f", confidence)+"  "+plate_num, location.left, location.top, textPain);
            }
            long end = System.currentTimeMillis();
            long costTime = (end - start);
            image.close();
            emitter.onNext(new Result(costTime, emptyCropSizeBitmap));
        }).subscribeOn(Schedulers.io()) // ????????????????????????,??????????????????????????????, ????????????????????????????????????, ?????????
                // ???????????????????????????, ??????????????????emitter???????????????????????????
                .observeOn(AndroidSchedulers.mainThread())
                // ?????????????????????????????????????????????????????????.
                .subscribe((Result result) -> {
                    boxLabelCanvas.setImageBitmap(result.bitmap);
                });
    }



    public Task<Text> textRecogni(Recognition res,Bitmap fullImageBitmap,RectF location){
//        final String[] resultText = new String[1];
            Bitmap carPlateBitmap = Bitmap.createBitmap(fullImageBitmap,
                    (int) location.left,
                    (int) location.top,
                    (int) location.width(),
                    (int) location.height());

            int newh = carPlateBitmap.getHeight()*3;
            int neww = carPlateBitmap.getWidth()*3;

//            Bitmap carimage = zoomImg2(carPlateBitmap,neww,newh);
            Bitmap carimage = Bitmap.createScaledBitmap(carPlateBitmap, neww, newh, true);

            TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            InputImage plateImage = InputImage.fromBitmap(carPlateBitmap,0);

            Task<Text> result =
                    recognizer.process(plateImage);
//            result.addOnSuccessListener(new OnSuccessListener<Text>() {
//                                @Override
//                                public void onSuccess(Text visionText) {
//                                    String resultText = visionText.getText();
////                                    map.put(key,resultText);
//                                    plate_num = resultText;
//                                }
//                            })
//                            .addOnFailureListener(new OnFailureListener() {
//                                @Override
//                                public void onFailure(@NonNull Exception e) {
//                                    Log.e(e.toString(),".");
//                                }
//                            });



            return result;
    }
}
