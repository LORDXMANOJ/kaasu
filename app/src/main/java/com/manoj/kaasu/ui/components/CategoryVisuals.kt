package com.manoj.kaasu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manoj.kaasu.ui.theme.categoryColor

object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "breakfast" to Icons.Rounded.FreeBreakfast,
        "fuel" to Icons.Rounded.LocalGasStation,
        "snacks" to Icons.Rounded.Cookie,
        "coffee" to Icons.Rounded.Coffee,
        "lunch" to Icons.Rounded.LunchDining,
        "dinner" to Icons.Rounded.DinnerDining,
        "fastfood" to Icons.Rounded.Fastfood,
        "icecream" to Icons.Rounded.Icecream,
        "bus" to Icons.Rounded.DirectionsBus,
        "train" to Icons.Rounded.Train,
        "taxi" to Icons.Rounded.LocalTaxi,
        "bike" to Icons.Rounded.TwoWheeler,
        "flight" to Icons.Rounded.Flight,
        "cart" to Icons.Rounded.ShoppingCart,
        "bag" to Icons.Rounded.ShoppingBag,
        "bolt" to Icons.Rounded.Bolt,
        "phone" to Icons.Rounded.PhoneAndroid,
        "water" to Icons.Rounded.WaterDrop,
        "home" to Icons.Rounded.Home,
        "health" to Icons.Rounded.LocalHospital,
        "medication" to Icons.Rounded.Medication,
        "gym" to Icons.Rounded.FitnessCenter,
        "salon" to Icons.Rounded.ContentCut,
        "laundry" to Icons.Rounded.LocalLaundryService,
        "movie" to Icons.Rounded.Movie,
        "sports" to Icons.Rounded.SportsSoccer,
        "book" to Icons.Rounded.Book,
        "school" to Icons.Rounded.School,
        "work" to Icons.Rounded.Work,
        "gift" to Icons.Rounded.CardGiftcard,
        "pets" to Icons.Rounded.Pets,
        "handshake" to Icons.Rounded.Handshake,
        "more" to Icons.Rounded.MoreHoriz,
        "repay" to Icons.Rounded.Replay,
        "income" to Icons.Rounded.Savings,
        "untagged" to Icons.Rounded.QuestionMark,
    )

    /** Icons offered when creating a category (system ones excluded). */
    val pickable: List<String> = all.keys.filter { it !in setOf("repay", "income", "untagged") }

    fun of(key: String?): ImageVector = all[key] ?: Icons.Rounded.MoreHoriz
}

@Composable
fun CategoryIcon(
    icon: String?,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = categoryColor(colorIndex),
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryIcons.of(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
