package jp.bunkaich.sukashimotion;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.List;

/** The first nine actual app icons make the folder recognizable without opening it. */
final class FolderIcon extends Drawable {
    private final List<AppCatalog.App> apps;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    FolderIcon(List<AppCatalog.App> apps, int color) { this.apps = List.copyOf(apps); this.color = color; }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds(); float size = Math.min(bounds.width(), bounds.height());
        paint.setColor(color); canvas.drawRoundRect(new RectF(bounds), size * .26f, size * .26f, paint);
        float padding = size * .14f, gap = size * .055f, cell = (size - padding * 2 - gap * 2) / 3;
        for (int index = 0; index < Math.min(9, apps.size()); index++) {
            Drawable icon = apps.get(index).icon(); Rect original = new Rect(icon.getBounds());
            int x = Math.round(bounds.left + padding + index % 3 * (cell + gap));
            int y = Math.round(bounds.top + padding + index / 3 * (cell + gap));
            icon.setBounds(x, y, Math.round(x + cell), Math.round(y + cell)); icon.draw(canvas); icon.setBounds(original);
        }
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
