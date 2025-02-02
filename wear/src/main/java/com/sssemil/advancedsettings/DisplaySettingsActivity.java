/*
 * Copyright (c) 2015 Emil Suleymanov <suleymanovemil8@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.sssemil.advancedsettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.sssemil.advancedsettings.util.DeviceCfg;
import com.sssemil.advancedsettings.util.Utils;
import com.sssemil.advancedsettings.util.preference.Preference;
import com.sssemil.advancedsettings.util.preference.PreferenceScreen;
import com.sssemil.advancedsettings.util.preference.WearPreferenceActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class DisplaySettingsActivity extends WearPreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private DeviceCfg mCfg;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCfg = Utils.getDeviceCfg(this);

        mContext = this;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        final View prefsRoot = inflater.inflate(R.layout.activity_display_settings, null);

        if (!(prefsRoot instanceof PreferenceScreen)) {
            throw new IllegalArgumentException("Preferences resource must use" +
                    " preference.PreferenceScreen as its root element");
        }

        final List<Preference> loadedPreferences = new ArrayList<>();
        for (int i = 0; i < ((PreferenceScreen) prefsRoot).getChildCount(); i++) {
            if ((parsePreference(((PreferenceScreen) prefsRoot).getChildAt(i)).getKey())
                    .equals("screen_saver_brightness_settings")) {
                if (sharedPreferences.getBoolean(
                        "manage_screen_saver_brightness_settings", false)) {
                    loadedPreferences.add(parsePreference(((PreferenceScreen)
                            prefsRoot).getChildAt(i)));
                }
            } else if ((parsePreference(((PreferenceScreen) prefsRoot).getChildAt(i)).getKey())
                    .equals("screen_saver_timeout_settings")) {
                if (Utils.isPackageInstalled("sssemil.com.screensavertimeoutplugin", this, 1)) {
                    loadedPreferences.add(parsePreference(((PreferenceScreen) prefsRoot).getChildAt(i)));
                }
            } else {
                loadedPreferences.add(parsePreference(((PreferenceScreen)
                        prefsRoot).getChildAt(i)));
            }
        }
        addPreferences(loadedPreferences);

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        sharedPreferences.edit().putString("screen_timeout_settings",
                String.valueOf(Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT, 0))).apply();

        sharedPreferences.edit().putString("brightness_settings",
                String.valueOf(Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 0))).apply();

        sharedPreferences.edit().putBoolean("touch_to_wake_screen",
                isTouchToWakeEnabled()).apply();

        /*try {
            Context myContext
                    = this.createPackageContext("com.google.android.wearable.app",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Log.i("tilt_to_wake", String.valueOf(myContext.getApplicationInfo()));
            Log.i("tilt_to_wake", String.valueOf(Utils.tiltToWakeEnabled(myContext)));
        } catch (PackageManager.NameNotFoundException e) {
            if(BuildConfig.DEBUG) {
                  Log.d(TAG, "catch " + e.toString() + " hit in run", e);
                                       }
        }*/
    }

    public boolean isTouchToWakeEnabled() {
        try {
            File idc = new File(mCfg.getTouchIdcPath());
            if (idc.exists() && idc.canRead()) {
                Scanner scanner = new Scanner(idc);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("touch.wake = 1")) {
                        return true;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setTouchToWake(final boolean enable) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String touch_idc_path = mCfg.getTouchIdcPath();
                try {
                    if (isTouchToWakeEnabled() != enable) {
                        Runtime.getRuntime().exec("su -c mount -o remount,rw /system").waitFor();
                        Runtime.getRuntime().exec("su -c cp " + touch_idc_path
                                + " /sdcard/backup.idc").waitFor();

                        File idc = new File("/sdcard/backup.idc");
                        if (idc.exists()) {
                            PrintWriter writer = new PrintWriter("/sdcard/tmp.idc", "UTF-8");
                            Scanner scanner = new Scanner(idc);
                            while (scanner.hasNextLine()) {
                                String line = scanner.nextLine();
                                if (line.contains("touch.wake =")) {
                                    if (enable) {
                                        writer.println("touch.wake = 1");
                                    } else {
                                        writer.println("touch.wake = 0");
                                    }
                                } else {
                                    writer.println(line);
                                }
                            }
                            writer.close();
                            Runtime.getRuntime().exec("su -c rm -rf " + touch_idc_path).waitFor();
                            Runtime.getRuntime().exec("su -c cp /sdcard/tmp.idc " + touch_idc_path).waitFor();
                            Runtime.getRuntime().exec("su -c chmod 644 " + touch_idc_path).waitFor();
                            Runtime.getRuntime().exec("su -c sync").waitFor();
                            if (!(new File(mCfg.getTouchIdcPath()).exists())) {
                                Runtime.getRuntime().exec("su -c cp /sdcard/backup.idc " + touch_idc_path).waitFor();
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, R.string.will_reboot_now, Toast.LENGTH_LONG).show();
                                }
                            });
                            Thread.sleep(3000);
                            Runtime.getRuntime().exec("su -c reboot").waitFor();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        Runtime.getRuntime().exec("su -c rm -rf " + touch_idc_path).waitFor();
                        Runtime.getRuntime().exec("su -c cp /sdcard/backup.idc " + touch_idc_path).waitFor();
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("screen_timeout_settings")
                && (Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0)
                != Integer.parseInt(sharedPreferences.getString(key, null)))) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                    Integer.parseInt(sharedPreferences.getString(key, null)));
        } else if (key.equals("brightness_settings")
                && (Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 0)
                != Integer.parseInt(sharedPreferences.getString(key, null)))) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Integer.parseInt(sharedPreferences.getString(key, null)));
        } else if (key.equals("touch_to_wake_screen")) {
            setTouchToWake(sharedPreferences.getBoolean("touch_to_wake_screen", true));
        } else if (key.equals("manage_screen_saver_brightness_settings")) {
            final View prefsRoot = inflater.inflate(R.layout.activity_display_settings, null);

            if (!(prefsRoot instanceof PreferenceScreen)) {
                throw new IllegalArgumentException("Preferences resource must use" +
                        " preference.PreferenceScreen as its root element");
            }

            final List<Preference> loadedPreferences = new ArrayList<>();
            for (int i = 0; i < ((PreferenceScreen) prefsRoot).getChildCount(); i++) {
                if ((parsePreference(((PreferenceScreen) prefsRoot).getChildAt(i)).getKey())
                        .equals("screen_saver_brightness_settings")) {
                    if (sharedPreferences.getBoolean(key, false)) {
                        loadedPreferences.add(parsePreference(((PreferenceScreen)
                                prefsRoot).getChildAt(i)));
                    }
                } else {
                    loadedPreferences.add(parsePreference(((PreferenceScreen)
                            prefsRoot).getChildAt(i)));
                }
            }
            addPreferences(loadedPreferences);
        }
    }
}
