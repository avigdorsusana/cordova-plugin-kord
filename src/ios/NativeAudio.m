#import "NativeAudio.h"
#import <AVFoundation/AVAudioSession.h>

@implementation NativeAudio

NSString* ERROR_ASSETPATH_INCORRECT = @"(NATIVE AUDIO) Asset not found.";
NSString* ERROR_REFERENCE_EXISTS = @"(NATIVE AUDIO) Asset reference already exists.";
NSString* ERROR_REFERENCE_MISSING = @"(NATIVE AUDIO) Asset reference does not exist.";
NSString* ERROR_REFERENCE_FAIL = @"(NATIVE AUDIO) Asset reference cant download.";
// NSString* ERROR_REFERENCE_DWNLD_FAIL = @"(NATIVE AUDIO) Asset reference cannot download.";
// NSString* ERROR_REFERENCE_WRITE_FAIL = @"(NATIVE AUDIO) Asset reference cannot write to file.";
NSString* ERROR_TYPE_RESTRICTED = @"(NATIVE AUDIO) Action restricted to assets loaded using preloadComplex.";
NSString* ERROR_VOLUME_NIL = @"(NATIVE AUDIO) Volume cannot be empty.";
/* NSString* ERROR_SPEED_NIL = @"(NATIVE AUDIO) Speed cannot be empty."; */
NSString* ERROR_SEEK_NIL = @"(NATIVE AUDIO) Seek cannot be empty.";
NSString* ERROR_VOLUME_FORMAT = @"(NATIVE AUDIO) Volume is declared as float between 0.0 - 1.0";
NSString* ERROR_SPEED_FORMAT = @"(NATIVE AUDIO) Speed is declared as float between 0.5 - 2.0";

NSString* INFO_ASSET_LOADED = @"(NATIVE AUDIO) Asset loaded.";
NSString* INFO_ASSET_UNLOADED = @"(NATIVE AUDIO) Asset unloaded.";
NSString* INFO_PLAYBACK_PLAY = @"(NATIVE AUDIO) Play.";
NSString* INFO_PLAYBACK_PLAYALL = @"(NATIVE AUDIO) Play All.";
NSString* INFO_PLAYBACK_STOP = @"(NATIVE AUDIO) Stop.";
NSString* INFO_PLAYBACK_PAUSE = @"(NATIVE AUDIO) Pause.";
NSString* INFO_PLAYBACK_PAUSEALL = @"(NATIVE AUDIO) Pause All.";
NSString* INFO_PLAYBACK_LOOP = @"(NATIVE AUDIO) Loop.";
NSString* INFO_PLAYBACK_SEEKALL = @"(NATIVE AUDIO) Seek All.";
NSString* INFO_VOLUME_CHANGED = @"(NATIVE AUDIO) Volume changed.";
NSString* INFO_PLAYBACK_DURATION = @"(NATIVE AUDIO) Duration.";
NSString* INFO_VOLUME_CURRENTTIME = @"(NATIVE AUDIO) Current Time.";
NSString* INFO_PLAYBACK_SPEED = @"(NATIVE AUDIO) Speed changed.";



- (void)pluginInitialize
{
    self.fadeMusic = NO;

    AudioSessionInitialize(NULL, NULL, nil , nil);
    AVAudioSession *session = [AVAudioSession sharedInstance];
    
	[session setActive: NO error: nil];
    NSError *setCategoryError = nil;

    //== allows the application to mix its audio with audio from other apps
    if (![session setCategory:AVAudioSessionCategoryAmbient
                  withOptions:AVAudioSessionCategoryOptionMixWithOthers
                        error:&setCategoryError]) {

        NSLog (@"Error setting audio session category.");
        return;
    }

    [session setActive: YES error: nil];
    [session setCategory:AVAudioSessionCategoryPlayback error:nil];
}

- (void) parseOptions:(NSDictionary*) options
{
    if ((NSNull *)options == [NSNull null]) return;

    NSString* str = nil;

    str = [options objectForKey:OPT_FADE_MUSIC];
    if(str) self.fadeMusic = [str boolValue];
}

- (void) setOptions:(CDVInvokedUrlCommand *)command
{
    if([command.arguments count] > 0) {
        NSDictionary* options = [command argumentAtIndex:0 withDefault:[NSNull null]];
        [self parseOptions:options];
    }

    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
}

