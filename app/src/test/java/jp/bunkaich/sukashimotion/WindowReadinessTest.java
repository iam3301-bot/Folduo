package jp.bunkaich.sukashimotion;

import org.junit.Test;
import static org.junit.Assert.*;

public class WindowReadinessTest {
    private static final String DRAWN="""
      Window #0 Window{anonymous}:
        mDisplayId=0 taskId=7
        ty=BASE_APPLICATION
        mViewVisibility=0x0 mHaveFrame=true mObscured=true
        mHasSurface=true isReadyForDisplay()=true
        Frames: parent=[0,0][1968,2184] display=[0,0][1968,2184] frame=[0,0][1968,2184] last=[0,0][1968,2184] insetsChanged=false
        Surface: shown=true mDrawState=HAS_DRAWN mLastHidden=false
        isOnScreen=true
    """;
    @Test public void drawnAppIsReadyEvenBehindFreeze(){assertTrue(WindowReadiness.parse(DRAWN,0).ready());}
    @Test public void appWaitingForBufferIsNotReady(){assertFalse(WindowReadiness.parse(DRAWN.replace("HAS_DRAWN","DRAW_PENDING"),0).ready());}
    @Test public void changingInsetsIsNotReady(){assertFalse(WindowReadiness.parse(DRAWN.replace("insetsChanged=false","insetsChanged=true"),0).ready());}
    @Test public void otherDisplayDoesNotQualify(){assertFalse(WindowReadiness.parse(DRAWN,1).ready());}
    @Test public void overlayDoesNotQualify(){assertFalse(WindowReadiness.parse(DRAWN.replace("BASE_APPLICATION","APPLICATION_OVERLAY"),0).ready());}
    @Test public void onlyGeometryIsReturned(){assertEquals("[0,0][1968,2184]",WindowReadiness.parse(DRAWN,0).geometry());}
    @Test public void exitingWindowIsNotReady(){assertFalse(WindowReadiness.parse(DRAWN+" mAnimatingExit=true",0).ready());}
    @Test public void anotherDrawnTaskDoesNotCompleteTheHandoff(){
        assertFalse(WindowReadiness.parse(DRAWN,0,8,false).ready());
        assertTrue(WindowReadiness.parse(DRAWN,0,7,false).ready());
    }
    @Test public void homeWithoutWallpaperIsNotACompletedNativeHandoff(){
        assertFalse(WindowReadiness.parse(DRAWN,0,7,true).ready());
    }
    @Test public void wallpaperOnTheOtherDisplayDoesNotHideTheBlackHomeBug(){
        String wallpaper=DRAWN.replace("Window #0","Window #1").replace("ty=BASE_APPLICATION","ty=WALLPAPER").replace("mDisplayId=0","mDisplayId=1");
        assertFalse(WindowReadiness.parse(DRAWN+wallpaper,0,7,true).ready());
    }
    @Test public void homeAndWallpaperMustBothHaveDrawnOnNativeDisplay(){
        String wallpaper=DRAWN.replace("Window #0","Window #1").replace("ty=BASE_APPLICATION","ty=WALLPAPER");
        assertTrue(WindowReadiness.parse(DRAWN+wallpaper,0,7,true).ready());
        assertFalse(WindowReadiness.parse(DRAWN+wallpaper.replace("shown=true","shown=false"),0,7,true).ready());
    }
    @Test public void expectedTaskCanFollowAnUnrelatedVisibleWindow(){
        String expected=DRAWN.replace("Window #0","Window #1").replace("taskId=7","taskId=8");
        assertTrue(WindowReadiness.parse(DRAWN+expected,0,8,false).ready());
    }
}
