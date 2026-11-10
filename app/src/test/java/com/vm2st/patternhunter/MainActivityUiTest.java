package com.vm2st.patternhunter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="ru-rRU-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityUiTest {
    private Context app(){return ApplicationProvider.getApplicationContext();}
    @Before public void reset(){
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().clear().commit();
    }
    static Object field(Object target,String name) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private ActivityController<MainActivity> activity(){return Robolectric.buildActivity(MainActivity.class).setup().visible();}
    static void close(ActivityController<?> controller){controller.pause().stop().destroy();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    static void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    static void waitFor(Activity activity,int statusId) throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(151));
        long end=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();if(activity.findViewById(statusId).getVisibility()==View.VISIBLE)return;Thread.sleep(5);}
        fail("UI analysis did not complete");
    }
    private void analyze(MainActivity activity,String text) throws Exception {((EditText)activity.findViewById(R.id.input)).setText(text);waitFor(activity,R.id.summaryCard);}

    @Test public void textAnalysisRendersCorrectStatisticsAndResults() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            analyze(c.get(),"😀a😀");
            assertEquals(c.get().getString(R.string.stat_length,3),((TextView)c.get().findViewById(R.id.statLength)).getText().toString());
            assertTrue(((RecyclerView)c.get().findViewById(R.id.recyclerView)).getAdapter().getItemCount()>0);
        }finally{close(c);}
    }
    @Test public void clearImmediatelyHidesOldStatisticsAndCancelsDebouncedWork() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            analyze(c.get(),"123456789");c.get().findViewById(R.id.btnClear).performClick();
            assertEquals(View.GONE,c.get().findViewById(R.id.summaryCard).getVisibility());
            assertEquals(0,((RecyclerView)c.get().findViewById(R.id.recyclerView)).getAdapter().getItemCount());
            assertEquals(c.get().getString(R.string.empty_prompt),((TextView)c.get().findViewById(R.id.statusHeader)).getText().toString());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(View.GONE,c.get().findViewById(R.id.summaryCard).getVisibility());
        }finally{close(c);}
    }
    @Test public void pendingInputNeverDisplaysPreviousStringStatistics() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            analyze(c.get(),"111");((EditText)c.get().findViewById(R.id.input)).setText("abcdef");
            assertEquals(View.GONE,c.get().findViewById(R.id.summaryCard).getVisibility());
            assertEquals(c.get().getString(R.string.analysis_pending),((TextView)c.get().findViewById(R.id.statusHeader)).getText().toString());
            waitFor(c.get(),R.id.summaryCard);assertEquals(c.get().getString(R.string.stat_length,6),((TextView)c.get().findViewById(R.id.statLength)).getText().toString());
        }finally{close(c);}
    }
    @Test public void whitespaceIsAnalyzedRatherThanTreatedAsEmpty() throws Exception {
        ActivityController<MainActivity> c=activity();try{analyze(c.get(),"   ");assertEquals(c.get().getString(R.string.stat_length,3),((TextView)c.get().findViewById(R.id.statLength)).getText().toString());}finally{close(c);}
    }
    @Test public void pasteUsesPlainTextWithoutUrlSpans() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            SpannableString text=new SpannableString("abacaba");text.setSpan(new URLSpan("https://example.invalid"),0,7,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ((ClipboardManager)c.get().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("label",text));
            c.get().findViewById(R.id.btnPaste).performClick();EditText input=c.get().findViewById(R.id.input);
            assertEquals("abacaba",input.getText().toString());assertEquals(0,input.getText().getSpans(0,input.length(),URLSpan.class).length);
            waitFor(c.get(),R.id.summaryCard);
        }finally{close(c);}
    }
    @Test public void hugePasteIsBoundedWithoutSplittingSurrogateOrInvalidCursor() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            String longText="a".repeat(PatternHunterEngine.MAX_INPUT_UTF16_LENGTH-1)+"😀tail";
            ((ClipboardManager)c.get().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("",longText));
            c.get().findViewById(R.id.btnPaste).performClick();EditText input=c.get().findViewById(R.id.input);
            assertEquals(PatternHunterEngine.MAX_INPUT_UTF16_LENGTH-1,input.length());assertEquals(input.length(),input.getSelectionStart());
            assertFalse(Character.isHighSurrogate(input.getText().charAt(input.length()-1)));
            assertEquals(c.get().getString(R.string.input_too_long),ShadowToast.getTextOfLatestToast());
        }finally{close(c);}
    }
    @Test public void nonTextClipboardDoesNotOpenContentUrisOrCrash() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            ((EditText)c.get().findViewById(R.id.input)).setText("keep");
            ((ClipboardManager)c.get().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newRawUri("",Uri.parse("content://untrusted.invalid/private")));
            c.get().findViewById(R.id.btnPaste).performClick();assertEquals("keep",((EditText)c.get().findViewById(R.id.input)).getText().toString());
            assertEquals(c.get().getString(R.string.clipboard_empty),ShadowToast.getTextOfLatestToast());
        }finally{close(c);}
    }
    @Test public void randomExamplesDoNotRepeatWithinPoolOrAcrossImmediateBoundary() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            Set<String> examples=new HashSet<>();String last="";
            for(int i=0;i<15;i++){c.get().findViewById(R.id.btnRandom).performClick();last=((EditText)c.get().findViewById(R.id.input)).getText().toString();assertTrue(examples.add(last));}
            c.get().findViewById(R.id.btnRandom).performClick();assertNotEquals(last,((EditText)c.get().findViewById(R.id.input)).getText().toString());
        }finally{close(c);}
    }
    @Test @SuppressWarnings("unchecked") public void lastPoolItemDoesNotRepeatManuallyEnteredCurrentText() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            ((EditText)c.get().findViewById(R.id.input)).setText("278179499912");
            List<Integer> pool=(List<Integer>)field(c.get(),"availableSampleIndices");pool.clear();pool.add(0);
            c.get().findViewById(R.id.btnRandom).performClick();assertNotEquals("278179499912",((EditText)c.get().findViewById(R.id.input)).getText().toString());
        }finally{close(c);}
    }
    @Test public void recreationRetainsTextAndRemainingRandomPool() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            c.get().findViewById(R.id.btnRandom).performClick();String text=((EditText)c.get().findViewById(R.id.input)).getText().toString();
            List<?> pool=new ArrayList<>((List<?>)field(c.get(),"availableSampleIndices"));Object previous=field(c.get(),"analysisDispatcher");
            c.recreate();assertEquals(text,((EditText)c.get().findViewById(R.id.input)).getText().toString());assertEquals(pool,field(c.get(),"availableSampleIndices"));
            waitFor(c.get(),R.id.summaryCard);assertEquals(Boolean.TRUE,field(previous,"closed"));
        }finally{close(c);}
    }
    @Test public void corruptThemePreferenceDoesNotPreventStartup() {
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().putString("theme_mode","bad").commit();
        ActivityController<MainActivity> c=activity();try{assertTrue(((RadioButton)c.get().findViewById(R.id.themeSystem)).isChecked());}finally{close(c);}
    }
    @Test @Config(qualifiers="en-rUS-w360dp-h800dp-port-mdpi") public void resourceLocaleWinsOverUnrelatedProcessDefault() throws Exception {
        Locale original=Locale.getDefault();Locale.setDefault(new Locale("ru"));
        ActivityController<MainActivity> c=activity();try{
            analyze(c.get(),"abcd");assertTrue(((TextView)c.get().findViewById(R.id.statType)).getText().toString().contains("Latin Alphabetic"));
        }finally{close(c);Locale.setDefault(original);}
    }
    @Test public void imeAndCutoutInsetsDoNotCoverInputOrContent() {
        ActivityController<MainActivity> c=activity();try{
            View root=c.get().findViewById(R.id.root);
            ViewCompat.dispatchApplyWindowInsets(root,new WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,24,0,48)).setInsets(WindowInsetsCompat.Type.displayCutout(),Insets.of(10,32,12,0)).setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,300)).build());
            assertEquals(10,root.getPaddingLeft());assertEquals(32,root.getPaddingTop());assertEquals(12,root.getPaddingRight());assertEquals(300,root.getPaddingBottom());
            ViewCompat.dispatchApplyWindowInsets(root,new WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,24,0,48)).build());assertEquals(48,root.getPaddingBottom());
        }finally{close(c);}
    }
    @Test public void telegramKeepsOriginalFixedHttpsTarget() {
        ActivityController<MainActivity> c=activity();try{
            c.get().findViewById(R.id.telegramLinkContainer).performClick();Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals("https://t.me/vm2_studios",intent.getDataString());
        }finally{close(c);}
    }
    @Test public void destroyingScreenClosesWorkerAndRemovesPendingDebounce() throws Exception {
        ActivityController<MainActivity> c=activity();((EditText)c.get().findViewById(R.id.input)).setText("abacaba");
        MainActivity activity=c.get();Object dispatcher=field(activity,"analysisDispatcher");close(c);
        assertEquals(Boolean.TRUE,field(dispatcher,"closed"));assertNull(field(activity,"pendingAnalysisRunnable"));
    }
    public static class FailingActivity extends MainActivity {@Override protected AnalysisSummary analyzeInput(String text){throw new IllegalStateException("Test failure");}}
    @Test public void workerFailureShowsAnErrorInsteadOfLeavingOldResults() throws Exception {
        ActivityController<FailingActivity> c=Robolectric.buildActivity(FailingActivity.class).setup().visible();try{
            ((EditText)c.get().findViewById(R.id.input)).setText("abc");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(151));
            long end=System.nanoTime()+3_000_000_000L;
            while(System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();if(((TextView)c.get().findViewById(R.id.statusHeader)).getText().toString().equals(c.get().getString(R.string.analysis_failed)))return;Thread.sleep(5);}
            fail("Error not displayed");
        }finally{close(c);}
    }
    private static final int[] THEME_OPTIONS={R.id.themeSystem,R.id.themeLight,R.id.themeDark,R.id.themeBurgundy};
    private static int savedTheme(){return ApplicationProvider.getApplicationContext().getSharedPreferences("pattern_hunter_prefs",0).getInt("theme_mode",Integer.MIN_VALUE);}
    static void assertOnlyChecked(Activity activity,int checkedId){
        for(int option:THEME_OPTIONS)assertEquals("Theme option "+activity.getResources().getResourceEntryName(option),option==checkedId,((RadioButton)activity.findViewById(option)).isChecked());
    }
    static int backgroundColor(View view){return ((android.graphics.drawable.ColorDrawable)view.getBackground()).getColor();}
    private static int solidColor(View view){return ((android.graphics.drawable.GradientDrawable)view.getBackground()).getColor().getDefaultColor();}
    private static void idleMain(){Shadows.shadowOf(Looper.getMainLooper()).idle();}

    @Test public void burgundyIsSavedAndAppliedWithItsOwnPalette() throws Exception {
        ActivityController<MainActivity> c=activity();try{
            assertOnlyChecked(c.get(),R.id.themeSystem);MainActivity before=c.get();
            analyze(before,"abacaba");
            before.findViewById(R.id.themeBurgundy).performClick();
            assertEquals(MainActivity.THEME_BURGUNDY,savedTheme());assertOnlyChecked(before,R.id.themeBurgundy);
            idleMain();MainActivity after=c.get();assertNotSame(before,after);
            assertOnlyChecked(after,R.id.themeBurgundy);
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES,AppCompatDelegate.getDefaultNightMode());
            assertEquals(0xFF892929,backgroundColor(after.findViewById(R.id.root)));
            // API 35 enforces edge-to-edge: bar colours are transparent and the burgundy root is drawn behind them.
            androidx.core.view.WindowInsetsControllerCompat bars=androidx.core.view.WindowCompat.getInsetsController(after.getWindow(),after.getWindow().getDecorView());
            assertFalse(bars.isAppearanceLightStatusBars());assertFalse(bars.isAppearanceLightNavigationBars());
            assertEquals(0xFFFFF5F5,((TextView)after.findViewById(R.id.themeBurgundy)).getCurrentTextColor());
            // The theme switch recreates the screen; the entered text is kept and analysed again.
            assertEquals("abacaba",((EditText)after.findViewById(R.id.input)).getText().toString());
            waitFor(after,R.id.summaryCard);
            assertEquals(after.getString(R.string.stat_length,7),((TextView)after.findViewById(R.id.statLength)).getText().toString());
            assertEquals(0xFF6B1F1F,solidColor(after.findViewById(R.id.summaryCard)));
        }finally{close(c);}
    }
    @Test public void burgundyAndDarkSwitchEvenThoughNightModeStaysTheSame() throws Exception {
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().putInt("theme_mode",AppCompatDelegate.MODE_NIGHT_YES).commit();
        ActivityController<MainActivity> c=activity();try{
            assertOnlyChecked(c.get(),R.id.themeDark);int dark=backgroundColor(c.get().findViewById(R.id.root));assertEquals(0xFF140A0A,dark);
            c.get().findViewById(R.id.themeBurgundy).performClick();idleMain();
            assertOnlyChecked(c.get(),R.id.themeBurgundy);assertEquals(0xFF892929,backgroundColor(c.get().findViewById(R.id.root)));
            c.get().findViewById(R.id.themeDark).performClick();idleMain();
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES,savedTheme());assertOnlyChecked(c.get(),R.id.themeDark);
            assertEquals(dark,backgroundColor(c.get().findViewById(R.id.root)));
        }finally{close(c);}
    }
    @Test public void leavingBurgundyRestoresOriginalLightPalette() throws Exception {
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().putInt("theme_mode",MainActivity.THEME_BURGUNDY).commit();
        ActivityController<MainActivity> c=activity();try{
            assertOnlyChecked(c.get(),R.id.themeBurgundy);assertEquals(0xFF892929,backgroundColor(c.get().findViewById(R.id.root)));
            c.get().findViewById(R.id.themeLight).performClick();idleMain();
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO,savedTheme());assertEquals(AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.getDefaultNightMode());
            assertOnlyChecked(c.get(),R.id.themeLight);assertEquals(0xFFFAF5F5,backgroundColor(c.get().findViewById(R.id.root)));
            c.get().findViewById(R.id.themeSystem).performClick();idleMain();
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,savedTheme());assertOnlyChecked(c.get(),R.id.themeSystem);
        }finally{close(c);}
    }
    @Test public void unknownStoredThemeFallsBackToSystem() {
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().putInt("theme_mode",3).commit();
        ActivityController<MainActivity> c=activity();try{assertOnlyChecked(c.get(),R.id.themeSystem);assertEquals(0xFFFAF5F5,backgroundColor(c.get().findViewById(R.id.root)));}finally{close(c);}
    }
    private static double luminance(int color){
        double[] v=new double[3];int[] channels={(color>>16)&255,(color>>8)&255,color&255};
        for(int i=0;i<3;i++){double x=channels[i]/255.0;v[i]=x<=0.03928?x/12.92:Math.pow((x+0.055)/1.055,2.4);}
        return 0.2126*v[0]+0.7152*v[1]+0.0722*v[2];
    }
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
    @Test public void burgundyTextColoursMeetNormalTextContrast() {
        Context c=app();java.util.function.IntUnaryOperator color=id->androidx.core.content.ContextCompat.getColor(c,id);
        int window=color.applyAsInt(R.color.burgundy_window_background),card=color.applyAsInt(R.color.burgundy_surface_card),nested=color.applyAsInt(R.color.burgundy_surface_card_nested),chip=color.applyAsInt(R.color.burgundy_chip_bg),input=color.applyAsInt(R.color.burgundy_input_bg);
        int primary=color.applyAsInt(R.color.burgundy_text_primary),secondary=color.applyAsInt(R.color.burgundy_text_secondary),tertiary=color.applyAsInt(R.color.burgundy_text_tertiary),brand=color.applyAsInt(R.color.burgundy_brand_text),chipText=color.applyAsInt(R.color.burgundy_chip_text),heroSub=color.applyAsInt(R.color.burgundy_hero_text_sub);
        int[][] pairs={{primary,window},{primary,card},{primary,nested},{primary,input},{secondary,window},{secondary,chip},{secondary,nested},{tertiary,card},{tertiary,input},{brand,chip},{brand,nested},{chipText,chip},
            {0xFFFFFFFF,0xFF6B1F1F},{0xFFFFFFFF,0xFF561818},{heroSub,0xFF6B1F1F},{heroSub,0xFF3A0F0F}};
        for(int[] pair:pairs)assertTrue(String.format("Contrast %08X on %08X",pair[0],pair[1]),contrast(pair[0],pair[1])>=4.5);
        for(PatternCategory category:PatternCategory.values())assertTrue(category.name(),contrast(android.graphics.Color.parseColor(category.getDarkTextColor()),android.graphics.Color.parseColor(category.getDarkBgColor()))>=4.5);
    }
    @Test public void burgundyScreensRenderForReview() throws Exception {
        app().getSharedPreferences("pattern_hunter_prefs",0).edit().putInt("theme_mode",MainActivity.THEME_BURGUNDY).commit();
        ActivityController<MainActivity> c=activity();try{
            renderContent(c.get(),"burgundy-empty").recycle();
            showSummary(c.get());renderContent(c.get(),"burgundy-results").recycle();
            RecyclerView list=c.get().findViewById(R.id.recyclerView);@SuppressWarnings("rawtypes") RecyclerView.Adapter adapter=list.getAdapter();
            RecyclerView.ViewHolder holder=adapter.createViewHolder(list,0);adapter.bindViewHolder(holder,0);
            assertEquals(0xFF6B1F1F,solidColor(holder.itemView));render(holder.itemView,"burgundy-card",328,220).recycle();
        }finally{close(c);}
    }
    @Test @Config(qualifiers="ru-rRU-w320dp-h640dp-port-mdpi",fontScale=1.3f) public void russianThemeLabelsStayOnOneLineOnNarrowScreenWithLargeText() throws Exception {
        assertThemeLabelsFit("narrow-ru");
    }
    @Test @Config(qualifiers="en-rUS-w320dp-h640dp-port-mdpi",fontScale=1.3f) public void englishThemeLabelsStayOnOneLineOnNarrowScreenWithLargeText() throws Exception {
        assertThemeLabelsFit("narrow-en");
    }
    private void assertThemeLabelsFit(String name) throws Exception {
        ActivityController<MainActivity> c=activity();try{
            View root=c.get().findViewById(R.id.root);layout(root,320,640);
            for(int option:THEME_OPTIONS){RadioButton button=c.get().findViewById(option);assertEquals(button.getText().toString(),1,button.getLineCount());assertTrue(button.getLayout().getEllipsisCount(0)==0);}
            render(root,"theme-selector-"+name,320,640).recycle();
        }finally{close(c);}
    }
    private static AnalysisSummary sampleSummary(){
        return new AnalysisSummary(7,3,1.45,"Latin Alphabetic (a–z)","Латинские буквы (a–z)",Arrays.asList(
            new PatternItem(PatternCategory.PALINDROME,"Entire string is a perfect palindrome of length 7!","Вся строка является совершенным палиндромом длины 7!","abacaba",1,7,100),
            new PatternItem(PatternCategory.SANDWICH,"Sandwich structure 'aba'","Сэндвич-структура 'aba'","aba",1,3,82)));
    }
    static void showSummary(Activity activity) throws Exception {
        Method method=activity.getClass().getDeclaredMethod("updateUI",AnalysisSummary.class,boolean.class);method.setAccessible(true);method.invoke(activity,sampleSummary(),true);
    }
    @Test public void lightAndDarkKeepOriginalRenderingApartFromAddedThemeRow() throws Exception {
        for(int mode:new int[]{AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.MODE_NIGHT_YES}){
            app().getSharedPreferences("pattern_hunter_prefs",0).edit().putInt("theme_mode",mode).commit();
            ActivityController<LegacyMainActivity> old=Robolectric.buildActivity(LegacyMainActivity.class).setup().visible();
            ActivityController<MainActivity> fixed=activity();
            try{
                String theme=mode==AppCompatDelegate.MODE_NIGHT_YES?"dark":"light";
                compareScreens(old.get(),fixed.get(),"empty-"+theme);
                showSummary(old.get());showSummary(fixed.get());
                compareScreens(old.get(),fixed.get(),"results-"+theme);
                RecyclerView oldList=old.get().findViewById(R.id.recyclerView),newList=fixed.get().findViewById(R.id.recyclerView);
                @SuppressWarnings("rawtypes") RecyclerView.Adapter a=oldList.getAdapter(),b=newList.getAdapter();
                RecyclerView.ViewHolder av=a.createViewHolder(oldList,0),bv=b.createViewHolder(newList,0);a.bindViewHolder(av,0);b.bindViewHolder(bv,0);
                compareViews(av.itemView,bv.itemView,"card-"+theme,328,220);
            }finally{close(fixed);close(old);}
        }
    }
    /** Whole scroll content is compared: identical above the theme selector and, shifted by the added row, below it. */
    private void compareScreens(Activity old,Activity fixed,String name) throws Exception {
        Bitmap a=renderContent(old,name+"-original"),b=renderContent(fixed,name+"-fixed");
        try{
            View oldSelector=old.findViewById(R.id.themeGroup),newSelector=fixed.findViewById(R.id.themeGroup);
            int top=oldSelector.getTop(),oldBottom=oldSelector.getBottom(),newBottom=newSelector.getBottom();
            assertEquals(top,newSelector.getTop());assertTrue(newBottom>oldBottom);
            assertEquals("Content below the selector must only shift by the added row: "+name,a.getHeight()-oldBottom,b.getHeight()-newBottom);
            assertTrue("Original header changed: "+name,region(a,0,top).sameAs(region(b,0,top)));
            assertTrue("Original content below the selector changed: "+name,region(a,oldBottom,a.getHeight()-oldBottom).sameAs(region(b,newBottom,b.getHeight()-newBottom)));
            writeRegions(name,a.getHeight(),b.getHeight(),top,oldBottom,newBottom);
        }finally{a.recycle();b.recycle();}
    }
    /** Records the compared regions next to the screenshots so the verification report can cite them. */
    private static void writeRegions(String name,int originalHeight,int fixedHeight,int top,int oldBottom,int newBottom) throws Exception {
        File folder=new File("build/ui-previews");assertTrue(folder.isDirectory()||folder.mkdirs());
        String json=String.format(java.util.Locale.ROOT,"{\"state\":\"%s\",\"width\":360,\"original_height\":%d,\"fixed_height\":%d,\"selector_top\":%d,\"original_selector_bottom\":%d,\"fixed_selector_bottom\":%d}%n",name,originalHeight,fixedHeight,top,oldBottom,newBottom);
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+"-regions.json"))){out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    private static Bitmap region(Bitmap source,int y,int height){return Bitmap.createBitmap(source,0,y,source.getWidth(),height);}
    static Bitmap renderContent(Activity activity,String name) throws Exception {
        EditText input=activity.findViewById(R.id.input);input.setCursorVisible(false);input.clearFocus();
        View root=activity.findViewById(R.id.root);layout(root,360,800);
        ViewCompat.dispatchApplyWindowInsets(root,new WindowInsetsCompat.Builder().build());
        View content=((android.view.ViewGroup)root).getChildAt(0);
        content.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        return render(root,name,360,content.getMeasuredHeight());
    }
    private void compareViews(View old,View fixed,String name,int width,int height) throws Exception {
        Bitmap a=render(old,name+"-original",width,height),b=render(fixed,name+"-fixed",width,height);
        try{assertTrue("Original layout changed: "+name,a.sameAs(b));}finally{a.recycle();b.recycle();}
    }
    static Bitmap render(View view,String name,int width,int height) throws Exception {
        layout(view,width,height);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.translate(-view.getScrollX(),-view.getScrollY());view.draw(canvas);
        File folder=new File("build/ui-previews");assertTrue(folder.isDirectory()||folder.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}return bitmap;
    }
}
