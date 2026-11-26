package net.kdt.pojavlaunch.tasks;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;
import static net.kdt.pojavlaunch.utils.DownloadUtils.downloadString;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/** Class getting the version list, and that's all really */
public class AsyncVersionList {

    public void getVersionList(@Nullable VersionDoneListener listener, boolean secondPass){
        sExecutorService.execute(() -> {
            File versionFile = new File(Tools.DIR_DATA + "/version_list.json");
            JMinecraftVersionList versionList = null;
            try{
                if(!versionFile.exists() || (System.currentTimeMillis() > versionFile.lastModified() + 86400000 )){
                    versionList = downloadVersionList(LauncherPreferences.PREF_VERSION_REPOS);
                }
            }catch (Exception e){
                Log.e("AsyncVersionList", "Refreshing version list failed :" + e);
                e.printStackTrace();
            }

            // Fallback when no network or not needed
            if (versionList == null) {
                try {
                    versionList = Tools.GLOBAL_GSON.fromJson(new JsonReader(new FileReader(versionFile)), JMinecraftVersionList.class);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (JsonIOException | JsonSyntaxException e) {
                    e.printStackTrace();
                    versionFile.delete();
                    if(!secondPass)
                        getVersionList(listener, true);
                }
            }

            // Pojav1.8.9: only show Minecraft 1.8.9 in the version list
            versionList = keepOnly189(versionList);

            if(listener != null)
                listener.onVersionDone(versionList);
        });
    }


    private static final String ONLY_VERSION = "1.8.9";

    /** Drop every version except 1.8.9, and point "latest" at it so the default profile works */
    private static JMinecraftVersionList keepOnly189(JMinecraftVersionList list) {
        if (list == null || list.versions == null) return list;
        java.util.List<JMinecraftVersionList.Version> kept = new java.util.ArrayList<>();
        for (JMinecraftVersionList.Version v : list.versions) {
            if (ONLY_VERSION.equals(v.id)) kept.add(v);
        }
        list.versions = kept.toArray(new JMinecraftVersionList.Version[0]);
        java.util.Map<String, String> latest = new java.util.HashMap<>();
        latest.put("release", ONLY_VERSION);
        latest.put("snapshot", ONLY_VERSION);
        list.latest = latest;
        return list;
    }

    @SuppressWarnings("SameParameterValue")
    private JMinecraftVersionList downloadVersionList(String mirror){
        JMinecraftVersionList list = null;
        try{
            Log.i("ExtVL", "Syncing to external: " + mirror);
            String jsonString = downloadString(mirror);
            list = Tools.GLOBAL_GSON.fromJson(jsonString, JMinecraftVersionList.class);
            Log.i("ExtVL","Downloaded the version list, len=" + list.versions.length);

            // Then save the version list
            //TODO make it not save at times ?
            FileOutputStream fos = new FileOutputStream(Tools.DIR_DATA + "/version_list.json");
            fos.write(jsonString.getBytes());
            fos.close();



        }catch (IOException e){
            Log.e("AsyncVersionList", e.toString());
        }
        return list;
    }

    /** Basic listener, acting as a callback */
    public interface VersionDoneListener{
        void onVersionDone(JMinecraftVersionList versions);
    }

}
