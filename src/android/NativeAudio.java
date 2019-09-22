package com.rjfun.cordova.plugin.nativeaudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Calendar;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;

import android.os.AsyncTask;
import android.app.DownloadManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
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
	// private static int trackcount;



	private static Calendar playTime = Calendar.getInstance();
	private static Timer playTimer;





    public void setOptions(JSONObject options) {
		if(options != null) {
			if(options.has(OPT_FADE_MUSIC)) this.fadeMusic = options.optBoolean(OPT_FADE_MUSIC);
		}
	}

	private PluginResult executePreload(JSONArray data) {
		String audioID;
		String assetDirectory = this.cordova.getActivity().getApplicationContext().getFilesDir().getAbsolutePath();
		try {
			audioID = data.getString(0);
			if (!assetMap.containsKey(audioID)) {
				String assetPath = data.getString(1);

				Log.d(LOGTAG, "preloadComplex - " + audioID + ": " + assetPath);
				
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

				// String fullPath = "www/".concat(assetPath);

				Context ctx = cordova.getActivity().getApplicationContext();
				// AssetManager am = ctx.getResources().getAssets();
				// AssetFileDescriptor afd = am.openFd(fullPath);
				// AssetFileDescriptor afd = am.openFd(assetDirectory + "/" + audioID + ".mp3");

				NativeAudioAsset asset = new NativeAudioAsset(
					assetDirectory + "/" + audioID + ".mp3", voices, (float)volume, ctx);

					// asset.prepare(new Callable<Void>() {
					// 	public Void call() throws Exception {
					// 		if (completeCallbacks != null) {
					// 			CallbackContext callbackContext = completeCallbacks.get(key);
					// 			if (callbackContext != null) {
					// 			JSONObject done = new JSONObject();
					// 			done.put("id", key);
					// 			// callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
					// 			}
					// 		}
					// 		return null;
					// 	}
					// });
				// NativeAudioAsset asset = new NativeAudioAsset(
				// 	afd, voices, (float)volume);

				//asset.prepare();

				assetMap.put(audioID, asset);
			} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			// Writer writer = new StringWriter();
			// e.printStackTrace(new PrintWriter(writer));
			// String s = writer.toString();
			// return new PluginResult(Status.ERROR, s);
			return new PluginResult(Status.ERROR, "IOException");
		}
		
		return new PluginResult(Status.OK);
	}
	
	private PluginResult executePlayOrLoop(String action, JSONArray data) {
		final String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "play - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				if (LOOP.equals(action))
					asset.loop();
				else
					asset.play(new Callable<Void>() {
                        public Void call() throws Exception {
							if (completeCallbacks != null) {
								CallbackContext callbackContext = completeCallbacks.get(audioID);
								if (callbackContext != null) {
								JSONObject done = new JSONObject();
								done.put("id", audioID);
								callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
								}
							}
                            return null;
                        }
                    });
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
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK);
	}

//======================CUSTOM====================================
//================================================================
//================================================================

	private PluginResult executePreloadDownload(JSONArray data){
		Context appContext = this.cordova.getActivity().getApplicationContext();
		String assetDirectory = "";

		try {
			URLConnection connection = new 	URL(data.getString(1)).openConnection();
			InputStream istream = connection.getInputStream();

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
			}
		}
		catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		catch (IOException e){
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		if (assetDirectory == "")
			return new PluginResult(Status.ERROR, "Asset not downloaded");
		else
			return new PluginResult(Status.OK, assetDirectory + "|" + cordova.getThreadPool());
	}

	private PluginResult executeSyncAll(){
		int x = 0, curtime = 0, state = -1;
		String debug = "";

		for (String key : assetMap.keySet()) {
			try {
				// audioID = data.getString(0);
				//Log.d( LOGTAG, "play - " + audioID );
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					if (x == 0) curtime = getCurrentTime(key);
					asset.trueStop();
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

		return new PluginResult(Status.OK, debug);
	}

	private PluginResult executePlayAll(){
		// final String audioID;
		
		// int x = 0, curtime = 0;
		// for (String key : assetMap.keySet()) {
		// 	try {
		// 		// audioID = data.getString(0);
		// 		//Log.d( LOGTAG, "play - " + audioID );
		// 		if (assetMap.containsKey(key)) {
		// 			NativeAudioAsset asset = assetMap.get(key);
		// 			if (x == 0) curtime = getCurrentTime(key);
		// 			asset.seek(curtime);
		// 			x++;
		// 		}
		// 	}
		// 	catch (Exception e) {
		// 		return new PluginResult(Status.ERROR, e.toString());
		// 	}
		// }

		//trackcount = 0;
		//synctime = 0;
		// while (trackcount < assetMap.size()){
		// 	NativeAudioAsset _asset = assetMap.get(trackcount);
		// 	if (trackcount == 0) synctime = _asset.currentTime();
		// 	try{
		// 		// _asset.seek(synctime, new Callable<Void>(){
		// 		// 	public Void call() throws Exception {
		// 		// 		// trackcount++;
		// 		// 		return null;
		// 		// 	}
		// 		// });

		// 		_asset.seek(synctime);
		// 		trackcount++;
		// 	}	
		// 	catch (Exception e){
		// 		return new PluginResult(Status.ERROR, e.toString());
		// 	}
		// }

		String debug = "";
		playTime.getTime();
		playTime.add(Calendar.MILLISECOND, 500);

		for (String key : assetMap.keySet()) {
			try {
				// audioID = data.getString(0);
				//Log.d( LOGTAG, "play - " + audioID );
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					// if (LOOP.equals(action))
					// 	asset.loop();
					// else

						synctime = asset.currentTime();
						debug += key + "|" + synctime + ",";
						
						//timer task goes here
						asset.storeCallback(new Callable<Void>() {
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

						playTimer.schedule(
							asset.run(),
							playTime
						);
						
						// asset.play(new Callable<Void>() {
						// 	public Void call() throws Exception {
						// 		if (completeCallbacks != null) {
						// 			CallbackContext callbackContext = completeCallbacks.get(key);
						// 			if (callbackContext != null) {
						// 			JSONObject done = new JSONObject();
						// 			done.put("id", key);
						// 			// callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
						// 			}
						// 		}
						// 		return null;
						// 	}
						// });
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (Exception e) {
				return new PluginResult(Status.ERROR, e.toString());
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
		for (String key : assetMap.keySet()) {
			try {
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
						// asset.seek(data.getInt(0));
						asset.seek(data.getInt(0));
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (Exception e) {
				return new PluginResult(Status.ERROR, "eeee " + e.toString());
			}
		}
		return new PluginResult(Status.OK); 
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
}
