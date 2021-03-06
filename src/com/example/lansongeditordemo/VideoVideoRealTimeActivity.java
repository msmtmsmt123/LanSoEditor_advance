package com.example.lansongeditordemo;


import java.io.IOException;
import java.util.Locale;

import jp.co.cyberagent.lansongsdk.gpuimage.GPUImageFilter;
import jp.co.cyberagent.lansongsdk.gpuimage.GPUImageSepiaFilter;

import com.example.lansongeditordemo.view.DrawPadView;
import com.lansoeditor.demo.R;
import com.lansosdk.box.AudioEncodeDecode;
import com.lansosdk.box.DrawPad;
import com.lansosdk.box.DrawPadUpdateMode;
import com.lansosdk.box.AudioMixManager;
import com.lansosdk.box.VideoPen;
import com.lansosdk.box.ViewPen;
import com.lansosdk.box.Pen;
import com.lansosdk.box.onDrawPadCompletedListener;
import com.lansosdk.box.onDrawPadProgressListener;
import com.lansosdk.box.onDrawPadSizeChangedListener;
import com.lansosdk.videoeditor.MediaInfo;
import com.lansosdk.videoeditor.SDKDir;
import com.lansosdk.videoeditor.SDKFileUtils;
import com.lansosdk.videoeditor.VideoEditor;
import com.lansosdk.videoeditor.player.IMediaPlayer;
import com.lansosdk.videoeditor.player.IMediaPlayer.OnPlayerPreparedListener;
import com.lansosdk.videoeditor.player.VPlayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * 演示: 使用DrawPad来实现 视频和视频的实时叠加.
 * 
 * 流程是: 
 * 先创建一个DrawPad,获取主VideoPen,在播放过程中,再次获取一个VideoPen然后可以调节SeekBar来对
 * Pen的每个参数进行调节.
 * 
 * 可以调节的有:平移,旋转,缩放,RGBA值,显示/不显示(闪烁)效果.
 * 实际使用中, 可用这些属性来扩展一些功能.
 * 
 * 比如 调节另一个视频的RGBA中的A值来实现透明叠加效果,类似MV的效果.
 * 
 * 比如 调节另一个视频的平移,缩放,旋转来实现贴纸的效果.
 * 
 */
public class VideoVideoRealTimeActivity extends Activity implements OnSeekBarChangeListener {
    private static final String TAG = "VideoActivity";

    private String mVideoPath;

    private DrawPadView mPlayView;
    
    private MediaPlayer mplayer=null;
    private VPlayer  vplayer=null;
    
    private MediaPlayer mplayer2=null;
    
    private VideoPen subVideoPen=null;
    private Pen  mPenMain=null;
    
    private String editTmpPath=null;
    private String dstPath=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
		 Thread.setDefaultUncaughtExceptionHandler(new snoCrashHandler());
        setContentView(R.layout.drawpad_layout);
        
        
        mVideoPath = getIntent().getStringExtra("videopath");
        mPlayView = (DrawPadView) findViewById(R.id.DrawPad_view);
        
        initSeekBar(R.id.id_DrawPad_skbar_rotate,360); //角度是旋转360度.
        initSeekBar(R.id.id_DrawPad_skbar_move,100);   //move的百分比暂时没有用到,举例而已.
        initSeekBar(R.id.id_DrawPad_skbar_scale,800);   //这里设置最大可放大8倍
        
        initSeekBar(R.id.id_DrawPad_skbar_red,100);  //red最大为100
        initSeekBar(R.id.id_DrawPad_skbar_green,100);
        initSeekBar(R.id.id_DrawPad_skbar_blue,100);
        initSeekBar(R.id.id_DrawPad_skbar_alpha,100);
        
