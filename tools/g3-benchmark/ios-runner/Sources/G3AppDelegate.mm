#import "G3AppDelegate.h"

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <mach/mach.h>
#import <ort_env.h>
#import <ort_session.h>
#import <ort_value.h>
#import <sys/resource.h>
#import <time.h>

static const int kImageSize = 224;
static const int kWarmupRuns = 5;
static const int kMeasuredRuns = 50;
static const float kSemanticMinConfidence = 0.55f;
static NSString* const kModelName = @"swin-finetuned-food101-matmul-int8.onnx";
static NSString* const kModelSha256 =
    @"9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df";
static NSString* const kMealManifestSha256 =
    @"d312e9c7408aa474df38ff823d9fd3fc6c8a3a86cffc94df7ce53391c6975292";

static NSArray<NSString*>* G3Fixtures(void) {
  return @[ @"steak-side-plate", @"fish-side-plate", @"chopped-salad", @"buddha-bowl",
            @"salmon-grain-plate", @"steak", @"greek-salad", @"salmon-on-grill", @"sushi",
            @"waffles" ];
}

static NSArray* G3ExpectedLabels(void) {
  return @[ [NSNull null], [NSNull null], [NSNull null], [NSNull null], [NSNull null],
            @"steak", @"greek_salad", @"grilled_salmon", @"sushi", @"waffles" ];
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

static NSArray<NSString*>* G3ReadLabels(NSError** error) {
  NSString* path = [NSBundle.mainBundle pathForResource:@"food101-labels" ofType:@"txt"];
  if (path == nil) {
    if (error != NULL) {
      *error = [NSError errorWithDomain:@"SuperNutritionG3" code:1
                               userInfo:@{NSLocalizedDescriptionKey : @"label map not found"}];
    }
    return nil;
  }
  NSString* contents = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:error];
  if (contents == nil) return nil;
  NSMutableArray<NSString*>* labels = [NSMutableArray array];
  for (NSString* line in [contents componentsSeparatedByString:@"\n"]) {
    NSString* trimmed = [line stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (trimmed.length > 0) [labels addObject:trimmed];
  }
  if (labels.count != 101 && error != NULL) {
    *error = [NSError errorWithDomain:@"SuperNutritionG3" code:2 userInfo:@{
      NSLocalizedDescriptionKey : @"Food101 label map must contain 101 labels"
    }];
  }
  return labels.count == 101 ? labels : nil;
}

static NSMutableData* G3InputForImage(UIImage* image) {
  if (image.CGImage == NULL) return nil;
  size_t width = CGImageGetWidth(image.CGImage);
  size_t height = CGImageGetHeight(image.CGImage);
  size_t side = MIN(width, height);
  CGRect crop = CGRectMake((width - side) / 2.0, (height - side) / 2.0, side, side);
  CGImageRef croppedImage = CGImageCreateWithImageInRect(image.CGImage, crop);
  if (croppedImage == NULL) return nil;

  size_t bytesPerRow = kImageSize * 4;
  NSMutableData* rgba = [NSMutableData dataWithLength:kImageSize * bytesPerRow];
  CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
  CGContextRef context = CGBitmapContextCreate(
      rgba.mutableBytes, kImageSize, kImageSize, 8, bytesPerRow, colorSpace,
      kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
  CGColorSpaceRelease(colorSpace);
  if (context == NULL) {
    CGImageRelease(croppedImage);
    return nil;
  }
  CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
  CGContextDrawImage(context, CGRectMake(0, 0, kImageSize, kImageSize), croppedImage);
  CGContextRelease(context);
  CGImageRelease(croppedImage);

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

static NSMutableDictionary* G3JsonObject(NSString* fixture, NSString* expected, NSDictionary* inference,
                                           NSArray<NSString*>* labels) {
  int top1Index = [inference[@"top1_index"] intValue];
  float top1Confidence = [inference[@"top1_confidence"] floatValue];
  NSString* top1Label = labels[top1Index];
  BOOL hasExpected = expected != nil;
  BOOL correct = hasExpected && [expected isEqualToString:top1Label] &&
                 top1Confidence >= kSemanticMinConfidence;
  NSMutableDictionary* object = [@{
    @"fixture" : [fixture stringByAppendingString:@".jpg"],
    @"expected_label" : hasExpected ? expected : [NSNull null],
    @"top1_label" : top1Label,
    @"top1_confidence" : @(top1Confidence),
    @"correct" : @(correct),
    @"top5" : inference[@"top5"]
  } mutableCopy];
  return object;
}

static NSString* G3JsonString(NSDictionary* object) {
  NSError* error = nil;
  NSData* data = [NSJSONSerialization dataWithJSONObject:object options:0 error:&error];
  if (data == nil) return [NSString stringWithFormat:@"{\"status\":\"error\",\"message\":\"%@\"}", error];
  return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
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
    NSString* resultPath = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES).firstObject
        stringByAppendingPathComponent:@"Caches/result.json"];
    [[NSFileManager defaultManager] removeItemAtPath:resultPath error:nil];
    NSError* error = nil;
    NSArray<NSString*>* labels = G3ReadLabels(&error);
    if (labels == nil) {
      [self writeFailure:error.localizedDescription ?: @"label map unavailable"];
      return;
    }
    NSString* modelPath = [NSBundle.mainBundle pathForResource:@"swin-finetuned-food101-matmul-int8" ofType:@"onnx"];
    if (modelPath == nil) {
      [self writeFailure:@"model not found in bundle"];
      return;
    }
    UIDevice.currentDevice.batteryMonitoringEnabled = YES;
    float batteryBefore = UIDevice.currentDevice.batteryLevel;
    uint64_t sessionStarted = G3NowNs();
    ORTEnv* environment = [[ORTEnv alloc] initWithLoggingLevel:ORTLoggingLevelWarning error:&error];
    ORTSessionOptions* options = [[ORTSessionOptions alloc] initWithError:&error];
    [options setGraphOptimizationLevel:ORTGraphOptimizationLevelExtended error:&error];
    [options setIntraOpNumThreads:2 error:&error];
    [options appendExecutionProvider:@"XNNPACK"
                    providerOptions:@{ @"intra_op_num_threads" : @"2" }
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
    NSArray<NSString*>* fixtures = G3Fixtures();
    NSMutableArray<NSMutableData*>* inputs = [NSMutableArray arrayWithCapacity:fixtures.count];
    for (NSString* fixture in fixtures) {
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
      NSDictionary* unused = [self runSession:session inputName:inputName outputName:outputName
                                        tensor:inputs[index % inputs.count] labels:labels error:&error];
      if (unused == nil) {
        [self writeFailure:error.localizedDescription ?: @"warmup inference failed"];
        return;
      }
    }
    uint64_t pssBefore = G3ResidentKb();
    NSInteger thermalBefore = NSProcessInfo.processInfo.thermalState;
    NSMutableArray<NSNumber*>* durations = [NSMutableArray arrayWithCapacity:kMeasuredRuns];
    NSMutableArray<NSDictionary*>* firstInferences = [NSMutableArray arrayWithCapacity:inputs.count];
    for (int index = 0; index < kMeasuredRuns; index++) {
      uint64_t started = G3NowNs();
      NSDictionary* inference = [self runSession:session inputName:inputName outputName:outputName
                                            tensor:inputs[index % inputs.count] labels:labels error:&error];
      long durationMs = (long)((G3NowNs() - started) / 1000000ULL);
      if (inference == nil) {
        [self writeFailure:error.localizedDescription ?: @"inference failed"];
        return;
      }
      [durations addObject:@(durationMs)];
      if (index < inputs.count) [firstInferences addObject:inference];
    }
    [durations sortUsingSelector:@selector(compare:)];
    uint64_t pssAfter = G3ResidentKb();
    NSInteger thermalAfter = NSProcessInfo.processInfo.thermalState;
    float batteryAfter = UIDevice.currentDevice.batteryLevel;

    NSArray* expectedLabels = G3ExpectedLabels();
    NSMutableArray* fixtureResults = [NSMutableArray arrayWithCapacity:firstInferences.count];
    int expectedCount = 0;
    int correctCount = 0;
    for (NSUInteger index = 0; index < firstInferences.count; index++) {
      id expectedObject = expectedLabels[index];
      NSString* expected = expectedObject == [NSNull null] ? nil : expectedObject;
      NSDictionary* result = G3JsonObject(fixtures[index], expected, firstInferences[index], labels);
      [fixtureResults addObject:result];
      if (expected != nil) {
        expectedCount++;
        if ([result[@"correct"] boolValue]) correctCount++;
      }
    }
    BOOL semanticPass = expectedCount > 0 && correctCount == expectedCount;
    NSDictionary* result = @{
      @"status" : @"success",
      @"runtime" : @"onnxruntime-objc",
      @"runtime_version" : @"1.27.0",
      @"model" : kModelName,
      @"model_sha256" : kModelSha256,
      @"meal_benchmark_manifest_sha256" : kMealManifestSha256,
      @"meal_benchmark_fixture_count" : @5,
      @"fixture_count" : @(firstInferences.count),
      @"semantic_expected_count" : @(expectedCount),
      @"semantic_correct_count" : @(correctCount),
      @"semantic_accuracy" : @(expectedCount == 0 ? 0.0f : (float)correctCount / expectedCount),
      @"semantic_min_confidence" : @(kSemanticMinConfidence),
      @"semantic_pass" : @(semanticPass),
      @"ingredient_benchmark" : @{
        @"status" : @"unsupported",
        @"scope" : @"visible_ingredient_set",
        @"ground_truth_case_count" : @5,
        @"predicted_case_count" : @0,
        @"reason" : @"Food-101 runner emits one dish label and no ingredient candidates, boxes, or masks."
      },
      @"weight_benchmark" : @{
        @"status" : @"not-evaluable",
        @"scope" : @"plated_mass_g",
        @"ground_truth_case_count" : @0,
        @"predicted_case_count" : @0,
        @"reason" : @"Fixtures have no calibrated scale ground truth and the current model emits no mass estimate."
      },
      @"warmup_runs" : @(kWarmupRuns),
      @"measured_runs" : @(kMeasuredRuns),
      @"session_init_ms" : @(sessionInitMs),
      @"inference_p50_ms" : durations[(NSUInteger)MAX(0, MIN((int)durations.count - 1, (int)ceil(0.50 * durations.count) - 1))],
      @"inference_p95_ms" : durations[(NSUInteger)MAX(0, MIN((int)durations.count - 1, (int)ceil(0.95 * durations.count) - 1))],
      @"inference_max_ms" : durations.lastObject,
      @"resident_before_kb" : @(pssBefore),
      @"resident_after_kb" : @(pssAfter),
      @"battery_level_before" : @(batteryBefore),
      @"battery_level_after" : @(batteryAfter),
      @"thermal_before" : @(thermalBefore),
      @"thermal_after" : @(thermalAfter),
      @"fixture_results" : fixtureResults
    };
    [self writeResult:G3JsonString(result)];
    dispatch_async(dispatch_get_main_queue(), ^{
      self.statusLabel.text = semanticPass ? @"G-3 semantic pass" : @"G-3 semantic failed";
    });
  }
}

- (NSDictionary*)runSession:(ORTSession*)session
                   inputName:(NSString*)inputName
                  outputName:(NSString*)outputName
                      tensor:(NSMutableData*)tensorData
                      labels:(NSArray<NSString*>*)labels
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
  if (data == nil) return nil;
  NSUInteger count = data.length / sizeof(float);
  if (count != labels.count) {
    if (error != NULL) {
      *error = [NSError errorWithDomain:@"SuperNutritionG3" code:3 userInfo:@{
        NSLocalizedDescriptionKey : [NSString stringWithFormat:@"Expected Food101 output length 101, got %lu", (unsigned long)count]
      }];
    }
    return nil;
  }
  const float* scores = (const float*)data.bytes;
  float maxScore = scores[0];
  for (NSUInteger index = 1; index < count; index++) maxScore = MAX(maxScore, scores[index]);
  double sum = 0.0;
  float* probabilities = static_cast<float*>(calloc(count, sizeof(float)));
  for (NSUInteger index = 0; index < count; index++) {
    probabilities[index] = expf(scores[index] - maxScore);
    sum += probabilities[index];
  }
  for (NSUInteger index = 0; index < count; index++) probabilities[index] /= (float)sum;

  NSMutableArray* top5 = [NSMutableArray arrayWithCapacity:5];
  BOOL* used = static_cast<BOOL*>(calloc(count, sizeof(BOOL)));
  int top1Index = 0;
  for (int rank = 0; rank < 5; rank++) {
    int best = -1;
    for (NSUInteger index = 0; index < count; index++) {
      if (!used[index] && (best < 0 || probabilities[index] > probabilities[best])) best = (int)index;
    }
    used[best] = YES;
    if (rank == 0) top1Index = best;
    [top5 addObject:@{ @"label" : labels[best], @"confidence" : @(probabilities[best]) }];
  }
  NSDictionary* result = @{
    @"top1_index" : @(top1Index),
    @"top1_confidence" : @(probabilities[top1Index]),
    @"top5" : top5
  };
  free(used);
  free(probabilities);
  return result;
}

- (void)writeResult:(NSString*)result {
  NSLog(@"G3_RESULT %@", result);
  NSString* path = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES).firstObject
      stringByAppendingPathComponent:@"Caches/result.json"];
  [[result dataUsingEncoding:NSUTF8StringEncoding] writeToFile:path atomically:YES];
}

- (void)writeFailure:(NSString*)message {
  NSString* escaped = [message stringByReplacingOccurrencesOfString:@"\\" withString:@"\\\\"];
  escaped = [escaped stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""];
  NSString* result = [NSString stringWithFormat:@"{\"status\":\"error\",\"message\":\"%@\"}", escaped];
  [self writeResult:result];
  dispatch_async(dispatch_get_main_queue(), ^{
    self.statusLabel.text = @"G-3 benchmark failed";
  });
}

@end
