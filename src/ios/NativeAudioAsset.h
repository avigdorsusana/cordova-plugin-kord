#import <Foundation/Foundation.h>
#import <AVFoundation/AVAudioPlayer.h>

typedef void (^CompleteCallback)(NSString*);

@interface NativeAudioAsset : NSObject<AVAudioPlayerDelegate> {
    NSMutableArray* voices;
    int playIndex;
    NSString* audioId;
    CompleteCallback finished;
    NSNumber *initialVolume;
    NSNumber *fadeDelay;
}

- (id) initWithPath:(NSString*) path withVoices:(NSNumber*) numVoices withVolume:(NSNumber*) volume withFadeDelay:(NSNumber *)delay;
- (void) play;
- (void) playWithFade;
- (void) pause;
- (void) stop;
- (void) seek:(NSNumber*) time;
//- (void) seek;
- (void) stopWithFade;
- (void) loop;
- (void) unload;
- (double) duration;
- (void) currentTime;
- (void) setVolume:(NSNumber*) volume;
- (void) setCallbackAndId:(CompleteCallback)cb audioId:(NSString*)audioId;
- (void) audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag;
- (void) audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error;
@end
