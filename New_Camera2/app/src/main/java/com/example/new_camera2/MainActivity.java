package com.example.new_camera2;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Policy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collector;

public class MainActivity extends Activity  {
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    /**
     * 用于预览的TextureView
     * 在布局文件中加入TextureView控件,实现其监听事件
     */
    private AutoFitTextureView mTextureView;

    private static final int PERMISSION_REQUEST=1;

    /**
     * 相机设备
     */
    private int mCameraLensFacingDirection;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    /**
     * 预览尺寸
     */
    private Size mPreviewSize;

    private boolean isRecording=false;
    public MediaRecorder mediaRecorder;

    /**
     * 图片读取ImageReader
     */
    private ImageReader mImageReader;

    private String mNextVideoAbsolutePath;

    private Integer mSensorOrientation;

    private Integer PREVIEW_MODE=0;

    /**
     * 拍照尺寸
     */
    private Size mCaptureSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCameraCaptureSession;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private static final String TAG = "HuangXin";
    private Spinner spinner_changeSize;
    private Button take_photo;
    private Button change_Front;
    private Button record_video;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        //获取相机权限
        requestPermissions();
        mTextureView = (AutoFitTextureView) findViewById(R.id.textureView);
        record_video = (Button)findViewById(R.id.recordButton);
        record_video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isRecording){
                    stopRecord();
                    stopPreview();
                    startPreview();
                    record_video.setText("录像");
                    isRecording=false;
                    return;
                }
                setupMediaRecorder();
                startRecord();
            }
        });
        change_Front=(Button)findViewById(R.id.changeFront);
        change_Front.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        take_photo=(Button)findViewById(R.id.photoButton);
        take_photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockFocus();
            }
        });
        spinner_changeSize=(Spinner)findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter=ArrayAdapter.createFromResource(this,
                R.array.planets_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_changeSize.setAdapter(adapter);
        spinner_changeSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG,"selected"+position);
                updatePreview();
                if (position==0){
                    Log.d(TAG,"Height is "+mTextureView.getHeight());
                    setupCamera(1920,1080);
                    mTextureView.setAspectRatio(9,16);
                }if(position==1){
                    Log.d(TAG,"Height is "+mTextureView.getHeight());
                    setupCamera(1440,1080);
                    mTextureView.setAspectRatio(3,4);
                }if(position==2){
                    Log.d(TAG,"Height is "+mTextureView.getHeight());
                    mTextureView.setAspectRatio(1,1);
                    setupCamera(1080,1080);
                }
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (!mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(mTextureListener);
        } else {
            reopenCamera();
        }
    }
    /**
     * 获取支持分辨率
     * */