- (void) preloadSimple:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];
    NSString *assetPath = [arguments objectAtIndex:1];

    if(audioMapping == nil) {
        audioMapping = [NSMutableDictionary dictionary];
    }

    NSNumber* existingReference = audioMapping[audioID];

    [self.commandDelegate runInBackground:^{
        if (existingReference == nil) {

            NSString* basePath = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"www"];
            NSString* path = [NSString stringWithFormat:@"%@", assetPath];
            NSString* pathFromWWW = [NSString stringWithFormat:@"%@/%@", basePath, assetPath];

            if ([[NSFileManager defaultManager] fileExistsAtPath : path]) {
                NSURL *pathURL = [NSURL fileURLWithPath : path];
                CFURLRef soundFileURLRef = (CFURLRef) CFBridgingRetain(pathURL);
                SystemSoundID soundID;
                AudioServicesCreateSystemSoundID(soundFileURLRef, & soundID);
                audioMapping[audioID] = [NSNumber numberWithInt:soundID];

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_LOADED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else if ([[NSFileManager defaultManager] fileExistsAtPath : pathFromWWW]) {
                NSURL *pathURL = [NSURL fileURLWithPath : pathFromWWW];
                CFURLRef        soundFileURLRef = (CFURLRef) CFBridgingRetain(pathURL);
                SystemSoundID soundID;
                AudioServicesCreateSystemSoundID(soundFileURLRef, & soundID);
                audioMapping[audioID] = [NSNumber numberWithInt:soundID];

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_LOADED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else {
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_ASSETPATH_INCORRECT, assetPath];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_EXISTS, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

- (void) preloadComplexDownload:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
	NSString *audioID = [arguments objectAtIndex:0];
    NSString *assetPath = [arguments objectAtIndex:1];
	//NSString* filename = [assetPath lastPathComponent];
	NSString* filename = [NSString stringWithFormat:@"%@.mp3",audioID];

    // [self.commandDelegate runInBackground:^{
        
	// 	NSURL *url = [NSURL URLWithString:assetPath];
        
    //     // NSURLSessionDownloadTask safely downloads network-based files
    //     // to a temp location, regardless of device connection speed.
    //     NSURLSession *thisSession = [NSURLSession sharedSession];
    //     NSURLSessionDownloadTask *downloadAudioTask = [thisSession downloadTaskWithURL:url completionHandler:^(NSURL *location, NSURLResponse *response, NSError *error) {
            
    //         if (!error) {
    //             // Audio file was successfully downloaded.
                
    //             // Destination cache file path.
    //             NSArray *pathArray = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask,YES);
    //             NSString *cachesDirectory = [pathArray objectAtIndex:0];
    //             NSString  *filePath = [cachesDirectory stringByAppendingPathComponent:filename];
                
    //             // NSData dataWithContentsOfURL is safe for local files,
    //             // such as downloaded local temp file location.
    //             NSData *urlData = [NSData dataWithContentsOfURL:location];
    //             if (urlData) {
                    
    //                 // Write to destination cache file (overwrites if it already exists).
    //                 [urlData writeToFile:filePath atomically:YES];
                    
    //                 NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_LOADED, filePath];
    //                 [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
                    
    //             } else {
    //                 // ERROR: urlData is NIL, data could not be written to cache file.
    //                 NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_WRITE_FAIL, filePath];
    //                 [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    //             }
                
    //         } else {
    //             // ERROR: Audio file could NOT be downloaded.
    //             NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_DWNLD_FAIL, audioID];
    //             [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    //         }
            
    //     }];
    //     [downloadAudioTask resume];
        
    // }];

	[self.commandDelegate runInBackground:^{
		NSURL  *url = [NSURL URLWithString:assetPath];
		NSData *urlData = [NSData dataWithContentsOfURL:url];
		
		NSArray   *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
		NSString  *documentsDirectory = [paths objectAtIndex:0];  
		NSString  *filePath = [NSString stringWithFormat:@"%@/%@", documentsDirectory,filename];
		
		if ( urlData ) {
			[urlData writeToFile:filePath atomically:YES];
			
			NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_LOADED, filePath];
			[self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
			
		} else {
			NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_FAIL, filePath];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
			
		}

    }];
}

- (void) preloadComplex:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];
    NSString *assetPath = [arguments objectAtIndex:1];
	//NSString* filename = [assetPath lastPathComponent];
	NSString* filename = [NSString stringWithFormat:@"%@.mp3",audioID];

    NSNumber *volume = nil;
    if ( [arguments count] > 2 ) {
        volume = [arguments objectAtIndex:2];
        if([volume isEqual:nil]) {
            volume = [NSNumber numberWithFloat:1.0f];
        }
    } else {
        volume = [NSNumber numberWithFloat:1.0f];
    }

    NSNumber *voices = nil;
    if ( [arguments count] > 3 ) {
        voices = [arguments objectAtIndex:3];
        if([voices isEqual:nil]) {
            voices = [NSNumber numberWithInt:1];
        }
    } else {
        voices = [NSNumber numberWithInt:1];
    }

    NSNumber *delay = nil;
    if ( [arguments count] > 4 && [arguments objectAtIndex:4] != [NSNull null])
    {
        //== the delay is determines how fast the asset is faded in and out
        delay = [arguments objectAtIndex:4];
    }

    if(audioMapping == nil) {
        audioMapping = [NSMutableDictionary dictionary];
    }

    NSNumber* existingReference = audioMapping[audioID];

    [self.commandDelegate runInBackground:^{
        if (existingReference == nil) {
			// NSArray *pathArray = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask,YES);
			// NSString *cachesDirectory = [pathArray objectAtIndex:0];
			// NSString *path = [cachesDirectory stringByAppendingPathComponent:filename];

			NSArray *pathArray = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask,YES);
			NSString *documentsDirectory = [pathArray objectAtIndex:0];
			NSString *path = [documentsDirectory stringByAppendingPathComponent:filename];
			
			if ([[NSFileManager defaultManager] fileExistsAtPath:path]) {
			
                NativeAudioAsset* asset = [[NativeAudioAsset alloc] initWithPath:path
                                                                      withVoices:voices
                                                                      withVolume:volume
                                                                   withFadeDelay:delay];

                audioMapping[audioID] = asset;

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_LOADED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else {
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_ASSETPATH_INCORRECT, path];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_EXISTS, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }

    }];
}

