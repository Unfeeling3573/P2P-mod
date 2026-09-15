/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.network.connectionmanager.notices

import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.listStateOf
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.state.Sale
import gg.essential.notices.NoticeType
import gg.essential.notices.model.Notice
import java.time.Instant
import kotlin.Int
import kotlin.Number
import kotlin.String

class SaleNoticeManager(noticesManager: NoticesManager) {
    private val saleSuppressedByJvmFlag = System.getProperty("essential.disableSale", "false") == "true"

    private val notices: ListState<Notice> =
        if (saleSuppressedByJvmFlag) listStateOf()
        else noticesManager.activeNotices.filter { it.type == NoticeType.SALE }

    val saleState: ListState<Sale> =
        notices.mapEach { notice ->
            val discount = (notice.metadata["discount"] as Number).toInt()
            var packagesSet: MutableSet<Int>? = null
            if (notice.metadata.containsKey("packages")) {
                packagesSet = mutableSetOf()
                for (packages in (notice.metadata["packages"] as Collection<Number>)) {
                    packagesSet.add(packages.toInt())
                }
                if (packagesSet.isEmpty()) {
                    packagesSet = null
                }
            }
            var onlyCosmetics: Set<String>? = null
            if (notice.metadata.containsKey("cosmetics")) {
                onlyCosmetics = (notice.metadata["cosmetics"] as Collection<String>).toSet()
            }
            Sale(
                notice.expiresAt?.toInstant() ?: Instant.MAX,
                notice.metadata["sale_name"] as String,
                if (notice.metadata.containsKey("sale_name_compact")) (notice.metadata["sale_name_compact"] as String) else (if (discount == 0) null else "SALE"),
                discount,
                notice.metadata["display_time"] as Boolean? ?: true,
                notice.metadata["category"] as String?,
                packagesSet,
                onlyCosmetics,
                notice.metadata["tooltip"] as String?,
                notice.metadata["coupon"] as String?,
            )
        }
}
