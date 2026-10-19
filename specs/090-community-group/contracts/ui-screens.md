# Contract: màn hình spec 090

Package `com.example.booking_app.feature.community` (`ui/`, `data/`, `domain/`). Nav graph lồng `nav_community.xml`, được `include` vào graph chính. Wireframe: [ui/README.md](../ui/README.md).

## 1. Màn hình

| Màn hình | Layout | Story | Safe Args | Ghi chú |
|---|---|---|---|---|
| `CommunityHomeFragment` | `fragment_community_home.xml` | US1, US2 | — | Tab "Nhóm của tôi" và "Khám phá"; nút "Tạo nhóm". Wireframe `09_community-chat_buoc1_nhom.png` |
| `GroupSearchFragment` (tab "Khám phá") | `fragment_group_search.xml`, `item_group.xml` | US2 | — | Ô tìm tên, chip khu vực (điền sẵn từ `UserProfileReader.favoriteDistrictIds`), phân trang 20; rỗng có "Tạo nhóm của bạn" |
| `GroupFormFragment` | `fragment_group_form.xml` | US1 | `groupId: String?` (null = tạo) | Tên, mô tả, ảnh (Photo Picker), khu vực, chế độ; nút "Xóa nhóm" khi sửa |
| `GroupDetailFragment` | `fragment_group_detail.xml`, `item_post.xml`, `item_event.xml` | US2, US3, US4, US6 | `groupId` | Người ngoài: trang giới thiệu + "Tham gia"/"Xin tham gia"/"Đang chờ duyệt". Thành viên: bảng tin (phân trang 20), nút "Đăng bài", "Tạo sự kiện", "Chat nhóm" (spec 091), "Thành viên", "Yêu cầu tham gia (N)". Wireframe `09_community-chat_buoc2_bang-tin-nhom.png` |
| `PostComposerFragment` | `fragment_post_composer.xml` | US3, US4 | `groupId`, `postId: String?`, `type: POST / EVENT` | Nội dung, tối đa 4 ảnh (trạng thái từng ảnh), trường sự kiện; hỏi "Bỏ bài đang viết?" khi thoát |
| `EventAttendeesBottomSheet` | `bottom_sheet_event_attendees.xml` | US4 | `groupId`, `postId` | Danh sách tên người tham gia |
| `GroupMembersFragment` | `fragment_group_members.xml`, `item_group_member.xml` | US5 | `groupId` | Chủ nhóm, quản trị viên ở đầu; menu: phong/gỡ quản trị viên, chuyển quyền, mời ra |
| `JoinRequestsFragment` | `fragment_join_requests.xml`, `item_join_request.xml` | US2, US5 | `groupId` | Duyệt/Từ chối; nhãn "Từng bị mời ra" |
| `ReportPostBottomSheet` | `bottom_sheet_report_post.xml` | US6 | `groupId`, `postId` | 5 lý do, ô mô tả khi "Khác". Wireframe `09_community-chat_buoc3_bao-cao-bai-dang.png` |
| `PhotoViewerFragment` | `fragment_photo_viewer.xml` | US3 | `urls`, `startIndex` | Dùng lại của spec 070 nếu đã đưa lên `core/ui` |

Deep link: `smashnow://community/group/{groupId}` (thông báo `GROUP_ACTIVITY`), `smashnow://community/group/{groupId}/requests`.

Mọi màn hình có đủ 4 trạng thái (`include_state_views`), mất mạng giữ nội dung đang soạn (FR-020). Chuỗi trong `strings_community.xml`, tiền tố `community_`.

## 2. ViewModel

| ViewModel | Trạng thái và sự kiện |
|---|---|
| `CommunityHomeViewModel` | `myGroups: UiState<List<GroupCard>>` (collection group `members where userId == uid`, đọc nhóm theo lô ≤ 10, bỏ `DELETED`) |
| `GroupSearchViewModel` | `query`, `districtIds`, `UiState<Page<GroupCard>>`; debounce 300 ms; giữ bộ lọc qua `SavedStateHandle` |
| `GroupFormViewModel` | `Form(name, description, districtId, visibility, avatar)`, lỗi từng ô; `Saving`, `AvatarUploadFailed` (giữ form, cho thử lại hoặc bỏ ảnh); `requestId` qua `SavedStateHandle` |
| `GroupDetailViewModel` | `group` (realtime), `membership: GroupMembership`, `feed: UiState<List<PostItem>>` (gộp `VISIBLE` + bài ẩn của mình, hoặc mọi bài nếu là quản trị viên); sự kiện `OpenChat`, `NavigateToLogin` |
| `PostComposerViewModel` | `PostDraft`; ảnh: `PENDING`, `UPLOADING`, `UPLOADED`, `FAILED`; nút "Đăng" chỉ bật khi không còn ảnh `UPLOADING`/`FAILED`; `Posted` → quay lại bảng tin |
| `GroupMembersViewModel` | `UiState<List<GroupMember>>`, `permissions: GroupPermissions` |
| `JoinRequestsViewModel` | `UiState<List<JoinRequest>>`; thao tác lạc quan, lỗi thì hoàn lại |

## 3. Điểm nối cho nhóm khác

| Nhóm | Việc của họ | Spec 090 cung cấp |
|---|---|---|
| C, spec 091 | Chat nhóm | Hook `onGroupMembershipChanged` ([contract Edge Function mục 4](edge-functions-community.md#4-hàm-dùng-chung-_sharedgroupsts)); nút "Chat nhóm" mở `smashnow://chat/group/{groupId}` |
| A, `delete-account` | Xóa tài khoản | `anonymizeCommunityGroups(uid)` |
| A, spec 011 | Khu vực hay chơi | Đọc qua `UserProfileReader` (010-T012) |
| D, spec 100 | Gửi `GROUP_ACTIVITY` | Danh sách `data.event` ([research R15](../research.md#r15-thông-báo-fr-021)) và deep link ở trên |
| D, spec 120 | Xử lý `reports`, ẩn bài, khóa nhóm | `targetPath`, quy ước `hiddenBy = SYSTEM`, `groups.status = LOCKED` |
