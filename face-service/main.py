import os
import io
import base64
import numpy as np
from PIL import Image
import cv2
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI(
    title="GAMS Face Recognition Microservice",
    description="SFace & YuNet deep learning face recognition microservice.",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

DIR_PATH = os.path.dirname(os.path.abspath(__file__))
YUNET_PATH = os.path.join(DIR_PATH, "face_detection_yunet_2023mar.onnx")
SFACE_PATH = os.path.join(DIR_PATH, "face_recognition_sface_2021dec.onnx")
HAAR_CASCADE_PATH = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'

# Initialize models
haar_detector = cv2.CascadeClassifier(HAAR_CASCADE_PATH)

yunet_detector = None
sface_recognizer = None

if os.path.exists(YUNET_PATH) and os.path.exists(SFACE_PATH):
    try:
        yunet_detector = cv2.FaceDetectorYN.create(YUNET_PATH, "", (320, 320))
        sface_recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
        print("[SFace] Deep Learning YuNet & SFace models loaded successfully!")
    except Exception as e:
        print("[SFace] Error initializing SFace models, falling back:", e)

class CompareRequest(BaseModel):
    enrolledEmbedding: List[float]
    imageBufferBase64: str
    threshold: Optional[float] = 0.363

def decode_image_base64(base64_str: str) -> np.ndarray:
    try:
        if "," in base64_str:
            base64_str = base64_str.split(",")[1]
        img_bytes = base64.b64decode(base64_str)
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        return cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image encoding: {str(e)}")

def extract_face_vector(bgr_image: np.ndarray, target_dim: int = 128) -> np.ndarray:
    """
    Extracts high-dimensional facial embedding using YuNet + SFace neural network.
    Falls back to Haar Cascade + DCT if neural models are unavailable.
    """
    h, w, _ = bgr_image.shape

    if yunet_detector is not None and sface_recognizer is not None:
        try:
            yunet_detector.setInputSize((w, h))
            _, faces = yunet_detector.detect(bgr_image)
            if faces is not None and len(faces) > 0:
                # Use highest confidence face
                best_face = max(faces, key=lambda f: f[-1])
                aligned = sface_recognizer.alignCrop(bgr_image, best_face)
                feature = sface_recognizer.feature(aligned)
                vec = feature.flatten()
                norm = np.linalg.norm(vec)
                if norm > 0:
                    vec = vec / norm
                return vec.tolist()
        except Exception as e:
            print("[SFace] YuNet detection exception, falling back:", e)

    # Fallback to Haar Cascade / DCT feature extraction
    gray = cv2.cvtColor(bgr_image, cv2.COLOR_BGR2GRAY)
    faces = haar_detector.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))
    if len(faces) > 0:
        x, y, fw, fh = max(faces, key=lambda f: f[2] * f[3])
        face_roi = gray[y:y+fh, x:x+fw]
    else:
        face_roi = gray[h//4: 3*h//4, w//4: 3*w//4]

    resized = cv2.resize(face_roi, (112, 112))
    dct = cv2.dct(np.float32(resized) / 255.0)
    dct[0, 0] = 0.0 # Remove global DC illumination bias
    coeffs = dct[:16, :8].flatten()
    if len(coeffs) > target_dim:
        vec = coeffs[:target_dim]
    else:
        vec = np.pad(coeffs, (0, target_dim - len(coeffs)))
    norm = np.linalg.norm(vec)
    if norm > 0:
        vec = vec / norm
    return vec.tolist()

def compute_cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
    a = np.array(vec1, dtype=np.float32)
    b = np.array(vec2, dtype=np.float32)

    min_len = min(len(a), len(b))
    a = a[:min_len]
    b = b[:min_len]

    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)

    if norm_a == 0 or norm_b == 0:
        return 0.0

    return float(np.dot(a, b) / (norm_a * norm_b))

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "GAMS Face Recognition Microservice (SFace + YuNet)",
        "sface_active": sface_recognizer is not None,
    }

@app.post("/extract-embedding")
def extract_embedding(request_body: dict):
    img_b64 = request_body.get("imageBase64")
    if not img_b64:
        raise HTTPException(status_code=400, detail="imageBase64 is required")

    cv_img = decode_image_base64(img_b64)
    emb = extract_face_vector(cv_img, target_dim=128)
    return {
        "success": True,
        "embedding": emb,
        "dimension": len(emb)
    }

@app.post("/compare-face")
def compare_face(payload: CompareRequest):
    cv_img = decode_image_base64(payload.imageBufferBase64)
    selfie_embedding = extract_face_vector(cv_img, target_dim=len(payload.enrolledEmbedding))
    similarity = compute_cosine_similarity(payload.enrolledEmbedding, selfie_embedding)

    threshold = payload.threshold or 0.363
    is_match = similarity >= threshold

    return {
        "success": True,
        "similarity": round(similarity, 4),
        "raw_similarity": round(similarity, 4),
        "isMatch": is_match,
        "threshold": threshold,
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
