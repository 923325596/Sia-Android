/*
 * Copyright (c) 2017 Nicholas van Dyke
 *
 * This file is subject to the terms and conditions defined in Licensing section of the file 'README.md'
 * included in this source code package. All rights are reserved, with the exception of what is specified there.
 */

package vandyke.siamobile.backend.coldstorage

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import siawallet.Wallet
import vandyke.siamobile.prefs
import vandyke.siamobile.util.GenUtil
import java.io.IOException
import java.util.ArrayList
import kotlin.collections.HashSet

class ColdStorageHttpServer : NanoHTTPD("localhost", 9990) {

    private var seed: String = prefs.coldStorageSeed
    private var addresses: ArrayList<String> = ArrayList(prefs.coldStorageAddresses)
    private var password: String = prefs.coldStoragePassword
    private var exists: Boolean = prefs.coldStorageExists
    private var unlocked: Boolean = false

    private val unlockResponse by lazy { response(JSONObject().put("message","wallet must be unlocked before it can be used"),
            Response.Status.BAD_REQUEST) }
    private val createResponse by lazy { response(JSONObject().put("message", "wallet has not been encrypted yet"), 
            Response.Status.BAD_REQUEST) }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val parms = session.parms
        try {
            session.parseBody(parms)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NanoHTTPD.ResponseException) {
            e.printStackTrace()
        }

        return when (session.uri) {
            "/wallet/address" -> address()
            "/wallet/addresses" -> addresses()
            "/wallet/seeds" -> seeds()
            "/wallet/init" -> init(parms["encryptionpassword"], parms["force"] == "true")
            "/wallet/init/seed" -> initSeed(parms["encryptionpassword"], parms["seed"], parms["force"] == "true")
            "/wallet/unlock" -> unlock(parms["encryptionpassword"])
            "/wallet/lock" -> lock()
            "/wallet" -> wallet()
            "/wallet/transactions" -> transactions()
            "/consensus" -> consensus()
            else -> response(JSONObject().put("message", "unsupported on cold storage wallet"), Response.Status.NOT_IMPLEMENTED)
        }
    }
    
    fun init(password: String?, force: Boolean): Response {
        if (exists && !force)
            return response(JSONObject().put("message", "wallet is already encrypted, cannot encrypt again"), Response.Status.BAD_REQUEST)
        initWallet(password ?: "")
        return response()
    }

    fun initSeed(password: String?, seed: String?, force: Boolean): Response {
        if (exists && !force)
            return response(JSONObject().put("message", "wallet is already encrypted, cannot encrypt again"), Response.Status.BAD_REQUEST)
        initWallet(password ?: "", seed ?: "")
        return response()
    }

    fun wallet(): Response = response(JSONObject().put("encrypted", exists)
            .put("unlocked", unlocked)
            .put("rescanning", false)
            .put("confirmedsiacoinbalance", 0)
            .put("unconfirmedoutgoingsiacoins", 0)
            .put("unconfirmedincomingsiacoins", 0)
            .put("siafundbalance", 0)
            .put("siacoinclaimbalance", 0))
    
    fun seeds(): Response = when {
        !exists -> createResponse
        !unlocked -> unlockResponse
        else -> response(JSONObject().put("allseeds", JSONArray().put(seed)))
    }
    
    fun unlock(password: String?): Response {
        if (!exists)
            return createResponse
        if (password == this.password) {
            unlocked = true
            return response()
        }
        return response(JSONObject().put("message", "provided encryption key is incorrect"), Response.Status.BAD_REQUEST)
    }
    
    fun lock(): Response = when {
        !exists -> createResponse
        else -> response()
    }
    
    fun transactions(): Response {
        return response()
    }

    fun address(): Response = when {
        !exists -> createResponse
        !unlocked -> unlockResponse
        else -> response(JSONObject().put("address", addresses[(Math.random() * addresses.size).toInt()]))
    }

    fun addresses(): Response = when {
        !exists -> createResponse
        !unlocked -> unlockResponse
        else -> {
            val addressArray = JSONArray()
            for (address in addresses)
                addressArray.put(address)
            response(JSONObject().put("addresses", addressArray))
        }
    }
    
    fun consensus(): Response {
        return response(JSONObject().put("synced", true))
    }

    fun response(json: JSONObject = JSONObject(), status: Response.Status = Response.Status.OK): Response {
        val response = NanoHTTPD.newFixedLengthResponse(json.toString())
        response.status = status
        return response
    }

    fun initWallet(password: String, seed: String = "generate new seed") {
        val wallet = Wallet()
        if (seed == "generate new seed")
        try {
            wallet.generateSeed()
            this.seed = wallet.seed
        } catch (e: Exception) {
            e.printStackTrace()
            this.seed = "Failed to generate seed"
        } else {
            this.seed = seed
            wallet.seed = seed
        }

        addresses.clear()
        for (i in 0..19)
            addresses.add(wallet.getAddress(i.toLong()))

        this.password = password
        exists = true
        unlocked = false
        prefs.coldStorageSeed = seed
        prefs.coldStorageAddresses = HashSet(addresses)
        prefs.coldStoragePassword = password
        prefs.coldStorageExists = exists
    }

    companion object {
        fun showColdStorageHelp(context: Context) {
            GenUtil.getDialogBuilder(context)
                    .setTitle("Cold storage help")
                    .setMessage("Sia Mobile's cold storage wallet operates independently of the Sia network." +
                            " Since it doesn't have a copy of the Sia blockchain and is not connected to the " +
                            "Sia network, it cannot perform certain functions that require this. It also cannot display your correct balance and transactions." +
                            "\n\nIf you wish to use unsupported functions, or view your cold wallet balance and transactions, you will have to run a full" +
                            " Sia node (either in Sia Mobile or using something like Sia-UI on your computer), and then load your" +
                            " wallet seed on that full node. Your coins are not \"lost\" - if you did everything properly, they will be there when you load your seed" +
                            " on a full node at any time in the future. No need to worry.")
                    .setPositiveButton("OK", null)
                    .show()
        }
    }
}