        findViewById(R.id.id_DrawPad_saveplay).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				 if(SDKFileUtils.fileExist(dstPath)){
		   			 	Intent intent=new Intent(VideoVideoRealTimeActivity.this,VideoPlayerActivity.class);
			    	    	intent.putExtra("videopath", dstPath);
			    	    	startActivity(intent);
		   		 }else{
		   			 Toast.makeText(VideoVideoRealTimeActivity.this, "目标文件不存在", Toast.LENGTH_SHORT).show();
		   		 }
			}
		});
        
    	findViewById(R.id.id_DrawPad_saveplay).setVisibility(View.GONE);

        //在手机的/sdcard/lansongBox/路径下创建一个文件名,用来保存生成的视频文件,(在onDestroy中删除)
        editTmpPath=SDKFileUtils.newMp4PathInBox();
        dstPath=SDKFileUtils.newMp4PathInBox();
        
        //增加提示缩放到480的文字.
        DemoUtils.showScale480HintDialog(VideoVideoRealTimeActivity.this);
    }
    private void initSeekBar(int resId,int maxvalue)
    {
    	SeekBar   skbar=(SeekBar)findViewById(resId);
           skbar.setOnSeekBarChangeListener(this);
           skbar.setMax(maxvalue);
    }
    @Override
    protected void onResume() {
    	// TODO Auto-generated method stub
    	super.onResume();
    
    	new Handler().postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				 startPlayVideo();
			}
		}, 100);
    }
    private void startPlayVideo()
    {
          if (mVideoPath != null){
        	  mplayer=new MediaPlayer();
        	  try {
				mplayer.setDataSource(mVideoPath);
				
			}  catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	  mplayer.setOnPreparedListener(new OnPreparedListener() {
				
				@Override
				public void onPrepared(MediaPlayer mp) {
					// TODO Auto-generated method stub
					start(mp);
				}
			});
        	  mplayer.setOnCompletionListener(new OnCompletionListener() {
				
				@Override
				public void onCompletion(MediaPlayer mp) {
					// TODO Auto-generated method stub
					if(mPlayView!=null && mPlayView.isRunning()){
						mPlayView.stopDrawPad();
						
						toastStop();
						
						if(SDKFileUtils.fileExist(editTmpPath)){
							boolean ret=VideoEditor.encoderAddAudio(mVideoPath,editTmpPath,SDKDir.TMP_DIR,dstPath);
							if(!ret){
								dstPath=editTmpPath;
							}else
								SDKFileUtils.deleteFile(editTmpPath);
							
							findViewById(R.id.id_DrawPad_saveplay).setVisibility(View.VISIBLE);
						}
					}
				}
			});
        	  mplayer.prepareAsync();
          }
          else {
              Log.e(TAG, "Null Data Source\n");
              finish();
              return;
          }
    }
    private void start(MediaPlayer mp)
    {
    	MediaInfo info=new MediaInfo(mVideoPath,false);
    	info.prepare();
		
    	// 设置DrawPad的刷新模式,默认 {@link DrawPad.UpdateMode#ALL_VIDEO_READY};
    	mPlayView.setUpdateMode(DrawPadUpdateMode.ALL_VIDEO_READY,25);
    	
    	
    	if(DemoCfg.ENCODE){
    		//设置使能 实时录制, 即把正在DrawPad中呈现的画面实时的保存下来,起到所见即所得的模式
    		mPlayView.setRealEncodeEnable(480,480,1000000,(int)info.vFrameRate,editTmpPath);
    	}
    	//设置当前DrawPad的宽度和高度,并把宽度自动缩放到父view的宽度,然后等比例调整高度.
    	mPlayView.setDrawPadSize(480,480,new onDrawPadSizeChangedListener() {
			
			@Override
			public void onSizeChanged(int viewWidth, int viewHeight) {
				// TODO Auto-generated method stub
				
				// 开始DrawPad的渲染线程. 
//				mPlayView.startDrawPad(new DrawPadProgressListener(),new DrawPadCompleted());
				mPlayView.startDrawPad(null,null); //这里为了演示方便, 设置为null,当然你也可以使用上面这句,然后把当前行屏蔽, 这样会调用两个回调,在回调中实现可以根据时间戳的关系来设计各种效果.
				
				//获取一个主视频的 VideoPen
				mPenMain=mPlayView.addMainVideoPen(mplayer.getVideoWidth(),mplayer.getVideoHeight());
				if(mPenMain!=null){
					mplayer.setSurface(new Surface(mPenMain.getVideoTexture()));
				}
				mplayer.start();
				
				
//				startVPlayer();
				startPlayer2();
			}
		});
    }
    private class DrawPadCompleted implements onDrawPadCompletedListener
    {

		@Override
		public void onCompleted(DrawPad v) {
			// TODO Auto-generated method stub
			if(isDestorying==false){
				
			}
		}
    }
    boolean isFirstRemove=false;
    private class DrawPadProgressListener implements onDrawPadProgressListener
    {

		@Override
		public void onProgress(DrawPad v, long currentTimeUs) {
			// TODO Auto-generated method stub
//			  Log.i(TAG,"DrawPadProgressListener: us:"+currentTimeUs);
			
			//这里根据每帧的效果,设置当大于10秒时, 删除第二个视频画面.
			  if(currentTimeUs>=3*1000*1000 && subVideoPen!=null && isFirstRemove==false)  
			  {
					  if(mPlayView!=null){
						  mPlayView.removePen(subVideoPen);
						  subVideoPen=null;
							
							if(mplayer2!=null){
								mplayer2.stop();
								mplayer2.release();
								mplayer2=null;
							}
					  }
					  isFirstRemove=true;
					  Log.i(TAG,"subVideoPen removed: !!!!!!!!!:");
			  }
			  
			  if(currentTimeUs>=6*1000*1000&& mplayer2==null)  
			  {
					  if(mPlayView!=null){
						  startPlayer2();
						  Log.i(TAG,"subVideoPen restart!!!!!!!!:");
					  }
			  }
			  
		}
    }
    
    private void startPlayer2()
    {
    	mplayer2=new MediaPlayer();
    	try {
    		mplayer2.setDataSource(mVideoPath);
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	mplayer2.setOnPreparedListener(new OnPreparedListener() {
			
			@Override
			public void onPrepared(MediaPlayer mp) {
				// TODO Auto-generated method stub
				
				// 获取一个VideoPen
				subVideoPen=mPlayView.addSubVideoPen(mp.getVideoWidth(),mp.getVideoHeight());
				Log.i(TAG,"sub video Pen ....obtain..");
				if(subVideoPen!=null){
					mplayer2.setSurface(new Surface(subVideoPen.getVideoTexture()));	
					subVideoPen.setScale(0.5f);  //这里先缩小一半
				}
				mplayer2.start();
			}
		});
    	mplayer2.setOnErrorListener(new OnErrorListener() {
			
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				// TODO Auto-generated method stub
				return false;
			}
		});
    	mplayer2.setOnInfoListener( new OnInfoListener() {
			
			@Override
			public boolean onInfo(MediaPlayer mp, int what, int extra) {
				// TODO Auto-generated method stub
				return false;
			}
		});
		mplayer2.prepareAsync();
    }
    private void startVPlayer()
    {
    	vplayer=new VPlayer(this);
    	vplayer.setVideoPath(mVideoPath);
    	vplayer.setOnPreparedListener(new OnPlayerPreparedListener() {
			
			@Override
			public void onPrepared(IMediaPlayer mp) {
				// TODO Auto-generated method stub
				
				
				subVideoPen=mPlayView.addSubVideoPen(mp.getVideoWidth(),mp.getVideoHeight());
				if(subVideoPen!=null){
					vplayer.setSurface(new Surface(subVideoPen.getVideoTexture()));	
					subVideoPen.setScale(0.5f);
				}
				vplayer.start();
			}
		});
    	vplayer.prepareAsync();
    }
    private void toastStop()
    {
    	Toast.makeText(getApplicationContext(), "录制已停止!!", Toast.LENGTH_SHORT).show();
    }

    boolean isDestorying=false;  //是否正在销毁, 因为销毁会停止DrawPad
    
    @Override
    protected void onDestroy() {
    	// TODO Auto-generated method stub
    	super.onDestroy();
    	
    	
    		isDestorying=true;
			if(mplayer!=null){
				mplayer.stop();
				mplayer.release();
				mplayer=null;
			}
			
			if(mplayer2!=null){
				mplayer2.stop();
				mplayer2.release();
				mplayer2=null;
			}
			
			if(vplayer!=null){
				vplayer.stop();
				vplayer.release();
				vplayer=null;
			}
			
			if(mPlayView!=null){
				mPlayView.stopDrawPad();
				mPlayView=null;        		   
			}
			if(SDKFileUtils.fileExist(dstPath)){
				SDKFileUtils.deleteFile(dstPath);
		    }
		    if(SDKFileUtils.fileExist(editTmpPath)){
		    	SDKFileUtils.deleteFile(editTmpPath);
		    } 
	}
    private float xpos=0,ypos=0;
    
    /**
     * 提示:实际使用中没有主次之分, 只要是继承自IPen的对象(FilterPen除外),都可以调节,这里仅仅是举例
     */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		// TODO Auto-generated method stub
		switch (seekBar.getId()) {
			case R.id.id_DrawPad_skbar_rotate:
				if(subVideoPen!=null){
					subVideoPen.setRotate(progress);
				}
				break;
			case R.id.id_DrawPad_skbar_move:
					if(subVideoPen!=null){
						 xpos+=10;
						 ypos+=10;
						 
						 if(xpos>mPlayView.getViewWidth())
							 xpos=0;
						 if(ypos>mPlayView.getViewWidth())
							 ypos=0;
						 subVideoPen.setPosition(xpos, ypos);
					}
				break;				
			case R.id.id_DrawPad_skbar_scale:
				if(subVideoPen!=null){
					float scale=(float)progress/100;
					subVideoPen.setScale(scale);
				}
			break;		
			case R.id.id_DrawPad_skbar_red:
					if(subVideoPen!=null){
						subVideoPen.setRedPercent(progress);  //设置每个RGBA的比例,默认是1
					}
				break;

			case R.id.id_DrawPad_skbar_green:
					if(subVideoPen!=null){
						subVideoPen.setGreenPercent(progress);
					}
				break;

			case R.id.id_DrawPad_skbar_blue:
					if(subVideoPen!=null){
						subVideoPen.setBluePercent(progress);
					}
				break;

			case R.id.id_DrawPad_skbar_alpha:
					if(subVideoPen!=null){
						subVideoPen.setAlphaPercent(progress);
					}
				break;
				
			default:
				break;
		}
	}
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}
}
