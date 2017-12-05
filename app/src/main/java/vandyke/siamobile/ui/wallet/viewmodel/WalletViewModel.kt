/*
 * Copyright (c) 2017 Nicholas van Dyke
 *
 * This file is subject to the terms and conditions defined in 'LICENSE.md'
 */

package vandyke.siamobile.ui.wallet.viewmodel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import vandyke.siamobile.backend.data.consensus.ConsensusData
import vandyke.siamobile.backend.data.wallet.ScPriceData
import vandyke.siamobile.backend.data.wallet.TransactionsData
import vandyke.siamobile.backend.data.wallet.WalletData
import vandyke.siamobile.backend.networking.SiaError
import vandyke.siamobile.backend.networking.siaApi
import vandyke.siamobile.backend.networking.sub
import vandyke.siamobile.ui.wallet.model.IWalletModel
import vandyke.siamobile.ui.wallet.model.WalletModelHttp
import vandyke.siamobile.util.toSC

class WalletViewModel : ViewModel() {
    val wallet = MutableLiveData<WalletData>()
    val usd = MutableLiveData<ScPriceData>()
    val consensus = MutableLiveData<ConsensusData>()
    val transactions = MutableLiveData<TransactionsData>()
    val activeTasks = MutableLiveData<Int>()
    val success = MutableLiveData<String>()
    val error = MutableLiveData<SiaError>()

    /* for communicating the seed to the view when a new wallet is created */
    val seed = MutableLiveData<String>()

    var model: IWalletModel = WalletModelHttp()

    init {
        activeTasks.value = 0
    }

    /* success and error are immediately set back to null because the view only reacts on
       non-null values of them, and if they're holding a non-null value and the
       view is recreated, then it'll display the success/error even though it shouldn't.
       There might be a better way around that */
    private fun setSuccess(msg: String) {
        success.value = msg
        success.value = null
        decrementTasks()
    }

    private val onError: (SiaError) -> Unit = {
        error.value = it
        error.value = null
        decrementTasks()
    }

    private fun incrementTasks() {
        activeTasks.value = activeTasks.value!! + 1
    }

    private fun decrementTasks() {
        if (activeTasks.value!! > 0)
            activeTasks.value = activeTasks.value!! - 1
    }

    fun refresh() {
        refreshWallet()
        refreshTransactions()
        refreshConsensus()
    }

    fun refreshWallet() {
        incrementTasks()
        model.getWallet().sub({
            wallet.value = it
            decrementTasks()
        }, onError)
        /* we don't track retrieving the usd price as a task since it tends to take a long time and is less reliable */
        siaApi.getScPrice().sub({
            usd.value = it
        }, onError)
    }

    fun refreshTransactions() {
        incrementTasks()
        model.getTransactions().sub({
            transactions.value = it
            decrementTasks()
        }, onError)
    }

    fun refreshConsensus() {
        incrementTasks()
        model.getConsensus().sub({
            consensus.value = it
            decrementTasks()
        }, onError)
    }

    fun unlock(password: String) {
        incrementTasks()
        model.unlock(password).sub({
            setSuccess("Unlocked") // TODO: maybe have cool animations eventually, to indicate locking/unlocking/creating?
            refreshWallet()
        }, {
            if (it.reason == SiaError.Reason.WALLET_SCAN_IN_PROGRESS)
                setSuccess("Blockchain scan in progress, please wait...")
            else
                onError(it)
        })
    }

    fun lock() {
        incrementTasks()
        model.lock().sub({
            setSuccess("Locked")
            refreshWallet()
        }, onError)
    }

    fun create(password: String, force: Boolean, seed: String? = null) {
        incrementTasks()
        if (seed == null) {
            model.init(password, "english", force).sub({ it ->
                setSuccess("Created wallet")
                refreshWallet()
                this.seed.value = it.primaryseed
            }, onError)
        } else {
            model.initSeed(password, "english", seed, force).sub({
                setSuccess("Created wallet")
                refreshWallet()
                this.seed.value = seed
            }, onError)
        }
    }

    fun send(amount: String, destination: String) {
        incrementTasks()
        model.send(amount, destination)
                .sub({
                    setSuccess("Sent ${amount.toSC()} SC to $destination")
                    refreshWallet()
                }, onError)
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        incrementTasks()
        model.changePassword(currentPassword, newPassword).sub({
            setSuccess("Changed password")
        }, onError)
    }

    fun sweep(seed: String) {
        incrementTasks()
        model.sweep("english", seed).sub({
            setSuccess("Scanning blockchain, please wait...")
        }, onError)
    }
}