package jp.bunkaich.sukashimotion;

import android.content.Context;
import android.view.*;

/** Own surface lets capture exclude only our pixels, without ever hiding the visible freeze. */
final class SnapshotSurface extends SurfaceView implements SurfaceHolder.Callback {
    final SnapshotView image;private SurfaceControlViewHost host;private final Runnable committed;
    SnapshotSurface(Context context,SnapshotView image,Runnable committed){
        super(context);this.image=image;this.committed=committed;
        setZOrderOnTop(true);getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setOnTouchListener((v,event)->true);
    }
    @Override public void surfaceCreated(SurfaceHolder holder){
        host=new SurfaceControlViewHost(getContext(),getDisplay(),getHostToken());
        image.logicalWidth=getWidth();host.setView(image,getWidth(),getHeight());
        SurfaceControlViewHost.SurfacePackage surface=host.getSurfacePackage();
        if(surface!=null)setChildSurfacePackage(surface);
        // Initial attachment also needs its parent window presented. Later texture/endpoint
        // updates use image.afterFrame directly and keep this already-attached surface alive.
        image.afterFrame(()->SnapshotView.afterSubmittedFrame(this,committed));
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        if(host!=null){image.logicalWidth=width;host.relayout(width,height);}
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(host!=null){host.release();host=null;}}
}
