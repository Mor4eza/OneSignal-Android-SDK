/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.onesignal.OneSignal.Log;

class OSUtils {

   static final int UNINITIALIZABLE_STATUS = -999;

   public enum SchemaType {
      DATA("data"),
      HTTPS("https"),
      HTTP("http"),
      ;

      private final String text;

      SchemaType(final String text) {
         this.text = text;
      }

      public static SchemaType fromString(String text) {
         for (SchemaType type : SchemaType.values()) {
            if (type.text.equalsIgnoreCase(text)) {
               return type;
            }
         }
         return null;
      }
   }

   int initializationChecker(Context context, int deviceType, String oneSignalAppId) {
      int subscribableStatus = 1;

      try {
         //noinspection ResultOfMethodCallIgnored
         UUID.fromString(oneSignalAppId);
      } catch (Throwable t) {
         Log(OneSignal.LOG_LEVEL.FATAL, "OneSignal AppId format is invalid.\nExample: 'b2f7f966-d8cc-11e4-bed1-df8f05be55ba'\n", t);
         return UNINITIALIZABLE_STATUS;
      }

      if ("b2f7f966-d8cc-11e4-bed1-df8f05be55ba".equals(oneSignalAppId) ||
          "5eb5a37e-b458-11e3-ac11-000c2940e62c".equals(oneSignalAppId))
         Log(OneSignal.LOG_LEVEL.ERROR, "OneSignal Example AppID detected, please update to your app's id found on OneSignal.com");

      if (deviceType == UserState.DEVICE_TYPE_ANDROID) {
         Integer pushErrorType = checkForGooglePushLibrary();
         if (pushErrorType != null)
            subscribableStatus = pushErrorType;
      }

      Integer supportErrorType = checkAndroidSupportLibrary(context);
      if (supportErrorType != null)
         subscribableStatus = supportErrorType;

      return subscribableStatus;
   }

