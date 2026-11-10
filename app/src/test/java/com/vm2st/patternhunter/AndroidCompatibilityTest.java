package com.vm2st.patternhunter;

import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,36},qualifiers="ru-rRU-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class AndroidCompatibilityTest {
    @Test public void startupInputAnalysisRecreationAndShutdownWorkOnOldAndNewAndroid() throws Exception {
        android.content.Context app=ApplicationProvider.getApplicationContext();app.getSharedPreferences("pattern_hunter_prefs",0).edit().clear().commit();AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible();try{
            ((EditText)c.get().findViewById(R.id.input)).setText("123456789");MainActivityUiTest.waitFor(c.get(),R.id.summaryCard);
            assertEquals(c.get().getString(R.string.stat_length,9),((TextView)c.get().findViewById(R.id.statLength)).getText().toString());
            c.recreate();assertEquals("123456789",((EditText)c.get().findViewById(R.id.input)).getText().toString());MainActivityUiTest.waitFor(c.get(),R.id.summaryCard);
        }finally{MainActivityUiTest.close(c);}
    }
    @Test public void burgundyThemeStartsAndSurvivesRecreationOnOldAndNewAndroid() throws Exception {
        android.content.Context app=ApplicationProvider.getApplicationContext();app.getSharedPreferences("pattern_hunter_prefs",0).edit().clear().putInt("theme_mode",MainActivity.THEME_BURGUNDY).commit();AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible();try{
            MainActivityUiTest.assertOnlyChecked(c.get(),R.id.themeBurgundy);assertEquals(0xFF892929,MainActivityUiTest.backgroundColor(c.get().findViewById(R.id.root)));
            if(android.os.Build.VERSION.SDK_INT<35){assertEquals(0xFF892929,c.get().getWindow().getStatusBarColor());assertEquals(0xFF892929,c.get().getWindow().getNavigationBarColor());}
            ((EditText)c.get().findViewById(R.id.input)).setText("abacaba");MainActivityUiTest.waitFor(c.get(),R.id.summaryCard);
            c.recreate();MainActivityUiTest.assertOnlyChecked(c.get(),R.id.themeBurgundy);assertEquals(0xFF892929,MainActivityUiTest.backgroundColor(c.get().findViewById(R.id.root)));
            assertEquals("abacaba",((EditText)c.get().findViewById(R.id.input)).getText().toString());MainActivityUiTest.waitFor(c.get(),R.id.summaryCard);
        }finally{MainActivityUiTest.close(c);}
    }
}
