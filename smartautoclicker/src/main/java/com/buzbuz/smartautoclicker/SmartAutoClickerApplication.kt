/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.netease.nimlib.sdk.NIMClient
import com.netease.nimlib.sdk.RequestCallback
import com.netease.nimlib.sdk.SDKOptions
import com.netease.nimlib.sdk.auth.AuthService
import com.netease.nimlib.sdk.auth.LoginInfo
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class SmartAutoClickerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        NIMClient.init(this, loginInfo(), options());
        // 如果提供用户信息，将同时进行自动登录。如果当前还没有登录用户，请传入null。


        val info = LoginInfo("","")
        val callback: RequestCallback<LoginInfo?> = object : RequestCallback<LoginInfo?> {
            override fun onSuccess(param: LoginInfo?) {
              //  LogUtil.i(TAG, "login success")
                // your code
            }

            override fun onFailed(code: Int) {
                if (code == 302) {
                  //  LogUtil.i(TAG, "账号密码错误")
                    // your code
                } else {
                    // your code
                }
            }

            override fun onException(exception: Throwable) {
                // your code
            }
        }
        NIMClient.getService(AuthService::class.java).login(info).setCallback(callback)
    }


    private fun loginInfo(): LoginInfo? {
        return null
    }
    // 设置初始化配置参数，如果返回值为 null，则全部使用默认参数。
    private fun options(): SDKOptions? {
        val options = SDKOptions()
        options.appKey = "3c4f31f7f277ac27ec689b97b304da6d"
        return options
    }

}