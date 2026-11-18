package com.example.booking_app.feature.search.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.databinding.DialogCourtSearchPriceBinding
import com.example.booking_app.databinding.DialogCourtSearchWindowBinding
import com.example.booking_app.databinding.BottomSheetCourtSearchFilterBinding
import com.example.booking_app.databinding.FragmentCourtSearchBinding
import com.example.booking_app.databinding.ItemSearchRecommendationBinding
import com.example.booking_app.databinding.BottomSheetSearchSourcesBinding
import com.example.booking_app.databinding.DialogSearchExplanationBinding
import com.example.booking_app.databinding.ItemSearchClaimBinding
import com.example.booking_app.databinding.ItemSearchSourceBinding
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import com.example.booking_app.databinding.ViewCourtSearchControlsBinding
import com.example.booking_app.databinding.ItemSearchHistoryBinding
import com.example.booking_app.databinding.SheetSearchHistoryBinding
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.Relaxation
import com.example.booking_app.feature.search.domain.RelaxationKind
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.ReviewPreference
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.domain.map.CourtMap
import com.example.booking_app.feature.search.domain.map.MapViewport
import com.example.booking_app.feature.search.domain.map.VisibleVenues
import com.example.booking_app.feature.search.ui.map.CourtMapController
import com.example.booking_app.feature.search.ui.map.MapCardAdapter
import com.example.booking_app.feature.search.ui.map.NearbyAdapter
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Số chip "Tìm gần đây" trên màn tìm; "Xem tất cả" mở đủ 10 mục. */
private const val RECENT_CHIPS = 5

/** Mức phóng khi mở bản đồ từ "Sân gần bạn": thấy khoảng bán kính 5 km; "Tìm rộng hơn" thấy vài quận (spec 021). */
private const val NEARBY_ZOOM = 13.5
private const val WIDER_ZOOM = 12.0
private const val LIST_PANE_PERCENT = 0.4f

/** Thông báo của micro: hiện dưới ô AI thay cho dòng thông báo chung. */
private val VOICE_NOTICES = setOf(SearchNotice.VOICE_NO_MATCH, SearchNotice.VOICE_NETWORK)

@AndroidEntryPoint
class CourtSearchFragment : Fragment(R.layout.fragment_court_search) {
    private val viewModel: CourtSearchViewModel by viewModels()
    private var aiExpanded = false
    @Inject lateinit var avatarSource: AccountAvatarSource
    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator
    @Inject lateinit var authNavigator: AuthNavigator

