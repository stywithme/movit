import AVFoundation
import CoreMedia
import Foundation
import MovitApp
import UIKit

#if canImport(MediaPipeTasksVision)
import MediaPipeTasksVision
#endif

/// Swift MediaPipe Tasks Vision bridge for KMP `IosPoseDetector`.
/// Registers via `IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge` from `iOSApp` init.
///
/// **Platform note:** MediaPipe CocoaPod is not wired in `project.yml` yet — compiles as an honest
/// stub (`isAvailable == false`) until `pod 'MediaPipeTasksVision'` + `pose_landmarker_full.task`
/// ship on a Mac. Live inference needs Mac + Xcode + physical device validation (audit §35.1).
final class MovitPoseLandmarkerBridge: NSObject, IosPoseLandmarkerBridge {
    private var resultHandler: IosPoseLandmarkerResultHandler?
    private var isFrontCamera = true
    private var warmedUp = false

    #if canImport(MediaPipeTasksVision)
    private var landmarker: PoseLandmarker?
    private var lastFrameImage: UIImage?
    #endif

    var isAvailable: Bool {
        #if canImport(MediaPipeTasksVision)
        return Bundle.main.path(forResource: "pose_landmarker_full", ofType: "task") != nil
        #else
        return false
        #endif
    }

    func bindResultHandler(handler: IosPoseLandmarkerResultHandler?) {
        resultHandler = handler
    }

    func warmUp(configuration: PoseDetectorConfiguration) -> KotlinBoolean {
        #if canImport(MediaPipeTasksVision)
        guard let modelPath = Bundle.main.path(forResource: "pose_landmarker_full", ofType: "task") else {
            warmedUp = false
            return KotlinBoolean(bool: false)
        }
        do {
            let baseOptions = BaseOptions(modelPath: modelPath)
            baseOptions.delegate = configuration.useGpu ? .GPU : .CPU
            var options = PoseLandmarkerOptions()
            options.baseOptions = baseOptions
            options.runningMode = .liveStream
            options.numPoses = 1
            options.minPoseDetectionConfidence = configuration.minDetectionConfidence
            options.minTrackingConfidence = configuration.minTrackingConfidence
            options.poseLandmarkerLiveStreamDelegate = self
            landmarker = try PoseLandmarker(options: options)
            warmedUp = true
            return KotlinBoolean(bool: true)
        } catch {
            warmedUp = false
            return KotlinBoolean(bool: false)
        }
        #else
        warmedUp = false
        return KotlinBoolean(bool: false)
        #endif
    }

    func detectAsync(
        sampleBuffer: CMSampleBufferRef?,
        isFrontCamera: Bool,
        timestampMs: Int64
    ) {
        #if canImport(MediaPipeTasksVision)
        guard warmedUp, let landmarker, let sampleBuffer else {
            resultHandler?.onNoPoseDetected(isFrontCamera: isFrontCamera)
            return
        }
        self.isFrontCamera = isFrontCamera
        let image = MPImage(sampleBuffer: sampleBuffer, orientation: .up)
        lastFrameImage = image.uiImage
        do {
            try landmarker.detectAsync(image: image, timestampInMilliseconds: Int(timestampMs))
        } catch {
            resultHandler?.onError(message: error.localizedDescription)
        }
        #else
        resultHandler?.onNoPoseDetected(isFrontCamera: isFrontCamera)
        #endif
    }

    func takeSnapshotJpeg(maxDimension: Int32, quality: Int32) -> KotlinByteArray? {
        #if canImport(MediaPipeTasksVision)
        guard let source = lastFrameImage else { return nil }
        let scaled = Self.scale(image: source, maxDimension: Int(maxDimension))
        guard let data = scaled.jpegData(compressionQuality: CGFloat(quality) / 100.0) else {
            return nil
        }
        return data.toKotlinByteArray()
        #else
        return nil
        #endif
    }

    func shutdown() {
        #if canImport(MediaPipeTasksVision)
        landmarker = nil
        lastFrameImage = nil
        #endif
        warmedUp = false
        resultHandler = nil
    }

    private static func scale(image: UIImage, maxDimension: Int) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        guard longest > CGFloat(maxDimension) else { return image }
        let scale = CGFloat(maxDimension) / longest
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        image.draw(in: CGRect(origin: .zero, size: size))
        let output = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return output ?? image
    }
}

#if canImport(MediaPipeTasksVision)
extension MovitPoseLandmarkerBridge: PoseLandmarkerLiveStreamDelegate {
    func poseLandmarker(
        _ poseLandmarker: PoseLandmarker,
        didFinishDetection result: PoseLandmarkerResult?,
        timestampInMilliseconds: Int,
        error: Error?
    ) {
        if let error {
            resultHandler?.onError(message: error.localizedDescription)
            return
        }
        guard let result, let pose = result.landmarks.first, !pose.isEmpty else {
            resultHandler?.onNoPoseDetected(isFrontCamera: isFrontCamera)
            return
        }
        let world = result.worldLandmarks.first
        let inferenceMs = Int64(Date().timeIntervalSince1970 * 1000) - Int64(timestampInMilliseconds)
        let width = lastFrameImage.map { Int32($0.size.width) } ?? 0
        let height = lastFrameImage.map { Int32($0.size.height) } ?? 0
        resultHandler?.onPoseDetected(
            landmarksFlat: Self.flatLandmarks(from: pose),
            worldLandmarksFlat: Self.flatWorld(from: world),
            timestampMs: Int64(timestampInMilliseconds),
            inferenceTimeMs: max(0, inferenceMs),
            isFrontCamera: isFrontCamera,
            analysisImageWidth: width,
            analysisImageHeight: height
        )
    }

    private static func flatLandmarks(from landmarks: [NormalizedLandmark]) -> KotlinFloatArray {
        flat(from: landmarks.map { ($0.x, $0.y, $0.z, $0.visibility?.floatValue ?? 0, $0.presence?.floatValue ?? 0) })
    }

    private static func flatWorld(from landmarks: [Landmark]?) -> KotlinFloatArray? {
        guard let landmarks else { return nil }
        return flat(from: landmarks.map { ($0.x, $0.y, $0.z, $0.visibility?.floatValue ?? 1, $0.presence?.floatValue ?? 1) })
    }

    private static func flat(from tuples: [(Float, Float, Float, Float, Float)]) -> KotlinFloatArray {
        var values = [Float]()
        values.reserveCapacity(33 * 5)
        for tuple in tuples.prefix(33) {
            values.append(tuple.0)
            values.append(tuple.1)
            values.append(tuple.2)
            values.append(tuple.3)
            values.append(tuple.4)
        }
        while values.count < 33 * 5 {
            values.append(0)
        }
        return KotlinFloatArray(size: Int32(values.count)) { index in
            KotlinFloat(value: values[Int(index)])
        }
    }
}
#endif

private extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        KotlinByteArray(size: Int32(count)) { index in
            KotlinByte(char: Int8(bitPattern: self[Int(index)]))
        }
    }
}
