package studio.stone.appcamera;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FullscreenActivity extends Activity implements SensorEventListener{
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private final int LED_NOTIFICATION_ID=Notification.DEFAULT_LIGHTS;
    private boolean recording=false;
    private boolean onShot=false;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private MediaRecorder mRecorder;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private NotificationManager nm;
    private Notification notif;
    private float gX,gY,gZ;
    private String mCurrentPhotoPath;
    private String mCurrentVideoPath;
    private Timer mHandlerShot;
    private PowerManager.WakeLock wl;
    private VR videoRecorder;
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD, WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_fullscreen);
        mSurfaceView =(SurfaceView)findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.setSizeFromLayout();
        mHolder.setKeepScreenOn(true);
        mHolder.addCallback(this.mSurfaceHolderCallback);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        System.out.println("studio.stone.appCamera App Start");
        nm = ( NotificationManager ) getSystemService( NOTIFICATION_SERVICE );
        notif = new Notification();
        notif.ledARGB = 0xFFFF0000;
        notif.flags = Notification.FLAG_SHOW_LIGHTS;
        notif.ledOnMS = 60000;
        notif.ledOffMS = 0;
        nm.notify(LED_NOTIFICATION_ID, notif);
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyWEAK");
        wl.acquire();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nm.notify(LED_NOTIFICATION_ID, notif);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try{
            nm.cancel(LED_NOTIFICATION_ID);
        }catch(Exception e){
            e.printStackTrace();
        }
        mSensorManager.unregisterListener(this);
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        wl.release();
    }
    @Override
    public boolean onTouchEvent(MotionEvent event){
        System.out.println("ON TOUCH EVENT HANDLER");
        int x=(int)event.getX();
        int y=(int)event.getY();
        System.out.println("X="+x);
        System.out.println("Y="+y);
        Rect focusRect=getFocusRect(x,y);
        Camera.Parameters params = mCamera.getParameters();
        if (params.getMaxNumMeteringAreas() > 0){
            List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(new Camera.Area(focusRect,200));
            params.setMeteringAreas(focusAreas);
            params.setFocusAreas(focusAreas);
            mCamera.setParameters(params);
        }
        return super.onTouchEvent(event);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if(recording){
                //stopRecord();
                videoRecorder.interrupt();
                videoRecorder=null;
            }
            else{
                //startRecord();
                videoRecorder = new VR();
                videoRecorder.start();
            }
            return true;
        } else if(keyCode==KeyEvent.KEYCODE_BACK){
            if(recording){
                videoRecorder.interrupt();
                videoRecorder=null;
            }
            nm.cancel(LED_NOTIFICATION_ID);
            finish();
            return true;
        }else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            //todo app volume up
            if(!onShot)
            {
                System.out.println("Ready For Shot");
                onShot=true;
                takePicture();
                mHandlerShot=new Timer();
                mHandlerShot.schedule(new TimerTask(){
                    @Override
                    public void run(){
                        onShot=false;
                        mHandlerShot.cancel();
                    };
                },2000);
            }
            else
                System.out.println("Not Ready For Shot");
            return true;
        }else
            return super.onKeyDown(keyCode,event);
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }
    @Override
    public void onSensorChanged(SensorEvent event){
        gX=event.values[0];
        gY=event.values[1];
        gZ=event.values[2];
    }
    private void galleryAddPic(String mCurrentMediaPath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentMediaPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
    private static File getOutputMediaFile(int mType){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "/.mydroid");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(".mydroid", "failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
        File mediaFile;
        if (mType == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(mType == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }
    private void takePicture(){
        if(!recording){
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            params.setPictureSize(2688, 1520);
            params.setJpegQuality(100);
            params.setRotation(getRotation());
            mCamera.setParameters(params);
            mCamera.takePicture(null, null, mPicture);
            Toast.makeText(getBaseContext(), "Take Picture OK.", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getBaseContext(), "In Recording,  Can't take picture.", Toast.LENGTH_SHORT).show();

    }
    private void stopRecord(){
        if(mRecorder!=null){
            if(recording)
            {
                mRecorder.stop();
                recording=false;
                Toast.makeText(getBaseContext(), "Stop Recording.", Toast.LENGTH_SHORT).show();
                galleryAddPic(mCurrentVideoPath);
            }
            mRecorder.release();
            mRecorder=null;
        }
        mCamera.lock();

    }
    private void startRecord(){
        try{
            mRecorder=new MediaRecorder();
            mCamera.unlock();
            mRecorder.setCamera(mCamera);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mRecorder.setOrientationHint(getRotation());
            //mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            CamcorderProfile prof=CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            System.out.println("AudioBitRate="+prof.audioBitRate);
            System.out.println("AudioSampleRate="+prof.audioSampleRate);
            System.out.println("VideoBitRate="+prof.videoBitRate);
            System.out.println("Quality="+prof.quality);
            prof.audioBitRate=192000;
            prof.audioChannels=2;
            prof.videoBitRate=20000000;
            prof.videoFrameHeight=1080;
            prof.videoFrameWidth=1920;
            prof.videoFrameRate=30;
            mRecorder.setProfile(prof);
            //mRecorder.setVideoSize(1920, 1080);
            //mRecorder.setVideoEncodingBitRate(20000000);
            //mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
           // mRecorder.setVideoFrameRate(30);
            mRecorder.setPreviewDisplay(mHolder.getSurface());
            mCurrentVideoPath=getOutputMediaFile(MEDIA_TYPE_VIDEO).toString();
            mRecorder.setOutputFile(mCurrentVideoPath);
            mRecorder.prepare();
            mRecorder.start();
            recording=true;
            Toast.makeText(getBaseContext(), "Start Recording.", Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            e.printStackTrace();
            mRecorder.release();
            mRecorder=null;
            mCamera.lock();
            recording=false;
            Toast.makeText(getBaseContext(), "Recording Fail.", Toast.LENGTH_SHORT).show();
        }
    }
    private int getRotation(){
        int rotation=0;
        if(gX>6&&gY<3&&gY>-3){
            rotation=0;
        }else if (gY>6&&gX<3&&gX>-3){
            rotation=90;
        }else if (gX<-6&&gY<3&&gY>-3){
            rotation=180;
        }else if (gY<-6&&gX<3&&gX>-3){
            rotation=270;
        }
        return rotation;
    }
    private Rect getFocusRect(int x,int y){
        x=(x*2000/ mSurfaceView.getWidth())-1000;
        y=(y*2000/ mSurfaceView.getHeight())-1000;
        Rect myRect;
        if(x>=900){
            if(y<-900)
                myRect=new Rect(x-200,y,x,y+200);
            else if(y>=900)
                myRect=new Rect(x-200,y-200,x,y);
            else
                myRect=new Rect(x-200,y+100,x,y-100);
        }
        else if(x<=-900){
            if(y<-900)
                myRect=new Rect(x,y,x+200,y+200);
            else if(y>900)
                myRect=new Rect(x,y-200,x+200,y);
            else
                myRect=new Rect(x,y+100,x+200,y-100);
        }
        else{
            if(y<-900)
                myRect=new Rect(x-100,y,x+100,y+200);
            else if(y>900)
                myRect=new Rect(x-100,y-200,x+100,y);
            else
                myRect=new Rect(x-100,y-100,x+100,y+100);
        }
        return myRect;
    }
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            mCurrentPhotoPath=pictureFile.toString();
            if (pictureFile == null){
                //Log.d(mPreview.TAG, "Error creating media file, check storage permissions: " );
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                galleryAddPic(mCurrentPhotoPath);
                camera.startPreview();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                //Log.d(mPreview.TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                //Log.d(mPreview.TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };
    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback(){
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mCamera = Camera.open();
            try{
                Camera.Parameters params=mCamera.getParameters();
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                params.setPictureSize(2688, 1520);
                params.setPreviewSize(1920, 1080);
                params.setVideoStabilization(true);
                params.set("auto-exposure-value","center-weighted");
                params.setPreviewFpsRange(120000, 120000);
                mCamera.setParameters(params);
            }catch(Exception ec){
                ec.printStackTrace();
            }
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (holder.getSurface() == null){
                return;
            }
            try {
                mCamera.stopPreview();
            } catch (Exception e2){
                e2.printStackTrace();
            }
            holder.setSizeFromLayout();
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (Exception e3){
                e3.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            holder.getSurface().release();
            mCamera.release();
            mCamera=null;
        }
    };
    private class VR extends Thread{
        public synchronized void start (){
            startRecord();
        }
        public void interrupt (){
            stopRecord();
        }
    }
}
