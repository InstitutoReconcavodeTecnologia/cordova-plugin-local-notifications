/*
 * Apache 2.0 License
 *
 * Copyright (c) Sebastian Katzer 2017
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 */

// codebeat:disable[TOO_MANY_FUNCTIONS]

package de.appplant.cordova.plugin.localnotification;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;
import android.os.Build;
import android.app.AlarmManager;
import android.content.IntentFilter;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.appplant.cordova.plugin.localnotification.notification.Manager;
import de.appplant.cordova.plugin.localnotification.notification.Notification;
import de.appplant.cordova.plugin.localnotification.notification.Options;
import de.appplant.cordova.plugin.localnotification.notification.Request;
import de.appplant.cordova.plugin.localnotification.notification.action.ActionGroup;
import de.appplant.cordova.plugin.localnotification.notification.util.CallbackContextUtil;
import de.appplant.cordova.plugin.localnotification.AlarmPermissionReceiver;

import static de.appplant.cordova.plugin.localnotification.notification.Notification.Type.SCHEDULED;
import static de.appplant.cordova.plugin.localnotification.notification.Notification.Type.TRIGGERED;

/**
 * This plugin utilizes the Android AlarmManager in combination with local
 * notifications. When a local notification is scheduled the alarm manager takes
 * care of firing the event. When the event is processed, a notification is put
 * in the Android notification center and status bar.
 */
@SuppressWarnings({"Convert2Diamond", "Convert2Lambda"})
public class LocalNotification extends CordovaPlugin {

    public static final String TAG = "LocalNotification";

    // Reference to the web view for static access
    private static WeakReference<CordovaWebView> webView = null;

    // Indicates if the device is ready (to receive events)
    private static Boolean deviceready = false;

    // Queues all events before deviceready
    private static ArrayList<String> eventQueue = new ArrayList<String>();

    // Launch details
    private static Pair<Integer, String> launchDetails;

    private AlarmPermissionReceiver alarmPermissionReceiver = new AlarmPermissionReceiver();

