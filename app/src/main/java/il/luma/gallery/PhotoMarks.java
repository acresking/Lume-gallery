package il.luma.gallery;

import android.graphics.*;
import android.text.*;
import java.util.*;

/** Normalized vector marks render at preview and export resolutions. */
final class PhotoMarks {
  static final class Stroke {
    final ArrayList<PointF> points = new ArrayList<>();
    int color;
    float width;
    boolean mosaic;

    Stroke copy() {
      Stroke s = new Stroke();
      s.color = color;
      s.width = width;
      s.mosaic = mosaic;
      for (PointF p : points) s.points.add(new PointF(p.x, p.y));
      return s;
    }
  }

  final ArrayList<Stroke> strokes = new ArrayList<>();
  String caption = "";
  int textColor = Color.WHITE;
  float textSize = .055f, textY = .75f;

  PhotoMarks copy() {
    PhotoMarks m = new PhotoMarks();
    m.caption = caption;
    m.textColor = textColor;
    m.textSize = textSize;
    m.textY = textY;
    for (Stroke s : strokes) m.strokes.add(s.copy());
    return m;
  }

  void draw(Canvas canvas, Bitmap base) {
    int w = base.getWidth(), h = base.getHeight();
    Bitmap tiny = null;
    for (Stroke stroke : strokes) {
      if (stroke.points.isEmpty()) continue;
      Path path = new Path();
      PointF p = stroke.points.get(0);
      path.moveTo(p.x * w, p.y * h);
      if (stroke.points.size() == 1) path.lineTo(p.x * w + .01f, p.y * h + .01f);
      for (int i = 1; i < stroke.points.size(); i++) {
        p = stroke.points.get(i);
        path.lineTo(p.x * w, p.y * h);
      }
      Paint paint = new Paint(3);
      paint.setColor(stroke.color);
      paint.setStrokeWidth(Math.max(1, stroke.width * w));
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeJoin(Paint.Join.ROUND);
      paint.setStyle(Paint.Style.STROKE);
      if (stroke.mosaic) {
        if (tiny == null)
          tiny = Bitmap.createScaledBitmap(base, 48, Math.max(1, Math.round(48f * h / w)), false);
        Path area = new Path();
        paint.getFillPath(path, area);
        canvas.save();
        canvas.clipPath(area);
        Paint pixels = new Paint();
        pixels.setFilterBitmap(false);
        canvas.drawBitmap(tiny, null, new Rect(0, 0, w, h), pixels);
        canvas.restore();
      } else canvas.drawPath(path, paint);
    }
    if (!caption.isBlank()) {
      TextPaint text = new TextPaint(3);
      text.setColor(textColor);
      text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      text.setTextSize(w * textSize);
      text.setShadowLayer(Math.max(1, w * .003f), 0, 1, 0xdd000000);
      StaticLayout layout =
          StaticLayout.Builder.obtain(
                  caption, 0, caption.length(), text, Math.max(1, (int) (w * .9f)))
              .setAlignment(Layout.Alignment.ALIGN_CENTER)
              .setIncludePad(false)
              .build();
      canvas.save();
      canvas.translate(w * .05f, Math.max(0, Math.min(h - layout.getHeight(), h * textY)));
      layout.draw(canvas);
      canvas.restore();
    }
    if (tiny != null && tiny != base) tiny.recycle();
  }
}
