package com.buzbuz.smartautoclicker.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.lang.reflect.Field

interface AppLaunchListener {
    fun onAppLaunchSuccess()
    fun onAppLaunchFailed()
}

object AppUtil {

    // Method to open an app by its package name
    fun openAppByPackageName(context: Context, packageName: String, listener: AppLaunchListener) {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            context.startActivity(launchIntent)
            listener.onAppLaunchSuccess()
        } else {
            Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
            listener.onAppLaunchFailed()
        }
    }

    // Method to check if an app is installed
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        val packageManager = context.packageManager
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppOpenIntentByPackageName(context: Context, packageName: String): Intent? {
        //Activity完整名
        var mainAct: String? = null
        //根据包名寻找
        val pkgMag = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = pkgMag.queryIntentActivities(
            intent,
            PackageManager.GET_ACTIVITIES
        )
        for (i in list.indices) {
            val info = list[i]
            if (info.activityInfo.packageName == packageName) {
                mainAct = info.activityInfo.name
                break
            }
        }
        if (TextUtils.isEmpty(mainAct)) {
            return null
        }
        intent.setComponent(ComponentName(packageName, mainAct!!))
        return intent
    }

    fun getPackageContext(context: Context, packageName: String): Context? {
        var pkgContext: Context? = null
        if (context.packageName == packageName) {
            pkgContext = context
        } else {
            // 创建第三方应用的上下文环境
            try {
                pkgContext = context.createPackageContext(
                    packageName, Context.CONTEXT_IGNORE_SECURITY
                            or Context.CONTEXT_INCLUDE_CODE
                )
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        return pkgContext
    }

    fun openPackage(context: Context, packageName: String): Boolean {
        val pkgContext = getPackageContext(context, packageName)
        val intent = getAppOpenIntentByPackageName(context, packageName)
        if (pkgContext != null && intent != null) {
            pkgContext.startActivity(intent)
           // closeKeyboard(pkgContext as Activity)
//            val targetPackageName = "com.openai.chatgpt" // 替换为目标应用的包名
//            val activityNames: Activity ? = getMainActivityInstance(context)
//            Log.d("activityNames","${activityNames.toString()}")


//
//            val packageName = "com.openai.chatgpt"
//            val activityName = "$packageName.MainActivity"
//            val mainActivity = getActivityByClassName(context, activityName)
//
//            Log.d("activityNames：zz=","${mainActivity.toString()}")
//            mainActivity?.let {
//                MainScope().launch {
//                    delay(2000) // 延时2秒
//                    closeKeyboard(it)
//                }
//            }

            return true
        }
        return false
    }


    fun closeKeyboard(activity: Activity) {
        // 获取当前焦点的视图
        val view: View? = activity.getCurrentFocus()
        if (view != null) {
            // 获取InputMethodManager服务
            // 关闭软键盘
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
    }


    private fun getMainActivityInstance(context: Context): Activity? {
        val packageName = "com.openai.chatgpt"
        val packageManager = context.packageManager
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities
            if (activities != null) {
                for (activityInfo in activities) {
                    Log.d("activityName:",activityInfo.name.toString())
                    if (activityInfo.name == "com.openai.chatgpt.MainActivity") {
                        // 使用反射获取 MainActivity 的实例
                        val mainActivityClass = Class.forName(activityInfo.name)
                        val constructor = mainActivityClass.getDeclaredConstructor()
                        constructor.isAccessible = true
                        val instance = constructor.newInstance() as Activity
                        return instance
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("error:",e.toString())
            e.printStackTrace()
        }
        return null
    }



    fun getActivityByClassName(context: Context, className: String): Activity? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 需要反射获取 ActivityThread 和 ActivityThread 的 mActivities 字段
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val activityThread = currentActivityThreadMethod.invoke(null)

            val activitiesField: Field = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activityThreadClass.getDeclaredField("mActivities")
            } else {
                activityThreadClass.getDeclaredField("mActivities")
            }
            activitiesField.isAccessible = true
            val activities = activitiesField.get(activityThread) as Map<*, *>

            for (activityRecord in activities.values) {
                val activityRecordClass = activityRecord?.javaClass
                val activityField = activityRecordClass?.getDeclaredField("activity")
                if (activityField != null) {
                    activityField.isAccessible = true
                }
                val activity = activityField?.get(activityRecord) as Activity
                if (activity.javaClass.name == className) {
                    return activity
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}
