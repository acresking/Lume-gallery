package il.luma.gallery;

import android.content.Context;
import android.graphics.*;
import com.google.mediapipe.framework.image.*;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.*;
import com.google.mediapipe.tasks.vision.imageclassifier.*;
import com.google.mediapipe.tasks.vision.imageembedder.*;
import com.google.mediapipe.tasks.vision.imagesegmenter.*;
import java.nio.*;
import java.util.*;

/**
 * Small dedicated CPU vision models. No language model or network inference. Use off the UI thread.
 */
final class AiEngine implements AutoCloseable {
  private final Context context;
  private ImageClassifier classifier;
  private ImageEmbedder embedder;
  private ImageSegmenter segmenter;
  private FaceDetector faces;
  private com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector objects;

  AiEngine(Context c) {
    context = c.getApplicationContext();
  }

  BaseOptions base(String id) throws Exception {
    return BaseOptions.builder().setModelAssetPath(LocalModels.path(context, id)).build();
  }

  String labels(Bitmap bitmap) throws Exception {
    if (classifier == null)
      classifier =
          ImageClassifier.createFromOptions(
              context,
              ImageClassifier.ImageClassifierOptions.builder()
                  .setBaseOptions(base("classify"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setMaxResults(5)
                  .setScoreThreshold(
                      context.getSharedPreferences("luma", 0).getFloat("aiConfidence", .2f))
                  .build());
    MPImage input = new BitmapImageBuilder(bitmap).build();
    try {
      StringBuilder tags = new StringBuilder();
      for (var group : classifier.classify(input).classificationResult().classifications())
        for (var category : group.categories())
          tags.append(category.categoryName().replace('_', ' ')).append("; ");
      return AiIndex.keywords(tags.toString());
    } finally {
      input.close();
    }
  }

  float[] vector(Bitmap bitmap) throws Exception {
    if (embedder == null)
      embedder =
          ImageEmbedder.createFromOptions(
              context,
              ImageEmbedder.ImageEmbedderOptions.builder()
                  .setBaseOptions(base("similar"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setL2Normalize(true)
                  .build());
    MPImage input = new BitmapImageBuilder(bitmap).build();
    try {
      return embedder.embed(input).embeddingResult().embeddings().get(0).floatEmbedding().clone();
    } finally {
      input.close();
    }
  }

  java.util.List<RectF> faceBoxes(Bitmap bitmap) throws Exception {
    if (faces == null)
      faces =
          FaceDetector.createFromOptions(
              context,
              FaceDetector.FaceDetectorOptions.builder()
                  .setBaseOptions(base("faces"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setMinDetectionConfidence(.5f)
                  .build());
    MPImage input = new BitmapImageBuilder(bitmap).build();
    try {
      ArrayList<RectF> boxes = new ArrayList<>();
      for (var detection : faces.detect(input).detections())
        boxes.add(new RectF(detection.boundingBox()));
      return boxes;
    } finally {
      input.close();
    }
  }

  static final class ObjectBox {
    final RectF box;
    final String label;

    ObjectBox(RectF b, String l) {
      box = b;
      label = l;
    }
  }

  java.util.List<ObjectBox> objects(Bitmap bitmap) throws Exception {
    if (objects == null)
      objects =
          com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.createFromOptions(
              context,
              com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions
                  .builder()
                  .setBaseOptions(base("objects"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setMaxResults(12)
                  .setScoreThreshold(.35f)
                  .build());
    MPImage input = new BitmapImageBuilder(bitmap).build();
    try {
      ArrayList<ObjectBox> result = new ArrayList<>();
      for (var detection : objects.detect(input).detections())
        result.add(
            new ObjectBox(
                new RectF(detection.boundingBox()),
                detection.categories().isEmpty()
                    ? "פריט"
                    : detection.categories().get(0).categoryName()));
      return result;
    } finally {
      input.close();
    }
  }

  java.util.List<Bitmap> alignedFaces(Bitmap bitmap) throws Exception {
    return alignedFaces(bitmap,null);
  }

  java.util.List<Bitmap> alignedFaces(Bitmap bitmap, java.util.List<Bitmap> covers) throws Exception {
    if (faces == null)
      faces =
          FaceDetector.createFromOptions(
              context,
              FaceDetector.FaceDetectorOptions.builder()
                  .setBaseOptions(base("faces"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setMinDetectionConfidence(.65f)
                  .build());
    MPImage input = new BitmapImageBuilder(bitmap).build();
    try {
      ArrayList<Bitmap> result = new ArrayList<>();
      for (var detection : faces.detect(input).detections()) {
        var landmarks = detection.keypoints().orElse(Collections.emptyList());
        Bitmap aligned = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(aligned);
        Matrix matrix = new Matrix();
        if (landmarks.size() >= 3) {
          float[] src = {
            landmarks.get(0).x() * bitmap.getWidth(),
            landmarks.get(0).y() * bitmap.getHeight(),
            landmarks.get(1).x() * bitmap.getWidth(),
            landmarks.get(1).y() * bitmap.getHeight(),
            landmarks.get(2).x() * bitmap.getWidth(),
            landmarks.get(2).y() * bitmap.getHeight()
          };
          float[] dst = {38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f};
          matrix.setPolyToPoly(src, 0, dst, 0, 3);
          canvas.drawBitmap(bitmap, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
          RectF box = detection.boundingBox();
          Rect rect =
              new Rect(
                  Math.max(0, (int) box.left),
                  Math.max(0, (int) box.top),
                  Math.min(bitmap.getWidth(), (int) box.right),
                  Math.min(bitmap.getHeight(), (int) box.bottom));
          if (rect.width() < 12 || rect.height() < 12) continue;
          canvas.drawBitmap(
              bitmap, rect, new Rect(0, 0, 112, 112), new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        if(covers != null) {
          RectF box = detection.boundingBox();
          int left=Math.max(0,(int)(box.left-box.width()*.3f));
          int top=Math.max(0,(int)(box.top-box.height()*.4f));
          int right=Math.min(bitmap.getWidth(),(int)(box.right+box.width()*.3f));
          int bottom=Math.min(bitmap.getHeight(),(int)(box.bottom+box.height()*.3f));
          Bitmap crop = right>left && bottom>top ? Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top) : aligned;
          covers.add(crop.copy(Bitmap.Config.ARGB_8888,false));
        }
        result.add(aligned);
      }
      return result;
    } finally {
      input.close();
    }
  }

  Bitmap pixelateFaces(Bitmap source) throws Exception {
    java.util.List<RectF> boxes = faceBoxes(source);
    if (boxes.isEmpty())
      throw new java.io.IOException("לא אותרו פנים. המודל עלול לפספס פנים קטנות או מוסתרות.");
    Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint();
    for (RectF box : boxes) {
      int pad = (int) (box.width() * .14f);
      int x = Math.max(0, (int) box.left - pad),
          y = Math.max(0, (int) box.top - pad),
          right = Math.min(source.getWidth(), (int) box.right + pad),
          bottom = Math.min(source.getHeight(), (int) box.bottom + pad);
      if (right <= x || bottom <= y) continue;
      Bitmap part = Bitmap.createBitmap(source, x, y, right - x, bottom - y);
      Bitmap tiny = Bitmap.createScaledBitmap(part, 12, 12, false);
      canvas.drawBitmap(tiny, null, new Rect(x, y, right, bottom), paint);
    }
    return result;
  }

  Bitmap portrait(Bitmap source, boolean transparent) throws Exception {
    if (segmenter == null)
      segmenter =
          ImageSegmenter.createFromOptions(
              context,
              ImageSegmenter.ImageSegmenterOptions.builder()
                  .setBaseOptions(base("portrait"))
                  .setRunningMode(RunningMode.IMAGE)
                  .setOutputConfidenceMasks(true)
                  .setOutputCategoryMask(false)
                  .build());
    MPImage input = new BitmapImageBuilder(source).build();
    try {
      ImageSegmenterResult result = segmenter.segment(input);
      java.util.List<MPImage> masks =
          result
              .confidenceMasks()
              .orElseThrow(() -> new IllegalStateException("לא התקבלה מסכת אדם"));
      try {
        MPImage foreground = masks.get(masks.size() == 1 ? 0 : 1);
        FloatBuffer values =
            ByteBufferExtractor.extract(foreground).order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] mask = new float[values.remaining()];
        values.get(mask);
        int mw = foreground.getWidth(),
            mh = foreground.getHeight(),
            w = source.getWidth(),
            h = source.getHeight();
        int[] pixels = new int[w * h];
        source.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] background = null;
        if (!transparent) {
          Bitmap low =
              Bitmap.createScaledBitmap(source, Math.max(1, w / 48), Math.max(1, h / 48), true);
          Bitmap soft = Bitmap.createScaledBitmap(low, w, h, true);
          background = new int[w * h];
          soft.getPixels(background, 0, w, 0, 0, w, h);
        }
        double mass = 0;
        for (int y = 0; y < h; y++)
          for (int x = 0; x < w; x++) {
            float probability =
                mask[Math.min(mh - 1, y * mh / h) * mw + Math.min(mw - 1, x * mw / w)];
            float alpha = Math.max(0, Math.min(1, (probability - .12f) / .76f));
            mass += alpha;
            int i = y * w + x, original = pixels[i];
            if (transparent)
              pixels[i] =
                  Color.argb(
                      Math.round(alpha * Color.alpha(original)),
                      Color.red(original),
                      Color.green(original),
                      Color.blue(original));
            else {
              int blur = background[i];
              pixels[i] =
                  Color.rgb(
                      Math.round(Color.red(original) * alpha + Color.red(blur) * (1 - alpha)),
                      Math.round(Color.green(original) * alpha + Color.green(blur) * (1 - alpha)),
                      Math.round(Color.blue(original) * alpha + Color.blue(blur) * (1 - alpha)));
            }
          }
        if (mass / (w * h) < .01)
          throw new java.io.IOException("לא אותר אדם ברור בתמונה. כלי זה מיועד לדיוקנאות בלבד.");
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
      } finally {
        for (MPImage mask : masks) mask.close();
      }
    } finally {
      input.close();
    }
  }

  @Override
  public void close() {
    if (classifier != null) classifier.close();
    if (embedder != null) embedder.close();
    if (segmenter != null) segmenter.close();
    if (faces != null) faces.close();
    if (objects != null) objects.close();
  }
}