    private var locationReason = LocationReason.SORT
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.locationResult(locationReason, grants.values.any { it })
    }

    /** Bản đồ tạo lần đầu khi chuyển sang chế độ bản đồ, gắn với view hiện tại (spec 021). */
    private var mapController: CourtMapController? = null
    private var mapCards: MapCardAdapter? = null
    private var venueCards: VenueCardAdapter? = null
    private var listSelection: String? = null

    /** Máy tính bảng: danh sách và bản đồ cạnh nhau (spec 021 FR-023). */
    private val twoPane: Boolean get() = resources.getBoolean(R.bool.search_two_pane)

    /** Điện thoại xoay ngang: ẩn tiêu đề khi xem bản đồ, thẻ ở góc trái nên nút vị trí không cần nhích. */
    private val compactLandscape: Boolean get() = resources.getBoolean(R.bool.map_compact_landscape)
    private var lastFocusId: Int? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.voicePermission(granted)
        if (!granted) showVoiceDenied()
    }

    override fun onResume() {
        super.onResume()
        viewModel.voicePermissionChecked(hasMicPermission())
    }

    override fun onDestroyView() {
        // Hiệu ứng micro gắn với view cũ; view mới bắt đầu lại từ trạng thái nghỉ.
        micPulse?.cancel()
        micPulse = null
        micListening = false
        aiFieldIdle = null
        mapController = null
        mapCards = null
        venueCards = null
        listSelection = null
        super.onDestroyView()
    }

    override fun onStop() {
        // Không nghe khi màn hình không còn hiển thị (spec 022 T083).
        viewModel.stopVoice()
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourtSearchBinding.bind(view)
        val manager = LinearLayoutManager(requireContext())
        binding.results.layoutManager = manager
        val controls = ViewCourtSearchControlsBinding.inflate(layoutInflater, binding.results, false)
        binding.header.tabTitle.setText(R.string.nav_tab_search)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)

        val venues = VenueCardAdapter(::onVenueCard).also { venueCards = it }
        venues.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.results.adapter = ConcatAdapter(ControlsAdapter(controls.root), venues)
        binding.results.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && manager.findLastVisibleItemPosition() >= venues.itemCount - 4) viewModel.loadMore()
            }
        })

        val initial = viewModel.state.value
        controls.keyword.setText(initial.criteria.keyword)
        controls.aiInput.setText(initial.aiInput)
        controls.keyword.doAfterTextChanged { viewModel.keyword(it.toString()) }
        controls.aiInput.doAfterTextChanged { viewModel.input(it.toString()) }
        controls.filtersKeptClear.setOnClickListener { viewModel.clearFilters() }
        controls.keyword.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard(controls.keyword)
            viewModel.submitKeyword()
            true
        }
        controls.aiInput.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEND || !controls.aiSubmit.isEnabled) return@setOnEditorActionListener false
            hideKeyboard(controls.aiInput)
            viewModel.submitAi()
            true
        }
        controls.aiSubmit.setOnClickListener {
            if (viewModel.state.value.guest) authNavigator.openLogin(findNavController())
            else {
                hideKeyboard(controls.aiInput)
                viewModel.submitAi()
            }
        }
        controls.district.setOnClickListener { chooseDistrict() }
        controls.allFilters.setOnClickListener { showFilters() }
        controls.price.setOnClickListener { choosePrice() }
        controls.window.setOnClickListener { chooseWindow() }
        controls.amenities.setOnClickListener { chooseAmenities() }
        controls.sort.setOnClickListener { chooseSort() }
        controls.retry.setOnClickListener { viewModel.retry() }
        controls.recentAll.setOnClickListener { showHistorySheet() }
        controls.aiField.setStartIconOnClickListener { onMicClicked() }
        controls.historyGuestHint.setOnClickListener { authNavigator.openLogin(findNavController()) }
        controls.aiToggle.setOnClickListener {
            if (viewModel.state.value.guest) authNavigator.openLogin(findNavController())
            else {
                aiExpanded = !aiExpanded
                controls.aiBody.isVisible = aiExpanded
                if (aiExpanded) controls.aiInput.requestFocus()
            }
        }

        lastFocusId = viewModel.map.value.focus?.id
        // Máy tính bảng luôn hiện bản đồ bên cạnh danh sách (cũng thử lại nếu lần trước tile lỗi).
        if (twoPane) viewModel.showMap(true)
        binding.mapToggle.setOnClickListener { viewModel.showMap(!viewModel.map.value.visible) }
        binding.map.mapFilters.setOnClickListener { showFilters() }
        binding.map.myLocation.setOnClickListener { viewModel.locateOnMap() }
        binding.map.searchArea.setOnClickListener { viewModel.searchThisArea() }
        val nearby = NearbyAdapter(::openVenue)
        controls.nearbyList.adapter = nearby
        controls.nearbySeeMap.setOnClickListener { viewModel.openNearbyMap(NEARBY_ZOOM) }
        controls.nearbySecondary.setOnClickListener { chooseDistrict() }
        viewModel.startNearby()
        binding.map.mapRetry.setOnClickListener { viewModel.retry() }
        val cards = MapCardAdapter(::openVenue).also { mapCards = it }
        binding.map.mapCards.adapter = cards
        binding.map.mapCards.offscreenPageLimit = 1
        binding.map.mapCards.setPageTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.space_2)))
        binding.map.mapCards.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Vuốt thẻ: làm nổi bật marker của sân đang xem (FR-008).
                val venue = cards.currentList.getOrNull(position) ?: return
                if (venue.id != viewModel.map.value.selectedId) viewModel.selectVenue(venue.id, fromCard = true)
            }
        })

        var previous: CourtSearchUiState? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        render(controls, venues, previous, state)
                        val noticeHidden = manager.findFirstVisibleItemPosition() > 0 || viewModel.map.value.visible
                        if (state.notice != previous?.notice && state.notice != null && noticeHidden) {
                            // Trên bản đồ, thiếu vị trí thì nói rõ bản đồ đang ở khu vực mặc định (FR-013).
                            val text = if (viewModel.map.value.visible && state.notice == SearchNotice.LOCATION_UNAVAILABLE) {
                                R.string.map_no_location
                            } else noticeText(state.notice)
                            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
                        }
                        previous = state
                    }
                }
                launch { viewModel.effects.collect(::handleEffect) }
                launch {
                    combine(viewModel.state, viewModel.map, ::Pair).collect { (state, map) -> renderMap(binding, state, map) }
                }
                launch { viewModel.voiceLevel.collect { renderVoiceLevel(controls, it) } }
                launch {
                    viewModel.refreshUsage()
                    var day = viewModel.today()
                    var wasLocked = viewModel.lockSeconds(viewModel.state.value.usage) > 0
                    while (true) {
                        delay(1_000)
                        val locked = viewModel.lockSeconds(viewModel.state.value.usage) > 0
                        if (viewModel.today() != day || (wasLocked && !locked)) {
                            day = viewModel.today()
                            viewModel.refreshUsage()
                        }
                        wasLocked = locked
                        renderUsage(controls, viewModel.state.value)
                    }
                }
            }
        }
    }

    private fun render(
        binding: ViewCourtSearchControlsBinding,
        venues: VenueCardAdapter,
        previous: CourtSearchUiState?,
        state: CourtSearchUiState,
    ) {
        renderNearby(binding, state)
        if (binding.keyword.text.toString() != state.criteria.keyword) binding.keyword.setText(state.criteria.keyword)
        if (binding.aiInput.text.toString() != state.aiInput) binding.aiInput.setText(state.aiInput)
        binding.aiField.isVisible = !state.guest
        renderVoice(binding, state)
        binding.filtersKeptRow.isVisible = state.filtersKept && state.criteria.hasFilters()
        renderHistory(binding, previous, state)
        renderRecent(binding, previous, state)
        if (state.clarification != null) aiExpanded = true
        binding.aiBody.isVisible = aiExpanded && !state.guest
        renderUsage(binding, state)
        // Lỗi ngày hiện trong khung AI, ngay trên câu hỏi chọn ngày; lỗi micro hiện dưới ô AI (renderVoice);
        // các thông báo khác giữ vị trí cũ.
        val dateNotice = state.notice == SearchNotice.INVALID_DATE
        val inAiPanel = dateNotice || state.notice in VOICE_NOTICES
        binding.aiDateNotice.isVisible = dateNotice
        if (dateNotice) binding.aiDateNoticeText.setText(noticeText(SearchNotice.INVALID_DATE))
        binding.notice.isVisible = state.notice != null && !inAiPanel
        state.notice?.takeIf { !inAiPanel }?.let { binding.notice.setText(noticeText(it)) }
        binding.clarify.isVisible = state.clarification != null
        binding.clarifyChoices.isVisible = state.clarification != null

        if (previous?.clarification != state.clarification) {
            binding.clarifyChoices.removeAllViews()
            state.clarification?.let { pending ->
                viewModel.clarificationDates(pending).forEach { date ->
                    binding.clarifyChoices.addChoice(dateLabel(date)) { viewModel.clarify(date) }
                }
                binding.clarifyChoices.addChoice(getString(R.string.search_ai_manual), viewModel::useManualClarification)
            }
        }
        if (previous?.criteria != state.criteria || previous?.preference != state.preference ||
            previous?.districts != state.districts || previous?.amenities != state.amenities) {
            renderChips(binding.filters, state)
        }
        if (previous?.recommendations != state.recommendations || previous?.reviewBusy != state.reviewBusy ||
            previous?.usage != state.usage) renderRecommendations(binding, state)

        binding.loading.isVisible = state.loading
        binding.sort.isVisible = true
        binding.sort.setText(sortLabel(state.criteria.sort))
        binding.resultsTitle.text = if (!state.criteria.hasFilters()) {
            if (state.complete && !state.capped) getString(R.string.search_all_count, state.venues.size)
            else getString(R.string.search_all_title)
        } else if (state.complete && state.venues.isNotEmpty() && !state.capped) {
            getString(R.string.search_result_count, state.venues.size)
        } else getString(R.string.search_result_title)
        val message = when {
            state.failed -> R.string.search_error_generic
            state.loading -> null
            !state.criteria.hasFilters() && state.venues.isEmpty() && state.complete -> R.string.search_all_empty
            state.capped -> R.string.search_result_capped
            state.venues.isEmpty() && state.complete -> R.string.search_result_empty
            else -> null
        }
        binding.resultsMessage.isVisible = message != null
        message?.let(binding.resultsMessage::setText)
        binding.retry.isVisible = state.failed
        renderRelaxations(binding, previous, state)
        if (previous?.venues != state.venues || previous?.shownCount != state.shownCount) {
            venues.submitList(state.venues.take(state.shownCount))
        }
    }

    /** Micro ở mép trái ô AI: ẩn khi máy không hỗ trợ, mờ khi bị từ chối quyền, "Đang nghe…" khi nghe. */
    private fun renderVoice(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        val field = binding.aiField
        field.isStartIconVisible = state.voiceAvailable
        if (!state.voiceAvailable) return
        val denied = state.voiceDenied && !state.listening
        field.setStartIconDrawable(if (denied) R.drawable.ic_mic_off_24 else R.drawable.ic_mic_24)
        field.startIconContentDescription = getString(when {
            state.listening -> R.string.search_voice_stop_cd
            denied -> R.string.search_voice_denied_cd
            else -> R.string.search_voice_start_cd
        })
        val primary = MaterialColors.getColor(field, androidx.appcompat.R.attr.colorPrimary)
        val muted = MaterialColors.getColor(field, com.google.android.material.R.attr.colorOnSurfaceVariant)
        field.setStartIconTintList(ColorStateList.valueOf(if (state.listening) primary else muted))
        micIcon(binding)?.alpha = if (denied) 0.38f else 1f
        // Lỗi nhận dạng hiện ngay dưới ô (cạnh micro), không ở dòng thông báo chung bên dưới bộ lọc.
        field.helperText = when {
            state.listening -> getString(R.string.search_voice_listening)
            state.notice in VOICE_NOTICES -> getString(noticeText(state.notice!!))
            else -> null
        }
        field.setHelperTextColor(ColorStateList.valueOf(if (state.listening) primary else muted))
        if (state.listening != micListening) {
            micListening = state.listening
            if (state.listening) startListeningEffect(binding, primary) else stopListeningEffect(binding)
        }
    }

    private var micListening = false
    private var micPulse: ValueAnimator? = null
    private var aiFieldIdle: AiFieldLook? = null

    /** Gợi ý và độ dày viền gốc của ô AI để trả lại sau khi nghe xong. */
    private class AiFieldLook(val hint: CharSequence?, val width: Int)

    private fun micIcon(binding: ViewCourtSearchControlsBinding): View? =
        binding.aiField.findViewById(com.google.android.material.R.id.text_input_start_icon)

    /**
     * "Đang nghe" phải thấy được ngay (spec 022 US4): viền ô sáng màu chủ đạo, gợi ý đổi thành
     * "Mời bạn nói…", vòng sáng sau micro "thở" liên tục và micro nhún theo âm lượng ([renderVoiceLevel]).
     * Tắt hiệu ứng chuyển động khi người dùng tắt hoạt ảnh trong cài đặt máy.
     */
    private fun startListeningEffect(binding: ViewCourtSearchControlsBinding, primary: Int) {
        val field = binding.aiField
        if (aiFieldIdle == null) {
            aiFieldIdle = AiFieldLook(field.hint, field.boxStrokeWidth)
        }
        field.hint = getString(R.string.search_voice_speak_hint)
        field.setBoxStrokeColorStateList(ColorStateList.valueOf(primary))
        field.boxStrokeWidth = field.boxStrokeWidthFocused
        field.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        field.announceForAccessibility(getString(R.string.search_voice_listening))
        val icon = micIcon(binding) ?: return
        icon.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_voice_pulse)?.mutate()?.apply {
            setTint(primary)
            alpha = 60
        }
        if (!ValueAnimator.areAnimatorsEnabled()) return
        micPulse?.cancel()
        micPulse = ValueAnimator.ofInt(40, 110).apply {
            duration = 650
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { icon.background?.alpha = it.animatedValue as Int }
            start()
        }
    }

    private fun stopListeningEffect(binding: ViewCourtSearchControlsBinding) {
        micPulse?.cancel()
        micPulse = null
        micIcon(binding)?.apply {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            background = null
        }
        val field = binding.aiField
        aiFieldIdle?.let { look ->
            field.hint = look.hint
            // Màu viền mặc định của ô Outlined trong Material 3 (theo trạng thái focus/hover).
            ContextCompat.getColorStateList(requireContext(), com.google.android.material.R.color.m3_textfield_stroke_color)
                ?.let(field::setBoxStrokeColorStateList)
            field.boxStrokeWidth = look.width
        }
    }

    /** Micro nhún theo âm lượng thu được (0..1): im lặng thì nhỏ, nói to thì phồng tới 1,25 lần (vòng sáng vẫn nằm trong ô). */
    private fun renderVoiceLevel(binding: ViewCourtSearchControlsBinding, level: Float) {
        if (!micListening || !ValueAnimator.areAnimatorsEnabled()) return
        val scale = 1f + 0.25f * level
        micIcon(binding)?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(90)?.start()
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    /** Có quyền thì nghe/dừng; chưa có thì giải thích trước rồi mới xin; đã từ chối thì gợi ý mở Cài đặt. */
    private fun onMicClicked() {
        val state = viewModel.state.value
        when {
            state.listening || hasMicPermission() -> {
                hideKeyboard(requireView())
                viewModel.toggleVoice()
            }
            state.voiceDenied -> showVoiceDenied()
            else -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.search_voice_rationale_title)
                .setMessage(R.string.search_voice_rationale)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.search_voice_continue) { _, _ -> micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                .show()
        }
    }

    private fun showVoiceDenied() {
        val root = view ?: return
        Snackbar.make(root, R.string.search_voice_denied, Snackbar.LENGTH_LONG)
            .setAction(R.string.search_voice_open_settings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null)))
            }
            .show()
    }

    /** "Thử nới điều kiện:" dưới thông báo rỗng (spec 022 US6); không có gợi ý thì ẩn cả khối. */
    private fun renderRelaxations(binding: ViewCourtSearchControlsBinding, previous: CourtSearchUiState?, state: CourtSearchUiState) {
        binding.relaxSection.isVisible = state.relaxations.isNotEmpty() && !state.loading
        if (previous?.relaxations == state.relaxations && previous.amenities == state.amenities) return
        binding.relaxChips.removeAllViews()
        for (relaxation in state.relaxations) {
            binding.relaxChips.addView(Chip(requireContext()).apply {
                text = relaxationLabel(relaxation, state)
                setOnClickListener { viewModel.applyRelaxation(relaxation) }
            })
        }
    }

    /** "Giá đến 120k · 3 sân", "20:00–22:00 · 2 sân", "Bỏ “Gửi xe” · 4 sân", "Mai 19:00–21:00 · 1 sân", "Mọi quận · 5 sân". */
    private fun relaxationLabel(relaxation: Relaxation, state: CourtSearchUiState): String {
        val window = relaxation.criteria.window
        return when (relaxation.kind) {
            RelaxationKind.PRICE -> getString(
                R.string.search_relax_price, ((relaxation.criteria.priceMax ?: 0) / 1000).toString(), relaxation.count,
            )
            RelaxationKind.LATER, RelaxationKind.EARLIER -> getString(
                R.string.search_relax_window, window!!.start.format(), window.end.format(), relaxation.count,
            )
            RelaxationKind.DROP_AMENITY -> getString(
                R.string.search_relax_amenity,
                state.amenities.firstOrNull { it.id == relaxation.amenityId }?.name ?: relaxation.amenityId.orEmpty(),
                relaxation.count,
            )
            RelaxationKind.TOMORROW -> getString(
                R.string.search_relax_tomorrow,
                if (window!!.date == viewModel.today().plusDays(1)) getString(R.string.search_history_tomorrow) else dateLabel(window.date),
                window.start.format(), window.end.format(), relaxation.count,
            )
            RelaxationKind.ALL_DISTRICTS -> getString(R.string.search_relax_all_districts, relaxation.count)
        }
    }

    /**
     * "Tìm gần đây" (spec 020 US4): hàng chip ngang dưới ô tên sân, chỉ khi chưa có điều kiện nào.
     * Khách thấy gợi ý đăng nhập khi đang tìm, vì lần tìm đó không được lưu.
     */
    private fun renderRecent(binding: ViewCourtSearchControlsBinding, previous: CourtSearchUiState?, state: CourtSearchUiState) {
        val idle = !state.criteria.hasFilters()
        binding.recentSection.isVisible = !state.guest && idle && state.history.isNotEmpty()
        binding.historyGuestHint.isVisible = state.guest && !idle
        if (previous != null && previous.history == state.history && previous.districts == state.districts &&
            previous.amenities == state.amenities) return
        binding.recentChips.removeAllViews()
        for (entry in state.history.take(RECENT_CHIPS)) {
            binding.recentChips.addView(Chip(requireContext()).apply {
                text = historyLabel(entry.criteria, state)
                setChipIconResource(R.drawable.ic_history_24)
                isChipIconVisible = true
                setOnClickListener { viewModel.applyHistory(entry) }
            })
        }
        historySheetRefresh?.invoke(state)
    }

    private var historySheetRefresh: ((CourtSearchUiState) -> Unit)? = null

    /** Toàn bộ lịch sử (tối đa 10): chạm để tìm lại, ✕ để xóa một mục, "Xóa tất cả" có xác nhận. */
    private fun showHistorySheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = SheetSearchHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        // Mở hết ngay, kể cả màn hình ngang (mặc định chỉ hé phần tiêu đề).
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        fun fill(state: CourtSearchUiState) {
            sheet.items.removeAllViews()
            sheet.empty.isVisible = state.history.isEmpty()
            sheet.clearAll.isEnabled = state.history.isNotEmpty()
            for (entry in state.history) {
                val row = ItemSearchHistoryBinding.inflate(layoutInflater, sheet.items, false)
                val label = historyLabel(entry.criteria, state)
                row.label.text = label
                row.root.setOnClickListener {
                    dialog.dismiss()
                    viewModel.applyHistory(entry)
                }
                row.delete.contentDescription = getString(R.string.search_history_delete, label)
                row.delete.setOnClickListener { viewModel.deleteHistory(entry.id) }
                sheet.items.addView(row.root)
            }
        }
        sheet.clearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.search_history_clear_title)
                .setMessage(R.string.search_history_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.search_history_clear) { _, _ ->
                    viewModel.clearHistory()
                    dialog.dismiss()
                }
                .show()
        }
        fill(viewModel.state.value)
        historySheetRefresh = ::fill
        dialog.setOnDismissListener { historySheetRefresh = null }
        dialog.show()
    }

    /** "“Trường Chinh” · Quận 10 · ≤100k · Mai 19:00–21:00 · Gửi xe". */
    private fun historyLabel(criteria: CourtSearchCriteria, state: CourtSearchUiState): String {
        val parts = mutableListOf<String>()
        criteria.keyword.trim().takeIf { it.isNotEmpty() }?.let { parts += getString(R.string.search_history_keyword, it) }
        criteria.districtId?.let { id -> parts += state.districts.firstOrNull { it.id == id }?.name ?: id }
        val min = criteria.priceMin?.let { (it / 1000).toString() }
        val max = criteria.priceMax?.let { (it / 1000).toString() }
        when {
            min != null && max != null -> parts += getString(R.string.search_history_price_range, min, max)
            max != null -> parts += getString(R.string.search_history_price_max, max)
            min != null -> parts += getString(R.string.search_history_price_min, min)
        }
        criteria.window?.let { window ->
            val today = viewModel.today()
            val day = when (window.date) {
                today -> getString(R.string.search_history_today)
                today.plusDays(1) -> getString(R.string.search_history_tomorrow)
                else -> dateLabel(window.date)
            }
            parts += "$day ${window.start.format()}–${window.end.format()}"
        }
        criteria.amenityIds.mapNotNull { id -> state.amenities.firstOrNull { it.id == id }?.name }
            .takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(", ") }
        if (criteria.sort != CourtSearchSort.RATING) parts += getString(sortLabel(criteria.sort))
        return parts.joinToString(" · ")
    }

    /** Lịch sử câu hỏi AI: chỉ hiện khi ô AI đang trống. Chạm để điền lại, ✕ để xóa khỏi lịch sử. */
    private fun renderHistory(binding: ViewCourtSearchControlsBinding, previous: CourtSearchUiState?, state: CourtSearchUiState) {
        val visible = !state.guest && state.aiInput.isBlank() && state.aiHistory.isNotEmpty()
        binding.aiHistoryTitle.isVisible = visible
        binding.aiHistory.isVisible = visible
        if (previous != null && previous.aiHistory == state.aiHistory) return
        binding.aiHistory.removeAllViews()
        for (query in state.aiHistory) {
            binding.aiHistory.addView(Chip(requireContext()).apply {
                text = query
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.search_ai_history_remove, query)
                setOnClickListener {
                    binding.aiInput.setText(query)
                    binding.aiInput.setSelection(binding.aiInput.length())
                }
                setOnCloseIconClickListener { viewModel.removeHistory(query) }
            })
        }
    }

    private fun renderUsage(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        val locked = viewModel.lockSeconds(state.usage)
        binding.aiToggle.text = state.usage?.let { getString(R.string.search_ai_compact_usage, it.remaining, it.limit) }
            ?: getString(R.string.search_ai_compact)
        binding.usage.isVisible = !state.guest && (state.usage != null || locked > 0)
        binding.usage.text = when {
            locked > 0 -> getString(R.string.search_ai_lock_remaining, locked / 60, locked % 60)
            state.usage != null -> getString(R.string.search_ai_usage, state.usage.remaining, state.usage.limit)
            else -> ""
        }
        val input = state.aiInput.trim()
        binding.aiSubmit.isEnabled = state.guest || (!state.aiBusy && locked == 0L && state.usage?.remaining != 0 &&
            input.isNotEmpty() && input.codePointCount(0, input.length) <= 200)
        binding.aiSubmit.setText(when {
            state.guest -> R.string.search_ai_login
            state.aiBusy -> R.string.search_ai_loading
            else -> R.string.search_ai_submit
        })
    }

    private fun renderRecommendations(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        binding.recommendations.isVisible = state.recommendations.isNotEmpty()
        binding.recommendationRows.removeAllViews()
        for (recommendation in state.recommendations) {
            val venue = state.venues.firstOrNull { it.id == recommendation.venueId } ?: continue
            val row = ItemSearchRecommendationBinding.inflate(layoutInflater, binding.recommendationRows, false)
            row.venueName.text = venue.name
            row.venueName.setOnClickListener { openVenue(venue) }
            row.rating.isVisible = venue.ratingCount > 0
            row.rating.text = "${String.format(Locale.ROOT, "%.1f", venue.ratingAvg).replace('.', ',')} (${venue.ratingCount})"
            val quote = recommendation.quotes.firstOrNull { it.supports }?.text
            row.quote.isVisible = quote != null
            quote?.let { row.quote.text = getString(R.string.search_rag_quote, it) }
            row.source.isEnabled = !state.reviewBusy && recommendation.sources.isNotEmpty()
            row.explain.isEnabled = !state.reviewBusy && recommendation.sources.isNotEmpty()
            row.source.setOnClickListener { viewModel.sources(venue.id) }
            row.explain.setOnClickListener { viewModel.explain(venue.id) }
            binding.recommendationRows.addView(row.root)
        }
    }

    private fun renderChips(group: ChipGroup, state: CourtSearchUiState) {
        group.removeAllViews()
        val criteria = state.criteria
        criteria.districtId?.let { id ->
            group.addRemovable(state.districts.firstOrNull { it.id == id }?.name ?: id) { viewModel.district(null) }
        }
        criteria.window?.let { window ->
            group.addRemovable(getString(R.string.search_window_label, dateLabel(window.date), window.start.format(), window.end.format())) {
                viewModel.window(null)
            }
        }
        val min = criteria.priceMin?.let(HomeFormat::vnd)
        val max = criteria.priceMax?.let(HomeFormat::vnd)
        val price = when {
            min != null && max != null -> getString(R.string.search_price_range_chip, min, max)
            min != null -> getString(R.string.search_price_min_chip, min)
            max != null -> getString(R.string.search_price_max_chip, max)
            else -> null
        }
        price?.let { group.addRemovable(it) { viewModel.price(null, null) } }
        criteria.amenityIds.forEach { id ->
            group.addRemovable(state.amenities.firstOrNull { it.id == id }?.name ?: id) {
                viewModel.amenities(viewModel.state.value.criteria.amenityIds - id)
            }
        }
        state.preference?.let {
            group.addRemovable(getString(R.string.search_rag_chip, getString(preferenceLabel(it))), viewModel::removePreference)
        }
        if (group.childCount > 1) group.addChoice(getString(R.string.search_filter_clear), viewModel::clearFilters)
    }

    private fun chooseDistrict() {
        val state = viewModel.state.value
        if (state.districts.isEmpty()) return missingCatalog()
        val names = listOf(getString(R.string.search_filter_all_districts)) + state.districts.map { it.name }
        val checked = state.districts.indexOfFirst { it.id == state.criteria.districtId } + 1
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_district)
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                viewModel.district(state.districts.getOrNull(which - 1)?.id)
                dialog.dismiss()
            }.show()
    }

    private fun showFilters() {
        val current = viewModel.state.value
        if (!current.catalogReady) viewModel.loadCatalog()
        val sheet = BottomSheetCourtSearchFilterBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheet.root)
        val original = current.criteria
        var district = original.districtId
        var selectedDay = original.window?.date
        var start = original.window?.start ?: TimeOfDay(18 * 60)
        var end = original.window?.end ?: TimeOfDay(20 * 60)
        val selectedAmenities = original.amenityIds.toMutableSet()
        var priceChanged = false
        val minValue = ((original.priceMin ?: 0).coerceIn(0, 200_000) / 10_000 * 10_000).toFloat()
        val maxValue = ((original.priceMax ?: 200_000).coerceIn(0, 200_000) / 10_000 * 10_000).toFloat()
        sheet.priceRange.setValues(minOf(minValue, maxValue), maxOf(minValue, maxValue))

        fun renderDistrict() {
            sheet.district.text = viewModel.state.value.districts.firstOrNull { it.id == district }?.name
                ?: getString(R.string.search_filter_all_districts)
        }
        fun renderPrice() {
            val low = if (priceChanged) sheet.priceRange.values[0].toLong() else original.priceMin ?: 0L
            val high = if (priceChanged) sheet.priceRange.values[1].toLong().takeIf { it < 200_000 }
                else original.priceMax
            sheet.priceLabel.text = if (high == null) {
                getString(R.string.search_price_sheet_unlimited, HomeFormat.vnd(low))
            } else getString(R.string.search_price_sheet_range, HomeFormat.vnd(low), HomeFormat.vnd(high))
        }
        fun renderTime() {
            sheet.start.text = getString(R.string.search_choose_start, start.format())
            sheet.end.text = getString(R.string.search_choose_end, end.format())
            sheet.windowError.isVisible = false
        }
        fun renderDays() {
            sheet.days.removeAllViews()
            val today = viewModel.today()
            val noDay = Chip(requireContext()).apply {
                text = getString(R.string.search_filter_any_day)
                isCheckable = true
                isChecked = selectedDay == null
                setOnClickListener { selectedDay = null; renderDays() }
            }
            sheet.days.addView(noDay)
            for (offset in 0..BookingWindow.ADVANCE_DAYS) {
                val date = today.plusDays(offset.toLong())
                val label = when (offset) {
                    0 -> getString(R.string.search_today, dateLabel(date))
                    1 -> getString(R.string.search_tomorrow, dateLabel(date))
                    else -> date.format(DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.forLanguageTag("vi-VN")))
                }
                sheet.days.addView(Chip(requireContext()).apply {
                    text = label
                    isCheckable = true
                    isChecked = date == selectedDay
                    setOnClickListener { selectedDay = date; renderDays() }
                })
            }
        }
        renderDistrict()
        renderPrice()
        renderTime()
        renderDays()
        sheet.priceRange.addOnChangeListener { _, _, fromUser ->
            if (fromUser) priceChanged = true
            renderPrice()
        }
        sheet.district.setOnClickListener {
            val districts = viewModel.state.value.districts
            if (districts.isEmpty()) {
                missingCatalog()
            } else {
                val names = listOf(getString(R.string.search_filter_all_districts)) + districts.map { it.name }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_district_full)
                    .setSingleChoiceItems(names.toTypedArray(), districts.indexOfFirst { it.id == district } + 1) { picker, which ->
                        district = districts.getOrNull(which - 1)?.id
                        sheet.district.text = districts.getOrNull(which - 1)?.name ?: names.first()
                        picker.dismiss()
                    }.show()
            }
        }
        val catalogJob = viewLifecycleOwner.lifecycleScope.launch {
            var rendered = emptyList<com.example.booking_app.core.catalog.Amenity>()
            viewModel.state.collect { state ->
                renderDistrict()
                if (rendered != state.amenities) {
                    rendered = state.amenities
                    sheet.amenities.removeAllViews()
                    for (amenity in rendered) {
                        sheet.amenities.addView(Chip(requireContext()).apply {
                            text = amenity.name
                            isCheckable = true
                            isChecked = amenity.id in selectedAmenities
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selectedAmenities.add(amenity.id) else selectedAmenities.remove(amenity.id)
                            }
                        })
                    }
                }
            }
        }
        sheet.start.setOnClickListener { chooseTime(start, false) { start = it; if (selectedDay == null) selectedDay = viewModel.today().plusDays(1); renderTime(); renderDays() } }
        sheet.end.setOnClickListener { chooseTime(end, true) { end = it; if (selectedDay == null) selectedDay = viewModel.today().plusDays(1); renderTime(); renderDays() } }
        sheet.close.setOnClickListener { dialog.dismiss() }
        sheet.clear.setOnClickListener {
            selectedDay = null
            selectedAmenities.clear()
            for (index in 0 until sheet.amenities.childCount) (sheet.amenities.getChildAt(index) as? Chip)?.isChecked = false
            sheet.priceRange.setValues(0f, 200_000f)
            priceChanged = true
            renderDistrict()
            renderPrice()
            renderDays()
        }
        sheet.apply.setOnClickListener {
            val values = sheet.priceRange.values
            val min = if (priceChanged) values[0].toLong().takeIf { it > 0 } else original.priceMin
            val max = if (priceChanged) values[1].toLong().takeIf { it < 200_000 } else original.priceMax
            val window = selectedDay?.let { BookingSearchWindow(it, start, end) }
            if (viewModel.applyFilters(district, min, max, selectedAmenities.toSet(), window)) dialog.dismiss()
            else sheet.windowError.isVisible = true
        }
        dialog.setOnShowListener {
            sheet.root.layoutParams = sheet.root.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            BottomSheetBehavior.from(sheet.root.parent as View).state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnDismissListener { catalogJob.cancel() }
        dialog.show()
    }

    private fun chooseAmenities() {
        val state = viewModel.state.value
        if (state.amenities.isEmpty()) return missingCatalog()
        val selected = state.criteria.amenityIds.toMutableSet()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_amenities)
            .setMultiChoiceItems(state.amenities.map { it.name }.toTypedArray(),
                state.amenities.map { it.id in selected }.toBooleanArray()) { _, index, checked ->
                val id = state.amenities[index].id
                if (checked) selected.add(id) else selected.remove(id)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply) { _, _ -> viewModel.amenities(selected.toSet()) }
            .show()
    }

    private fun choosePrice() {
        val price = DialogCourtSearchPriceBinding.inflate(layoutInflater)
        val criteria = viewModel.state.value.criteria
        price.min.setText(criteria.priceMin?.toString().orEmpty())
        price.max.setText(criteria.priceMax?.toString().orEmpty())
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_price)
            .setView(price.root).setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val min = price.min.text.toString().toLongOrNull()
                val max = price.max.text.toString().toLongOrNull()
                // Giá tính bằng đồng: "50" nhiều khả năng là 50.000 đ gõ thiếu, báo thay vì tìm với 50 đ.
                val tooLow = listOfNotNull(min, max).any { it < CourtSearchCriteria.PRICE_STEP }
                if (tooLow || (min != null && max != null && min > max)) {
                    price.maxField.error = getString(if (tooLow) R.string.search_price_too_low else R.string.search_price_invalid)
                } else {
                    viewModel.price(min, max)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun chooseWindow(suggestedDate: LocalDate? = null) {
        val view = DialogCourtSearchWindowBinding.inflate(layoutInflater)
        val current = viewModel.state.value.criteria.window
        val today = viewModel.today()
        var day = suggestedDate?.takeIf { BookingWindow.isDateAllowed(it, today) } ?: current?.date ?: today.plusDays(1)
        var start = current?.start ?: TimeOfDay(18 * 60)
        var end = current?.end ?: TimeOfDay(20 * 60)
        fun render() {
            view.dayValue.text = dayWithWeekday(day)
            view.startValue.text = start.format()
            view.endValue.text = end.format()
            view.error.isVisible = false
        }
        render()
        view.day.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, selectedDay ->
                day = LocalDate.of(year, month + 1, selectedDay)
                render()
            }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
                val zone = ZoneId.systemDefault()
                datePicker.minDate = today.atStartOfDay(zone).toInstant().toEpochMilli()
                datePicker.maxDate = today.plusDays(BookingWindow.ADVANCE_DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }.show()
        }
        view.start.setOnClickListener { chooseTime(start, false) { start = it; render() } }
        view.end.setOnClickListener { chooseTime(end, true) { end = it; render() } }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_filter_window)
            .setView(view.root).setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.search_filter_apply, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (viewModel.window(BookingSearchWindow(day, start, end))) dialog.dismiss()
                else view.error.isVisible = true
            }
        }
        dialog.show()
    }

    private fun chooseTime(current: TimeOfDay, end: Boolean, selected: (TimeOfDay) -> Unit) {
        val times = (0..(if (end) 48 else 47)).map { TimeOfDay(it * 30) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (end) getString(R.string.search_choose_end, current.format()) else getString(R.string.search_choose_start, current.format()))
            .setSingleChoiceItems(times.map(TimeOfDay::format).toTypedArray(), times.indexOf(current)) { dialog, which ->
                selected(times[which])
                dialog.dismiss()
            }.show()
    }

    private fun chooseSort() {
        val sorts = CourtSearchSort.entries
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_sort_title)
            .setSingleChoiceItems(sorts.map { getString(sortLabel(it)) }.toTypedArray(),
                sorts.indexOf(viewModel.state.value.criteria.sort)) { dialog, which ->
                viewModel.sort(sorts[which])
                dialog.dismiss()
            }.show()
    }

    private fun missingCatalog() {
        viewModel.loadCatalog()
        view?.let { Snackbar.make(it, R.string.search_filter_no_catalog, Snackbar.LENGTH_SHORT).show() }
    }

    private fun handleEffect(effect: CourtSearchEffect) {
        when (effect) {
            is CourtSearchEffect.ChooseWindow -> chooseWindow(effect.date)
            is CourtSearchEffect.DropIn -> findNavController().navigate(effect.link)
            is CourtSearchEffect.Policy -> showPolicy(effect.topic)
            is CourtSearchEffect.Sources -> showSources(effect)
            is CourtSearchEffect.Explanation -> showExplanation(effect)
            is CourtSearchEffect.RequestLocation -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(when (effect.reason) {
                    LocationReason.SORT -> R.string.search_location_title
                    LocationReason.MAP -> R.string.map_my_location
                    LocationReason.NEARBY -> R.string.nearby_title
                })
                .setMessage(when (effect.reason) {
                    LocationReason.SORT -> R.string.search_location_rationale
                    LocationReason.MAP -> R.string.map_location_rationale
                    LocationReason.NEARBY -> R.string.nearby_location_rationale
                })
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    locationReason = effect.reason
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }.show()
        }
    }

    /** "Vì sao phù hợp?": Điểm cộng (màu chính) và Điểm trừ (màu tertiary); chạm một dòng để mở nguồn. */
    private fun showExplanation(effect: CourtSearchEffect.Explanation) {
        val view = DialogSearchExplanationBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.search_rag_summary_label)
            .setView(view.root).setPositiveButton(android.R.string.ok, null).create()
        for (claim in effect.claims) {
            val row = ItemSearchClaimBinding.inflate(layoutInflater, view.claims, false)
            val color = MaterialColors.getColor(row.root,
                if (claim.supports) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorTertiary)
            row.dot.backgroundTintList = ColorStateList.valueOf(color)
            row.label.setText(if (claim.supports) R.string.search_rag_plus else R.string.search_rag_minus)
            row.label.setTextColor(color)
            row.text.text = claim.excerpt.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi-VN")) }
            row.root.setOnClickListener {
                dialog.dismiss()
                viewModel.source(effect.venueId, claim.source, claim.supports)
            }
            view.claims.addView(row.root)
        }
        dialog.show()
    }

    /** Đánh giá gốc trong bottom sheet: mỗi đánh giá một thẻ, có nhãn Ủng hộ/Trái chiều và vị trí "1/2". */
    private fun showSources(effect: CourtSearchEffect.Sources) {
        val sheet = BottomSheetSearchSourcesBinding.inflate(layoutInflater)
        val venueName = viewModel.state.value.venues.firstOrNull { it.id == effect.venueId }?.name
        sheet.venue.isVisible = venueName != null
        sheet.venue.text = venueName
        effect.items.forEachIndexed { index, item ->
            val card = ItemSearchSourceBinding.inflate(layoutInflater, sheet.items, false)
            card.comment.text = getString(R.string.search_rag_quote, item.comment.trim())
            card.counter.isVisible = effect.items.size > 1
            card.counter.text = getString(R.string.search_rag_position, index + 1, effect.items.size)
            card.stance.isVisible = item.supports != null
            item.supports?.let { supports ->
                card.stance.setText(if (supports) R.string.search_rag_stance_support else R.string.search_rag_stance_counter)
                card.stance.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(card.stance,
                    if (supports) com.google.android.material.R.attr.colorPrimaryContainer
                    else com.google.android.material.R.attr.colorTertiaryContainer))
                card.stance.setTextColor(MaterialColors.getColor(card.stance,
                    if (supports) com.google.android.material.R.attr.colorOnPrimaryContainer
                    else com.google.android.material.R.attr.colorOnTertiaryContainer))
            }
            sheet.items.addView(card.root)
        }
        BottomSheetDialog(requireContext()).apply {
            setContentView(sheet.root)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }.show()
    }

    private fun showPolicy(topic: String?) {
        val policies = listOf(
            "CANCELLATION" to (R.string.search_ai_policy_cancellation to R.string.search_ai_cancellation_body),
            "DEPOSIT" to (R.string.search_ai_policy_deposit to R.string.search_ai_deposit_body),
            "VOUCHER" to (R.string.search_ai_policy_voucher to R.string.search_ai_voucher_body),
            "PAYMENT" to (R.string.search_ai_policy_payment to R.string.search_ai_payment_body),
        )
        val policy = policies.firstOrNull { it.first == topic }?.second
        val dialog = MaterialAlertDialogBuilder(requireContext())
        if (policy == null) dialog.setTitle(R.string.search_ai_policy)
            .setItems(policies.map { getString(it.second.first) }.toTypedArray()) { _, which -> showPolicy(policies[which].first) }
        else dialog.setTitle(policy.first).setMessage(policy.second).setPositiveButton(android.R.string.ok, null)
        dialog.show()
    }

    /**
     * Máy tính bảng (spec 021 FR-023): danh sách luôn hiện, chiếm 40% khi có bản đồ và cả màn khi bản đồ lỗi; thẻ của
     * sân đang chọn được tô nền và cuộn vào tầm nhìn khi chọn từ bản đồ.
     */
    private fun renderListPane(binding: FragmentCourtSearchBinding, map: CourtMapState) {
        binding.results.isVisible = true
        binding.mapToggle.isVisible = false
        (binding.results.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            val percent = if (map.visible) LIST_PANE_PERCENT else 1f
            if (params.matchConstraintPercentWidth != percent) {
                params.matchConstraintPercentWidth = percent
                binding.results.layoutParams = params
            }
        }
        val adapter = venueCards ?: return
        val selected = if (map.visible) map.selectedId else null
        adapter.selectedId = selected
        if (selected == null || selected == listSelection) {
            listSelection = selected
            return
        }
        listSelection = selected
        val manager = binding.results.layoutManager as? LinearLayoutManager ?: return
        // Ô đầu là phần điều khiển (ô tìm, bộ lọc), thẻ sân bắt đầu từ vị trí 1.
        val index = adapter.currentList.indexOfFirst { it.id == selected }.takeIf { it >= 0 }?.plus(1) ?: return
        if (index !in manager.findFirstCompletelyVisibleItemPosition()..manager.findLastCompletelyVisibleItemPosition()) {
            manager.scrollToPositionWithOffset(index, resources.getDimensionPixelSize(R.dimen.space_4))
        }
    }

    /** Chạm thẻ trong danh sách: máy tính bảng chọn sân trên bản đồ (sân không có tọa độ thì mở chi tiết). */
    private fun onVenueCard(venue: HomeVenue) {
        if (twoPane && viewModel.map.value.visible && venue.position != null) viewModel.selectFromList(venue.id)
        else openVenue(venue)
    }

    private fun openVenue(venue: HomeVenue) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.courtSearchFragment) return
        // Mở chi tiết sân từ kết quả cũng là một lần tìm (FR-021).
        viewModel.recordSearch()
        val window = venue.window
        nav.navigate(R.id.action_search_to_courtDetail, bundleOf(
            "venueId" to venue.id, "date" to window?.dateKey(),
            "startTime" to window?.start?.toHhmm(), "endTime" to window?.end?.toHhmm(),
        ))
    }

    private fun ChipGroup.addRemovable(label: String, action: () -> Unit) {
        addView(Chip(context).apply {
            text = label
            setChipBackgroundColorResource(R.color.sn_court_container)
            isCloseIconVisible = true
            setOnCloseIconClickListener { action() }
        })
    }

    private fun ChipGroup.addChoice(label: String, action: () -> Unit) {
        addView(Chip(context).apply { text = label; setOnClickListener { action() } })
    }

    private fun hideKeyboard(view: View) {
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun dateLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("dd/MM"))

    /** "Thứ hai, 16/11": thứ giúp người dùng thấy ngày đã chọn đúng chưa. */
    private fun dayWithWeekday(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi-VN")))
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi-VN")) }

    private fun sortLabel(sort: CourtSearchSort): Int = when (sort) {
        CourtSearchSort.RATING -> R.string.search_sort_rating
        CourtSearchSort.PRICE_ASC -> R.string.search_sort_price
        CourtSearchSort.NEAREST -> R.string.search_sort_nearest
    }

    private fun preferenceLabel(preference: ReviewPreference): Int = when (preference) {
        ReviewPreference.GRIP -> R.string.search_rag_grip
        ReviewPreference.HIGH_CEILING -> R.string.search_rag_ceiling
        ReviewPreference.QUIET -> R.string.search_rag_quiet
        ReviewPreference.CLEAN -> R.string.search_rag_clean
        ReviewPreference.GOOD_LIGHT -> R.string.search_rag_light
        ReviewPreference.VENTILATED -> R.string.search_rag_air
        ReviewPreference.GOOD_SERVICE -> R.string.search_rag_service
    }

    /**
     * Chế độ bản đồ (spec 021): nút nổi đổi nhãn, danh sách ẩn để TalkBack không đọc phía sau bản đồ, marker là các
     * sân của kết quả tìm kiếm hiện tại nằm trong vùng đang xem (dùng chung bộ lọc với danh sách, FR-001, FR-002).
     */
    private fun renderMap(binding: FragmentCourtSearchBinding, state: CourtSearchUiState, map: CourtMapState) {
        binding.mapToggle.setText(if (map.visible) R.string.map_toggle_list else R.string.map_toggle_map)
        binding.mapToggle.setIconResource(if (map.visible) R.drawable.ic_list_24 else R.drawable.ic_map_24)
        binding.map.root.isVisible = map.visible
        // Điện thoại xoay ngang: ẩn dòng tiêu đề khi xem bản đồ để bản đồ đủ cao (FR-023).
        binding.header.root.isVisible = !(map.visible && compactLandscape)
        if (twoPane) renderListPane(binding, map) else binding.results.isVisible = !map.visible
        if (!map.visible) return

        val controller = mapController ?: CourtMapController(binding.map.mapHost, viewLifecycleOwner, mapListener)
            .also {
                mapController = it
                it.restore(map.viewport)
            }
        map.focus?.takeIf { it.id != lastFocusId }?.let {
            lastFocusId = it.id
            controller.focus(it.center, it.zoom)
        }
        val viewport = map.viewport ?: controller.viewport()
        val visible = viewport?.let { CourtMap.visible(state.venues, it) }
        val shown = visible?.venues.orEmpty()
        controller.showMyLocation(map.myLocation)
        controller.render(shown, map.selectedId)
        renderMapCards(binding, shown, map)
        renderMapStatus(binding, state, visible)
        // Chỉ có ích khi đang lọc theo quận: kết quả không lọc quận đã gồm mọi sân khớp bộ lọc (FR-005).
        binding.map.searchArea.isVisible = map.moved && state.criteria.districtId != null

        val active = activeFilterCount(state.criteria)
        binding.map.mapFilters.text = if (active == 0) getString(R.string.map_filters)
        else getString(R.string.map_filters_count, active)
    }

    /**
     * Một dòng trạng thái nhỏ ở trên cùng, không che bản đồ (FR-019): đang tải, lỗi kèm "Thử lại", quá 100 sân
     * (FR-006), không có sân trong vùng.
     */
    private fun renderMapStatus(binding: FragmentCourtSearchBinding, state: CourtSearchUiState, visible: VisibleVenues?) {
        val loading = state.loading && state.venues.isEmpty()
        val text = when {
            state.failed -> R.string.map_error
            loading -> R.string.map_loading
            visible?.capped == true || state.capped -> R.string.map_capped
            visible != null && visible.venues.isEmpty() && state.complete -> R.string.map_empty
            else -> null
        }
        binding.map.mapStatus.isVisible = text != null
        text?.let(binding.map.mapStatusText::setText)
        binding.map.mapLoading.isVisible = loading && !state.failed
        binding.map.mapRetry.isVisible = state.failed
    }

    /**
     * "Sân gần bạn" (spec 021 US4): chỉ khi chưa có từ khóa và bộ lọc (FR-015a). Có vị trí thì tối đa 10 sân trong
     * 5 km (FR-016); không có sân thì "Tìm rộng hơn" / "Tìm theo quận"; chưa có vị trí thì mời bật (FR-018).
     */
    private fun renderNearby(binding: ViewCourtSearchControlsBinding, state: CourtSearchUiState) {
        val show = !state.criteria.hasFilters() && state.criteria.keyword.isBlank()
        binding.nearbySection.isVisible = show
        if (!show) return
        val here = state.nearbyLocation
        val venues = if (state.nearbyStatus == NearbyStatus.READY && here != null) CourtMap.nearby(state.venues, here) else emptyList()
        val waitingForVenues = state.venues.isEmpty() && !state.complete
        (binding.nearbyList.adapter as? NearbyAdapter)?.submitList(venues)
        binding.nearbyList.isVisible = venues.isNotEmpty()
        binding.nearbySeeMap.isVisible = venues.isNotEmpty()
        val message = when (state.nearbyStatus) {
            NearbyStatus.NEED_LOCATION -> R.string.nearby_need_location
            NearbyStatus.LOCATING -> R.string.nearby_locating
            NearbyStatus.UNAVAILABLE -> R.string.nearby_unavailable
            NearbyStatus.READY -> if (venues.isEmpty() && !waitingForVenues) R.string.nearby_empty else null
        }
        binding.nearbyMessage.isVisible = message != null
        message?.let(binding.nearbyMessage::setText)
        val action = when (state.nearbyStatus) {
            NearbyStatus.NEED_LOCATION -> R.string.nearby_enable to { viewModel.enableNearby() }
            NearbyStatus.UNAVAILABLE -> R.string.nearby_try_again to { viewModel.enableNearby() }
            NearbyStatus.READY -> if (message != null) R.string.nearby_wider to { viewModel.openNearbyMap(WIDER_ZOOM, wider = true) } else null
            NearbyStatus.LOCATING -> null
        }
        binding.nearbyAction.isVisible = action != null
        action?.let { (text, onClick) ->
            binding.nearbyAction.setText(text)
            binding.nearbyAction.setOnClickListener { onClick() }
        }
        binding.nearbySecondary.isVisible = state.nearbyStatus == NearbyStatus.READY && message != null
    }

    /** Thẻ tóm tắt (FR-007, FR-008): thứ tự theo sân được chạm, cuộn tới sân đang chọn; ẩn khi bỏ chọn (FR-010). */
    private fun renderMapCards(binding: FragmentCourtSearchBinding, shown: List<HomeVenue>, map: CourtMapState) {
        val adapter = mapCards ?: return
        val pager = binding.map.mapCards
        val anchor = map.cardAnchorId?.takeIf { id -> shown.any { it.id == id } }
        val here = map.myLocation
        // Có vị trí trên máy thì thẻ luôn có khoảng cách (FR-014), kể cả khi kết quả chưa được tìm lại theo vị trí.
        val cards = if (anchor != null && map.selectedId != null) CourtMap.cardOrder(shown, anchor).map { venue ->
            val position = venue.position
            if (venue.distanceMeters == null && here != null && position != null) {
                venue.copy(distanceMeters = CourtMap.meters(here, position))
            } else venue
        } else emptyList()
        pager.isVisible = cards.isNotEmpty()
        adapter.submitList(cards) {
            val index = cards.indexOfFirst { it.id == map.selectedId }
            if (index >= 0 && pager.currentItem != index) pager.setCurrentItem(index, false)
        }
        // Nút "Vị trí của tôi" nhích lên khi thẻ hiện để không bị che. Điện thoại xoay ngang thẻ ở góc trái nên không cần.
        val lift = if (pager.isVisible && !compactLandscape) pager.height.takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.map_card_height) else 0
        binding.map.myLocation.translationY = -lift.toFloat()
    }

    private val mapListener = object : CourtMapController.Listener {
        override fun onViewportChanged(viewport: MapViewport, byUser: Boolean) = viewModel.mapMoved(viewport, byUser)
        override fun onMarkerTapped(venueId: String) = viewModel.selectVenue(venueId)
        override fun onEmptyTapped() = viewModel.selectVenue(null)
        override fun onTilesFailed() {
            // Bản đồ không tải được: về danh sách, giữ bộ lọc (FR-020).
            viewModel.showMap(false)
            view?.let { Snackbar.make(it, R.string.map_tiles_failed, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun activeFilterCount(criteria: CourtSearchCriteria): Int = listOf(
        criteria.districtId != null,
        criteria.priceMin != null || criteria.priceMax != null,
        criteria.amenityIds.isNotEmpty(),
        criteria.window != null,
    ).count { it }

    private fun noticeText(notice: SearchNotice): Int = when (notice) {
        SearchNotice.MAP_DISTRICT_DROPPED -> R.string.map_district_dropped
        SearchNotice.AI_ERROR -> R.string.search_ai_unavailable
        SearchNotice.QUOTA -> R.string.search_ai_quota
        SearchNotice.LOCKED -> R.string.search_ai_locked
        SearchNotice.SOURCE_UNAVAILABLE -> R.string.search_rag_source_unavailable
        SearchNotice.INVALID_WINDOW -> R.string.search_invalid_window
        SearchNotice.INVALID_DATE -> R.string.search_ai_invalid_date
        SearchNotice.DROPPED_FILTERS -> R.string.search_ai_dropped
        SearchNotice.OFF_TOPIC -> R.string.search_ai_off_topic
        SearchNotice.LOCATION_UNAVAILABLE -> R.string.search_location_unavailable
        SearchNotice.HISTORY_DATE_PAST -> R.string.search_history_date_past
        SearchNotice.VOICE_NO_MATCH -> R.string.search_voice_no_match
        SearchNotice.VOICE_NETWORK -> R.string.search_voice_network
    }

    private class ControlsAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(view) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
}
