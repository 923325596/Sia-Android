/*
 * Copyright (c) 2017 Nicholas van Dyke. All rights reserved.
 */

package com.vandyke.sia.data.models.renter

import android.arch.persistence.room.Entity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.vandyke.sia.data.local.models.renter.Node
import java.math.BigDecimal

@Entity(tableName = "files")
data class RenterFileData
@JsonCreator constructor(
        @JsonProperty(value = "siapath")
        var siapath: String, // TODO: change back to val
        @JsonProperty(value = "localpath")
        val localpath: String,
        @JsonProperty(value = "filesize")
        val filesize: BigDecimal, // bytes
        @JsonProperty(value = "available")
        val available: Boolean,
        @JsonProperty(value = "renewing")
        val renewing: Boolean,
        @JsonProperty(value = "redundancy")
        val redundancy: Double,
        @JsonProperty(value = "uploadedbytes")
        val uploadedBytes: Long,
        @JsonProperty(value = "uploadprogress")
        val uploadProgress: Int,
        @JsonProperty(value = "expiration")
        val expiration: Long) : Node(siapath, filesize) // TODO: want to store only siapath or path, not both

typealias SiaFile = RenterFileData