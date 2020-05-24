package com.rjfun.cordova.plugin.nativeaudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URL;


import org.json.JSONArray;
import org.json.JSONException;

import android.os.AsyncTask;
import android.app.DownloadManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaDataSource;
import android.util.Log;
import android.view.KeyEvent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;


public class NativeAudio extends CordovaPlugin implements AudioManager.OnAudioFocusChangeListener {

    /* options */
    public static final String OPT_FADE_MUSIC = "fadeMusic";

	public static final String ERROR_NO_AUDIOID="A reference does not exist for the specified audio id.";
	public static final String ERROR_AUDIOID_EXISTS="A reference already exists for the specified audio id.";
	
	public static final String SET_OPTIONS="setOptions";
	public static final String PRELOAD_SIMPLE="preloadSimple";
	public static final String PRELOAD_COMPLEX="preloadComplex";
	public static final String PRELOAD_COMPLEX_DOWNLOAD="preloadComplexDownload";
	public static final String PLAY="play";
	public static final String PLAY_ALL="playAll";
	public static final String PAUSE_ALL="pauseAll";
	public static final String SEEK_ALL="seekAll";
	public static final String SYNC_ALL="syncAll";
	public static final String DURATION="duration";
	public static final String CURRENT_TIME="currentTime";
	public static final String STOP="stop";
	public static final String LOOP="loop";
	public static final String UNLOAD="unload";
	public static final String SET_SPEED="setSpeed";
    public static final String ADD_COMPLETE_LISTENER="addCompleteListener";
	public static final String SET_VOLUME_FOR_COMPLEX_ASSET="setVolumeForComplexAsset";

	private static final String LOGTAG = "NativeAudio";
	
	private static HashMap<String, NativeAudioAsset> assetMap;
	private static ArrayList<NativeAudioAsset> resumeList;
	private static ArrayList<Thread> threadList;
	private static HashMap<String, CallbackContext> completeCallbacks;
	// private static HashMap<String, CallbackContext> prepareCallbacks;
	private boolean fadeMusic = false;
	private static int synctime;
	private static int trackcount;
	private static CyclicBarrier barrier = null;





    public void setOptions(JSONObject options) {
		if(options != null) {
			if(options.has(OPT_FADE_MUSIC)) this.fadeMusic = options.optBoolean(OPT_FADE_MUSIC);
		}
	}

	private PluginResult executePreload(JSONArray data) {
		String audioID;
		String songUri;
		String assetDirectory = this.cordova.getActivity().getApplicationContext().getFilesDir().getAbsolutePath();
		String debug = "";

		try {

			audioID = data.getString(0);
			songUri = data.getString(1);

			if (!assetMap.containsKey(audioID)) {

				double volume;
				if (data.length() <= 2) {
					volume = 1.0;
				} else {
					volume = data.getDouble(2);
				}

				int voices;
				if (data.length() <= 3) {
					voices = 1;
				} else {
					voices = data.getInt(3);
				}

				NativeAudioAsset asset;

				if (songUri.indexOf("|file|") > -1) {
					Context ctx = cordova.getActivity().getApplicationContext();

					// ===== 0.5.10 9/24
					asset = new NativeAudioAsset(
						assetDirectory + "/" + audioID + ".mp3", voices, (float)volume, ctx);
				} 
				else {
					asset = new NativeAudioAsset(songUri, voices, (float) volume);
				}

				assetMap.put(audioID, asset);
			}
			else
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
		}
		catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		catch (IOException e){
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}
	
	private PluginResult executePlayOrLoop(String action, JSONArray data) {

		//if (barrier == null) barrier = new CyclicBarrier(assetMap.size());

		final String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "play - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);

				synctime = asset.currentTime();

				if (LOOP.equals(action))
					asset.loop();
				else
					// asset.play(barrier, new Callable<Void>() {
					asset.play(new Callable<Void>() {
                        public Void call() throws Exception {
							if (completeCallbacks != null) {
								CallbackContext callbackContext = completeCallbacks.get(audioID);
								if (callbackContext != null) {
								JSONObject done = new JSONObject();
								done.put("id", audioID);
								callbackContext.sendPluginResult(new PluginResult(Status.OK, "|" + synctime + "|" + done));
								}
							}
                            return null;
                        }
                    });
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (Exception e) {
			return new PluginResult(Status.ERROR,Log.getStackTraceString(e));
		}
		
		return new PluginResult(Status.OK, "|" + synctime + "|");
	}

	private PluginResult executeStop(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "stop - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.stop();
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}			
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}

