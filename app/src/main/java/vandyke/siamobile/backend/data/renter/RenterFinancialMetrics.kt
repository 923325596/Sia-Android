package vandyke.siamobile.backend.data.renter

import java.math.BigDecimal

data class RenterFinancialMetrics(val contractspending: BigDecimal = BigDecimal.ZERO,
                                  val downloadspending: BigDecimal = BigDecimal.ZERO,
                                  val storagespending: BigDecimal = BigDecimal.ZERO,
                                  val uploadspending: BigDecimal = BigDecimal.ZERO,
                                  val unspent: BigDecimal = BigDecimal.ZERO)