//    private Size getMatchingSize2(){
//        Size selectSize = null;
//        try {
//            CameraManager mCameraManager=(CameraManager) getSystemService(Context.CAMERA_SERVICE);
//            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
//            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
//            DisplayMetrics displayMetrics = getResources().getDisplayMetrics(); //因为我这里是将预览铺满屏幕,所以直接获取屏幕分辨率
//            int deviceWidth = displayMetrics.widthPixels; //屏幕分辨率宽
//            int deviceHeigh = displayMetrics.heightPixels; //屏幕分辨率高
//            Log.e(TAG, "getMatchingSize2: 屏幕密度宽度="+deviceWidth);
//            Log.e(TAG, "getMatchingSize2: 屏幕密度高度="+deviceHeigh );
//            /**
//             * 循环40次,让宽度范围从最小逐步增加,找到最符合屏幕宽度的分辨率,
//             * 你要是不放心那就增加循环,肯定会找到一个分辨率,不会出现此方法返回一个null的Size的情况
//             * ,但是循环越大后获取的分辨率就越不匹配
//             */
//            for (int j = 1; j < 41; j++) {
//                for (int i = 0; i < sizes.length; i++) { //遍历所有Size
//                    Size itemSize = sizes[i];
//                    Log.e(TAG,"当前itemSize 宽="+itemSize.getWidth()+"高="+itemSize.getHeight());
//                    //判断当前Size高度小于屏幕宽度+j*5  &&  判断当前Size高度大于屏幕宽度-j*5
//                    if (itemSize.getHeight() < (deviceWidth + j*5) && itemSize.getHeight() > (deviceWidth - j*5)) {
//                        if (selectSize != null){ //如果之前已经找到一个匹配的宽度
//                            if (Math.abs(deviceHeigh-itemSize.getWidth()) < Math.abs(deviceHeigh - selectSize.getWidth())){ //求绝对值算出最接近设备高度的尺寸
//                                selectSize = itemSize;
//                                continue;
//                            }
//                        }else {
//                            selectSize = itemSize;
//                        }
//                    }
//                }
//
//            }
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//        Log.e(TAG, "getMatchingSize2: 选择的分辨率宽度="+selectSize.getWidth());
//        Log.e(TAG, "getMatchingSize2: 选择的分辨率高度="+selectSize.getHeight());
//        return selectSize;
//    }

    /**
     * 开启线程
     */
    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * 关闭线程
     * */
    public void stopBackgroundThread() {
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                mCameraThread = null;
                mCameraThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * TextureView监听事件
     */
    private TextureView.SurfaceTextureListener mTextureListener = new
            TextureView.SurfaceTextureListener() {

                //当SurfaceTexture准备好后会回调SurfaceTextureListener的
                // onSurfaceTextureAvailable方法
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    //当SurfaceTexture可用时,设置相机参数并打开相机
                    setupCamera(width, height);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    //setupCamera(width, height);
                    //closeCamera();
                    startPreview();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                }
            };

    /**
     * 设置相机参数,
     * 根据TextureView的尺寸设置预览尺寸
     */
    private void setupCamera(int width, int height) {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                //默认打开后置摄像头
                if (facing != null && facing == mCameraLensFacingDirection) {
                    continue;
                }
                //获取StreamConfigurationMap,他管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                if(PREVIEW_MODE==0){
                    //根据TextureView的尺寸设置预览尺寸
                    mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            width, height);
                }
                //此ImageReader用于拍照
                setupImageReader();
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 切换前后摄
     * */
    private void switchCamera(){
        if(mCameraLensFacingDirection==CameraCharacteristics.LENS_FACING_BACK){
            mCameraLensFacingDirection=CameraCharacteristics.LENS_FACING_FRONT;
            closeCamera();
            reopenCamera();
        }else if(mCameraLensFacingDirection==CameraCharacteristics.LENS_FACING_FRONT){
            mCameraLensFacingDirection=CameraCharacteristics.LENS_FACING_BACK;
            closeCamera();
            reopenCamera();
        }
    }
    private void reopenCamera(){
        if(mTextureView.isAvailable()){
            setupCamera(mTextureView.getWidth(),mTextureView.getHeight());
            openCamera();
        }else {
            mTextureView.setSurfaceTextureListener(mTextureListener);
        }
    }
    private void closeCamera(){
        // 关闭捕获会话
        if (null != mCameraCaptureSession) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        // 关闭当前相机
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        // 关闭拍照处理器
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * 选择SizeMap中最接近width和height的size
     */
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() >height) {
                    sizeList.add(option);
                }
            } else{
                if (option.getWidth() >= height && option.getHeight() >= width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }


    /**
     * 开启相机
     */
    private void openCamera() {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //检查权限
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    /**
     * 实现mStateCallback接口
     */
    private CameraDevice.StateCallback mStateCallback = new
            CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    mCameraDevice = null;
                }
            };
    /**
     * 开启录像
     * */
    private void startRecord(){
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            stopPreview();
            //添加预览的Surface
            SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
            assert surfaceTexture!=null;

            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());

            Surface previewSurface=new Surface(surfaceTexture);
            mPreviewRequestBuilder.addTarget(previewSurface);

            Surface recorderSurface=mediaRecorder.getSurface();
            mPreviewRequestBuilder.addTarget(recorderSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession=session;
                    //重新开始预览
                    updatePreview();
                    record_video.post(new Runnable() {
                        @Override
                        public void run() {
                            isRecording=true;
                            record_video.setText("停止");
                            mediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mCameraHandler);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }
    /**
     * 更新预览
     * */
    private void updatePreview(){
        if(null==mCameraDevice){
            return;
        }
        try{
            setUpCaptureRequestBuilder(mPreviewRequestBuilder);
            HandlerThread thread=new HandlerThread("CameraPreview");
            thread.start();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),null,mCameraHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    /**
     * 关闭录像
     * */
    private void stopRecord(){
        if(mediaRecorder!=null){
            try {
                mediaRecorder.stop();
            }catch (RuntimeException e){
                e.printStackTrace();
            }
            mediaRecorder.reset();
            mNextVideoAbsolutePath = null;
        }

    }
    private String getVideoFilePath(Context context) {
        String path = Environment.getExternalStorageDirectory() + "/DCIM/CameraV2/";
        final File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdir();
        }
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }
    private void setupMediaRecorder(){
        mediaRecorder =new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(this);
        }
        mediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        Log.d(TAG,"视频位置："+mNextVideoAbsolutePath);
        mediaRecorder.setVideoEncodingBitRate(100000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case 90:
                mediaRecorder.setOrientationHint(ORIENTATION.get(rotation));
                break;
            case 270:
                mediaRecorder.setOrientationHint(ORIENTATION.get(rotation)+180);
                break;
        }
        try {
            mediaRecorder.prepare();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 关闭预览
     * */
    private void stopPreview(){
        if(mCameraCaptureSession!=null){
            mCameraCaptureSession.close();
            mCameraCaptureSession=null;
        }
    }
    /**
     * 开启预览
     * 使用TextureView显示相机预览数据,
     * 预览和拍照数据都是使用CameraCaptureSession会话来请求
     */
    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        //设置TextureView的缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),
                mPreviewSize.getHeight());
        //获取Surface显示预览数据
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            //创建CaptureRequestBuilder,TEMPLATE_PREVIEW比表示预览请求
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //设置Surface作为预览数据的显示界面
            mPreviewRequestBuilder.addTarget(previewSurface);
            //创建相机捕获会话,第一个参数是捕获数据Surface列表,
            // 第二个参数是CameraCaptureSession的状态回调接口,
            //当他创建好后会回调onConfigured方法,第三个参数用来确定Callback在哪个线程执行
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        //创建捕获请求
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mCameraCaptureSession = session;
                        //设置反复捕获数据的请求
                        mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
//        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onStop() {
        //closeCamera();
        stopBackgroundThread();
        super.onStop();
    }
    /**
     * 动态获取相机权限
     * */
//    private void  requestCameraPermission(){
//        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
//            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
//            startPreview();
//        }else {
//            Toast.makeText(this,"Permission OK",Toast.LENGTH_SHORT).show();
//        }
//    }
    /**
     * 动态获取所有权限
     * */
    private void requestPermissions(){
        List<String> permissionList =new ArrayList<>();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.CAMERA);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.RECORD_AUDIO);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if(!permissionList.isEmpty()){
            ActivityCompat.requestPermissions(this,permissionList.toArray(new String[permissionList.size()]),PERMISSION_REQUEST);
        }else {
            Toast.makeText(this,"All Permission Ok",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            switch (requestCode){
                case PERMISSION_REQUEST:
                    if(grantResults.length>0){
                        for(int i=0;i<grantResults.length;i++){
                            if(grantResults[i]==PackageManager.PERMISSION_DENIED){
                                Toast.makeText(this,permissions[i]+"被拒绝",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    break;
            }
    }
    /**
     * 拍照
     * */
    private void capture(){
        try{
            //首先创建请求拍照的CaptureRequest
            final CaptureRequest.Builder mCaptureBuilder
                    =mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            //设置CaptureRequest输出到mImageReader
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            //设置照片储存方向
            if(mCameraLensFacingDirection==CameraCharacteristics.LENS_FACING_BACK){
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATION.get(rotation)+180);
            }else {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATION.get(rotation));
            }
            //这个回调接口用于拍照结束时重启预览，因为拍照会导致预览停止
            CameraCaptureSession.CaptureCallback CaptureCallback=new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Toast.makeText(getApplicationContext(),"Image Saved",Toast.LENGTH_SHORT).show();;
                    unlockFocus();
                }
            };
            //停止预览
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(mCaptureBuilder.build(),CaptureCallback,mCameraHandler);
            //开始拍照，然后回调上面的接口重启预览，因为mCaptureBuilder设置ImageReader作为target，
            // 所以会自动回调ImageReader的onImageAvailable()方法保存图片
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    /**
     * 锁定对焦并拍照
     * */
    private void lockFocus(){
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(),
                    mCaptureCallback,mCameraHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    /**
     * 解除对焦,重新预览
     * */
    private void unlockFocus(){
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest,null,mCameraHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    /**
     * CaptureSession的回调方法
     * */

    private CameraCaptureSession.CaptureCallback mCaptureCallback=
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    capture();
                }
            };

    /**
     * 设置图片读取ImageReader
     */
    private void setupImageReader() {
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.JPEG, 2);
        //监听ImageReader事件,当有图像流数据时回调onImageAvailable方法

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //参数是帧数据
                mCameraHandler.post(new imageSaver(reader.acquireNextImage()));
                Image image = reader.acquireNextImage();
            }
        }, mCameraHandler);
    }

    /**
     * 创建保存图片的线程
     */
    public static class imageSaver implements Runnable {

        private Image mImage;

        public imageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String path = Environment.getExternalStorageDirectory() + "/DCIM/CameraV2/";
            File mImageFile = new File(path);
            if (!mImageFile.exists()) {
                mImageFile.mkdir();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = path + "IMG_" + timeStamp + ".jpg";
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(fileName);
                fos.write(data, 0, data.length);
                mImage.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
