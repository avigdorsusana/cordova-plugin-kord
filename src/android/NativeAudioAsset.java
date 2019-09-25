package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.ArrayList;
import android.media.MediaDataSource;
import java.util.concurrent.Callable;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

public class NativeAudioAsset
{

	private ArrayList<NativeAudioAssetComplex> voices;
	private int playIndex = 0;

	// private Callable<Void> completeCb;
	
	public NativeAudioAsset(AssetFileDescriptor afd, int numVoices, float volume) throws IOException
	{
		voices = new ArrayList<NativeAudioAssetComplex>();
		
		if ( numVoices < 0 )
			numVoices = 1;
		
		for ( int x=0; x<numVoices; x++) 
		{
			NativeAudioAssetComplex voice = new NativeAudioAssetComplex(afd, volume);
			voices.add( voice );
		}
	}

	public NativeAudioAsset(String file, int numVoices, float volume, Context context) throws IOException
	{
		voices = new ArrayList<NativeAudioAssetComplex>();
		
		if ( numVoices < 0 )
			numVoices = 1;
		
		for ( int x=0; x<numVoices; x++) 
		{
			NativeAudioAssetComplex voice = new NativeAudioAssetComplex(file, volume, context);
			voices.add( voice );
		}
	}

	// public NativeAudioAsset(MediaDataSource data, int numVoices, float volume) throws IOException
	// {
	// 	voices = new ArrayList<NativeAudioAssetComplex>();
		
	// 	if ( numVoices < 0 )
	// 		numVoices = 1;
		
	// 	for ( int x=0; x<numVoices; x++) 
	// 	{
	// 		NativeAudioAssetComplex voice = new NativeAudioAssetComplex(data, volume);
	// 		voices.add( voice );
	// 	}
	// }
	
	public void play(Callable<Void> completeCb) throws IOException
	{
		NativeAudioAssetComplex voice = voices.get(playIndex);
		voice.play(completeCb);
		playIndex++;
		playIndex = playIndex % voices.size();
	}

	public boolean pause()
	{
		boolean wasPlaying = false;
		for ( int x=0; x<voices.size(); x++)
		{
				NativeAudioAssetComplex voice = voices.get(x);
				wasPlaying |= voice.pause();
		}
		return wasPlaying;
	}

	public void resume()
	{
		// only resumes first instance, assume being used on a stream and not multiple sfx
		if (voices.size() > 0)
		{
				NativeAudioAssetComplex voice = voices.get(0);
				voice.resume();
		}
	}

	public void trueStop()
	{
		for ( int x=0; x<voices.size(); x++) 
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.trueStop();
		}
	}

    public void stop()
	{
		for ( int x=0; x<voices.size(); x++) 
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.stop();
		}
	}

	// public void seek(int time)
	// {
	// 	// Time is not actually in milliseconds when it gets here
	// 	// sorry future me
	// 	for ( int x=0; x<voices.size(); x++) 
	// 	{
	// 		NativeAudioAssetComplex voice = voices.get(x);
	// 		voice.seek(time * 1000);
	// 	}
	// }

	public int seek(int time)
	{
		// Time is not actually in milliseconds when it gets here
		// sorry future me
		int currentSec = -1;
		for ( int x=0; x<voices.size(); x++) 
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.seek(time * 1000);
			currentSec = voice.getCurrentTime();
		}

		return currentSec;
	}

	// public void seek(int time, Callable<Void> completeCb) throws IOException
	// {
	// 	//Time is not actually in milliseconds when it gets here
	// 	//sorry future me
	// 	// for ( int x=0; x<voices.size(); x++) 
	// 	// {
	// 	// 	NativeAudioAssetComplex voice = voices.get(x);
	// 	// 	voice.seek(time * 1000, completeCb);
	// 	// }
	// }

	public void prepare() throws IOException
	{
		for ( int x=0; x<voices.size(); x++) 
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.invokePrepare();
		}
	}

	public int duration()
	{
		// for ( int x=0; x<voices.size(); x++) 
		// {
		// 	NativeAudioAssetComplex voice = voices.get(x);
		// 	voice.seek(timeMS);
		// }
		NativeAudioAssetComplex voice = voices.get(0);
		return (voice.getDuration() / 1000); //time in sec

	}

	public int currentTime()
	{
		// for ( int x=0; x<voices.size(); x++) 
		// {
		// 	NativeAudioAssetComplex voice = voices.get(x);
		// 	voice.seek(timeMS);
		// }
		NativeAudioAssetComplex voice = voices.get(0);
		return (voice.getCurrentTime() / 1000); //time in sec

	}

	public int currentTime(Boolean convert)
	{
		// for ( int x=0; x<voices.size(); x++) 
		// {
		// 	NativeAudioAssetComplex voice = voices.get(x);
		// 	voice.seek(timeMS);
		// }
		NativeAudioAssetComplex voice = voices.get(0);
		int timeToReturn = -1;
		if (!convert){
			timeToReturn = voice.getCurrentTime();
		}
		else{
			timeToReturn = (voice.getCurrentTime() / 1000); //time in sec
		}

		return  timeToReturn;

	}

	public int getState(){
		NativeAudioAssetComplex voice = voices.get(0);
		return voice.getState();
	}
	
	public void loop() throws IOException
	{
		NativeAudioAssetComplex voice = voices.get(playIndex);
		voice.loop();
		playIndex++;
		playIndex = playIndex % voices.size();
	}
	
	public void unload() throws IOException
	{
		this.stop();
		for ( int x=0; x<voices.size(); x++) 
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.unload();
		}
		voices.removeAll(voices);
	}
	
	public void setVolume(float volume)
	{
		for (int x = 0; x < voices.size(); x++)
		{
			NativeAudioAssetComplex voice = voices.get(x);
			voice.setVolume(volume);
		}
	}

	// public void storeCallback(Callable<Void> completeCb){
	// 	this.completeCb = completeCb;
	// }

	// @Override
	// public void run(){
	// 	this.play(completeCb);
	// }
}