package jp.bunkaich.sukashimotion;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Three focused pages sharing a floating glass navigation bar. */
final class ControlPanel {
    private final MainActivity activity;
    private final FrameLayout root;
    private final LinearLayout page, navigation;
    private final ScrollView scroll;
    private int selected;
    TextView state, diagnostic;
    private final Button[] tabs = new Button[3];

    ControlPanel(MainActivity activity, int selected) {
        this.activity=activity; this.selected=selected;
        GlassStyle.configureWindow(activity);
        root=new FrameLayout(activity); GlassStyle.backdrop(root);
        scroll=new ScrollView(activity); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        page=new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        navigation=new LinearLayout(activity); navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(6),dp(6),dp(6),dp(6)); GlassStyle.panel(navigation,32);
        int[] names={R.string.tab_motion,R.string.tab_appearance,R.string.tab_settings};
        int[] ids={R.id.nav_motion,R.id.nav_appearance,R.id.nav_settings};
        for(int i=0;i<3;i++) {
            final int index=i; Button button=GlassStyle.button(activity,s(names[i]),false,()->show(index));
            button.setId(ids[i]); button.setMinWidth(0); button.setPadding(dp(6),dp(8),dp(6),dp(8));
            tabs[i]=button; navigation.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        FrameLayout.LayoutParams nav=new FrameLayout.LayoutParams(dp(320),dp(60),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        root.addView(navigation,nav);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            scroll.setPadding(safe.left,safe.top,safe.right,0);
            nav.bottomMargin=safe.bottom+dp(12); navigation.setLayoutParams(nav);
            page.setPadding(dp(20),dp(22),dp(20),safe.bottom+dp(100)); return insets;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int width=r-l;
            int margin=Math.max(0,(width-dp(720))/2);
            FrameLayout.LayoutParams content=(FrameLayout.LayoutParams)scroll.getLayoutParams();
            if(content.leftMargin!=margin){content.leftMargin=margin;content.rightMargin=margin;scroll.setLayoutParams(content);}
            int navWidth=Math.min(dp(340),Math.max(dp(220),width-dp(40)));
            if(nav.width!=navWidth){nav.width=navWidth;navigation.setLayoutParams(nav);}
        });
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->GlassStyle.refresh(page));
        activity.setContentView(root); show(selected);
    }
    int selected(){return selected;}
    private int dp(float n){return GlassStyle.dp(activity,n);}
    private String s(int id){return activity.getString(id);}
    void show(int index) {
        selected=index; state=null; diagnostic=null; page.removeAllViews();
        for(int i=0;i<tabs.length;i++) {
            boolean active=i==index; tabs[i].setSelected(active); tabs[i].setTextColor(active?GlassStyle.BLUE:GlassStyle.SECONDARY);
            tabs[i].setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
            tabs[i].setBackground(active?new GlassStyle.Material(tabs[i],26,false):GlassStyle.solid(Color.TRANSPARENT,dp(26)));
        }
        if(index==0)motion(); else if(index==1)appearance(); else settings();
        scroll.scrollTo(0,0); activity.refreshState();
    }
    private TextView text(LinearLayout parent,String value,int size,int color,int bottom) {
        TextView view=GlassStyle.text(activity,value,size,color);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(bottom);parent.addView(view,p);return view;
    }
    private void title(int heading,int description) {
        TextView title=text(page,s(heading),29,GlassStyle.INK,10);title.setTypeface(null,Typeface.BOLD);title.setAccessibilityHeading(true);
        text(page,s(description),15,GlassStyle.SECONDARY,24);
    }
    private LinearLayout card() {
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(20),dp(20),dp(20));GlassStyle.panel(card,28);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);page.addView(card,p);return card;
    }
    private void heading(LinearLayout parent,int id) {
        TextView view=text(parent,s(id),18,GlassStyle.INK,10);view.setTypeface(null,Typeface.BOLD);view.setAccessibilityHeading(true);
    }
    private Button button(LinearLayout parent,int name,boolean primary,Runnable action) {
        Button button=GlassStyle.button(activity,s(name),primary,action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);parent.addView(button,p);return button;
    }
    private void motion() {
        text(page,s(R.string.dashboard_eyebrow),12,GlassStyle.BLUE,12);
        title(R.string.dashboard_title,R.string.dashboard_body);
        FoldIllustration illustration=new FoldIllustration(activity);
        illustration.setContentDescription(s(R.string.fold_illustration));page.addView(illustration,new LinearLayout.LayoutParams(-1,dp(194)));
        LinearLayout status=card();heading(status,R.string.motion_status);
        state=text(status,"",14,GlassStyle.SECONDARY,6);
        button(status,R.string.quick_start,true,()->show(2));
        LinearLayout preview=card();heading(preview,R.string.preview);
        text(preview,s(R.string.preview_hint),14,GlassStyle.SECONDARY,2);
        button(preview,R.string.preview,false,()->activity.startActivity(new Intent(activity,PreviewActivity.class)));
        LinearLayout home=card();heading(home,R.string.home_description);
        text(home,s(R.string.home_setup),14,GlassStyle.SECONDARY,6);
        button(home,R.string.home_open,false,activity::openHome);
        text(page,s(R.string.privacy_short),12,GlassStyle.SECONDARY,8).setGravity(Gravity.CENTER);
    }
    private void appearance() {
        title(R.string.appearance_title,R.string.appearance_body);
        LinearLayout sample=card();sample.setMinimumHeight(dp(176));sample.setGravity(Gravity.CENTER_VERTICAL);
        text(sample,s(R.string.glass_sample_title),30,GlassStyle.INK,12).setTypeface(null,Typeface.BOLD);
        text(sample,s(R.string.glass_sample_body),15,GlassStyle.SECONDARY,12);
        LinearLayout material=card();heading(material,R.string.glass_title);
        text(material,s(R.string.glass_description),14,GlassStyle.SECONDARY,18);
        TextView label=text(material,"",15,GlassStyle.INK,6);
        SeekBar slider=new SeekBar(activity);slider.setId(R.id.glass_concentration);slider.setMax(100);slider.setMinHeight(dp(48));
        slider.setContentDescription(s(R.string.glass_opacity));slider.setProgress(Math.round(GlassStyle.concentration(activity)*100));
        material.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        label.setText(activity.getString(R.string.glass_value,slider.getProgress()));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar seek){} public void onStopTrackingTouch(SeekBar seek){}
            public void onProgressChanged(SeekBar seek,int progress,boolean user){
                GlassStyle.concentration(activity,progress);label.setText(activity.getString(R.string.glass_value,progress));GlassStyle.refresh(root);
            }
        });
        LinearLayout ends=new LinearLayout(activity);
        TextView clear=GlassStyle.text(activity,s(R.string.glass_clear),13,GlassStyle.SECONDARY),tinted=GlassStyle.text(activity,s(R.string.glass_tinted),13,GlassStyle.SECONDARY);tinted.setGravity(Gravity.END);
        ends.addView(clear,new LinearLayout.LayoutParams(0,-2,1));ends.addView(tinted,new LinearLayout.LayoutParams(0,-2,1));material.addView(ends);
        text(material,s(R.string.appearance_hint),13,GlassStyle.SECONDARY,0).setPadding(0,dp(18),0,0);
        LinearLayout accessibility=card();heading(accessibility,R.string.accessibility_title);
        Switch reduce=new Switch(activity);reduce.setText(R.string.reduce_motion);reduce.setTextSize(15);reduce.setTextColor(GlassStyle.INK);reduce.setMinHeight(dp(48));reduce.setChecked(GlassStyle.reduceMotion(activity));
        reduce.setOnCheckedChangeListener((v,checked)->activity.getSharedPreferences("appearance",0).edit().putBoolean("reduce_motion",checked).apply());accessibility.addView(reduce,new LinearLayout.LayoutParams(-1,-2));
        text(accessibility,s(R.string.reduce_motion_body),13,GlassStyle.SECONDARY,0);
    }
    private void settings() {
        title(R.string.nav_settings,R.string.permission_summary);
        LinearLayout device=card();heading(device,R.string.device_title);
        text(device,activity.getString(R.string.device_details,Build.MODEL,Build.VERSION.RELEASE),14,GlassStyle.INK,8);
        text(device,s("SM-F9760".equals(Build.MODEL)?R.string.device_fold8:DeviceSupport.eligible(Build.MODEL)?R.string.device_verified:R.string.device_preview_only),13,GlassStyle.SECONDARY,0);
        LinearLayout connect=card();heading(connect,R.string.setup_step_1);text(connect,s(R.string.setup_body),14,GlassStyle.SECONDARY,2);
        button(connect,R.string.connect_shizuku,true,activity::connectShizuku);button(connect,R.string.open_shizuku,false,activity::openShizuku);
        LinearLayout overlay=card();heading(overlay,R.string.setup_step_2);text(overlay,s(R.string.setup_overlay_body),14,GlassStyle.SECONDARY,2);
        button(overlay,R.string.allow_overlay,false,activity::allowOverlay);
        LinearLayout wallpaper=card();heading(wallpaper,R.string.wallpaper_title);text(wallpaper,s(R.string.wallpaper_body),14,GlassStyle.SECONDARY,2);
        button(wallpaper,R.string.read_setup,false,activity::openGuide);
        LinearLayout start=card();heading(start,R.string.setup_step_3);text(start,s(R.string.screen_access_body),14,GlassStyle.SECONDARY,8);text(start,s(R.string.power_body),13,GlassStyle.SECONDARY,2);
        button(start,MotionSettings.enabled(activity)?R.string.resume_animation:R.string.enable_animation,true,activity::startMotion);
        button(start,R.string.stop_animation,false,activity::stopMotion);state=text(start,"",13,GlassStyle.SECONDARY,0);state.setPadding(0,dp(16),0,0);
        LinearLayout home=card();heading(home,R.string.home_description);button(home,R.string.home_default,false,activity::defaultHome);
        button(home,R.string.inner_controls_title,false,()->explain(R.string.inner_controls_title,R.string.inner_controls_body));
        LinearLayout recovery=card();heading(recovery,R.string.recovery_title);text(recovery,s(R.string.recovery_body),14,GlassStyle.SECONDARY,12);text(recovery,s(R.string.battery_body),13,GlassStyle.SECONDARY,2);
        button(recovery,R.string.battery_settings,false,activity::batterySettings);
        LinearLayout sensor=card();heading(sensor,R.string.sensors_title);text(sensor,s(R.string.sensors_body),13,GlassStyle.SECONDARY,2);
        button(sensor,R.string.probe_sensors,false,()->activity.probe(0));diagnostic=text(sensor,s(R.string.not_measured),13,GlassStyle.SECONDARY,0);diagnostic.setPadding(0,dp(14),0,0);
        LinearLayout about=card();heading(about,R.string.about_title);text(about,s(R.string.about_body),13,GlassStyle.SECONDARY,10);
        Button language=button(about,R.string.settings_language,false,activity::chooseLanguage);language.setId(R.id.language_button);language.setText(activity.getString(R.string.language_current,activity.languageName()));
        text(page,activity.getString(R.string.app_version,BuildConfig.VERSION_NAME),12,GlassStyle.SECONDARY,10).setGravity(Gravity.CENTER);
    }
    private void explain(int title,int body){GlassStyle.dialog(new AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton(R.string.close,null).create());}

    private static final class FoldIllustration extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FoldIllustration(MainActivity c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float unit=Math.min(getWidth()/360f,getHeight()/194f);
            canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f-4*unit);canvas.scale(unit,unit);
            paint.setShader(new RadialGradient(0,58,126,new int[]{0x26346587,0x00346587},null,Shader.TileMode.CLAMP));canvas.drawOval(-145,44,145,95,paint);
            Path left=new Path();left.moveTo(-120,-63);left.lineTo(-8,-79);left.quadTo(-2,-80,0,-73);left.lineTo(0,65);left.lineTo(-120,45);left.quadTo(-128,43,-128,34);left.lineTo(-128,-51);left.quadTo(-128,-61,-120,-63);left.close();
            Path right=new Path();right.moveTo(10,-78);right.lineTo(114,-51);right.quadTo(122,-49,122,-39);right.lineTo(122,51);right.quadTo(122,60,113,62);right.lineTo(10,81);right.close();
            paint.setShader(new LinearGradient(-128,-80,120,80,new int[]{0xbfffffff,0x9ac7eafa,0xe8ffffff},null,Shader.TileMode.CLAMP));canvas.drawPath(left,paint);canvas.drawPath(right,paint);
            paint.setShader(null);paint.setColor(0xe6ffffff);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);canvas.drawPath(left,paint);canvas.drawPath(right,paint);
            paint.setColor(0x657eacca);paint.setStrokeWidth(1);canvas.drawLine(4,-73,4,69,paint);paint.setStyle(Paint.Style.FILL);
            for(int row=0;row<3;row++)for(int col=0;col<3;col++){
                float x=-104+col*29,y=-27+row*26;paint.setColor(new int[]{0x8049a1dc,0x707acdcf,0x98ffffff}[(row+col)%3]);canvas.drawRoundRect(x,y,x+18,y+18,6,6,paint);
                x=27+col*26;y=-20+row*24;canvas.drawRoundRect(x,y,x+16,y+16,6,6,paint);
            }
            paint.setColor(0x80ffffff);canvas.drawRoundRect(-105,-48,-34,-39,4,4,paint);canvas.drawRoundRect(28,58,87,62,2,2,paint);canvas.restore();
        }
    }
}
