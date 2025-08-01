/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.discussion.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.Reaction
import io.olvid.messenger.designsystem.components.AnimatedEmoji
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.contacts.ContactListItem

data class ReactionItem(val emoji: String = "", val isMine: Boolean = false, val count: Int = 0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Reactions(modifier: Modifier = Modifier, message: Message, useAnimatedEmojis : Boolean = true, loopAnimatedEmojis : Boolean = true) {

    message.reactions?.takeIf { it.isNotEmpty() }?.let { reactions ->

        val reactionItems = remember(reactions) {
            emptyList<ReactionItem>().toMutableList().apply {
                reactions.split(":").filter { it.isNotEmpty() }.takeIf { it.size % 2 == 0 }
                    ?.let { splitReactions ->
                        for (i in splitReactions.indices step 2) {
                            add(
                                ReactionItem(
                                    splitReactions[i].removePrefix("|"),
                                    splitReactions[i].startsWith("|"),
                                    splitReactions[i + 1].toInt()
                                )
                            )
                        }
                    }
            }
                .apply { if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE) reverse() }
        }

        var openReactionPopup by remember {
            mutableStateOf(false)
        }
        Box(
            modifier = modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(
                        placeable.width,
                        placeable.height - 4.dp.roundToPx()
                    ) { placeable.place(0, -4.dp.roundToPx()) }
                }
        ) {
            if (openReactionPopup) {
                val reactionsDetail by AppDatabase.getInstance().reactionDao()
                    .getAllNonNullForMessageSortedByTimestampLiveData(message.id)
                    .observeAsState()
                reactionsDetail?.let {
                    if (it.isEmpty()) {
                        openReactionPopup = false
                    }
                    ModalBottomSheet(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        containerColor = colorResource(id = R.color.dialogBackground),
                        onDismissRequest = { openReactionPopup = false }) {
                        ReactionsDetail(it, useAnimatedEmojis = useAnimatedEmojis, loopAnimatedEmojis = loopAnimatedEmojis)
                    }
                }
            }
            FlowRow(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        color = colorResource(id = R.color.lighterGrey),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = colorResource(id = R.color.almostWhite),
                        shape = CircleShape
                    )
                    .padding(1.dp)
                    .wrapContentWidth(if (message.isInbound) Alignment.Start else Alignment.End)
                    .clickable { openReactionPopup = true },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                reactionItems.forEach {
                    Row(
                        modifier = if (it.isMine) Modifier
                            .background(
                                color = colorResource(id = R.color.blueOverlay),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = colorResource(id = R.color.olvid_gradient_light),
                                shape = CircleShape
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                        else Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (useAnimatedEmojis) {
                            AnimatedEmoji(
                                modifier = Modifier.widthIn(max = 40.dp),
                                shortEmoji = it.emoji,
                                loop = loopAnimatedEmojis,
                                onClick = { openReactionPopup = true },
                                size = 14f
                            )
                        } else {
                            Text(
                                modifier = Modifier.widthIn(max = 40.dp),
                                text = it.emoji,
                                maxLines = 1,
                                fontSize = 14.sp,
                                color = colorResource(
                                    id = R.color.greyTint
                                )
                            )
                        }
                        if (it.count > 1) {
                            Text(
                                text = it.count.toString(),
                                fontSize = 10.sp,
                                color = colorResource(
                                    id = R.color.almostBlack
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionsDetail(reactions: List<Reaction>, useAnimatedEmojis: Boolean = true, loopAnimatedEmojis: Boolean = true) {
    val reactionTabs = remember(reactions) {
        reactions.groupBy(keySelector = { it.emoji }).map {
            ReactionItem(
                emoji = it.key ?: "",
                count = it.value.size
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        var selectedTab: String? by remember {
            mutableStateOf(null)
        }
        LaunchedEffect(reactions, selectedTab) {
            if (selectedTab != null && reactions.none { it.emoji == selectedTab }) {
                selectedTab = null
            }
        }
        val context = LocalContext.current
        LazyColumn(modifier = Modifier.height((24 + 48 * reactions.size).coerceAtMost(300).dp)) {
            items(items = reactions.filter { if (selectedTab != null) it.emoji == selectedTab else true }, key = {it -> BytesKey(it.bytesIdentity) }) { reaction ->
                val contactName = remember(reaction.bytesIdentity) {
                    ContactCacheSingleton.getContactDetailsSingleLine(
                        reaction.bytesIdentity
                            ?: AppSingleton.getBytesCurrentIdentity()
                    )
                }

                ContactListItem(
                    modifier = Modifier.animateItem(),
                    padding = PaddingValues(horizontal = 8.dp),
                    title = AnnotatedString(contactName ?: stringResource(R.string.text_deleted_contact)),
                    body = AnnotatedString(
                        StringUtils.getNiceDateString(
                            context,
                            reaction.timestamp
                        ).toString()
                    ),
                    endContent = reaction.emoji?.let {
                        {
                            if (useAnimatedEmojis) {
                                AnimatedEmoji(shortEmoji = it,
                                    loop = loopAnimatedEmojis,
                                    size = 32f)
                            } else {
                                Text(text = it, fontSize = 32.sp)
                            }
                        }
                    },
                    onClick = {
                        // only make the item clickable if a contact actually exists
                        if (contactName != null) {
                            reaction.bytesIdentity?.let { contactBytes ->
                                AppSingleton.getBytesCurrentIdentity()?.let { ownBytes ->
                                    App.openContactDetailsActivity(context, ownBytes, contactBytes)
                                }
                            }
                        }
                    },
                    initialViewSetup = {
                        it.setFromCache(
                            reaction.bytesIdentity
                                ?: AppSingleton.getBytesCurrentIdentity()
                                ?: byteArrayOf()
                        )
                    }
                )
            }
        }
        LazyRow(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(reactionTabs) { reactionTab ->
                Row(
                    modifier = Modifier
                        .background(
                            color = colorResource(
                                id =
                                    if (selectedTab == reactionTab.emoji)
                                        R.color.greyOverlay
                                    else
                                        R.color.dialogBackground
                            ),
                            shape = RoundedCornerShape(40.dp)
                        )
                        .clip(RoundedCornerShape(40.dp))
                        .clickable {
                            selectedTab =
                                reactionTab.emoji.takeUnless { selectedTab == reactionTab.emoji }
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (useAnimatedEmojis) {
                        AnimatedEmoji(shortEmoji = reactionTab.emoji,
                            loop = loopAnimatedEmojis,
                            size = 24f,
                            ignoreClicks = true)
                    } else {
                        Text(
                            text = reactionTab.emoji,
                            style = OlvidTypography.h2
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reactionTab.count.toString(),
                        style = OlvidTypography.h2,
                        color = colorResource(
                            id = R.color.primary700
                        )
                    )
                }
            }
        }
    }
}