//
//
//  NativeAudio.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import android.net.Uri;
import android.os.Environment;
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
	public static final String STOP="stop";
	public static final String LOOP="loop";
	public static final String UNLOAD="unload";
    public static final String ADD_COMPLETE_LISTENER="addCompleteListener";
	public static final String SET_VOLUME_FOR_COMPLEX_ASSET="setVolumeForComplexAsset";

	private static final String LOGTAG = "NativeAudio";
	private static String _dir = "";
	
	private static HashMap<String, NativeAudioAsset> assetMap;
    private static ArrayList<NativeAudioAsset> resumeList;
    private static HashMap<String, CallbackContext> completeCallbacks;
    private boolean fadeMusic = false;

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
				AssetManager am = ctx.getResources().getAssets();
				// AssetFileDescriptor afd = am.openFd(fullPath);
				AssetFileDescriptor afd = am.openFd(assetDirectory);

				NativeAudioAsset asset = new NativeAudioAsset(
						afd, voices, (float)volume);
				assetMap.put(audioID, asset);

				return new PluginResult(Status.OK);
			} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}		
	}

	private PluginResult executePlayAll(JSONArray data){
		// final String audioID;
		for (Callable key : assetMap) {
			try {
				// audioID = data.getString(0);
				//Log.d( LOGTAG, "play - " + audioID );
				if (assetMap.containsKey(key)) {
					NativeAudioAsset asset = assetMap.get(key);
					if (LOOP.equals(action))
						asset.loop();
					else
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
				} else {
					return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
				}
			} catch (JSONException e) {
				return new PluginResult(Status.ERROR, e.toString());
			} catch (IOException e) {
				return new PluginResult(Status.ERROR, e.toString());
			}
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

	private PluginResult executePreloadDownload(JSONArray data){
		final DownloadActivity downloadTask = new DownloadActivity(this.cordova.getActivity().getApplicationContext());
		try {
			if(data != null && data.length() > 0){
				downloadTask.execute(data.getString(0), data.getString(1));
			}
		}
		catch (Exception e){
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK, this._dir);
	}

	private void _getDownloadFolderPath_AsyncDebug(String path){
		this._dir = path;
	}

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
		            	callbackContext.sendPluginResult( executePlayAll(data) );
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
	

	//Async Custom Downloader
	private class DownloadActivity extends AsyncTask<String, Integer, String>{
        private Context appContext;

        public DownloadActivity(Context context){
            this.appContext = context;
        }

        @Override
        protected String doInBackground(String... fileToDownload){
            URL remoteFile;
            InputStream istream = null;
            OutputStream ostream = null;
            HttpURLConnection connection = null;
			String assetDirectory = appContext.getFilesDir().getAbsolutePath();
			String filepath = "";
            // File _manager = new File(assetDirectory);
            Log.d("~~DOWNLOAD", "Download Directory is " + assetDirectory);

            try {
                // if (!_manager.exists()){
                //     Log.d("~~DOWNLOAD", "Assets folder doesn't exist. Creating.");
                //     _manager.mkdir();
                // }

                Log.d("~~DOWNLOAD", "File: " + fileToDownload[1]);
                remoteFile = new URL(fileToDownload[1]);
                connection = (HttpURLConnection) remoteFile.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }
                else{
                    Log.d("~~DOWNLOAD", "ok");
                }

//                istream = connection.getInputStream();
                Log.d("~~Write", fileToDownload[1]);
                istream = new BufferedInputStream(connection.getInputStream());
                ostream = new FileOutputStream(assetDirectory + "/" + fileToDownload[0] + ".mp3");
				Log.d("~~DOWNLOAD", "Starting to download to " + assetDirectory + "/" + fileToDownload[0] + ".mp3");
				filepath += assetDirectory + "/" + fileToDownload[1] + ".mp3";

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = istream.read(data)) != -1) {
                    Log.d("~~~WRITER", "write " + istream.read(data));
                    // allow canceling with back button
                    if (isCancelled()) {
                        istream.close();
                        return null;
                    }
                    total += count;
                    ostream.write(data, 0, count);
                }
            }
            catch(Exception e) {
                Log.e("~~DOWNLOAD", e.getMessage());
;               Log.d("~~DOWNLOAD","exception");
				_getDownloadFolderPath_AsyncDebug(assetDirectory);
                return e.toString();
            }
            finally {
                try {
                    if (ostream != null)
                        ostream.close();

                    if (istream != null)
                        istream.close();
                }
                catch(Exception ignored) {
                    //Nothing to do?
                }

                if (connection != null)
					connection.disconnect();
				
				if(_dir == "")
					_getDownloadFolderPath_AsyncDebug(filepath);
			}
            return null;
        }
    }

}
