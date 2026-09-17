package jp.bunkaich.sukashimotion;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Checks persisted user choices and error paths, not just the existence of translated keys. */
public class ChineseInterfaceTest {
    private Context context;
    private ActivityScenario<MainActivity> screen;
    private int originalTint;
    @Before public void start(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        MotionSettings.setEnabled(context,false);originalTint=Math.round(GlassStyle.concentration(context)*100);
        context.getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags("zh-Hans"));
        screen=ActivityScenario.launch(MainActivity.class);
    }
    @After public void stop(){screen.close();GlassStyle.concentration(context,originalTint);}
    @Test public void concentrationAndActiveTabSurviveRecreation(){
        screen.onActivity(a->{
            assertEquals("折叠流光",a.getString(R.string.app_name));
            a.findViewById(R.id.nav_appearance).performClick();
            ((SeekBar)a.findViewById(R.id.glass_concentration)).setProgress(82);
        });
        screen.recreate();
        screen.onActivity(a->{
            assertTrue(a.findViewById(R.id.nav_appearance).isSelected());
            assertEquals(82,((SeekBar)a.findViewById(R.id.glass_concentration)).getProgress());
            assertEquals(.82f,GlassStyle.concentration(a),.001f);
        });
    }
    @Test public void nativeGlassShaderCompiles(){
        screen.onActivity(a->{
            try {
                var material=new GlassStyle.Material(a.findViewById(R.id.nav_motion),26,false);
                var shader=GlassStyle.Material.class.getDeclaredField("shader");shader.setAccessible(true);
                assertNotNull("The glass effect must compile, not silently fall back",shader.get(material));
            } catch (ReflectiveOperationException e) {throw new AssertionError(e);}
        });
    }
    @Test public void shellErrorsAndStoredRecoveryRemainChinese(){
        Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(LocaleList.forLanguageTags("zh-Hans"));
        Context zh=context.createConfigurationContext(config);
        UiText error=UiText.of(R.string.prepare_failed,UiText.raw("IllegalStateException: @folduo/err_states_unavailable"));
        String decoded=UiText.decode(zh,error.encode(zh)).resolve(zh);
        assertTrue(decoded.contains("系统未提供所需的双屏并行状态"));
        assertFalse(decoded.contains("IllegalStateException"));assertFalse(decoded.contains("@folduo/"));
        assertEquals(zh.getString(R.string.diagnostic_system_error),UiText.raw("NoSuchMethodException: hidden API changed").resolve(zh));
        assertEquals("折叠动画",zh.getString(R.string.notification_channel));
        assertEquals("恢复",zh.getString(R.string.resume));
    }
    @Test public void diagnosticShowsNumbersWithChineseLabels(){
        screen.onActivity(a->{
            Bundle b=new Bundle();b.putInt("uid",2000);b.putString("display","inner=7 / cover=8 / current=8 / base=1");
            String report=MainActivity.formatReport(a,b);
            assertTrue(report.contains("内屏状态 7"));assertTrue(report.contains("外屏状态 8"));
            assertFalse(report.contains("inner="));assertFalse(report.contains("\\n"));
            a.findViewById(R.id.nav_settings).performClick();
            assertNotNull(a.findViewById(R.id.language_button));
        });
    }
}
