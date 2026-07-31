#import "G3AppDelegate.h"

#import <CoreFoundation/CoreFoundation.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <mach/mach.h>
#import <mach/mach_time.h>
#import <ort_env.h>
#import <ort_session.h>
#import <ort_value.h>
#import <sys/resource.h>
#import <time.h>

static const int kImageSize = 224;
static const int kWarmupRuns = 5;
static const int kMeasuredRuns = 50;
static NSArray<NSString*>* G3Fixtures(void) {
  return @[ @"steak-side-plate", @"fish-side-plate", @"chopped-salad", @"buddha-bowl", @"salmon-grain-plate" ];
}

static uint64_t G3NowNs(void) {
  return clock_gettime_nsec_np(CLOCK_UPTIME_RAW);
}

static uint64_t G3ResidentKb(void) {
  mach_task_basic_info_data_t info;
  mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
  kern_return_t result = task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count);
  return result == KERN_SUCCESS ? (uint64_t)(info.resident_size / 1024) : 0;
}

static NSString* G3JsonEscape(NSString* value) {
  return [[value stringByReplacingOccurrencesOfString:@"\\" withString:@"\\\\"]
      stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""];
}

static NSMutableData* G3InputForImage(UIImage* image) {
  size_t bytesPerRow = kImageSize * 4;
  NSMutableData* rgba = [NSMutableData dataWithLength:kImageSize * bytesPerRow];
  CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
  CGContextRef context = CGBitmapContextCreate(
      rgba.mutableBytes,
      kImageSize,
      kImageSize,
      8,
      bytesPerRow,
      colorSpace,
      kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
  CGColorSpaceRelease(colorSpace);
  if (context == NULL || image.CGImage == NULL) {
    if (context != NULL) CGContextRelease(context);
    return nil;
  }
  CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
  CGContextDrawImage(context, CGRectMake(0, 0, kImageSize, kImageSize), image.CGImage);
  CGContextRelease(context);

  NSMutableData* tensor = [NSMutableData dataWithLength:3 * kImageSize * kImageSize * sizeof(float)];
  float* values = (float*)tensor.mutableBytes;
  const uint8_t* pixels = (const uint8_t*)rgba.bytes;
  for (int y = 0; y < kImageSize; y++) {
    for (int x = 0; x < kImageSize; x++) {
      size_t pixel = (size_t)(y * kImageSize + x) * 4;
      size_t offset = (size_t)(y * kImageSize + x);
      values[offset] = (((float)pixels[pixel] / 255.0f) - 0.485f) / 0.229f;
      values[kImageSize * kImageSize + offset] = (((float)pixels[pixel + 1] / 255.0f) - 0.456f) / 0.224f;
      values[2 * kImageSize * kImageSize + offset] = (((float)pixels[pixel + 2] / 255.0f) - 0.406f) / 0.225f;
    }
  }
  return tensor;
}

static long G3Percentile(NSMutableArray<NSNumber*>* values, double percentile) {
  [values sortUsingSelector:@selector(compare:)];
  NSUInteger index = (NSUInteger)ceil(percentile * values.count) - 1;
  return values[MAX(0, MIN(index, values.count - 1))].longLongValue;
}

@interface G3AppDelegate ()
@property(nonatomic, strong) UILabel* statusLabel;
@end

@implementation G3AppDelegate

- (BOOL)application:(UIApplication*)application didFinishLaunchingWithOptions:(NSDictionary*)launchOptions {
  self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
  UIViewController* controller = [[UIViewController alloc] init];
  self.statusLabel = [[UILabel alloc] initWithFrame:controller.view.bounds];
  self.statusLabel.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  self.statusLabel.textAlignment = NSTextAlignmentCenter;
  self.statusLabel.numberOfLines = 0;
  self.statusLabel.text = @"G-3 benchmark running…";
  [controller.view addSubview:self.statusLabel];
  self.window.rootViewController = controller;
  [self.window makeKeyAndVisible];
  dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
    [self runBenchmark];
  });
  return YES;
}

