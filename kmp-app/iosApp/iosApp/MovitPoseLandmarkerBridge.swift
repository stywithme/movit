import AVFoundation
import CoreMedia
import Foundation
import MovitApp
import QuartzCore
import UIKit

#if canImport(MediaPipeTasksVision)
import MediaPipeTasksVision
#endif

/// Swift MediaPipe Tasks Vision bridge for KMP `IosPoseDetector`.
/// Registers via `IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge` from `iOSApp` init.
///
/// Requires `MediaPipeTasksVision` CocoaPod (`iosApp/Podfile`) + `pose_landmarker_full.task` in app bundle
/// (`iosApp/Models/`). Without either, compiles as an honest stub (`isAvailable == false`).
/// Live inference validation needs Mac + Xcode + physical device (Phase 07 smoke).
final class MovitPoseLandmarkerBridge: NSObject, IosPoseLandmarkerBridge {
    private var resultHandler: IosPoseLandmarkerResultHandler?
    private var isFrontCamera = true
    private var warmedUp = false
    private var analysisImageWidth: Int32 = 0
    private var analysisImageHeight: Int32 = 0

    #if canImport(MediaPipeTasksVision)
    private var landmarker: PoseLandmarker?
    private var lastFrameImage: UIImage?
    // iOS: UIImage is immutable; each frame replaces the reference (no in-place reuse),
    // so snapshot tear from WP-09 Android rotatedBitmap reuse does not apply here.
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

    func warmUp(configuration_ configuration: PoseDetectorConfiguration) -> Bool {
        #if canImport(MediaPipeTasksVision)
        guard let modelPath = Bundle.main.path(forResource: "pose_landmarker_full", ofType: "task") else {
            warmedUp = false
            return false
        }
        do {
            let baseOptions = BaseOptions()
            baseOptions.modelAssetPath = modelPath
            baseOptions.delegate = configuration.useGpu ? .GPU : .CPU
            let options = PoseLandmarkerOptions()
            options.baseOptions = baseOptions
            options.runningMode = .liveStream
            options.numPoses = 1
            options.minPoseDetectionConfidence = configuration.minDetectionConfidence
            options.minTrackingConfidence = configuration.minTrackingConfidence
            options.minPosePresenceConfidence = configuration.minPosePresenceConfidence
            options.poseLandmarkerLiveStreamDelegate = self
            landmarker = try PoseLandmarker(options: options)
            warmedUp = true
            return true
        } catch {
            warmedUp = false
            return false
        }
        #else
        warmedUp = false
        return false
        #endif
    }

    func detectAsync(
        sampleBuffer: UnsafeMutableRawPointer?,
        isFrontCamera: Bool,
        timestampMs: Int64,
        analysisImageWidth: Int32,
        analysisImageHeight: Int32
    ) {
        #if canImport(MediaPipeTasksVision)
        let cmsampleBuffer: CMSampleBuffer? = sampleBuffer.map {
            Unmanaged<CMSampleBuffer>.fromOpaque($0).takeUnretainedValue()
        }
        guard warmedUp, let landmarker, let cmsampleBuffer else {
            resultHandler?.onNoPoseDetected(isFrontCamera: isFrontCamera)
            return
        }
        self.isFrontCamera = isFrontCamera
        do {
            var image = try MPImage(sampleBuffer: cmsampleBuffer, orientation: .up)
            var outW = Int(analysisImageWidth)
            var outH = Int(analysisImageHeight)
            if let uiImage = image.image {
                let pixelW = Int(uiImage.size.width * uiImage.scale)
                let pixelH = Int(uiImage.size.height * uiImage.scale)
                if outW > 0, outH > 0, pixelW > outW || pixelH > outH {
                    // B-01: downscale capture buffer to configured analysis size before MediaPipe.
                    let scaled = Self.scaleTo(image: uiImage, width: outW, height: outH)
                    image = try MPImage(uiImage: scaled)
                    lastFrameImage = scaled
                } else {
                    lastFrameImage = uiImage
                    if outW <= 0 || outH <= 0 {
                        outW = pixelW
                        outH = pixelH
                    }
                }
            }
            self.analysisImageWidth = Int32(max(outW, 0))
            self.analysisImageHeight = Int32(max(outH, 0))
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
        return movitKotlinByteArray(from: data)
        #else
        return nil
        #endif
    }

    func takeSnapshotJpegs(
        fullMaxDimension: Int32,
        fullQuality: Int32,
        thumbMaxDimension: Int32,
        thumbQuality: Int32
    ) -> FrameSnapshotJpegs? {
        #if canImport(MediaPipeTasksVision)
        guard let source = lastFrameImage else { return nil }
        let fullScaled = Self.scale(image: source, maxDimension: Int(fullMaxDimension))
        let thumbScaled = Self.scale(image: source, maxDimension: Int(thumbMaxDimension))
        guard let fullData = fullScaled.jpegData(compressionQuality: CGFloat(fullQuality) / 100.0),
              let thumbData = thumbScaled.jpegData(compressionQuality: CGFloat(thumbQuality) / 100.0) else {
            return nil
        }
        return FrameSnapshotJpegs(
            fullJpeg: movitKotlinByteArray(from: fullData),
            thumbJpeg: movitKotlinByteArray(from: thumbData)
        )
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
        analysisImageWidth = 0
        analysisImageHeight = 0
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

    private static func scaleTo(image: UIImage, width: Int, height: Int) -> UIImage {
        let size = CGSize(width: width, height: height)
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
        let inferenceMs = Int64(CACurrentMediaTime() * 1000.0) - Int64(timestampInMilliseconds)
        // B-01: report configured/scaled analysis dims — not UIImage.size (points).
        resultHandler?.onPoseDetected(
            landmarksFlat: Self.flatLandmarks(from: pose),
            worldLandmarksFlat: Self.flatWorld(from: world),
            timestampMs: Int64(timestampInMilliseconds),
            inferenceTimeMs: max(0, inferenceMs),
            isFrontCamera: isFrontCamera,
            analysisImageWidth: analysisImageWidth,
            analysisImageHeight: analysisImageHeight
        )
    }

    private static func flatLandmarks(from landmarks: [NormalizedLandmark]) -> KotlinFloatArray {
        flat(from: landmarks.map { ($0.x, $0.y, $0.z, $0.visibility?.floatValue ?? 0, $0.presence?.floatValue ?? 0) })
    }

    private static func flatWorld(from landmarks: [MediaPipeTasksVision.Landmark]?) -> KotlinFloatArray? {
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

private func movitKotlinByteArray(from data: Foundation.Data) -> KotlinByteArray {
    KotlinByteArray(size: Int32(data.count)) { index in
        KotlinByte(char: Int8(bitPattern: data[Int(index)]))
    }
}