    /**
     * Called after plugin construction and fields have been initialized.
     * Prefer to use pluginInitialize instead since there is no value in
     * having parameters on the initialize() function.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        LocalNotification.webView = new WeakReference<CordovaWebView>(webView);

        this.cordova.getActivity().getApplicationContext().registerReceiver(
            alarmPermissionReceiver,
            new IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        );
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app.
     */
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.d(TAG, "onResume");
        deviceready();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        deviceready = false;
        this.cordova.getActivity().getApplicationContext().unregisterReceiver(alarmPermissionReceiver);
    }

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread. To do a non-trivial
     * amount of work, use:
     *      cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action  The action to execute.
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     *
     * @return Whether the action was valid.
     */
    @Override
    public boolean execute(final String action, final JSONArray args,
                            final CallbackContext command) throws JSONException {

        if (action.equals("launch")) {
            launch(command);
            return true;
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (action.equals("ready")) {
                    deviceready();
                } else                
                if (action.equals("createChannel")) {
                    createChannel(args, command);
                } else
                if (action.equals("check")) {
                    check(command);
                } else
                if (action.equals("request")) {
                    request(command);
                } else
                if (action.equals("actions")) {
                    actions(args, command);
                } else
                if (action.equals("schedule")) {
                    schedule(args, command);
                } else
                if (action.equals("update")) {
                    update(args, command);
                } else
                if (action.equals("cancel")) {
                    cancel(args, command);
                } else
                if (action.equals("cancelAll")) {
                    cancelAll(command);
                } else
                if (action.equals("clear")) {
                    clear(args, command);
                } else
                if (action.equals("clearAll")) {
                    clearAll(command);
                } else
                if (action.equals("type")) {
                    type(args, command);
                } else
                if (action.equals("ids")) {
                    ids(args, command);
                } else
                if (action.equals("notification")) {
                    notification(args, command);
                } else
                if (action.equals("notifications")) {
                    notifications(args, command);
                } else
                if (action.equals("canScheduleExactAlarms")) {
                    canScheduleExactAlarms(command);
                } else
                if (action.equals("openNotificationSettings")) {
                    openNotificationSettings(command);
                } else
                if (action.equals("openAlarmSettings")) {
                    openAlarmSettings(command);
                }
            }
        });

        return true; // Action was found
    }

    /**
     * Set launchDetails object.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    @SuppressLint("DefaultLocale")
    private void launch(CallbackContext command) {
        if (launchDetails == null)
            return;

        JSONObject details = new JSONObject();

        try {
            details.put("id", launchDetails.first);
            details.put("action", launchDetails.second);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        command.success(details);

        launchDetails = null;
    }

    /**
     * Ask if user has enabled permission for local notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void check(CallbackContext command) {
        boolean allowed = getNotMgr().areNotificationsEnabled();
        success(command, allowed);
    }

    /**
     * Ask if if the setting to schedule exact alarms is enabled.
     *
     * @param command The callback context used when calling back into JavaScript.
     */
    private void canScheduleExactAlarms(CallbackContext command) {
        success(command, getNotMgr().canScheduleExactAlarms());
    }

    /**
     * Request permission for local notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void request(CallbackContext command) {
        if (getNotMgr().areNotificationsEnabled()) {
            success(command, true);

            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Notifications are disabled and POST_NOTIFICATIONS runtime permission is not supported.
            success(command, false);

            return;
        }

        // Request the runtime permission.
        int requestId = CallbackContextUtil.storeContext(command);

        cordova.requestPermissions(this, requestId, new String[]{Manifest.permission.POST_NOTIFICATIONS});
    }

    /**
     * Register action group.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void actions(JSONArray args, CallbackContext command) {
        int task        = args.optInt(0);
        String id       = args.optString(1);
        JSONArray list  = args.optJSONArray(2);
        Context context = cordova.getActivity();

        switch (task) {
            case 0:
                ActionGroup group = ActionGroup.parse(context, id, list);
                ActionGroup.register(group);
                command.success();
                break;
            case 1:
                ActionGroup.unregister(id);
                command.success();
                break;
            case 2:
                boolean found = ActionGroup.isRegistered(id);
                success(command, found);
                break;
        }
    }

    /**
     * Schedule multiple local notifications.
     *
     * @param toasts  The notifications to schedule.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void schedule(JSONArray toasts, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < toasts.length(); i++) {
            JSONObject dict    = toasts.optJSONObject(i);
            Options options    = new Options(dict);
            Request request    = new Request(options);
            Notification toast = mgr.schedule(request);

            if (toast != null) {
                fireEvent("add", toast);
            }
        }

        check(command);
    }    
    
    /**
     * Create Notification channel with options.
     *
     * @param args  The channel options.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void createChannel(JSONArray args, CallbackContext command) {
        Manager mgr = getNotMgr();
        JSONObject options = args.optJSONObject(0);
        mgr.createChannel(options);

        command.success();
    }

    /**
     * Update multiple local notifications.
     *
     * @param updates Notification properties including their IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void update(JSONArray updates, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < updates.length(); i++) {
            JSONObject update  = updates.optJSONObject(i);
            int id             = update.optInt("id", 0);
            Notification toast = mgr.update(id, update);

            if (toast == null)
                continue;

            fireEvent("update", toast);
        }

        check(command);
    }

    /**
     * Cancel multiple local notifications.
     *
     * @param ids     Set of local notification IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void cancel(JSONArray ids, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < ids.length(); i++) {
            int id             = ids.optInt(i, 0);
            Notification toast = mgr.cancel(id);

            if (toast == null)
                continue;

            fireEvent("cancel", toast);
        }

        command.success();
    }

    /**
     * Cancel all scheduled notifications.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void cancelAll(CallbackContext command) {
        getNotMgr().cancelAll();
        fireEvent("cancelall");
        command.success();
    }

    /**
     * Clear multiple local notifications without canceling them.
     *
     * @param ids     Set of local notification IDs.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void clear(JSONArray ids, CallbackContext command) {
        Manager mgr = getNotMgr();

        for (int i = 0; i < ids.length(); i++) {
            int id             = ids.optInt(i, 0);
            Notification toast = mgr.clear(id);

            if (toast == null)
                continue;

            fireEvent("clear", toast);
        }

        command.success();
    }

    /**
     * Clear all triggered notifications without canceling them.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void clearAll(CallbackContext command) {
        getNotMgr().clearAll();
        fireEvent("clearall");
        command.success();
    }

    /**
     * Get the type of the notification (unknown, scheduled, triggered).
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void type(JSONArray args, CallbackContext command) {
        int id             = args.optInt(0);
        Notification toast = getNotMgr().get(id);

        if (toast == null) {
            command.success("unknown");
            return;
        }

        switch (toast.getType()) {
            case SCHEDULED:
                command.success("scheduled");
                break;
            case TRIGGERED:
                command.success("triggered");
                break;
            default:
                command.success("unknown");
                break;
        }
    }

    /**
     * Set of IDs from all existent notifications.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void ids(JSONArray args, CallbackContext command) {
        int type    = args.optInt(0);
        Manager mgr = getNotMgr();
        List<Integer> ids;

        switch (type) {
            case 0:
                ids = mgr.getIds();
                break;
            case 1:
                ids = mgr.getIdsByType(SCHEDULED);
                break;
            case 2:
                ids = mgr.getIdsByType(TRIGGERED);
                break;
            default:
                ids = new ArrayList<Integer>(0);
                break;
        }

        command.success(new JSONArray(ids));
    }

    /**
     * Options from local notification.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void notification(JSONArray args, CallbackContext command) {
        int id       = args.optInt(0);
        Options opts = getNotMgr().getOptions(id);

        if (opts != null) {
            command.success(opts.getDict());
        } else {
            command.success();
        }
    }

    /**
     * Set of options from local notification.
     *
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into
     *                JavaScript.
     */
    private void notifications(JSONArray args, CallbackContext command) {
        int type      = args.optInt(0);
        JSONArray ids = args.optJSONArray(1);
        Manager mgr   = getNotMgr();
        List<JSONObject> options;

        switch (type) {
            case 0:
                options = mgr.getOptions();
                break;
            case 1:
                options = mgr.getOptionsByType(SCHEDULED);
                break;
            case 2:
                options = mgr.getOptionsByType(TRIGGERED);
                break;
            case 3:
                options = mgr.getOptionsById(toList(ids));
                break;
            default:
                options = new ArrayList<JSONObject>(0);
                break;
        }

        command.success(new JSONArray(options));
    }
    /**
     * Open the Android Notification settings for current app.
     *
     * @param command The callback context used when calling back into JavaScript.
     */
    private void openNotificationSettings(CallbackContext command) {
        String packageName = cordova.getActivity().getPackageName();
        Intent intent = new Intent();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        } else {
            // In old Android versions it's not possible to view notification settings, open app settings.
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
        }

        cordova.getActivity().startActivity(intent);

        command.success();
    }

    /**
     * Open the Alarms & Reminders setting for the current app.
     * This setting is available since Android 12 (SDK 31). If this method is called on
     * Android 11 (SDK 30) or older, the method will just call command.success().
     * 
     * @param command The callback context used when calling back into JavaScript.
     */
    private void openAlarmSettings(CallbackContext command) {
        // Setting available since Android 12 (SDK 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String packageName = cordova.getActivity().getPackageName();
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName));
            cordova.getActivity().startActivity(intent);
        }

        command.success();
    }

    /**
     * Call all pending callbacks after the deviceready event has been fired.
     */
    private static synchronized void deviceready() {
        deviceready = true;

        for (String js : eventQueue) {
            sendJavascript(js);
        }

        eventQueue.clear();
    }

    /**
     * Invoke success callback with a single boolean argument.
     *
     * @param command The callback context used when calling back into
     *                JavaScript.
     * @param arg     The single argument to pass through.
     */
    private void success(CallbackContext command, boolean arg) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, arg);
        command.sendPluginResult(result);
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event The event name.
     */
    private void fireEvent(String event) {
        fireEvent(event, null, new JSONObject());
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event        The event name.
     * @param notification Optional notification to pass with.
     */
    static void fireEvent(String event, Notification notification) {
        fireEvent(event, notification, new JSONObject());
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event The event name.
     * @param toast Optional notification to pass with.
     * @param data  Event object with additional data.
     */
    static void fireEvent(String event, Notification toast, JSONObject data) {
        String params, js;

        try {
            data.put("event", event);
            data.put("foreground", isInForeground());
            data.put("queued", !deviceready);

            if (toast != null) {
                data.put("notification", toast.getId());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (toast != null) {
            params = toast.toString() + "," + data.toString();
        } else {
            params = data.toString();
        }

        js = "cordova.plugins.notification.local.fireEvent(" +
                "\"" + event + "\"," + params + ")";

        if (launchDetails == null && !deviceready && toast != null) {
            launchDetails = new Pair<Integer, String>(toast.getId(), event);
        }

        sendJavascript(js);
    }

    /**
     * Use this instead of deprecated sendJavascript
     *
     * @param js JS code snippet as string.
     */
    private static synchronized void sendJavascript(final String js) {

        if (!deviceready || webView == null) {
            eventQueue.add(js);
            return;
        }

        final CordovaWebView view = webView.get();

        ((Activity)(view.getContext())).runOnUiThread(new Runnable() {
            public void run() {
                view.loadUrl("javascript:" + js);
            }
        });
    }

    /**
     * If the app is running in foreground.
     */
    private static boolean isInForeground() {

        if (!deviceready || webView == null)
            return false;

        CordovaWebView view = webView.get();

        KeyguardManager km = (KeyguardManager) view.getContext()
                .getSystemService(Context.KEYGUARD_SERVICE);

        //noinspection SimplifiableIfStatement
        if (km != null && km.isKeyguardLocked())
            return false;

        return view.getView().getWindowVisibility() == View.VISIBLE;
    }

    /**
     * If the app is running.
     */
    static boolean isAppRunning() {
        return webView != null;
    }

    /**
     * Convert JSON array of integers to List.
     *
     * @param ary Array of integers.
     */
    private List<Integer> toList(JSONArray ary) {
        List<Integer> list = new ArrayList<Integer>();

        for (int i = 0; i < ary.length(); i++) {
            list.add(ary.optInt(i));
        }

        return list;
    }

    /**
     * Notification manager instance.
     */
    private Manager getNotMgr() {
        return Manager.getInstance(cordova.getActivity());
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
            CallbackContext context = CallbackContextUtil.getContext(requestCode);
            CallbackContextUtil.clearContext(requestCode);

            success(context, grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } catch (Exception e) {
            String error = "Exception occurred onRequestPermissionsResult: ".concat(e.getMessage());
            Log.e(TAG, error);
        }
    }

}

// codebeat:enable[TOO_MANY_FUNCTIONS]