- (void)runBenchmark {
  @autoreleasepool {
    NSError* error = nil;
    NSString* modelPath = [NSBundle.mainBundle pathForResource:@"mobilenet_v2_food101" ofType:@"onnx"];
    if (modelPath == nil) {
      [self writeFailure:@"model not found in bundle"];
      return;
    }
    UIDevice.currentDevice.batteryMonitoringEnabled = YES;
    float batteryBefore = UIDevice.currentDevice.batteryLevel;
    uint64_t sessionStarted = G3NowNs();
    ORTEnv* environment = [[ORTEnv alloc] initWithLoggingLevel:ORTLoggingLevelWarning error:&error];
    ORTSessionOptions* options = [[ORTSessionOptions alloc] initWithError:&error];
    [options setIntraOpNumThreads:2 error:&error];
    [options appendExecutionProvider:@"XNNPACK"
                    providerOptions:@{ @"intra_op_num_threads": @"2" }
                                error:&error];
    ORTSession* session = [[ORTSession alloc] initWithEnv:environment
                                                modelPath:modelPath
                                           sessionOptions:options
                                                    error:&error];
    if (session == nil || error != nil) {
      [self writeFailure:error.localizedDescription ?: @"session creation failed"];
      return;
    }
    long sessionInitMs = (long)((G3NowNs() - sessionStarted) / 1000000ULL);

    NSArray<NSString*>* inputNames = [session inputNamesWithError:&error];
    NSArray<NSString*>* outputNames = [session outputNamesWithError:&error];
    NSString* inputName = inputNames.firstObject;
    NSString* outputName = outputNames.firstObject;
    NSMutableArray<NSMutableData*>* inputs = [NSMutableArray array];
    for (NSString* fixture in G3Fixtures()) {
      NSString* path = [NSBundle.mainBundle pathForResource:fixture ofType:@"jpg"];
      UIImage* image = path == nil ? nil : [UIImage imageWithContentsOfFile:path];
      NSMutableData* tensor = G3InputForImage(image);
      if (tensor == nil) {
        [self writeFailure:[NSString stringWithFormat:@"fixture unavailable: %@", fixture]];
        return;
      }
      [inputs addObject:tensor];
    }

    for (int index = 0; index < kWarmupRuns; index++) {
      [self runSession:session inputName:inputName outputName:outputName tensor:inputs[index % inputs.count] error:&error];
    }
    uint64_t pssBefore = G3ResidentKb();
    NSInteger thermalBefore = NSProcessInfo.processInfo.thermalState;
    NSMutableArray<NSNumber*>* durations = [NSMutableArray arrayWithCapacity:kMeasuredRuns];
    int firstTop1 = -1;
    for (int index = 0; index < kMeasuredRuns; index++) {
      uint64_t started = G3NowNs();
      int top1 = [self runSession:session
                        inputName:inputName
                       outputName:outputName
                           tensor:inputs[index % inputs.count]
                            error:&error];
      long durationMs = (long)((G3NowNs() - started) / 1000000ULL);
      [durations addObject:@(durationMs)];
      if (index == 0) firstTop1 = top1;
    }
    uint64_t pssAfter = G3ResidentKb();
    NSInteger thermalAfter = NSProcessInfo.processInfo.thermalState;
    float batteryAfter = UIDevice.currentDevice.batteryLevel;
    NSString* result = [NSString stringWithFormat:
        @"{\"status\":\"success\",\"runtime\":\"onnxruntime-objc\",\"runtime_version\":\"1.27.0\","
         "\"model\":\"mobilenet_v2_food101.onnx\",\"fixture_count\":%lu,\"warmup_runs\":%d,"
         "\"measured_runs\":%d,\"session_init_ms\":%ld,\"inference_p50_ms\":%ld,\"inference_p95_ms\":%ld,"
         "\"inference_max_ms\":%lld,\"resident_before_kb\":%llu,\"resident_after_kb\":%llu,"
         "\"battery_level_before\":%.3f,\"battery_level_after\":%.3f,"
         "\"thermal_before\":%ld,\"thermal_after\":%ld,\"first_top1_index\":%d}",
        (unsigned long)inputs.count,
        kWarmupRuns,
        kMeasuredRuns,
        sessionInitMs,
        G3Percentile(durations, 0.50),
        G3Percentile(durations, 0.95),
        durations.lastObject.longLongValue,
        pssBefore,
        pssAfter,
        batteryBefore,
        batteryAfter,
        (long)thermalBefore,
        (long)thermalAfter,
        firstTop1];
    [self writeResult:result];
    dispatch_async(dispatch_get_main_queue(), ^{
      self.statusLabel.text = @"G-3 benchmark complete";
    });
  }
}

- (int)runSession:(ORTSession*)session
        inputName:(NSString*)inputName
       outputName:(NSString*)outputName
           tensor:(NSMutableData*)tensorData
            error:(NSError**)error {
  ORTValue* value = [[ORTValue alloc] initWithTensorData:tensorData
                                             elementType:ORTTensorElementDataTypeFloat
                                                   shape:@[ @1, @3, @224, @224 ]
                                                   error:error];
  NSDictionary<NSString*, ORTValue*>* outputs = [session runWithInputs:@{ inputName : value }
                                                                outputNames:[NSSet setWithObject:outputName]
                                                                 runOptions:nil
                                                                      error:error];
  ORTValue* output = outputs[outputName];
  NSMutableData* data = [output tensorDataWithError:error];
  if (data == nil) return -1;
  const float* scores = (const float*)data.bytes;
  NSUInteger count = data.length / sizeof(float);
  int top1 = 0;
  for (NSUInteger index = 1; index < count; index++) {
    if (scores[index] > scores[top1]) top1 = (int)index;
  }
  return top1;
}

- (void)writeResult:(NSString*)result {
  NSLog(@"G3_RESULT %@", result);
  NSString* path = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES).firstObject
      stringByAppendingPathComponent:@"Caches/result.json"];
  [[result dataUsingEncoding:NSUTF8StringEncoding] writeToFile:path atomically:YES];
}

- (void)writeFailure:(NSString*)message {
  NSString* result = [NSString stringWithFormat:@"{\"status\":\"error\",\"message\":\"%@\"}", G3JsonEscape(message)];
  [self writeResult:result];
  dispatch_async(dispatch_get_main_queue(), ^{
    self.statusLabel.text = @"G-3 benchmark failed";
  });
}

@end
