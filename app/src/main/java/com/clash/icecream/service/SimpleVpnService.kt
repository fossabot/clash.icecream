package com.clash.icecream.service

import android.content.Context
import com.clash.icecream.R
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.support.annotation.RequiresApi
import android.util.Log
import tun2socks.Tun2socks
import clash.Clash
import java.io.FileInputStream
import java.io.FileOutputStream
import tun2socks.PacketFlow
import java.io.File
import java.nio.ByteBuffer
import kotlin.concurrent.thread

open class SimpleVpnService : VpnService() {
    private var pfd: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var buffer = ByteBuffer.allocate(1501)
    @Volatile
    private var running = false
    private lateinit var bgThread: Thread

    class Flow(stream: FileOutputStream?) : PacketFlow {
        private val flowOutputStream = stream
        override fun writePacket(pkt: ByteArray?) {
            flowOutputStream?.write(pkt)
        }
    }

    private fun handlePackets() {
        while (running) {
            try {
                val n = inputStream?.read(buffer.array())
                n?.let { it } ?: return
                if (n > 0) {
                    buffer.limit(n)
                    Tun2socks.inputPacket(buffer.array())
                    buffer.clear()
                }
            } catch (e: Exception) {
                println("failed to read bytes from TUN fd")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        bgThread = thread(start = true) {
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.233.233.233", 30)
                .addDnsServer("114.114.114.114")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication("com.clash.icecream")

            pfd = builder.establish()
            Tun2socks.setNonblock(pfd!!.fd.toLong(), false)

            inputStream = FileInputStream(pfd!!.fileDescriptor)
            outputStream = FileOutputStream(pfd!!.fileDescriptor)

            val geoipBytes0 = resources.openRawResource(R.raw.config).readBytes()
            val fos0 = openFileOutput("config.yaml", Context.MODE_PRIVATE)
            fos0.write(geoipBytes0)
            fos0.close()

            val geoipBytes = resources.openRawResource(R.raw.country).readBytes()
            val fos = openFileOutput("Country.mmdb", Context.MODE_PRIVATE)
            fos.write(geoipBytes)
            fos.close()

            Clash.start("$filesDir")

            val flow = Flow(outputStream)
            Tun2socks.start(flow, "127.0.0.1:1080", "192.168.2.2", "192.168.2.254")
            running = true
            Log.d("ttt", "ttt")
            handlePackets()
            Log.d("ttt", "ttt")
        }


        return START_NOT_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()

        // stop
        pfd?.close()
        pfd = null
        inputStream = null
        outputStream = null
        running = false
        Tun2socks.stop()
        Clash.stop()
        stopSelf()
    }
}