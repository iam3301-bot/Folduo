package jp.bunkaich.sukashimotion;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.View;

final class SnapshotView extends View {
    final RuntimeShader shader=new RuntimeShader(FoldShader.CODE);final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    FrameTexture frame;final boolean inner,leftOnly;
    int logicalWidth; private float angle;private FrameTexture rearFrame;private long rearSince;private boolean sharpHold;
    private final Paint holdPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect holdSource=new Rect(),holdDestination=new Rect();
    SnapshotView(Context context,FrameTexture frame,boolean inner,boolean leftOnly){
        super(context);this.frame=frame;this.inner=inner;this.leftOnly=leftOnly;angle=inner?180:0;paint.setShader(shader);
        setContentDescription(context.getString(R.string.snapshot_description));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Do not operate an unseen live app through its temporary frozen image.
        setOnTouchListener((v,event)->true);
    }
    private BitmapShader bitmap(Bitmap bitmap,float sx,float sy,float tx){
        BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        Matrix matrix=new Matrix();matrix.setScale(sx,sy);matrix.postTranslate(tx,0);shader.setLocalMatrix(matrix);return shader;
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){bindTextures();}
    private void bindTextures(){
        int w=logicalWidth>0?logicalWidth:getWidth(),h=getHeight();if(w==0||h==0)return;
        shader.setInputShader("content",bitmap(frame.sharp,w/(float)frame.sharp.getWidth(),h/(float)frame.sharp.getHeight(),0));
        for(int i=0;i<BlurCache.LEVELS.length;i++)shader.setInputShader("rest"+(int)BlurCache.LEVELS[i],bitmap(frame.levels[i],1,1,0));
        shader.setFloatUniform("cacheScale",frame.levels[0].getWidth()/(float)w,frame.levels[0].getHeight()/(float)h);
        // Cover sees the SAME right half of the inner snapshot, not an unrelated wallpaper.
        FrameTexture linked=rearFrame==null?frame:rearFrame;boolean crop=rearFrame!=null;
        shader.setInputShader("rear",bitmap(linked.sharp,w*(crop?2f:1f)/linked.sharp.getWidth(),h/(float)linked.sharp.getHeight(),crop?-w:0));
        for(int i=0;i<BlurCache.LEVELS.length;i++)shader.setInputShader("rear"+(int)BlurCache.LEVELS[i],bitmap(linked.levels[i],1,1,crop?-linked.levels[i].getWidth()*.5f:0));
        shader.setFloatUniform("rearCacheScale",linked.levels[0].getWidth()*(crop?.5f:1f)/w,linked.levels[0].getHeight()/(float)h);
        shader.setFloatUniform("size",w,h);shader.setFloatUniform("inner",inner?1:0);
        shader.setFloatUniform("pixelsPerDp",getResources().getDisplayMetrics().density);shader.setFloatUniform("radiusDp",28);
    }
    void setFrame(FrameTexture next){frame=next;bindTextures();invalidate();}
    void setSharpHold(boolean value){sharpHold=value;invalidate();}
    void afterFrame(Runnable presented){
        // A GPU commit only submits to the swap chain; the previous buffer may still be visible.
        getViewTreeObserver().registerFrameCommitCallback(()->post(()->afterSubmittedFrame(this,presented)));invalidate();
    }
    static void afterSubmittedFrame(View view,Runnable presented){
        if(!view.isAttachedToWindow())return;
        AttachedSurfaceControl root=view.getRootSurfaceControl();
        if(Build.VERSION.SDK_INT>=35&&root!=null){
            SurfaceControl.Transaction transaction=new SurfaceControl.Transaction();
            transaction.addTransactionCompletedListener(view.getContext().getMainExecutor(),stats->{
                if(view.isAttachedToWindow())presented.run();
            });
            // Merge with this ViewRoot's next buffer, rather than applying an unrelated transaction
            // that could overtake its buffer queue. The ViewRoot consumes the transaction on success.
            if(root.applyTransactionOnDraw(transaction)){view.invalidate();return;}
            transaction.close();
        }
        // API 33–34 have no public transaction-presented callback. Keep the submitted image in
        // place across two display frames before changing screens or removing the previous layer.
        view.postOnAnimation(()->view.postOnAnimation(()->{if(view.isAttachedToWindow())presented.run();}));
    }
    void setRearFrame(FrameTexture rear,boolean animate){
        if(inner||rear==rearFrame)return;
        rearFrame=rear;rearSince=animate?SystemClock.uptimeMillis():0;bindTextures();invalidate();
    }
    void setAngle(float value){
        if(!Float.isFinite(value))return;float next=GlassProjection.clamp(value);
        if(Math.abs(angle-next)<.00001f)return;angle=next;invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        if(sharpHold||!frame.prepared){
            canvas.drawColor(Color.BLACK);int w=getWidth(),h=getHeight();holdDestination.set(0,0,w,h);
            switch(frame.sharpMapping){
                case DIRECT -> canvas.drawBitmap(frame.sharp,null,holdDestination,holdPaint);
                case COVER_TO_INNER -> {
                    holdDestination.set(0,0,w/2,h);canvas.drawBitmap(frame.sharp,null,holdDestination,holdPaint);
                    holdDestination.set(w/2,0,w,h);canvas.drawBitmap(frame.sharp,null,holdDestination,holdPaint);
                }
                case INNER_RIGHT_TO_COVER -> {
                    holdSource.set(frame.sharp.getWidth()/2,0,frame.sharp.getWidth(),frame.sharp.getHeight());
                    canvas.drawBitmap(frame.sharp,holdSource,holdDestination,holdPaint);
                }
            }
            return;
        }
        GlassProjection.Pose pose=GlassProjection.coverPose(angle);
        shader.setFloatUniform("pose",pose.expansion(),pose.taper());
        GlassProjection.Plane plane=GlassProjection.innerPlane(angle);
        shader.setFloatUniform("innerDepth",plane.depth());
        shader.setFloatUniform("amount",FoldPolicy.blur(angle,inner));
        float ready=rearSince==0?1:Math.min(1,(SystemClock.uptimeMillis()-rearSince)/160f);ready=ready*ready*(3-2*ready);
        shader.setFloatUniform("rearBlend",rearFrame==null?0:GlassProjection.rearWeight(angle)*ready);
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);if(ready<1)postInvalidateOnAnimation();
    }
}
