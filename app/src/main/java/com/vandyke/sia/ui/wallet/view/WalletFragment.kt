/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.ui.wallet.view

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import com.vandyke.sia.R
import com.vandyke.sia.data.local.Prefs
import com.vandyke.sia.data.remote.WalletLocked
import com.vandyke.sia.data.siad.SiadStatus
import com.vandyke.sia.getAppComponent
import com.vandyke.sia.ui.common.BaseFragment
import com.vandyke.sia.ui.wallet.view.childfragments.*
import com.vandyke.sia.ui.wallet.view.transactionslist.TransactionAdapter
import com.vandyke.sia.ui.wallet.viewmodel.WalletViewModel
import com.vandyke.sia.util.*
import com.vandyke.sia.util.rx.observe
import io.github.tonnyl.light.Light
import kotlinx.android.synthetic.main.fragment_wallet.*
import net.cachapa.expandablelayout.ExpandableLayout
import java.math.BigDecimal
import java.text.NumberFormat
import javax.inject.Inject


class WalletFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_wallet
    override val hasOptionsMenu = true
    override val title: String = "Wallet"

    @Inject
    lateinit var siadStatus: SiadStatus
    @Inject
    lateinit var factory: ViewModelProvider.Factory
    private lateinit var vm: WalletViewModel

    private val adapter = TransactionAdapter()
    private var childFragment: BaseWalletFragment? = null
    private var fragmentToBeExpanded: BaseWalletFragment? = null
    private var statusButton: MenuItem? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context!!.getAppComponent().inject(this)

        vm = ViewModelProviders.of(this, factory).get(WalletViewModel::class.java)

        /* set up recyclerview for transactions */
        transactionList.addItemDecoration(DividerItemDecoration(transactionList.context,
                (transactionList.layoutManager as LinearLayoutManager).orientation))
        transactionList.adapter = adapter

        /* set up click listeners for the big buttons */
        fabWalletMenu.setOnMenuButtonClickListener {
            if (!fabWalletMenu.isOpened) {
                when {
                    childFragment != null -> if (childFragment?.onCheckPressed() == false) {
                        collapseFrame()
                    }
                    vm.wallet.value?.encrypted == false -> expandFrame(WalletCreateFragment())
                    vm.wallet.value?.unlocked == false -> expandFrame(WalletUnlockFragment())
                    else -> fabWalletMenu.open(true)
                }
            } else {
                fabWalletMenu.close(true)
            }
        }
        fabSend.setOnClickListener {
            fabWalletMenu.close(true)
            expandFrame(WalletSendFragment())
        }
        fabReceive.setOnClickListener {
            fabWalletMenu.close(true)
            expandFrame(WalletReceiveFragment())
        }
        balanceText.setOnClickListener { v ->
            AlertDialog.Builder(v.context)
                    .setTitle("Exact Balance")
                    .setMessage("${vm.wallet.value?.confirmedsiacoinbalance?.toSC()?.toPlainString() ?: 0} Siacoins")
                    .setPositiveButton("Close", null)
                    .show()
        }

        /* set swipe-down stuff */
        transactionListSwipe.setOnRefreshListener(vm::refreshAll)
        transactionListSwipe.setColors(context!!)
        progress_bar.setIndeterminateColorRes(android.R.color.white)

        expandableFrame.setOnExpansionUpdateListener { expansionFraction, state ->
            progress_bar.setIndeterminateColorRes(when (state) {
                ExpandableLayout.State.COLLAPSED -> android.R.color.white
                else -> R.color.colorPrimary
            })

            when (state) {
                ExpandableLayout.State.COLLAPSED -> {
                    childFragment = null
                    if (fragmentToBeExpanded != null) {
                        replaceExpandedFragment(fragmentToBeExpanded!!)
                        expandableFrame.expand(true)
                    } else {
                        childFragmentManager.findFragmentById(R.id.expandableFrame)?.let {
                            childFragmentManager.beginTransaction().remove(it).commit()
                        }
                    }
                }
                ExpandableLayout.State.COLLAPSING -> {
                    childFragment = null
                    KeyboardUtil.hideKeyboard(activity!!)
                    setFabIcon()
                }
                ExpandableLayout.State.EXPANDED -> setFabIcon()
            }
        }

        // setupChart() TODO: confirm/deny that this is working right and how I want it to

        /* observe VM stuff */
        vm.refreshing.observe(this, transactionListSwipe::setRefreshing)

        vm.activeTasks.observe(this) {
            // TODO: when being made visible, the bar flickers at the location it was at last, before restarting
            // Tried a few potential solutions, none worked
            progress_bar.goneUnless(it > 0)
            view.findViewById<ProgressBar>(R.id.progress_bar).goneUnless(it > 0)
        }

        /* observe data in the viewModel */
        vm.wallet.observe(this) {
            balanceText.text = it.confirmedsiacoinbalance.toSC().format()
            if (it.unconfirmedsiacoinbalance != BigDecimal.ZERO) {
                balanceUnconfirmedText.text =
                        "${if (it.unconfirmedsiacoinbalance > BigDecimal.ZERO) "+" else ""}" +
                                "${it.unconfirmedsiacoinbalance.toSC().format()} unconfirmed"
                balanceUnconfirmedText.visibility = View.VISIBLE
            } else {
                balanceUnconfirmedText.visibility = View.INVISIBLE
            }
            setFabIcon()
            setStatusIcon()
            updateFiatValue()
        }

        vm.scValue.observe(this) {
            updateFiatValue()
        }

        vm.transactions.observe(this) {
            adapter.submitList(it)
            if (it.isNotEmpty())
                Prefs.displayedTransaction = true
        }

        vm.consensus.observe(this) {
            setSyncStatus()
        }

        vm.numPeers.observe(this) {
            setSyncStatus()
        }

        vm.success.observe(this) {
            Light.success(wallet_coordinator, it, Snackbar.LENGTH_SHORT).show()
            collapseFrame()
        }

        vm.error.observe(this) {
            it.snackbar(wallet_coordinator)
            if (it is WalletLocked)
                expandFrame(WalletUnlockFragment())
        }

        vm.seed.observe(this) {
            WalletCreateFragment.showSeed(it, context!!)
        }

        siadStatus.state.observe(this) {
            if (it == SiadStatus.State.SIAD_LOADED)
                vm.refreshAll()
        }


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionStatus -> {
                when (vm.wallet.value?.encrypted ?: false || vm.wallet.value?.rescanning ?: false) {
                    false -> expandFrame(WalletCreateFragment())
                    true -> if (!vm.wallet.value!!.unlocked) expandFrame(WalletUnlockFragment())
                    else vm.lock()
                }
            }
            R.id.actionUnlock -> expandFrame(WalletUnlockFragment())
            R.id.actionLock -> vm.lock()
            R.id.actionChangePassword -> expandFrame(WalletChangePasswordFragment())
            R.id.actionViewSeeds -> expandFrame(WalletSeedsFragment())
            R.id.actionCreateWallet -> expandFrame(WalletCreateFragment())
            R.id.actionSweepSeed -> expandFrame(WalletSweepSeedFragment())
            R.id.actionViewAddresses -> expandFrame(WalletAddressesFragment())
            else -> return false
        }
        return true
    }

    private fun expandFrame(fragment: BaseWalletFragment) {
        if (expandableFrame.isExpanded) {
            fragmentToBeExpanded = fragment
            expandableFrame.collapse(true)
        } else {
            replaceExpandedFragment(fragment)
            expandableFrame.expand(true)
        }
    }

    private fun collapseFrame() {
        expandableFrame.collapse(true)
    }

    private fun replaceExpandedFragment(fragment: BaseWalletFragment) {
        childFragmentManager.beginTransaction().replace(R.id.expandableFrame, fragment).commit()
        fragmentToBeExpanded = null
        childFragment = fragment
    }

    override fun onShow() {
        actionBar.elevation = 0f
    }

    override fun onHide() {
        actionBar.elevation = 12f
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.toolbar_wallet, menu)
        statusButton = menu.findItem(R.id.actionStatus)
        setStatusIcon()
    }

    private fun updateFiatValue() {
        if (vm.wallet.value != null && vm.scValue.value != null)
            balanceUsdText.text = ("${(vm.wallet.value!!.confirmedsiacoinbalance.toSC()
                    * vm.scValue.value!![Prefs.fiatCurrency])
                    .format()} ${Prefs.fiatCurrency}")
    }

    private fun setStatusIcon() {
        statusButton?.setIcon(
                when (vm.wallet.value?.encrypted == true || vm.wallet.value?.rescanning == true) {
                    false -> R.drawable.ic_add_white
                    true -> {
                        if (!vm.wallet.value!!.unlocked)
                            R.drawable.ic_lock_outline_white
                        else
                            R.drawable.ic_lock_open_white
                    }
                })
    }

    private fun setSyncStatus() {
        val consensus = vm.consensus.value
        val height = NumberFormat.getInstance().format(consensus?.height ?: 0)
        if (vm.numPeers.value == 0) {
            syncText.text = ("Not syncing: $height (${consensus?.syncProgress?.toInt() ?: 0}%)")
        } else {
            if (consensus?.synced == true) {
                syncText.text = ("${getString(R.string.synced)}: $height")
            } else {
                syncText.text = ("${getString(R.string.syncing)}: $height (${consensus?.syncProgress?.toInt()
                        ?: 0}%)")
            }
        }
    }

    private fun setFabIcon() {
        val wallet = vm.wallet.value
        fabWalletMenu.menuIconView.setImageResource(when {
            childFragment != null -> R.drawable.ic_check_white
            wallet?.unlocked == false && wallet.encrypted == true -> R.drawable.ic_lock_open_white
            else -> R.drawable.ic_add_white
        })
    }

    override fun onBackPressed(): Boolean {
        return when {
            expandableFrame.state != ExpandableLayout.State.COLLAPSED -> {
                collapseFrame()
                true
            }
            fabWalletMenu.isOpened -> {
                fabWalletMenu.close(true)
                true
            }
            else -> false
        }
    }

    private fun setupChart() {
//        /* set up the chart and its data set */
//        val lineDataSet = LineDataSet(null, "")
//        lineDataSet.apply {
//            setDrawCircles(false)
//            setDrawValues(false)
//            setDrawFilled(true)
//            isHighlightEnabled = false
//            color = Color.TRANSPARENT
//            fillColor = context!!.getColorRes(R.color.colorPrimaryDark)
//            /* causes a crash if the dataset is empty, so we add an empty one. Bug with the lib it seems, based off googling */
//            addEntry(Entry(0f, 0f))
//        }
//        siaChart.apply {
//            setViewPortOffsets(0f, 0f, 0f, 0f)
//            data = LineData(lineDataSet)
//            isDragEnabled = false
//            setScaleEnabled(false)
//            legend.isEnabled = false
//            description.isEnabled = false
//            setDrawGridBackground(false)
//            xAxis.isEnabled = false
//            axisLeft.isEnabled = false
//            axisRight.isEnabled = false
//            invalidate()
//        }
//
//        viewModel.walletMonthHistory.observe(this) {
//            // TODO: still not completely sure this is working as I want it to... seems to be quirky
//            lineDataSet.values = it.map { walletData ->
//                Entry(walletData.timestamp.toFloat(), walletData.confirmedsiacoinbalance.toSC().toFloat())
//            }
//            /* causes a crash if the dataset is empty, so we add an empty one. Bug with the lib it seems, based off googling */
//            if (lineDataSet.values.isEmpty())
//                lineDataSet.addEntry(Entry(0f, 0f))
//            lineDataSet.notifyDataSetChanged()
//            siaChart.data.notifyDataChanged()
//            siaChart.notifyDataSetChanged()
//            siaChart.invalidate()
//        }
    }
}
