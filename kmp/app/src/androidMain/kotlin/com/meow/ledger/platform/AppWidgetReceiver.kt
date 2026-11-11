package com.meow.ledger.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 桌面小组件的更新入口。占位布局由 appwidget_info.xml 提供，数据在后续切片填充。 */
class AppWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 用户通过配置页添加小组件后，由后续切片绑定账本数据。
    }
}
