package jp.bunkaich.sukashimotion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** One cover page, retained on the right when unfolded. The left reveals a second page. */
final class HomeScene extends FrameLayout {
    interface Actions { void launch(AppCatalog.App app); void choose(int slot); void drawer(); void settings(); void note(); default void pin(AppCatalog.App app) {} default void appearance() {} default void edit(int slot) { choose(slot); } default void folder(int slot) {} }
    private final Actions actions;
    final HomePage primary;
    final TodayPage today;
    final Wallpaper wallpaper;
    boolean inner, reverse;
    float amount;
    long contentRevision;
    String lastTickKey="";
    private float startX,startY;
    private List<AppCatalog.App> catalog=List.of(),favorites=List.of(),remaining=List.of();
    private Map<Integer,HomeFolders.Folder> folders=Map.of();
    private int page;
    HomeScene(Context context, Actions actions) {
        super(context); this.actions=actions;
        if(android.os.Build.VERSION.SDK_INT>=36)setRequestedFrameRate(HomeActivity.FRAME_RATE); setClipChildren(true);
        wallpaper=new Wallpaper(context);addView(wallpaper);
        today=new TodayPage(context);addView(today);
        primary=new HomePage(context,true);addView(primary);
        setContentDescription(context.getString(R.string.home_description));
    }
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void setCatalog(List<AppCatalog.App> apps){catalog=new ArrayList<>(apps);rebuildPages();}
    void updateApps(List<AppCatalog.App> apps){favorites=new ArrayList<>(apps);rebuildPages();}
    void updateFolders(Map<Integer,HomeFolders.Folder> saved){folders=new LinkedHashMap<>(saved);rebuildPages();}
    private void rebuildPages(){
        HashSet<String> used=new HashSet<>();for(AppCatalog.App app:favorites)if(app!=null)used.add(app.component().flattenToString());
        for(HomeFolders.Folder folder:folders.values())used.addAll(folder.components());
        remaining=new ArrayList<>();for(AppCatalog.App app:catalog)if(used.add(app.component().flattenToString()))remaining.add(app);
        showPage(Math.min(page,pageCount()-1));
    }
    int pageCount(){return 1+(remaining.size()+15)/16;}
    int currentPage(){return page;}
    private void showPage(int next){
        page=Math.max(0,Math.min(pageCount()-1,next));
        int from=(page-1)*16;
        primary.updateApps(page==0?favorites:remaining.subList(from,Math.min(from+16,remaining.size())));
        primary.pageIndicator.setText(pageCount()>1?getContext().getString(R.string.home_page_number,page+1,pageCount()):getContext().getString(R.string.home_page_favorites));
        contentRevision++;
    }
    void applyTheme(){
        Context c=getContext();HomeTheme.backdrop(wallpaper);
        for(TextView text:new TextView[]{primary.clock,primary.date,primary.drawer,today.title,today.largeClock,today.battery,today.note})text.setTextColor(HomeTheme.ink(c));
        for(TextView text:primary.labels)text.setTextColor(HomeTheme.ink(c));
        for(TextView text:new TextView[]{primary.clockCaption,primary.dateCaption,primary.pageIndicator,primary.appearance,today.date,today.edit})text.setTextColor(HomeTheme.secondary(c));
        for(View view:new View[]{primary.clock,primary.date,today.battery,today.note})HomeTheme.panel(view,28);
        HomeTheme.panel(primary.drawer,24);primary.updateApps(primary.apps);invalidate();
    }
    void tick(int battery,String note){
        String key=LocalDate.now()+" "+LocalTime.now().getHour()+":"+LocalTime.now().getMinute()+"/"+battery+"/"+note;
        if(key.equals(lastTickKey))return;lastTickKey=key;
        primary.tick();today.tick(battery,note);contentRevision++;
    }
    Bitmap captureForBlur(float scale){
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(getWidth()*scale)),Math.max(1,Math.round(getHeight()*scale)),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.scale(bitmap.getWidth()/(float)getWidth(),bitmap.getHeight()/(float)getHeight());
        wallpaper.draw(canvas);
        if(inner){canvas.save();canvas.translate(today.getLeft(),today.getTop());today.draw(canvas);canvas.restore();}
        canvas.save();canvas.translate(primary.getLeft(),primary.getTop());primary.draw(canvas);canvas.restore();return bitmap;
    }
    void setFold(boolean isInner,float effect) {setFold(isInner,effect,reverse);}
    void setFold(boolean isInner,float effect,boolean reversed) {
        if(inner!=isInner||reverse!=reversed){inner=isInner;reverse=reversed;requestLayout();wallpaper.inner=inner;wallpaper.reverse=reverse;wallpaper.invalidate();}
        amount=effect;
        today.setVisibility(inner?VISIBLE:GONE);
        // Both pages remain in place. The effect only defocuses this exact content.
        today.setAlpha(1);
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);setMeasuredDimension(width,height);
        wallpaper.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
        int pageWidth=inner?width/2:width;
        primary.measure(exact(pageWidth),exact(height));today.measure(exact(pageWidth),exact(height));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int w=r-l,h=b-t,pw=inner?w/2:w;wallpaper.layout(0,0,w,h);
        int homeX=inner&&!reverse?w-pw:0,otherX=inner&&reverse?pw:0;
        primary.layout(homeX,0,homeX+pw,h);today.layout(otherX,0,otherX+pw,h);
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-startX,dy=e.getY()-startY;
            if(startY-e.getY()>dp(56)&&Math.abs(dx)<dp(80))return true;
            if(Math.abs(dx)>dp(56)&&Math.abs(dx)>Math.abs(dy)*1.3f)return true;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=e.getX()-startX,dy=e.getY()-startY;
            if(Math.abs(dx)>dp(56)&&Math.abs(dx)>Math.abs(dy)*1.3f){showPage(page+(dx<0?1:-1));performClick();return true;}
            if(-dy>dp(56)&&Math.abs(dx)<dp(80)){actions.drawer();performClick();return true;}
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    static GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setFontFeatureSettings("tnum");return v;}
    final class HomePage extends ViewGroup {
        final TextView clock,date,clockCaption,dateCaption;
        final LinearLayout[] tiles=new LinearLayout[16];
        final ImageView[] icons=new ImageView[16];
        final TextView[] labels=new TextView[16];
        final TextView drawer,pageIndicator,appearance;
        final boolean interactive;
        List<AppCatalog.App> apps=List.of();
        HomePage(Context c,boolean interactive){
            super(c);this.interactive=interactive;
            clock=text(c,"",34,HomeTheme.ink(c));clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            date=text(c,"",38,HomeTheme.ink(c));date.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            clockCaption=text(c,c.getString(R.string.home_time),11,HomeTheme.secondary(c));dateCaption=text(c,"",11,HomeTheme.secondary(c));
            HomeTheme.panel(clock,28);HomeTheme.panel(date,28);
            addView(clock);addView(date);addView(clockCaption);addView(dateCaption);
            for(int i=0;i<16;i++){
                final int slot=i;LinearLayout tile=new LinearLayout(c);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
                ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                TextView label=text(c,"",11,HomeTheme.ink(c));label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);
                tile.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(23));lp.topMargin=dp(5);tile.addView(label,lp);
                icons[i]=icon;labels[i]=label;tiles[i]=tile;addView(tile);
                if(interactive){
                    tile.setFocusable(true);tile.setClickable(true);tile.setBackground(round(0x00000000,dp(18)));
                    tile.setOnClickListener(v->{if(page==0&&folders.containsKey(slot))actions.folder(slot);else if(apps.size()>slot&&apps.get(slot)!=null)actions.launch(apps.get(slot));else if(page==0)actions.choose(slot);});
                    tile.setOnLongClickListener(v->{if(page==0)actions.edit(slot);else if(apps.size()>slot&&apps.get(slot)!=null)actions.pin(apps.get(slot));return true;});
                }
            }
            appearance=text(c,c.getString(R.string.home_theme_title),12,HomeTheme.secondary(c));appearance.setOnClickListener(v->actions.appearance());appearance.setFocusable(true);addView(appearance);
            pageIndicator=text(c,c.getString(R.string.home_page_favorites),11,HomeTheme.secondary(c));addView(pageIndicator);
            drawer=text(c,c.getString(R.string.home_all_apps),12,HomeTheme.ink(c));HomeTheme.panel(drawer,24);addView(drawer);
            if(interactive){drawer.setOnClickListener(v->actions.drawer());drawer.setFocusable(true);}
            else {setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setEnabled(false);}
            tick();
        }
        void updateApps(List<AppCatalog.App> list){apps=list;for(int i=0;i<16;i++){
            AppCatalog.App app=i<list.size()?list.get(i):null;
            HomeFolders.Folder folder=page==0?folders.get(i):null;
            if(folder!=null){
                tiles[i].setVisibility(VISIBLE);icons[i].setBackground(null);icons[i].setImageDrawable(new FolderIcon(folder.apps(catalog),HomeTheme.palette(getContext())==1?0xff566273:0xcce8eef7));
                labels[i].setText(folder.name());tiles[i].setContentDescription(getContext().getString(R.string.folder_description,folder.name(),folder.apps(catalog).size()));continue;
            }
            tiles[i].setVisibility(page>0&&app==null?INVISIBLE:VISIBLE);
            icons[i].setImageDrawable(app==null?null:app.icon().getConstantState()!=null?app.icon().getConstantState().newDrawable():app.icon());
            icons[i].setBackground(app==null?round(0x99ffffff,dp(16)):null);
            labels[i].setText(app==null?getContext().getString(R.string.home_add):app.label());tiles[i].setContentDescription(getContext().getString(page==0?R.string.folder_home_icon_description:R.string.home_page_pin_description,app==null?getContext().getString(R.string.home_add_app):app.label()));
        }}
        void tick(){clock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")));LocalDate d=LocalDate.now();date.setText(String.valueOf(d.getDayOfMonth()));dateCaption.setText(d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(getResources().getConfiguration().getLocales().get(0),"MMMEEE"),getResources().getConfiguration().getLocales().get(0))));}
        @Override protected void onMeasure(int ws,int hs){
            int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);
            int pad=dp(18),gap=dp(12),cardW=(w-2*pad-gap)/2,cardH=Math.min(dp(160),(int)(h*.205f));
            clock.measure(exact(cardW),exact(cardH));date.measure(exact(cardW),exact(cardH));
            clockCaption.measure(exact(cardW),exact(dp(26)));dateCaption.measure(exact(cardW),exact(dp(26)));
            int cellW=(w-2*dp(12))/4,cellH=(int)(h*.108f);int icon=Math.max(dp(24),Math.min(dp(60),Math.min((int)(cellW*.66f),cellH-dp(33))));
            for(int i=0;i<16;i++){icons[i].getLayoutParams().width=icon;icons[i].getLayoutParams().height=icon;tiles[i].measure(exact(cellW),exact(cellH));}
            drawer.measure(exact(Math.min(w-dp(36),dp(210))),exact(dp(46)));pageIndicator.measure(exact(w-dp(36)),exact(dp(24)));appearance.measure(exact(dp(108)),exact(dp(48)));
        }
        @Override protected void onLayout(boolean c,int l,int t,int r,int b){
            int w=r-l,h=b-t,pad=dp(18),cw=clock.getMeasuredWidth(),ch=clock.getMeasuredHeight();
            int top=(int)(h*.095f);clock.layout(pad,top,pad+cw,top+ch);date.layout(w-pad-cw,top,w-pad,top+ch);
            int captionTop=top+ch-dp(37);clockCaption.layout(pad,captionTop,pad+cw,captionTop+dp(26));dateCaption.layout(w-pad-cw,captionTop,w-pad,captionTop+dp(26));
            int gridTop=(int)(h*.36f),cellW=tiles[0].getMeasuredWidth(),cellH=tiles[0].getMeasuredHeight();
            for(int i=0;i<16;i++){int x=(w-cellW*4)/2+(i%4)*cellW,y=gridTop+(i/4)*cellH;tiles[i].layout(x,y,x+cellW,y+cellH);}
            appearance.layout(dp(16),h-dp(64),dp(124),h-dp(16));
            int py=(int)(h*.81f);pageIndicator.layout(pad,py,w-pad,py+dp(24));
            int dw=drawer.getMeasuredWidth(),dh=drawer.getMeasuredHeight(),dy=Math.min(h-dp(80),(int)(h*.855f));drawer.layout((w-dw)/2,dy,(w+dw)/2,dy+dh);
        }
    }
    final class TodayPage extends ViewGroup {
        final TextView title,largeClock,date,battery,note,edit;
        TodayPage(Context c){super(c);
            title=text(c,c.getString(R.string.home_today),18,HomeTheme.ink(c));title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            largeClock=text(c,"",62,HomeTheme.ink(c));largeClock.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);largeClock.setTypeface(Typeface.create("sans-serif-thin",Typeface.NORMAL));
            date=text(c,"",14,HomeTheme.secondary(c));date.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            battery=text(c,"",20,HomeTheme.ink(c));HomeTheme.panel(battery,28);
            note=text(c,"",17,HomeTheme.ink(c));note.setGravity(Gravity.TOP|Gravity.START);note.setPadding(dp(22),dp(22),dp(22),dp(22));note.setMaxLines(6);note.setEllipsize(TextUtils.TruncateAt.END);HomeTheme.panel(note,28);note.setOnClickListener(v->actions.note());
            edit=text(c,c.getString(R.string.home_edit_note),12,HomeTheme.secondary(c));edit.setOnClickListener(v->actions.note());
            for(View v:new View[]{title,largeClock,date,battery,note,edit})addView(v);
        }
        void tick(int level,String saved){largeClock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")));date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(getResources().getConfiguration().getLocales().get(0),"MMMddEEE"),getResources().getConfiguration().getLocales().get(0))));battery.setText(getContext().getString(R.string.home_battery,level<0?"—":level+"%"));note.setText(saved.isBlank()?getContext().getString(R.string.home_note_empty):saved);}
        @Override protected void onMeasure(int ws,int hs){
            int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);int cw=exact(w-dp(44));
            title.measure(cw,exact(dp(35)));largeClock.measure(cw,exact((int)(h*.28)-(int)(h*.15)));
            date.measure(cw,exact((int)(h*.33)-(int)(h*.28)));battery.measure(cw,exact((int)(h*.51)-(int)(h*.38)));
            note.measure(cw,exact((int)(h*.80)-(int)(h*.55)));edit.measure(cw,exact(dp(48)));
        }
        @Override protected void onLayout(boolean c,int l,int t,int r,int b){int w=r-l,h=b-t,p=dp(22);
            title.layout(p,(int)(h*.095),w-p,(int)(h*.095)+dp(35));largeClock.layout(p,(int)(h*.15),w-p,(int)(h*.28));date.layout(p,(int)(h*.28),w-p,(int)(h*.33));
            battery.layout(p,(int)(h*.38),w-p,(int)(h*.51));note.layout(p,(int)(h*.55),w-p,(int)(h*.80));edit.layout(p,(int)(h*.81),w-p,(int)(h*.81)+dp(48));
        }
    }
    static int exact(int n){return MeasureSpec.makeMeasureSpec(Math.max(0,n),MeasureSpec.EXACTLY);}
    static final class Wallpaper extends View {
        boolean inner,reverse;
        Wallpaper(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);HomeTheme.backdrop(this);}
    }
}