- (void) play:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    [self.commandDelegate runInBackground:^{
        if (audioMapping) {

            NSObject* asset = audioMapping[audioID];

            if (asset != nil) {
                if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                    NativeAudioAsset *_asset = (NativeAudioAsset*) asset;

                    if(self.fadeMusic) {
                        //== music assets are faded in
                        [_asset playWithFade];
                    } else {
                        [_asset play];
                    }

                    NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_PLAYBACK_PLAY, audioID];
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

                } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                    NSNumber *_asset = (NSNumber*) asset;
                    AudioServicesPlaySystemSound([_asset intValue]);

                    NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_PLAYBACK_PLAY, audioID];
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
                }

            } else {
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

- (void) playAll:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
	
	[self.commandDelegate runInBackground:^{

		if (audioMapping) {
			
			//== sync all tracks with first one
			int x = 0;
			double curtime = 0;
			double time = 0;

			for(id key in audioMapping) {
				
				NSObject* asset = audioMapping[key];
                
                if (asset != nil) {
                    if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                        
                        NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                        
                        if (x == 0) {
                            curtime = [_asset currentTime];
                            time = [_asset deviceCurrentTime] + 0.5;
                        }
                        x++;
                        
                        [_asset setCurrentTime:curtime];
                        [_asset playAt:time];
                        
                    } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_TYPE_RESTRICTED] callbackId:callbackId];
                    }
                    
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_REFERENCE_MISSING] callbackId:callbackId];
                }
			}
            
            //== ALL ASSETS COULD BE PLAYED WITHOUT ERROR, SO RETURN SUCCESS!
            NSString *RESULT = [NSString stringWithFormat:@"%@ |%f|", INFO_PLAYBACK_PLAYALL, curtime];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

- (void) pauseAll:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;

    [self.commandDelegate runInBackground:^{

		if (audioMapping) {

			for(id key in audioMapping) {
                
				 NSObject* asset = audioMapping[key];
                
                if (asset != nil) {
                    if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                        
                        NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                        [_asset pause];
                        
                    } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_TYPE_RESTRICTED] callbackId:callbackId];
                    }
                    
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_REFERENCE_MISSING] callbackId:callbackId];
                }
			}
            
            //== ALL ASSETS COULD BE PAUSED WITHOUT ERROR, SO RETURN SUCCESS!
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: INFO_PLAYBACK_PAUSEALL] callbackId:callbackId];
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

- (void) seekAll:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
	NSNumber *time = [arguments objectAtIndex:0];

    [self.commandDelegate runInBackground:^{

		if (audioMapping) {
            
            for(id key in audioMapping) {
                
                NSObject* asset = audioMapping[key];
                
                if (asset != nil) {
                    if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                        
                        NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                        [_asset seek:time];
                        
                    } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_TYPE_RESTRICTED] callbackId:callbackId];
                    }
                    
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_REFERENCE_MISSING] callbackId:callbackId];
                }
            }
            
            //== ALL ASSETS COULD SEEK WITHOUT ERROR, RETURN SUCCESS!
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: INFO_PLAYBACK_SEEKALL] callbackId:callbackId];
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

