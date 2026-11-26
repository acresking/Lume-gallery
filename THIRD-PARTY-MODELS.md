# מקורות רכיבי Lume AI

המודלים אינם כלולים ב־APK. הקטלוג הנעול, כולל גיבובים, גדלים וגרסאות, נמצא ב־app/src/main/assets/ai-models.json.

- MediaPipe EfficientNet Lite0, MobileNet V3 Small, Selfie Segmenter, BlazeFace ו־EfficientDet Lite0: [MediaPipe Solutions](https://developers.google.com/edge/mediapipe/solutions/vision).
- LaMa: [AUXOUT-TEAM/agit-lama-inpainting](https://huggingface.co/AUXOUT-TEAM/agit-lama-inpainting). גרסת ONNX עם משקולות FP16 וחישוב FP32, המבוססת על big-lama והעבודה Resolution-robust Large Mask Inpainting with Fourier Convolutions, WACV 2022. קוד ומשקולות מתוארים אצל המפיץ כ־Apache-2.0. אין שינוי במשקולות; שינויי פורמט המפיץ מתועדים בכרטיס המודל.
- SFace INT8: [OpenCV SFace](https://huggingface.co/opencv/face_recognition_sface). משמש לקיבוץ מקומי לפי דמיון, ללא שירות זיהוי שמות.
- ESPCN: [ONNX Model Zoo super-resolution-10](https://huggingface.co/onnxmodelzoo/super-resolution-10). מודל הגדלת ערוץ בהירות פי 3.

לפני הפצה מסחרית יש לבדוק ולצרף את הרישיונות וההודעות הרלוונטיים לרכיבים ולמשקולות בהתאם לגרסאות המופצות. מקורות הריצה הם MediaPipe Tasks Vision ו־Microsoft ONNX Runtime, דרך Maven.