	private PluginResult executeUnload(JSONArray data) {
		String audioID;
		try {
			if (barrier != null) barrier = null;

			audioID = data.getString(0);
			Log.d( LOGTAG, "unload - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.unload();
				assetMap.remove(audioID);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}

	private PluginResult executeSetVolumeForComplexAsset(JSONArray data) {
		String audioID;
		float volume;
		try {
			audioID = data.getString(0);
			volume = (float) data.getDouble(1);
			Log.d( LOGTAG, "setVolume - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.setVolume(volume);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, Log.getStackTraceString(e));
		}
		return new PluginResult(Status.OK);
	}

//======================CUSTOM====================================
//================================================================
//================================================================

	public PluginResult executePreloadDownload(JSONArray data){
		Context appContext = this.cordova.getActivity().getApplicationContext();
		String assetDirectory = "";
		int status_code = -1;

		try {
			URL url = new URL(data.getString(1));
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			InputStream istream = connection.getInputStream();
			status_code = connection.getResponseCode();

			assetDirectory = appContext.getFilesDir().getAbsolutePath() + "/" + data.getString(0) + ".mp3";
			OutputStream ostream = new FileOutputStream(new File(assetDirectory));

			byte[] buffer = new byte[4096];
			int length;
			while ((length = istream.read(buffer)) > 0) {
				ostream.write(buffer, 0, length);
			}

			try {
				if (ostream != null)
					ostream.close();

				if (istream != null)
					istream.close();
			}
			catch(Exception ignored) {
				//Nothing to do?
				return new PluginResult(Status.ERROR, ignored.getMessage() + " 12345 ");
			}
		}
		catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.getMessage());
		}
		catch (IOException e){
			String stackTrace = Log.getStackTraceString(e); 
			return new PluginResult(Status.ERROR, "HTTP STATUS: " + status_code + "\n" + stackTrace);
		}
		
		if (assetDirectory == "")
			return new PluginResult(Status.ERROR, "Asset not downloaded");
		else
			return new PluginResult(Status.OK, assetDirectory + "|" + cordova.getThreadPool());
	}

	private PluginResult executeSyncAll(){
		int x = 0, curtime = 0, state = -1;
		/*
		for (String key : assetMap.keySet()) {
			try {
				// audioID = data.getString(0);
				//Log.d( LOGTAG, "play - " + audioID );
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					if (x == 0) curtime = getCurrentTime(key);
					asset.stop();
					x++;
				}
			}
			catch (Exception e) {
				return new PluginResult(Status.ERROR, e.toString());
			}
		}
		
		for (String key : assetMap.keySet()) {
			try {
				// audioID = data.getString(0);
				//Log.d( LOGTAG, "play - " + audioID );
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					state = asset.getState();
					// asset.prepare();
					asset.seek(curtime);
					debug += key + "|" + curtime + "|" + state;
				}
			}
			catch (Exception e) {
				return new PluginResult(Status.ERROR, e.toString() + " ////////// " + state);
			}
		}
		*/

		NativeAudioAsset asset;
		for (String key : assetMap.keySet()) {
			try{
				if (assetMap.containsKey(key)){
					if (x == 0) curtime = getCurrentTime(key);
					asset = assetMap.get(key);
					asset.stop();
					asset.seek(curtime);
				}
				x++;
			}
			catch (Exception e){
				return new PluginResult(Status.ERROR, Log.getStackTraceString(e));
			}
		}

		return new PluginResult(Status.OK, curtime);
	}

	private PluginResult executePlayAll(){
		String debug = "";

		//if (barrier == null) barrier = new CyclicBarrier(assetMap.size());

		for (String key : assetMap.keySet()) {
			try {

				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					// if (LOOP.equals(action))
					// 	asset.loop();
					// else

						synctime = asset.currentTime();
						debug += key + "|" + synctime + "|" + asset.currentTime();

						// Thread mThread =
						// 	new Thread(){
						// 		public void run() {
						// 			try {
										//asset.play(barrier, new Callable<Void>() {
										asset.play(new Callable<Void>() {
											public Void call() throws Exception {
												if (completeCallbacks != null) {
													CallbackContext callbackContext = completeCallbacks.get(key);
													if (callbackContext != null) {
													JSONObject done = new JSONObject();
													done.put("id", key);
													// callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
													}
												}
												return null;
											}
										});	
							// 		}
							// 		catch (Exception e){
							// 			//debug += "||||ERROR IN THREAD";
							// 		}
	
							// 	}
							// };
						//mThread.start();
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (Exception e) {
				return new PluginResult(Status.ERROR, Log.getStackTraceString(e));
			}
			// } catch (IOException e) {
			// 	return new PluginResult(Status.ERROR, e.toString());
			// }
		}
		return new PluginResult(Status.OK, "|" + synctime + "|" + debug + "|");
	}

	private PluginResult executePauseAll(){
		String debug =  "";
		for (String key : assetMap.keySet()) {
			try {
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
						debug += key + "|" + synctime + ",";
						asset.pause();
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (Exception e) {
				return new PluginResult(Status.ERROR, e.toString());
			}
		}
		return new PluginResult(Status.OK, debug);
	}
	
	private PluginResult executeSeekAll(JSONArray data){
		String debug = "";
		for (String key : assetMap.keySet()) {
			int currentSec = -1;
			try {
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
						// asset.seek(data.getInt(0));
						asset.seek(data.getInt(0));
						currentSec = asset.getCurrentPosition(data.getInt(0));
						debug += "seekTo: " + data.getInt(0) + ", " + key + ": " + currentSec;
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (Exception e) {
				return new PluginResult(Status.ERROR, "eeee " + e.toString());
			}
		}
		return new PluginResult(Status.OK, debug); 
	}

	private PluginResult getDuration(JSONArray data){
		int timeToReturn = 0;
		try {
			if (assetMap.containsKey(data.getString(0))) {
				NativeAudioAsset asset = assetMap.get(data.getString(0));
				timeToReturn = (asset.duration());
				if (timeToReturn == -1)
					return new PluginResult(Status.ERROR, "Get Time Error: " + timeToReturn);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (Exception e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK,  "|" + timeToReturn + "|"); 
	}

	private PluginResult getCurrentTime(JSONArray data){
		int timeToReturn = 0;
		String test = "";
		try {
			if (assetMap.containsKey(data.getString(0))) {
				test = data.getString(0);
				NativeAudioAsset asset = assetMap.get(data.getString(0));
				timeToReturn = asset.currentTime();
				if (timeToReturn == -1)
					return new PluginResult(Status.ERROR, "Get Time Error: " + timeToReturn);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (Exception e) {
			return new PluginResult(Status.ERROR, e.toString() + " " + test);
		}
		return new PluginResult(Status.OK, "|" + timeToReturn + "|"); 
	}

	private int getCurrentTime(String assetKey){
		int timeToReturn = 0;
		try {
			if (assetMap.containsKey(assetKey)) {
				NativeAudioAsset asset = assetMap.get(assetKey);
				timeToReturn = asset.currentTime();
				// if (timeToReturn == -1)
					// return new PluginResult(Status.ERROR, "Get Time Error: " + timeToReturn);
			} else {
				// return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (Exception e) {
			// return new PluginResult(Status.ERROR, e.toString());
		}
		return timeToReturn;
		// return new PluginResult(Status.OK, "|" + timeToReturn + "|"); 
	}

	private PluginResult executeSetSpeed(JSONArray data){
		try {
			NativeAudioAsset asset;
			for (String key : assetMap.keySet()) {
				asset = assetMap.get(key);
				asset.setSpeed((float) data.getDouble(0));
			}
		}
		catch (JSONException e){
			return new PluginResult(Status.ERROR, "JSONException");
		}
		return new PluginResult(Status.OK);
	}

//===============================================================
//===============================================================
//===============================================================

	@Override
	protected void pluginInitialize() {
		AudioManager am = (AudioManager)cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

	        int result = am.requestAudioFocus(this,
	                // Use the music stream.
	                AudioManager.STREAM_MUSIC,
	                // Request permanent focus.
	                AudioManager.AUDIOFOCUS_GAIN);

		// Allow android to receive the volume events
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, false);
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, false);
	}

	@Override
	public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
		Log.d(LOGTAG, "Plugin Called: " + action);
		
		PluginResult result = null;
		initSoundPool();
		
		try {
			if (SET_OPTIONS.equals(action)) {
                JSONObject options = data.optJSONObject(0);
                this.setOptions(options);
                callbackContext.sendPluginResult( new PluginResult(Status.OK) );

			} else if (PRELOAD_SIMPLE.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });				
				
			} else if (PRELOAD_COMPLEX_DOWNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreloadDownload(data) );
		            }
		        });	

			} else if (PRELOAD_COMPLEX.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });				

			} else if (PLAY.equals(action) || LOOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
						callbackContext.sendPluginResult( executePlayOrLoop(action, data) );
		            }
		        });				
				
			} else if (PLAY_ALL.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePlayAll() );
		            }
		        });				
				
			} else if (PAUSE_ALL.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePauseAll() );
		            }
				});

			} else if (SYNC_ALL.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeSyncAll() );
		            }
		        });				
				
			} else if (SEEK_ALL.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeSeekAll(data) );
		            }
		        });				
				
			}  else if (SET_SPEED.equals(action)) {
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					callbackContext.sendPluginResult( executeSetSpeed(data) );
				}
			});				
			
			} else if (DURATION.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( getDuration(data) );
		            }
		        });				
				
			} else if (CURRENT_TIME.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( getCurrentTime(data) );
		            }
		        });				
				
			} else if (STOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeStop(data) );
		            }
		        });

            } else if (UNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        executeStop(data);
                        callbackContext.sendPluginResult( executeUnload(data) );
                    }
                });
            } else if (ADD_COMPLETE_LISTENER.equals(action)) {
                if (completeCallbacks == null) {
                    completeCallbacks = new HashMap<String, CallbackContext>();
                }
                try {
                    String audioID = data.getString(0);
                    completeCallbacks.put(audioID, callbackContext);
                } catch (JSONException e) {
                    callbackContext.sendPluginResult(new PluginResult(Status.ERROR, e.toString()));
				}	
	    } else if (SET_VOLUME_FOR_COMPLEX_ASSET.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
			public void run() {
	                        callbackContext.sendPluginResult( executeSetVolumeForComplexAsset(data) );
                    }
                 });
	    }
            else {
                result = new PluginResult(Status.OK);
            }
		} catch (Exception ex) {
			result = new PluginResult(Status.ERROR, ex.toString());
		}

		if(result != null) callbackContext.sendPluginResult( result );
		return true;
	}

	private void initSoundPool() {

		if (assetMap == null) {
			assetMap = new HashMap<String, NativeAudioAsset>();
		}

        if (resumeList == null) {
            resumeList = new ArrayList<NativeAudioAsset>();
        }
	}

    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Pause playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // Resume playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Stop playback
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        for (HashMap.Entry<String, NativeAudioAsset> entry : assetMap.entrySet()) {
            NativeAudioAsset asset = entry.getValue();
            boolean wasPlaying = asset.pause();
            if (wasPlaying) {
                resumeList.add(asset);
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        while (!resumeList.isEmpty()) {
            NativeAudioAsset asset = resumeList.remove(0);
            asset.resume();
        }
	}

	// private class DownloadActivity extends AsyncTask<String, String, CallbackContext>{
	// 	private Context appContext;
	// 	private CallbackContext callbackContext;
    //     public DownloadActivity(Context context, CallbackContext cbContext){
	// 		this.appContext = context;
	// 		this.callbackContext = cbContext;
    //     }

    //     @Override
    //     protected void doInBackground(String... fileToDownload){
    //     }
	// }

	// private class ScheduledPlay extends TimerTask{
	// 	NativeAudioAsset _asset;
	// 	String _id;
	// 	ScheduledPlay(NativeAudioAsset asset, String assetId){
	// 		_asset = asset;
	// 		_id = assetId;
	// 	}

	// 	@Override
	// 	public void run(){
	// 		try{
	// 			_asset.play(new Callable<Void>() {
	// 				public Void call() throws Exception {
	// 					if (completeCallbacks != null) {
	// 						CallbackContext callbackContext = completeCallbacks.get(_id);
	// 						if (callbackContext != null) {
	// 						JSONObject done = new JSONObject();
	// 						done.put("id", _id);
	// 						// callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
	// 						}
	// 					}
	// 					return null;
	// 				}
	// 			});
	// 		}
	// 		catch (Exception e){

	// 		}
	// 	}
	// }

	// private class AudioDataSource extends MediaDataSource{
	// 	byte[] array;
	// 	public AudioDataSource(byte[] array){
	// 		this.array = array;
	// 	}


	// 	@Override
	// 	public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
	// 		int length = array.length;
	// 		if (position >= length) {
	// 		return -1; // -1 indicates EOF
	// 		}
	// 		if (position + size > length) {
	// 			size -= ((position + size) - length);
	// 		}
	// 		System.arraycopy(array, (int)position, buffer, offset, size);
	// 		return size;
	// 	}
	// 	@Override
	// 	public long getSize() throws IOException {
	// 		return array.length;
	// 	}

	// 	@Override 
	// 	public void close(){

	// 	}

	// }
}