- (void) stop:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                if (self.fadeMusic) {
                    //== music assets are faded out
                    [_asset stopWithFade];
                } else {
                    [_asset stop];
                }

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_PLAYBACK_STOP, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else if ( [asset isKindOfClass:[NSNumber class]] ) {

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) pause:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                [_asset pause];

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_PLAYBACK_PAUSE, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else if ( [asset isKindOfClass:[NSNumber class]] ) {

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) duration:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                double time = [_asset duration];
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ |%f|", INFO_PLAYBACK_DURATION, time];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
                
            } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) currentTime:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                double time = [_asset currentTime];
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ |%d|", INFO_VOLUME_CURRENTTIME, time];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
                
            } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) loop:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                [_asset loop];
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_PLAYBACK_LOOP, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) unload:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    if ( audioMapping ) {
        NSObject* asset = audioMapping[audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                [_asset unload];
                
            } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                NSNumber *_asset = (NSNumber*) asset;
                AudioServicesDisposeSystemSoundID([_asset intValue]);
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }

        [audioMapping removeObjectForKey: audioID];

        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_ASSET_UNLOADED, audioID];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}

- (void) setVolumeForComplexAsset:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];
    NSNumber *volume = nil;

    if ( [arguments count] > 1 ) {

        volume = [arguments objectAtIndex:1];

        if ([volume isEqual:nil]) {

            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_VOLUME_NIL, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else if (([volume floatValue] < 0.0f) || ([volume floatValue] > 1.0f)) {

        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_VOLUME_FORMAT, audioID];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }

    if ( audioMapping ) {
        NSObject* asset = [audioMapping objectForKey: audioID];

        if (asset != nil) {

            if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                [_asset setVolume:volume];

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", INFO_VOLUME_CHANGED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: RESULT] callbackId:callbackId];

            } else if ( [asset isKindOfClass:[NSNumber class]] ) {

                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_TYPE_RESTRICTED, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    } else {
        NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
    }
}



- (void) setSpeed:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
	NSNumber *speed = [arguments objectAtIndex:0];

    [self.commandDelegate runInBackground:^{

		/*if (([speed floatValue] < 0.5f) || ([speed floatValue] > 2.0f)) {
			NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_SPEED_FORMAT, audioID];
			[self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
		}*/

		if (audioMapping) {
            
            for(id key in audioMapping) {
                
                NSObject* asset = audioMapping[key];
                
                if (asset != nil) {
                    if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                        
                        NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                        [_asset setSpeed:speed];
                        
                    } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_TYPE_RESTRICTED] callbackId:callbackId];
                    }
                    
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: ERROR_REFERENCE_MISSING] callbackId:callbackId];
                }
            }
            
            //== ALL ASSETS COULD SET SPEED WITHOUT ERROR, RETURN SUCCESS!
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: INFO_PLAYBACK_SPEED] callbackId:callbackId];
            
        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}



- (void) sendCompleteCallback:(NSString*)forId
{
    NSString* callbackId = self->completeCallbacks[forId];
    if (callbackId) {
        NSDictionary* RESULT = [NSDictionary dictionaryWithObject:forId forKey:@"id"];
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:RESULT] callbackId:callbackId];
        [self->completeCallbacks removeObjectForKey:forId];
    }
}

static void (mySystemSoundCompletionProc)(SystemSoundID ssID,void* clientData)
{
    NativeAudio* nativeAudio = (__bridge NativeAudio*)(clientData);
    NSNumber *idAsNum = [NSNumber numberWithInt:ssID];
    NSArray *temp = [nativeAudio->audioMapping allKeysForObject:idAsNum];
    NSString *audioID = [temp lastObject];

    [nativeAudio sendCompleteCallback:audioID];

    // Cleanup, these cb are one-shots
    AudioServicesRemoveSystemSoundCompletion(ssID);
}

- (void) addCompleteListener:(CDVInvokedUrlCommand *)command
{
    NSString *callbackId = command.callbackId;
    NSArray* arguments = command.arguments;
    NSString *audioID = [arguments objectAtIndex:0];

    [self.commandDelegate runInBackground:^{
        if (audioMapping) {

            NSObject* asset = audioMapping[audioID];

            if (asset != nil) {

                if (completeCallbacks == nil) {
                    completeCallbacks = [NSMutableDictionary dictionary];
                }
                completeCallbacks[audioID] = command.callbackId;

                if ([asset isKindOfClass:[NativeAudioAsset class]]) {
                    NativeAudioAsset *_asset = (NativeAudioAsset*) asset;
                    [_asset setCallbackAndId:^(NSString* audioID) {
                        [self sendCompleteCallback:audioID];
                    } audioId:audioID];

                } else if ( [asset isKindOfClass:[NSNumber class]] ) {
                    NSNumber *_asset = (NSNumber*) asset;
                    AudioServicesAddSystemSoundCompletion([_asset intValue],
                                                                       NULL,
                                                                       NULL,
                                                                       mySystemSoundCompletionProc,
                                                                       (__bridge void *)(self));
                }
                
            } else {
                NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, audioID];
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
            }

        } else {
            NSString *RESULT = [NSString stringWithFormat:@"%@ (%@)", ERROR_REFERENCE_MISSING, @"audioMapping"];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: RESULT] callbackId:callbackId];
        }
        
    }];
}

@end
