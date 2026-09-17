package jp.bunkaich.sukashimotion;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared native glass material. Refraction samples the same procedural scene as the backdrop. */
final class GlassStyle {
    static final int INK = 0xff172b40, SECONDARY = 0xff455d73, BLUE = 0xff0866ce;
    static final int PAPER = 0xffedf4fb;
    private GlassStyle() {}
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static float concentration(Context c) { return c.getSharedPreferences("appearance", 0).getInt("glass", 45) / 100f; }
    static void concentration(Context c, int value) { c.getSharedPreferences("appearance", 0).edit().putInt("glass", value).apply(); }
    static boolean reduceMotion(Context c) { return c.getSharedPreferences("appearance", 0).getBoolean("reduce_motion", false); }
    static void configureWindow(Activity a) {
        a.getWindow().setDecorFitsSystemWindows(false);
        a.getWindow().setStatusBarColor(Color.TRANSPARENT);
        a.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        a.getWindow().setNavigationBarContrastEnforced(false);
        View decor=a.getWindow().getDecorView();
        decor.post(() -> {
            WindowInsetsController controller=decor.getWindowInsetsController();
            if(controller!=null)controller.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
    }
    static TextView text(Context c, String value, int size, int color) {
        TextView view = new TextView(c); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern"); view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(c, 3), 1); return view;
    }
    static Button button(Context c, String title, boolean primary, Runnable action) {
        Button view = new Button(c) {
            @Override public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                if (!reduceMotion(c) && ValueAnimator.areAnimatorsEnabled()) {
                    float scale=pressed?.975f:1f;
                    animate().scaleX(scale).scaleY(scale).setDuration(180).start();
                }
            }
        }; view.setText(title); view.setAllCaps(false); view.setTextSize(15);
        view.setTextColor(primary ? Color.WHITE : INK); view.setMinHeight(dp(c, 52));
        view.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        view.setStateListAnimator(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x220866ce),
            primary ? solid(BLUE, dp(c, 26)) : new Material(view, 26, false), null));
        view.setOnClickListener(v -> action.run());
        return view;
    }
    static GradientDrawable solid(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    static void panel(View view, int radius) { view.setBackground(new Material(view, radius, false)); }
    static void backdrop(View view) { view.setBackground(new Material(view, 0, true)); }
    static void refresh(View view) {
        if (view.getBackground() != null) view.getBackground().invalidateSelf();
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) refresh(group.getChildAt(i));
    }
    static void dialog(AlertDialog dialog) {
        dialog.show();
        if (dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        dialog.getWindow().setBackgroundDrawable(new Material(decor, 30, false));
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        var attributes=dialog.getWindow().getAttributes(); attributes.dimAmount=.18f;
        dialog.getWindow().setAttributes(attributes);
    }

    private static final String SCENE = """
        uniform float2 size;
        uniform float2 origin;
        uniform float2 viewport;
        uniform float radius;
        uniform float density;
        uniform float concentration;
        uniform float backdrop;
        half3 scene(float2 p) {
            float2 uv = p / max(viewport, float2(1));
            half3 color = half3(0.94, 0.97, 0.995);
            float blue = exp(-3.9 * dot((uv-float2(.9,.28))*float2(1.1,1.8), (uv-float2(.9,.28))*float2(1.1,1.8)));
            float aqua = exp(-5.0 * dot((uv-float2(.03,.66))*float2(1.3,1.4), (uv-float2(.03,.66))*float2(1.3,1.4)));
            color = mix(color, half3(.49,.73,.96), blue * .72);
            color = mix(color, half3(.65,.91,.91), aqua * .64);
            float ribbon = exp(-pow((uv.y - (.50 + .18*sin(uv.x*4.2))) * 13.0, 2.0));
            color = mix(color, half3(.98,.995,1), ribbon*.52);
            return color;
        }
        half4 main(float2 p) {
            if (backdrop > .5) return half4(scene(p), 1);
            float2 q = abs(p-size*.5) - size*.5 + radius;
            float distance = length(max(q,float2(0))) + min(max(q.x,q.y),0) - radius;
            float edge = 1.0-smoothstep(0.0, 18.0*density, -distance);
            float2 normal = normalize((p-size*.5)/max(size*.5,float2(1)) + float2(.0001));
            float2 sampleAt = origin+p-normal*edge*11.0*density;
            float blur = (3.0 + concentration*9.0)*density;
            half3 color = (scene(sampleAt)*2 + scene(sampleAt+float2(blur,0)) + scene(sampleAt-float2(blur,0)) + scene(sampleAt+float2(0,blur)) + scene(sampleAt-float2(0,blur)))/6;
            color = mix(color,half3(1),.24 + concentration*.64);
            float bevel = 1.0-smoothstep(.2*density,1.4*density,-distance);
            float light = clamp(.6-dot(normal,normalize(float2(-.4,-1)))*.3,.0,1.);
            color = mix(color,half3(1),bevel*light*.95);
            color += half3(.015,.025,.035)*edge;
            return half4(color,1);
        }
        """;

    static final class Material extends Drawable {
        private final View owner;
        private final float radius;
        private final boolean backdrop;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] location = new int[2], rootLocation = new int[2];
        private RuntimeShader shader;
        Material(View owner, float radius, boolean backdrop) {
            this.owner=owner; this.radius=dp(owner.getContext(),radius); this.backdrop=backdrop;
            try { shader=new RuntimeShader(SCENE); } catch (RuntimeException error) {
                android.util.Log.w("FolduoGlass", "Glass shader unavailable; using fallback", error); shader=null;
            }
        }
        @Override public void draw(Canvas canvas) {
            Rect b=getBounds(); if(b.isEmpty())return;
            float d=owner.getResources().getDisplayMetrics().density;
            if(shader!=null && canvas.isHardwareAccelerated()) {
                owner.getLocationOnScreen(location); View root=owner.getRootView(); root.getLocationOnScreen(rootLocation);
                shader.setFloatUniform("size",b.width(),b.height());
                shader.setFloatUniform("origin",location[0]-rootLocation[0],location[1]-rootLocation[1]);
                shader.setFloatUniform("viewport",Math.max(root.getWidth(),1),Math.max(root.getHeight(),1));
                shader.setFloatUniform("radius",radius); shader.setFloatUniform("density",d);
                shader.setFloatUniform("concentration",concentration(owner.getContext())); shader.setFloatUniform("backdrop",backdrop?1:0);
                paint.setShader(shader);
            } else {
                paint.setShader(new LinearGradient(0,0,b.width(),b.height(),backdrop?0xffe5f2fd:0xfff2f8fc,backdrop?0xffbadbe9:0xffe8f3fa,Shader.TileMode.CLAMP));
            }
            canvas.save(); canvas.translate(b.left,b.top);
            canvas.drawRoundRect(0,0,b.width(),b.height(),radius,radius,paint);
            if(!backdrop) {
                rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(d); rim.setColor(0xbfffffff);
                canvas.drawRoundRect(d*.5f,d*.5f,b.width()-d*.5f,b.height()-d*.5f,radius,radius,rim);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
