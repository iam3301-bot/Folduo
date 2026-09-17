package jp.bunkaich.sukashimotion;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Permission-free visual preview uses generated content, never another app's screen. */
public final class PreviewActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private FrameLayout canvas;private SnapshotView snapshot;private PreviewRig rig;private boolean physical=true;private TextView degrees;private boolean inner=true,closed;private int angle=180,generation;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);if(saved!=null){angle=saved.getInt("angle",180);inner=saved.getBoolean("inner",true);physical=saved.getBoolean("physical",true);}getWindow().setDecorFitsSystemWindows(false);
        GlassStyle.configureWindow(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);GlassStyle.backdrop(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(dp(20)+safe.left,dp(12)+safe.top,dp(20)+safe.right,dp(12)+safe.bottom);return insets;
        });
        TextView title=GlassStyle.text(this,getString(R.string.preview_title),25,GlassStyle.INK);title.setTypeface(null,Typeface.BOLD);title.setAccessibilityHeading(true);root.addView(title);
        degrees=GlassStyle.text(this,"",14,GlassStyle.SECONDARY);degrees.setPadding(0,dp(10),0,dp(8));root.addView(degrees);
        canvas=new FrameLayout(this);root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(16),dp(12),dp(16),dp(12));GlassStyle.panel(controls,28);root.addView(controls);
        SeekBar seek=new SeekBar(this);seek.setMax(180);seek.setProgress(angle);seek.setContentDescription(getString(R.string.hinge_angle));controls.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean fromUser){angle=value;update();}});
        Button mode=GlassStyle.button(this,getString(inner?R.string.switch_cover:R.string.switch_inner),false,()->{});
        mode.setOnClickListener(v->{inner=!inner;mode.setText(inner?getString(R.string.switch_cover):getString(R.string.switch_inner));build();});controls.addView(mode,new LinearLayout.LayoutParams(-1,-2));
        Button view=GlassStyle.button(this,getString(physical?R.string.view_render:R.string.view_physical),false,()->{});
        view.setOnClickListener(v->{physical=!physical;view.setText(physical?getString(R.string.view_render):getString(R.string.view_physical));if(rig!=null)rig.setPhysical(physical);update();});controls.addView(view,new LinearLayout.LayoutParams(-1,-2));
        Button back=GlassStyle.button(this,getString(R.string.back_settings),true,this::finish);controls.addView(back,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);canvas.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)canvas.post(this::build);});canvas.post(this::build);update();
    }
    private int dp(int n){return GlassStyle.dp(this,n);}
    private void update(){degrees.setText(getString(R.string.preview_degrees,getString(inner?R.string.inner:R.string.cover),angle,getString(physical?R.string.physical_view:R.string.render_view)));if(rig!=null)rig.setAngle(angle);}
    private void build(){
        int ticket=++generation;boolean mode=inner;
        float aspect=(mode?.9f:.43f)*.72f/.92f;int h=Math.max(200,Math.min(canvas.getHeight(),Math.round(canvas.getWidth()/aspect)));int w=Math.round(h*aspect);
        worker.execute(()->{
            int imageW=w-Math.round(w*.04f)*2,imageH=h-Math.round(h*.14f)*2;
            Bitmap sample=localizedSample(mode?imageW:imageW*2,imageH);
            FrameTexture innerFrame=FrameTexture.prepare(sample,getResources().getDisplayMetrics().density,()->closed||ticket!=generation);
            if(innerFrame==null)return;
            FrameTexture frame=mode?innerFrame:FrameTexture.prepare(Bitmap.createBitmap(sample,imageW,0,imageW,imageH),getResources().getDisplayMetrics().density,()->closed||ticket!=generation);
            runOnUiThread(()->{
                if(closed||ticket!=generation||frame==null)return;canvas.removeAllViews();snapshot=new SnapshotView(this,frame,mode,false);
                if(!mode)snapshot.setRearFrame(innerFrame,false);
                rig=new PreviewRig(this,snapshot,innerFrame);rig.setPhysical(physical);canvas.addView(rig,new FrameLayout.LayoutParams(w,h,Gravity.CENTER));update();
            });
        });
    }

    static Bitmap sample(int w,int h){return sample(w,h,"折叠预览","");}
    private Bitmap localizedSample(int w,int h){return sample(w,h,getString(R.string.sample_title),getString(R.string.sample_subtitle));}
    private static Bitmap sample(int w,int h,String title,String subtitle){
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);Paint p=new Paint(3);
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0xffc1e3f9,0xffe5f3f9,0xff9dcedf},null,Shader.TileMode.CLAMP));c.drawPaint(p);p.setShader(null);
        p.setColor(GlassStyle.INK);p.setTextSize(w*.07f);if(p.measureText(title)>w*.88f)p.setTextSize(p.getTextSize()*w*.88f/p.measureText(title));c.drawText(title,w*.06f,h*.14f,p);
        p.setTextSize(w*.032f);if(p.measureText(subtitle)>w*.88f)p.setTextSize(p.getTextSize()*w*.88f/p.measureText(subtitle));c.drawText(subtitle,w*.06f,h*.20f,p);
        for(int row=0;row<3;row++)for(int col=0;col<4;col++){
            float x=w*(.06f+col*.235f),y=h*(.3f+row*.19f);p.setColor(new int[]{0xaaffffff,0xff91c5ec,0xffb9e1e5,0xffe9f1fc}[(row+col)%4]);c.drawRoundRect(x,y,x+w*.18f,y+h*.13f,22,22,p);p.setColor(GlassStyle.INK);p.setTextSize(w*.065f);c.drawText(""+(1+row*4+col),x+w*.04f,y+h*.09f,p);
        }return bitmap;
    }
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("angle",angle);saved.putBoolean("inner",inner);saved.putBoolean("physical",physical);}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);canvas.post(this::build);}
    @Override public void onDestroy(){closed=true;++generation;worker.shutdownNow();super.onDestroy();}
}
