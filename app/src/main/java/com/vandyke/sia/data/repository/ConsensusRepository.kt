/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.data.repository

import com.vandyke.sia.data.local.AppDatabase
import com.vandyke.sia.data.remote.SiaApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsensusRepository
@Inject constructor(
        private val api: SiaApi,
        private val db: AppDatabase
) {
    fun updateConsensus() = api.consensus().doOnSuccess {
        db.consensusDao().insertReplaceOnConflict(it)
    }.toCompletable()

    fun consensus() = db.consensusDao().mostRecent()
}