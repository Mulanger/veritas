package com.veritas.data.detection.ml.runtime

object ModelRegistry {
    const val IMAGE_DEEPFAKE_DETECTOR_ID = "image_deepfake_detector_v2"
    const val IMAGE_DEEPFAKE_DETECTOR_VERSION = "0.1.0-phase7"
    const val IMAGE_INPUT_SIZE = 224

    val imageInt8: ModelAssetSpec =
        ModelAssetSpec(
            id = IMAGE_DEEPFAKE_DETECTOR_ID,
            version = IMAGE_DEEPFAKE_DETECTOR_VERSION,
            assetPath = "models/image/deepfake-detector-v2-int8.tflite",
            signatureAssetPath = "models/image/deepfake-detector-v2-int8.tflite.sig",
            expectedSha256 = "1c2cb319ef5e01e5e6c0688b99817fcddf7719f8e8b69a18bba316972dbf2f1e",
            publicKeyBase64 = "MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10=",
        )
}
