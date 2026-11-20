package de.dritteliga.stickertracker.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.RangeFormatter
import de.dritteliga.stickertracker.domain.TradeCodec
import de.dritteliga.stickertracker.domain.TradeList
import de.dritteliga.stickertracker.domain.TradeMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TradeState(
    val list: TradeList = TradeList(emptyList(), emptyMap()),
    val missingText: String = "",
    val duplicatesText: String = "",
    val shareText: String = "",
    val qrPayload: String = "",
    val order: List<String> = emptyList(),
)

@HiltViewModel
class TradeViewModel @Inject constructor(private val repository: StickerRepository) : ViewModel() {

    val state: StateFlow<TradeState> = repository.stickers.map { stickers ->
        val list = TradeList.from(stickers)
        TradeState(
            list = list,
            missingText = RangeFormatter.format(list.missing),
            duplicatesText = RangeFormatter.formatWithCounts(list.duplicates.toList()),
            shareText = TradeCodec.shareText(list),
            qrPayload = TradeCodec.encode(list),
            order = stickers.sortedBy { it.position }.map { it.number },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TradeState())

    private val partner = MutableStateFlow<TradeList?>(null)

    /** Live-Abgleich: aktualisiert sich, wenn sich der eigene Bestand ändert. */
    val match: StateFlow<TradeMatch?> = combine(state, partner) { s, p ->
        p?.let { TradeCodec.match(s.list, it, s.order) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }

    fun onQrScanned(payload: String) {
        runCatching { TradeCodec.decode(payload) }
            .onSuccess { partner.value = it }
            .onFailure { _message.value = it.message ?: "QR-Code nicht lesbar." }
    }

    fun clearPartner() { partner.value = null }

    /** Bucht die 1:1-Paare: eigene Doppelte −1, erhaltene Sticker +1. */
    fun applyPairs(pairs: List<Pair<String, String>>) {
        viewModelScope.launch {
            pairs.forEach { (give, get) ->
                repository.changeCount(give, -1)
                repository.changeCount(get, +1)
            }
            _message.value = "${pairs.size} Tauschpaare gebucht."
            partner.value = null
        }
    }
}
