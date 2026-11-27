package com.example.booking_app.feature.community.ui.event

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.BottomSheetEventAttendeesBinding
import com.example.booking_app.databinding.ItemEventAttendeeBinding
import com.example.booking_app.feature.community.domain.model.Attendee
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** T056: bottom sheet danh sách thành viên tham gia sự kiện. */
@AndroidEntryPoint
class EventAttendeesBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: EventAttendeesViewModel by viewModels()
    private var binding: BottomSheetEventAttendeesBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = BottomSheetEventAttendeesBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.closeButton.setOnClickListener { dismiss() }

        val adapter = AttendeeAdapter()
        b.attendeeList.layoutManager = LinearLayoutManager(requireContext())
        b.attendeeList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(b, adapter, state) }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(
        binding: BottomSheetEventAttendeesBinding,
        adapter: AttendeeAdapter,
        state: EventAttendeesUiState,
    ) {
        val coreState: UiState<Unit> = when (state) {
            EventAttendeesUiState.Loading -> UiState.Loading
            is EventAttendeesUiState.Content -> UiState.Content(Unit)
            EventAttendeesUiState.Empty -> UiState.Empty
            is EventAttendeesUiState.Error -> UiState.Error(CommunityUiText.error(requireContext(), state.cause), viewModel::retry)
        }
        binding.stateViews.render(
            coreState,
            binding.attendeeList,
            EmptyStateSpec(message = R.string.community_empty_attendees, icon = R.drawable.ic_groups_24),
        )
        adapter.submitList((state as? EventAttendeesUiState.Content)?.attendees.orEmpty())
    }

    private class AttendeeAdapter : ListAdapter<Attendee, AttendeeAdapter.ViewHolder>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemEventAttendeeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

        class ViewHolder(private val binding: ItemEventAttendeeBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(attendee: Attendee) {
                val context = binding.root.context
                val name = attendee.displayName.ifBlank { context.getString(R.string.community_join_request_default_name) }
                binding.attendeeName.text = name
                binding.attendeeAvatar.contentDescription =
                    context.getString(R.string.community_event_attendee_avatar_cd, name)
                binding.attendeeAvatar.load(
                    attendee.avatarPath?.takeIf { it.isNotBlank() }?.let { StorageUrls.publicUrl("avatars", it) },
                ) {
                    placeholder(R.drawable.ic_person_outline_24)
                    error(R.drawable.ic_person_outline_24)
                }
            }
        }

        private object Diff : DiffUtil.ItemCallback<Attendee>() {
            override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee) = oldItem.userId == newItem.userId
            override fun areContentsTheSame(oldItem: Attendee, newItem: Attendee) = oldItem == newItem
        }
    }
}
