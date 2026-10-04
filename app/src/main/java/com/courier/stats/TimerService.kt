package com.courier.stats

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class TimerService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("shift", "Смена", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val start = Store.active(this).takeIf { it > 0 } ?: System.currentTimeMillis()
        val n = Notification.Builder(this, "shift")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Смена идёт")
            .setContentText("Время на смене")
            .setUsesChronometer(true).setShowWhen(true).setWhen(start)
            .setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        return START_STICKY
    }
}