   static boolean hasFCMLibrary() {
      try {
         // Using class instead of Strings for proguard compatibility
         // noinspection ConstantConditions
         return com.google.firebase.messaging.FirebaseMessaging.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   private static boolean hasGCMLibrary() {
      try {
         // noinspection ConstantConditions
         return com.google.android.gms.gcm.GoogleCloudMessaging.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   Integer checkForGooglePushLibrary() {
      boolean hasFCMLibrary = hasFCMLibrary();
      boolean hasGCMLibrary = hasGCMLibrary();

      if (!hasFCMLibrary && !hasGCMLibrary) {
         Log(OneSignal.LOG_LEVEL.FATAL, "The Firebase FCM library is missing! Please make sure to include it in your project.");
         return UserState.PUSH_STATUS_MISSING_FIREBASE_FCM_LIBRARY;
      }

      if (hasGCMLibrary && !hasFCMLibrary)
         Log(OneSignal.LOG_LEVEL.WARN, "GCM Library detected, please upgrade to Firebase FCM library as GCM is deprecated!");

      if (hasGCMLibrary && hasFCMLibrary)
         Log(OneSignal.LOG_LEVEL.WARN, "Both GCM & FCM Libraries detected! Please remove the deprecated GCM library.");

      return null;
   }

   private static boolean hasWakefulBroadcastReceiver() {
      try {
         // noinspection ConstantConditions
         return android.support.v4.content.WakefulBroadcastReceiver.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   private static boolean hasNotificationManagerCompat() {
      try {
         // noinspection ConstantConditions
         return android.support.v4.app.NotificationManagerCompat.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   private static boolean hasJobIntentService() {
      try {
         // noinspection ConstantConditions
         return android.support.v4.app.JobIntentService.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   private Integer checkAndroidSupportLibrary(Context context) {
      boolean hasWakefulBroadcastReceiver = hasWakefulBroadcastReceiver();
      boolean hasNotificationManagerCompat = hasNotificationManagerCompat();

      if (!hasWakefulBroadcastReceiver && !hasNotificationManagerCompat) {
         Log(OneSignal.LOG_LEVEL.FATAL, "Could not find the Android Support Library. Please make sure it has been correctly added to your project.");
         return UserState.PUSH_STATUS_MISSING_ANDROID_SUPPORT_LIBRARY;
      }

      if (!hasWakefulBroadcastReceiver || !hasNotificationManagerCompat) {
         Log(OneSignal.LOG_LEVEL.FATAL, "The included Android Support Library is to old or incomplete. Please update to the 26.0.0 revision or newer.");
         return UserState.PUSH_STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY;
      }

      // If running on Android O and targeting O we need version 26.0.0 for
      //   the new compat NotificationCompat.Builder constructor.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
         && getTargetSdkVersion(context) >= Build.VERSION_CODES.O) {
         // Class was added in 26.0.0-beta2
         if (!hasJobIntentService()) {
            Log(OneSignal.LOG_LEVEL.FATAL, "The included Android Support Library is to old or incomplete. Please update to the 26.0.0 revision or newer.");
            return UserState.PUSH_STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY;
         }
      }

      return null;
   }

   int getDeviceType() {
      try {
         // Class only available on the FireOS and only when the following is in the AndroidManifest.xml.
         // <amazon:enable-feature android:name="com.amazon.device.messaging" android:required="false"/>
         Class.forName("com.amazon.device.messaging.ADM");
         return UserState.DEVICE_TYPE_FIREOS;
      } catch (ClassNotFoundException e) {
         return UserState.DEVICE_TYPE_ANDROID;
      }
   }

   Integer getNetType () {
      ConnectivityManager cm = (ConnectivityManager) OneSignal.appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = cm.getActiveNetworkInfo();

      if (netInfo != null) {
         int networkType = netInfo.getType();
         if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET)
            return 0;
         return 1;
      }

      return null;
   }

   String getCarrierName() {
      try {
         TelephonyManager manager = (TelephonyManager) OneSignal.appContext.getSystemService(Context.TELEPHONY_SERVICE);
         // May throw even though it's not in noted in the Android docs.
         // Issue #427
         String carrierName = manager.getNetworkOperatorName();
         return "".equals(carrierName) ? null : carrierName;
      } catch(Throwable t) {
         t.printStackTrace();
         return null;
      }
   }

   static String getManifestMeta(Context context, String metaName) {
      try {
         ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
         Bundle bundle = ai.metaData;
         return bundle.getString(metaName);
      } catch (Throwable t) {
         Log(OneSignal.LOG_LEVEL.ERROR, "", t);
      }

      return null;
   }

   static String getResourceString(Context context, String key, String defaultStr) {
      Resources resources = context.getResources();
      int bodyResId = resources.getIdentifier(key, "string", context.getPackageName());
      if (bodyResId != 0)
         return resources.getString(bodyResId);
      return defaultStr;
   }

   static String getCorrectedLanguage() {
      String lang = Locale.getDefault().getLanguage();

      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/64
      if (lang.equals("iw"))
         return "he";
      if (lang.equals("in"))
         return "id";
      if (lang.equals("ji"))
         return "yi";

      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/98
      if (lang.equals("zh"))
         return lang + "-" + Locale.getDefault().getCountry();

      return lang;
   }

   static boolean isValidEmail(String email) {
      if (email == null)
         return false;

      String emRegex = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
      Pattern pattern = Pattern.compile(emRegex);
      return pattern.matcher(email).matches();
   }

   // Get the app's permission which will be false if the user disabled notifications for the app
   //   from Settings > Apps or by long pressing the notifications and selecting block.
   //   - Detection works on Android 4.4+, requires Android Support v4 Library 24.0.0+
   static boolean areNotificationsEnabled(Context  context) {
      try {
         return NotificationManagerCompat.from(OneSignal.appContext).areNotificationsEnabled();
      } catch (Throwable t) {}

      return true;
   }

   static void runOnMainUIThread(Runnable runnable) {
      if (Looper.getMainLooper().getThread() == Thread.currentThread())
         runnable.run();
      else {
         Handler handler = new Handler(Looper.getMainLooper());
         handler.post(runnable);
      }
   }

   static void runOnMainThreadDelayed(Runnable runnable, int delay) {
      Handler handler = new Handler(Looper.getMainLooper());
      handler.postDelayed(runnable, delay);
   }

   static int getTargetSdkVersion(Context context) {
      String packageName = context.getPackageName();
      PackageManager packageManager = context.getPackageManager();
      try {
         ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
         return applicationInfo.targetSdkVersion;
      } catch (PackageManager.NameNotFoundException e) {
         e.printStackTrace();
      }

      return Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
   }

   static boolean isValidResourceName(String name) {
      return (name != null && !name.matches("^[0-9]"));
   }

   static Uri getSoundUri(Context context, String sound) {
      Resources resources = context.getResources();
      String packageName = context.getPackageName();
      int soundId;

      if (isValidResourceName(sound)) {
         soundId = resources.getIdentifier(sound, "raw", packageName);
         if (soundId != 0)
            return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);
      }

      soundId = resources.getIdentifier("onesignal_default_sound", "raw", packageName);
      if (soundId != 0)
         return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);

      return null;
   }

   static long[] parseVibrationPattern(JSONObject gcmBundle) {
      try {
         Object patternObj = gcmBundle.opt("vib_pt");
         JSONArray jsonVibArray;
         if (patternObj instanceof String)
            jsonVibArray = new JSONArray((String)patternObj);
         else
            jsonVibArray = (JSONArray)patternObj;

         long[] longArray = new long[jsonVibArray.length()];
         for (int i = 0; i < jsonVibArray.length(); i++)
            longArray[i] = jsonVibArray.optLong(i);

         return longArray;
      } catch (JSONException e) {}

      return null;
   }

   static String hexDigest(String str, String digestInstance) throws Throwable {
      MessageDigest digest = java.security.MessageDigest.getInstance(digestInstance);
      digest.update(str.getBytes("UTF-8"));
      byte messageDigest[] = digest.digest();

      StringBuilder hexString = new StringBuilder();
      for (byte aMessageDigest : messageDigest) {
         String h = Integer.toHexString(0xFF & aMessageDigest);
         while (h.length() < 2)
            h = "0" + h;
         hexString.append(h);
      }
      return hexString.toString();
   }

   static void sleep(int ms) {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }

   static void openURLInBrowser(@NonNull String url) {
      openURLInBrowser(Uri.parse(url.trim()));
   }

   private static void openURLInBrowser(@NonNull Uri uri) {
      SchemaType type = uri.getScheme() != null ? SchemaType.fromString(uri.getScheme()) : null;
      if (type == null) {
          type = SchemaType.HTTP;
          if (!uri.toString().contains("://")) {
            uri = Uri.parse("http://" + uri.toString());
         }
      }
      Intent intent;
      switch (type) {
         case DATA:
            intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            intent.setData(uri);
            break;
         case HTTPS:
         case HTTP:
         default:
            intent = new Intent(Intent.ACTION_VIEW, uri);
            break;
      }
      intent.addFlags(
              Intent.FLAG_ACTIVITY_NO_HISTORY |
                      Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET |
                      Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                      Intent.FLAG_ACTIVITY_NEW_TASK);
      OneSignal.appContext.startActivity(intent);
   }

   // Creates a new Set<T> that supports reads and writes from more than one thread at a time
   static <T> Set<T> newConcurrentSet() {
      return Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
   }

   static boolean hasConfigChangeFlag(Activity activity, int configChangeFlag) {
      boolean hasFlag = false;
      try {
         int configChanges = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).configChanges;
         int flagInt = configChanges & configChangeFlag;
         hasFlag = flagInt != 0;
      } catch (PackageManager.NameNotFoundException e) {
         e.printStackTrace();
      }
      return hasFlag;
   }

   static @NonNull Collection<String> extractStringsFromCollection(@Nullable Collection<Object> collection) {
      Collection<String> result = new ArrayList<>();
      if (collection == null)
         return result;

      for (Object value : collection) {
         if (value instanceof String)
            result.add((String) value);
      }
      return result;
   }

   static boolean shouldLogMissingAppIdError(@Nullable String appId) {
      if (appId != null)
         return false;

      // Wrapper SDKs can't normally call on Application.onCreate so just count this as informational.
      Log(OneSignal.LOG_LEVEL.INFO, "OneSignal was not initialized, " +
         "ensure to always initialize OneSignal from the onCreate of your Application class.");
      return true;
   }
}