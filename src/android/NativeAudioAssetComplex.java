package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

// public class NativeAudioAssetComplex implements OnPreparedListener, OnCompletionListener, OnSeekCompleteListener {
public class NativeAudioAssetComplex implements OnPreparedListener, OnCompletionListener {


	private static final int INVALID = 0;
	private static final int PREPARED = 1;
	private static final int PENDING_PLAY = 2;
	private static final int PLAYING = 3;
	private static final int PENDING_LOOP = 4;
	private static final int LOOPING = 5;
	
	private MediaPlayer mp;
	private Context appContext;
	private Uri fileUri;
	private int state;
	private CyclicBarrier barrier;
	Callable<Void> completeCallback;
	// Callable<Void> preparedCallback;

	public NativeAudioAssetComplex( AssetFileDescriptor afd, float volume)  throws IOException, BrokenBarrierException
	{
		state = INVALID;
		mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
		mp.setDataSource( afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC); 
		mp.setVolume(volume, volume);
		mp.prepare();
	}

	public NativeAudioAssetComplex( String file, float volume, Context context, CyclicBarrier barrier)  throws IOException, BrokenBarrierException
	{
		state = INVALID;
		mp = new MediaPlayer();
		appContext = context; //cordova.getActivity().getApplicationContext();
		fileUri = Uri.parse(file);
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
		mp.setDataSource(appContext, fileUri);
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC); 
		mp.setVolume(volume, volume);
		mp.prepare();
	}

	public NativeAudioAssetComplex( String file, float volume, Context context)  throws IOException, BrokenBarrierException
	{
		state = INVALID;
		this.barrier = barrier;
		mp = new MediaPlayer();
		appContext = context; //cordova.getActivity().getApplicationContext();
		fileUri = Uri.parse(file);
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
		mp.setDataSource(appContext, fileUri);
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC); 
		mp.setVolume(volume, volume);
		mp.prepare();
	}
	
	public void play(Callable<Void> completeCb) throws IOException, BrokenBarrierException, InterruptedException
	{
        completeCallback = completeCb;
		invokePlay( false );
		barrier.await();
	}
	
	private void invokePlay( Boolean loop )
	{
		Boolean playing = mp.isPlaying();
		if ( playing )
		{
			mp.pause();
			mp.setLooping(loop);
			mp.seekTo(0);
			mp.start();
		}
		if ( !playing && state == PREPARED )
		{
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			onPrepared( mp );
		}
		else if ( !playing )
		{
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			mp.setLooping(loop);
			mp.start();
		}
	}

	public int getState(){
		return state;
	}

	// public void prepare(Callable<Void> completeCb) throws IOException
	// {
	// 	preparedCallback = completeCb;
	// 	invokePrepare();
	// }

	public void invokePrepare() throws IOException
	{
		mp.prepare();
		onPrepared( mp );
	}

	public boolean pause()
	{
		try
		{
			if ( mp.isPlaying() )
				{
					mp.pause();
					return true;
				}
        	}
		catch (IllegalStateException e)
		{
		// I don't know why this gets thrown; catch here to save app
		}
		return false;
	}

	public void resume()
	{
		mp.start();
	}

	public void seek(int timeMS){

		try {
			mp.seekTo(timeMS);
		}
		catch(IllegalStateException e){
		}
	}

	public int getCurrentTime(){
		try {
			return mp.getCurrentPosition();
			// return 0;
		}
		catch(IllegalStateException e){
			return -1;
		}
	}

	public int getDuration(){
		try {
			return mp.getDuration();
		}
		catch(IllegalStateException e){
			return -1;
		}
	}

	public void trueStop(){
		try
		{
			state = INVALID;
			mp.stop();
			mp.prepare();
		}
		catch (IOException e)
		{

		}
		catch (IllegalStateException e)
		{

		}
	}

    public void stop()
	{
		try
		{
			if ( mp.isPlaying() )
			{
				state = INVALID;
				mp.pause();
				mp.seekTo(0);
	           	}
		}
	        catch (IllegalStateException e)
	        {
            // I don't know why this gets thrown; catch here to save app
	        }
	}

	public void setVolume(float volume) 
	{
	        try
	        {
			mp.setVolume(volume,volume);
            	}
            	catch (IllegalStateException e) 
		{
                // I don't know why this gets thrown; catch here to save app
		}
	}
	
	public void loop() throws IOException
	{
		invokePlay( true );
	}
	
	public void unload() throws IOException
	{
		this.stop();
		mp.release();
	}
	
	public void onPrepared(MediaPlayer mPlayer) 
	{
		if (state == PENDING_PLAY) 
		{
			mp.setLooping(false);
			// mp.seekTo(0);
			mp.start();
			state = PLAYING;
		}
		else if ( state == PENDING_LOOP )
		{
			mp.setLooping(true);
			// mp.seekTo(0);
			mp.start();
			state = LOOPING;
		}
		else
		{
			state = PREPARED;
			// mp.seekTo(0);
		}

		// try
		// {
		// 	if (prepared != null){
		// 		preparedCallback.call();
		// 	}
		// }
		// catch (Exception e)
		// {
		// 	e.printStackTrace();
		// }

	}
	
	public void onCompletion(MediaPlayer mPlayer)
	{
		if (state != LOOPING)
		{
			this.state = INVALID;
			try {
				this.stop();
				if (completeCallback != null)
                completeCallback.call();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	// public void onSeekComplete(MediaPlayer mplayer){
	// 	if (completeSeek != null){
	// 		try {
	// 			completeSeek.call();
	// 		}
	// 		catch (Exception e){
	// 			e.printStackTrace();
	// 		}
	// 	}
	// }
}
