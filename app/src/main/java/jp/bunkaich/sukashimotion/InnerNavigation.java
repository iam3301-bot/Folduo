package jp.bunkaich.sukashimotion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Display-local controls. An overlay never creates or changes the app's system insets. */
final class InnerNavigation {
    static final int SETTINGS=1000, PREVIEW=1001;
    interface Actions {void run(int action,int taskId);}
    private final Context context;private final WindowManager wm;private final Actions actions;
    private View root;private volatile boolean recents;private boolean closed;private final boolean gestures;
    private final List<View> edges=new ArrayList<>();
    private final Map<Integer,ImageView> previews=new HashMap<>();
    final int displayId,width,height;
    InnerNavigation(Context c,int display,int width,int height,Actions actions){this(c,display,width,height,actions,gesturesFor(Build.MODEL,display));}
    InnerNavigation(Context c,int display,int width,int height,Actions actions,boolean gestures){context=UiText.localizedWindow(c);displayId=display;this.width=width;this.height=height;this.actions=actions;this.gestures=gestures&&display==1;wm=c.getSystemService(WindowManager.class);collapse();}
    static boolean gesturesFor(String model,int display){return "SM-F9760".equals(model)&&display==1;}
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}

    private void replace(View view,int w,int h){
        if(closed)return;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;p.y=dp(8);p.setFitInsetsTypes(0);
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setTitle(context.getString(R.string.inner_controls_title));p.windowAnimations=0;
        removeWindows();root=view;wm.addView(root,p);
    }
    private LinearLayout buttons(){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER);row.setPadding(dp(8),dp(4),dp(8),dp(4));GlassStyle.panel(row,26);
        int[] keys={KeyEvent.KEYCODE_APP_SWITCH,KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_BACK,SETTINGS};
        String[] labels={context.getString(R.string.nav_recents),context.getString(R.string.nav_home),context.getString(R.string.nav_back),context.getString(R.string.nav_settings)};
        for(int i=0;i<keys.length;i++){
            int key=keys[i];NavButton button=new NavButton(context,key);button.setContentDescription(labels[i]);
            button.setOnClickListener(v->{
                if(key==KeyEvent.KEYCODE_BACK&&recents){collapse();return;}
                if(key!=KeyEvent.KEYCODE_APP_SWITCH)collapse();
                actions.run(key,-1);
            });row.addView(button,new LinearLayout.LayoutParams(0,dp(44),1));
        }
        return row;
    }
    private void collapse(){if(closed)return;recents=false;previews.clear();if(gestures)showGestures();else replace(buttons(),Math.min(width-dp(24),dp(320)),dp(52));}
    private void showGestures(){
        removeWindows();
        try{
            GestureZone bottom=new GestureZone(context,GestureZone.BOTTOM);root=bottom;
            wm.addView(bottom,gestureLayout(width,dp(24),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
            int edgeHeight=Math.min(dp(440),Math.max(dp(48),height-dp(160)));
            for(int side:new int[]{GestureZone.LEFT,GestureZone.RIGHT}){
                GestureZone edge=new GestureZone(context,side);edges.add(edge);
                wm.addView(edge,gestureLayout(dp(12),edgeHeight,Gravity.CENTER_VERTICAL|(side==GestureZone.LEFT?Gravity.LEFT:Gravity.RIGHT)));
            }
        }catch(RuntimeException error){removeWindows();throw error;}
    }
    private WindowManager.LayoutParams gestureLayout(int w,int h,int gravity){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=gravity;p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setTitle(context.getString(R.string.inner_gesture_window));p.windowAnimations=0;return p;
    }
    private void gestureAction(int key){
        if(closed)return;
        if(key==KeyEvent.KEYCODE_BACK&&recents){collapse();return;}
        if(recents&&key!=KeyEvent.KEYCODE_APP_SWITCH)collapse();
        actions.run(key,-1);
    }
    boolean showingRecents(){return recents;}
    void showRecent(List<Bundle> apps){
        if(closed)return;
        recents=true;previews.clear();
        LinearLayout panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(14),dp(14),dp(8));GlassStyle.panel(panel,26);
        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(context);title.setText(context.getString(R.string.nav_recents));title.setTextSize(19);title.setTextColor(GlassStyle.INK);heading.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        Button close=GlassStyle.button(context,context.getString(R.string.close),false,this::collapse);heading.addView(close);panel.addView(heading);
        ScrollView scroll=new ScrollView(context);LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        int panelWidth=Math.min(width-dp(32),dp(680));int cardWidth=(panelWidth-dp(44))/2;
        if(apps.isEmpty()){TextView empty=new TextView(context);empty.setText(context.getString(R.string.no_recent_apps));empty.setTextColor(GlassStyle.INK);empty.setPadding(dp(12),dp(32),dp(12),dp(32));list.addView(empty);}
        LinearLayout row=null;
        for(int i=0;i<apps.size();i++){
            Bundle app=apps.get(i);int task=app.getInt("taskId",-1);String label=app.getString("label","");
            if(i%2==0){row=new LinearLayout(context);list.addView(row);}
            LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));GlassStyle.panel(card,16);
            ImageView preview=new ImageView(context);preview.setImageBitmap(app.getParcelable("icon",Bitmap.class));preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);preview.setBackgroundColor(GlassStyle.PAPER);card.addView(preview,new LinearLayout.LayoutParams(-1,dp(160)));previews.put(task,preview);
            TextView name=new TextView(context);name.setText(label);name.setTextSize(15);name.setTextColor(GlassStyle.INK);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(4),dp(10),dp(4),dp(4));card.addView(name);
            card.setContentDescription(label);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->{collapse();actions.run(0,task);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(cardWidth,dp(214));lp.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(card,lp);
        }
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));panel.addView(buttons(),new LinearLayout.LayoutParams(-1,dp(52)));
        replace(panel,panelWidth,Math.min(height-dp(100),dp(610)));
    }
    void setPreview(int task,Bitmap bitmap){ImageView view=previews.get(task);if(!recents||view==null)return;view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(bitmap);}
    private void removeWindows(){for(View edge:edges)try{wm.removeViewImmediate(edge);}catch(IllegalArgumentException ignored){}edges.clear();if(root!=null){try{wm.removeViewImmediate(root);}catch(IllegalArgumentException ignored){}root=null;}}
    void close(){closed=true;recents=false;previews.clear();removeWindows();}
    private final class GestureZone extends View {
        static final int BOTTOM=0,LEFT=1,RIGHT=2;final int side;final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float startX,startY,holdX,holdY;private boolean tracking,armed,triggered;private long downAt;
        private final Runnable hold=()->{if(tracking&&armed&&!triggered&&isAttachedToWindow()){triggered=true;performHapticFeedback(HapticFeedbackConstants.GESTURE_END);gestureAction(KeyEvent.KEYCODE_APP_SWITCH);}};
        GestureZone(Context context,int side){
            super(context);this.side=side;setClickable(true);setFocusable(side==BOTTOM);
            setContentDescription(context.getString(side==BOTTOM?R.string.inner_gesture_help:R.string.inner_gesture_back));
            if(side!=BOTTOM)setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas){
            if(side!=BOTTOM)return;float center=getWidth()/2f,y=getHeight()-dp(8),half=Math.min(dp(54),getWidth()/4f);
            line.setColor(0x70000000);canvas.drawRoundRect(center-half-dp(1),y-dp(1),center+half+dp(1),y+dp(5),dp(3),dp(3),line);
            line.setColor(0xe8ffffff);canvas.drawRoundRect(center-half,y,center+half,y+dp(4),dp(2),dp(2),line);
        }
        private boolean upward(float dx,float dy){return dy>=dp(44)&&Math.abs(dx)<Math.max(dp(36),dy*.8f);}
        @Override public boolean onTouchEvent(MotionEvent event){
            float x=event.getRawX(),y=event.getRawY();
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN -> {removeCallbacks(hold);tracking=true;armed=triggered=false;startX=holdX=x;startY=holdY=y;downAt=event.getEventTime();return true;}
                case MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> {cancelGesture();return true;}
                case MotionEvent.ACTION_MOVE -> {
                    if(!tracking)return true;float dx=x-startX,dy=startY-y;
                    if(side==BOTTOM){
                        if(!upward(dx,dy)){armed=false;removeCallbacks(hold);}
                        else if(!armed||Math.hypot(x-holdX,y-holdY)>dp(8)){armed=true;holdX=x;holdY=y;removeCallbacks(hold);postDelayed(hold,350);}
                    }else if(!triggered&&(side==LEFT?dx:-dx)>=dp(32)&&Math.abs(dy)<Math.max(dp(28),Math.abs(dx)*.8f)){
                        triggered=true;performHapticFeedback(HapticFeedbackConstants.GESTURE_END);gestureAction(KeyEvent.KEYCODE_BACK);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP -> {
                    if(!tracking)return true;removeCallbacks(hold);tracking=false;
                    if(!triggered&&side==BOTTOM){
                        if(upward(x-startX,startY-y)){triggered=true;gestureAction(KeyEvent.KEYCODE_HOME);}
                        else if(Math.hypot(x-startX,y-startY)<=ViewConfiguration.get(getContext()).getScaledTouchSlop()&&event.getEventTime()-downAt<350)performClick();
                    }
                    return true;
                }
                default -> {return true;}
            }
        }
        @Override public boolean performClick(){super.performClick();if(side==BOTTOM)gestureAction(KeyEvent.KEYCODE_APP_SWITCH);return true;}
        private void cancelGesture(){removeCallbacks(hold);tracking=armed=false;triggered=true;}
        @Override protected void onDetachedFromWindow(){cancelGesture();super.onDetachedFromWindow();}
    }
    private static final class NavButton extends View {
        final int key;final Paint p=new Paint(3);final float density;
        NavButton(Context c,int key){super(c);this.key=key;density=c.getResources().getDisplayMetrics().density;setClickable(true);setFocusable(true);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=8*density;p.setColor(GlassStyle.INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f*density);p.setStrokeCap(Paint.Cap.ROUND);
            if(key==KeyEvent.KEYCODE_HOME)canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,2*density,2*density,p);
            else if(key==KeyEvent.KEYCODE_BACK){canvas.drawLine(cx+r/2,cy-r,cx-r/2,cy,p);canvas.drawLine(cx-r/2,cy,cx+r/2,cy+r,p);}
            else if(key==SETTINGS){canvas.drawCircle(cx,cy,r*.8f,p);canvas.drawCircle(cx,cy,r*.28f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(cx+(float)Math.cos(a)*r*.8f,cy+(float)Math.sin(a)*r*.8f,cx+(float)Math.cos(a)*r*1.16f,cy+(float)Math.sin(a)*r*1.16f,p);}}
            else {canvas.drawRoundRect(cx-r,cy-r,cx+r*.45f,cy+r*.7f,2*density,2*density,p);canvas.drawLine(cx+r,cy-r*.55f,cx+r,cy+r,p);}
        }
    }
}
