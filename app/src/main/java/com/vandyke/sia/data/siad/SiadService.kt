/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.data.siad

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import com.vandyke.sia.*
import com.vandyke.sia.data.local.Prefs
import com.vandyke.sia.ui.main.MainActivity
import com.vandyke.sia.util.NotificationUtil
import com.vandyke.sia.util.StorageUtil
import io.reactivex.Single
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class SiadService : Service() {

    private var siadFile: File? = null
    private var siadProcess: java.lang.Process? = null
    lateinit var wakeLock: PowerManager.WakeLock
    private val SIAD_NOTIFICATION = 3
    val siadProcessIsRunning: Boolean
        get() = siadProcess != null
    private val receiver = SiadReceiver()

    override fun onCreate() {
        val filter = IntentFilter(SiadReceiver.START_SIAD)
        filter.addAction(SiadReceiver.STOP_SIAD)
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(receiver, filter)
        // TODO: need some way to do this such that if I push an update with a new version of siad, that it will overwrite the
        // current one. Maybe just keep the version in sharedprefs and check against it?
        siadFile = StorageUtil.copyFromAssetsToAppStorage("siad", this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sia node")

        startForeground(SIAD_NOTIFICATION, siadNotification("Not running"))

        isSiadServiceStarted.onNext(true)

        if (Prefs.startSiaAutomatically) {
            Prefs.siaStoppedManually = false
            startSiad()
        } else {
            Prefs.siaStoppedManually = true
            isSiadProcessStarting.onNext(false)
        }
    }

    fun startSiad() {
        Prefs.siaStoppedManually = false
        if (siadProcessIsRunning) {
            return
        }
        if (siadFile == null) {
            showSiadNotification("Couldn't start Siad")
            return
        }
        isSiadLoaded.onNext(false)
        isSiadProcessStarting.onNext(true)

        /* acquire partial wake lock to keep device CPU awake and therefore keep the Sia node active */
        if (Prefs.SiaNodeWakeLock && !wakeLock.isHeld)
            wakeLock.acquire()

        val pb = ProcessBuilder(siadFile!!.absolutePath, "-M", "gctw") // TODO: maybe let user set which modules to load?
        pb.redirectErrorStream(true)

        /* start the node with an api password if it's not set to something empty */
        if (Prefs.apiPassword.isNotEmpty()) {
            val args = pb.command()
            args.add("--authenticate-api")
            pb.environment().put("SIA_API_PASSWORD", Prefs.apiPassword)
            pb.command(args)
        }

        /* determine what directory Sia should use. Display notification with errors if external storage is set and not working */
        if (Prefs.useExternal) {
            if (StorageUtil.isExternalStorageWritable) {
                val dir = getExternalFilesDir(null)
                if (dir != null) {
                    pb.directory(dir)
                } else {
                    showSiadNotification("Error getting external storage")
                    return
                }
            } else {
                showSiadNotification(StorageUtil.externalStorageStateDescription())
                return
            }
        } else {
            pb.directory(filesDir)
        }

        try {
            siadProcess = pb.start() // TODO: this causes the application to skip about a second of frames when starting at the same time as the app. Preventable?

            showSiadNotification("Starting Sia node...")

            /* launch a coroutine that will read output from the siad process, and update siad observables from it's output */
            launch(CommonPool) {
                /* need another try-catch block since this is inside a coroutine */
                try {
                    val inputReader = BufferedReader(InputStreamReader(siadProcess!!.inputStream))
                    var line: String? = inputReader.readLine()
                    while (line != null) {
                        siadOutput.onNext(line)
                        if (line.contains("Finished loading"))
                            isSiadLoaded.onNext(true)
                        /* sometimes the phone runs a portscan, and siad receives an HTTP request from it, and outputs a weird
                         * error message thingy. It doesn't affect operation at all, and we don't want the user to see it since
                         * it'd just be confusing */
                        if (!line.contains("Unsolicited response received on idle HTTP channel starting with"))
                            showSiadNotification(line)
                        line = inputReader.readLine()
                    }
                    inputReader.close()
                    siadOutput.onComplete()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                isSiadProcessStarting.onNext(false)
            }
        } catch (e: IOException) {
            showSiadNotification(e.localizedMessage ?: "Error starting Sia node")
        }
    }

    fun stopSiad() {
        if (wakeLock.isHeld)
            wakeLock.release()
        // TODO: maybe shut it down using stop http request instead? Takes ages sometimes
        siadProcess?.destroy()
        siadProcess = null
        isSiadLoaded.onNext(false)
        showSiadNotification("Stopped")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        stopSiad()
        if (wakeLock.isHeld)
            wakeLock.release()
        /* need to clear the notification, otherwise the
         * "Stopped" notification that stopSiad() displays will persist */
        NotificationUtil.cancelNotification(this, SIAD_NOTIFICATION)
        isSiadServiceStarted.onNext(false)
    }

    fun showSiadNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SIAD_NOTIFICATION, siadNotification(text))
    }

    private fun siadNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, NotificationUtil.SIA_NODE_CHANNEL)
                .setSmallIcon(R.drawable.siacoin_logo_svg_white)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.sia_logo_transparent))
                .setContentTitle("Sia node")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))

        /* the intent that launches MainActivity when the notification is selected */
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPI = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(contentPI)

        /* the action to stop/start the Sia node */
        if (siadProcessIsRunning) {
            val stopIntent = Intent(SiadReceiver.STOP_SIAD)
            stopIntent.setClass(this, SiadReceiver::class.java)
            val stopPI = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(R.drawable.siacoin_logo_svg_white, "Stop", stopPI)
        } else {
            val startIntent = Intent(SiadReceiver.START_SIAD)
            startIntent.setClass(this, SiadReceiver::class.java)
            val startPI = PendingIntent.getBroadcast(this, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(R.drawable.siacoin_logo_svg_white, "Start", startPI)
        }

        return builder.build()
    }

    companion object {
        // TODO: maybe emit an error from this if the service isn't already running, and don't attempt to bind?
        // If I don't want to start it as a result of binding, that is. Not sure if that's what I'll want
        /** Note that the service is returned and then immediately unbound. So if the service is started because
          * of this binding, then it will also immediately stop */
        fun getService(context: Context) = Single.create<SiadService> {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    it.onSuccess((service as SiadService.LocalBinder).service)
                    context.unbindService(this)
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            }
            context.bindService(Intent(context, SiadService::class.java), connection, Context.BIND_AUTO_CREATE)
        }!!
    }

    /* binding stuff */
    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        val service: SiadService
            get() = this@SiadService
    }
}
