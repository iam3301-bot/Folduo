package jp.bunkaich.sukashimotion;

import android.content.*;
import android.graphics.drawable.ColorDrawable;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class HomeFoldersTest {
    private SharedPreferences prefs;
    private List<AppCatalog.App> apps;
    @Before public void prepare(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs=context.getSharedPreferences("folder-regression",0);prefs.edit().clear().commit();
        apps=new ArrayList<>();for(int i=0;i<40;i++)apps.add(new AppCatalog.App("应用"+i,new ComponentName("folder.app"+i,"folder.app"+i+".Main"),new ColorDrawable(0xff5588aa)));
        SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<16;i++)edit.putString("slot_"+i,key(i));edit.commit();
    }
    @After public void cleanup(){prefs.edit().clear().commit();}
    @Test public void creatingWithFewerThanTwoDistinctAppsLeavesTheHomeUntouched(){
        Map<String,?> before=new HashMap<>(prefs.getAll());
        for(List<String> selection:List.of(List.<String>of(),List.of(key(0)),List.of(key(0),key(0)))){
            try{HomeFolders.create(prefs,0,"文件夹",selection);fail("A folder requires two distinct apps");}catch(IllegalArgumentException expected){}
            assertEquals(before,prefs.getAll());
        }
    }
    @Test public void creatingFolderPreservesSlotsAndPersistsMemberOrder(){
        HomeFolders.Folder created=HomeFolders.create(prefs,3,"学习",List.of(key(3),key(8),key(20),key(8)));
        SharedPreferences reopened=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("folder-regression",0);
        HomeFolders.Folder loaded=HomeFolders.read(reopened).get(3);
        assertEquals(created.id(),loaded.id());assertEquals("学习",loaded.name());assertEquals(List.of(key(3),key(8),key(20)),loaded.components());
        assertEquals("Moved apps vacate their old favorite position","",prefs.getString("slot_8",null));
        assertEquals(key(7),prefs.getString("slot_7",null));assertEquals(key(9),prefs.getString("slot_9",null));
        assertNull(AppCatalog.favorites(apps,prefs).get(3));assertNull(AppCatalog.favorites(apps,prefs).get(8));
        assertEquals(List.of(apps.get(3),apps.get(8),apps.get(20)),loaded.apps(apps));
    }
    @Test public void organizingMovesSelectedAppsOutOfOtherFoldersAndKeepsRemainingOrder(){
        HomeFolders.create(prefs,0,"第一组",List.of(key(0),key(1),key(2)));
        HomeFolders.create(prefs,4,"第二组",List.of(key(4),key(5)));
        HomeFolders.update(prefs,4,List.of(key(4),key(2),key(0),key(18)));
        assertEquals(List.of(key(1)),HomeFolders.read(prefs).get(0).components());
        assertEquals(List.of(key(4),key(2),key(0),key(18)),HomeFolders.read(prefs).get(4).components());
        assertFalse("Removed app is released back to subsequent app pages",HomeFolders.read(prefs).get(4).components().contains(key(5)));
        HomeFolders.update(prefs,4,List.of(key(4),key(2),key(0),key(18),key(1)));
        assertFalse("An emptied source folder must not leave a dead icon",HomeFolders.read(prefs).containsKey(0));
        assertEquals("",prefs.getString("slot_0",null));
    }
    @Test public void renameAndTemporaryUninstallDoNotDiscardStoredMembership(){
        HomeFolders.create(prefs,0,"工具",List.of(key(0),key(18),key(3)));
        HomeFolders.rename(prefs,0,"  常用工具  ");HomeFolders.rename(prefs,0," ");
        HomeFolders.Folder saved=HomeFolders.read(prefs).get(0);
        assertEquals("常用工具",saved.name());
        assertEquals(List.of(apps.get(0),apps.get(3)),saved.apps(apps.subList(0,16)));
        assertEquals(List.of(apps.get(0),apps.get(18),apps.get(3)),HomeFolders.read(prefs).get(0).apps(apps));
    }
    @Test public void dissolvingReturnsAppsWithoutReplacingUnrelatedFavorites(){
        HomeFolders.create(prefs,5,"工具",List.of(key(5),key(21),key(22),key(23)));
        HomeFolders.dissolve(prefs,5);
        assertTrue(HomeFolders.read(prefs).isEmpty());
        for(int i=0;i<16;i++)assertEquals(key(i),prefs.getString("slot_"+i,null));
        assertEquals("The complete catalog remains untouched",40,apps.size());
        // Overflow has no folder membership and therefore appears on the automatically paged catalog.
        assertTrue(apps.contains(apps.get(23)));assertFalse(prefs.getAll().keySet().stream().anyMatch(k->k.startsWith("folder:")));
    }
    @Test public void pinningIntoAFolderAddsAndPinningOutMovesWithoutDuplicates(){
        HomeFolders.create(prefs,0,"工具",List.of(key(0),key(1)));
        AppCatalog.pin(prefs,apps.get(4).component(),0);
        assertEquals(List.of(key(0),key(1),key(4)),HomeFolders.read(prefs).get(0).components());assertEquals("",prefs.getString("slot_4",null));
        AppCatalog.pin(prefs,apps.get(1).component(),6);
        assertEquals(List.of(key(0),key(4)),HomeFolders.read(prefs).get(0).components());assertEquals(key(1),prefs.getString("slot_6",null));
    }
    @Test public void unopenedDefaultPositionsNeverDuplicateFolderMembers(){
        prefs.edit().clear().putString("slot_0",key(0)).commit();
        HomeFolders.create(prefs,0,"一组",List.of(key(0),key(1),key(2)));
        for(AppCatalog.App favorite:AppCatalog.favorites(apps,prefs))
            if(favorite!=null)assertFalse(HomeFolders.read(prefs).get(0).components().contains(favorite.component().flattenToString()));
    }
    private String key(int index){return apps.get(index).component().flattenToString();}
}
