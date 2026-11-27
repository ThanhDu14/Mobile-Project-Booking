package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostAuthor
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.model.PostType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T015: bảng quyền ở data-model mục 8 (chỉ để ẩn/hiện nút, server vẫn kiểm tra). */
class GroupPermissionsTest {

    private fun perms(role: GroupRole?, status: GroupStatus = GroupStatus.ACTIVE, uid: String = "me") =
        GroupPermissions(myUid = uid, role = role, groupStatus = status)

    private fun post(
        authorId: String = "other",
        status: PostStatus = PostStatus.VISIBLE,
        hiddenBy: HiddenBy? = null,
    ) = Post(
        id = "p1", groupId = "g1", type = PostType.POST, content = "x", photoPaths = emptyList(),
        author = PostAuthor(authorId, "A", null, isDeleted = false),
        status = status, hiddenBy = hiddenBy, isEdited = false, createdAt = null, event = null,
    )

    // ---- canEdit, canManageRoles ----

    @Test
    fun `canEdit va canManageRoles chi OWNER`() {
        assertTrue(perms(GroupRole.OWNER).canEdit)
        assertFalse(perms(GroupRole.ADMIN).canEdit)
        assertFalse(perms(GroupRole.MEMBER).canEdit)
        assertFalse(perms(null).canEdit)
        assertTrue(perms(GroupRole.OWNER).canManageRoles)
        assertFalse(perms(GroupRole.ADMIN).canManageRoles)
        assertFalse(perms(GroupRole.MEMBER).canManageRoles)
        assertFalse(perms(null).canManageRoles)
    }

    // ---- canRemove ----

    @Test
    fun `OWNER moi duoc admin va thanh vien nhung khong moi chinh minh`() {
        val p = perms(GroupRole.OWNER)
        assertTrue(p.canRemove("u1", GroupRole.ADMIN))
        assertTrue(p.canRemove("u2", GroupRole.MEMBER))
        assertFalse(p.canRemove("me", GroupRole.OWNER))
    }

    @Test
    fun `ADMIN chi moi duoc MEMBER`() {
        val p = perms(GroupRole.ADMIN)
        assertTrue(p.canRemove("u2", GroupRole.MEMBER))
        assertFalse(p.canRemove("u1", GroupRole.ADMIN))
        assertFalse(p.canRemove("u0", GroupRole.OWNER))
        assertFalse(p.canRemove("me", GroupRole.ADMIN))
    }

    @Test
    fun `MEMBER va nguoi ngoai khong moi ai`() {
        assertFalse(perms(GroupRole.MEMBER).canRemove("u2", GroupRole.MEMBER))
        assertFalse(perms(null).canRemove("u2", GroupRole.MEMBER))
    }

    // ---- canModerate, canUnhide ----

    @Test
    fun `canModerate la OWNER hoac ADMIN`() {
        assertTrue(perms(GroupRole.OWNER).canModerate)
        assertTrue(perms(GroupRole.ADMIN).canModerate)
        assertFalse(perms(GroupRole.MEMBER).canModerate)
        assertFalse(perms(null).canModerate)
    }

    @Test
    fun `canUnhide chi khi bai bi nhom an, khong phai he thong an`() {
        val byGroup = post(status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP)
        val bySystem = post(status = PostStatus.HIDDEN, hiddenBy = HiddenBy.SYSTEM)
        assertTrue(perms(GroupRole.ADMIN).canUnhide(byGroup))
        assertTrue(perms(GroupRole.OWNER).canUnhide(byGroup))
        assertFalse(perms(GroupRole.OWNER).canUnhide(bySystem))
        assertFalse(perms(GroupRole.MEMBER).canUnhide(byGroup))
        assertFalse(perms(GroupRole.ADMIN).canUnhide(post()))
    }

    @Test
    fun `canHide cho quan tri vien khi bai dang hien`() {
        assertTrue(perms(GroupRole.ADMIN).canHide(post()))
        assertFalse(perms(GroupRole.MEMBER).canHide(post()))
        assertFalse(perms(GroupRole.ADMIN).canHide(post(status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP)))
    }

    // ---- canReport, canEditPost ----

    @Test
    fun `canReport khi khong phai bai cua minh va bai dang VISIBLE`() {
        assertTrue(perms(GroupRole.MEMBER).canReport(post(authorId = "other")))
        assertFalse(perms(GroupRole.MEMBER).canReport(post(authorId = "me")))
        assertFalse(perms(null).canReport(post()))
        assertFalse(perms(GroupRole.OWNER).canReport(post(authorId = "other", status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP)))
        assertFalse(perms(GroupRole.ADMIN).canReport(post(authorId = "other", status = PostStatus.HIDDEN, hiddenBy = HiddenBy.SYSTEM)))
    }

    @Test
    fun `canEditPost va canDeletePost chi nguoi dang`() {
        assertTrue(perms(GroupRole.MEMBER).canEditPost(post(authorId = "me")))
        assertFalse(perms(GroupRole.MEMBER).canEditPost(post(authorId = "other")))
        assertFalse(perms(GroupRole.OWNER).canEditPost(post(authorId = "other")))
        assertTrue(perms(GroupRole.MEMBER).canDeletePost(post(authorId = "me")))
        assertFalse(perms(GroupRole.OWNER).canDeletePost(post(authorId = "other")))
    }

    @Test
    fun `canPost chi thanh vien`() {
        assertTrue(perms(GroupRole.MEMBER).canPost)
        assertFalse(perms(null).canPost)
    }

    // ---- Nhóm LOCKED: mọi quyền ghi trừ rời nhóm là false ----

    @Test
    fun `nhom LOCKED khoa moi quyen ghi tru roi nhom`() {
        val owner = perms(GroupRole.OWNER, GroupStatus.LOCKED)
        val admin = perms(GroupRole.ADMIN, GroupStatus.LOCKED)
        val member = perms(GroupRole.MEMBER, GroupStatus.LOCKED)

        assertFalse(owner.canEdit)
        assertFalse(owner.canManageRoles)
        assertFalse(owner.canDeleteGroup)
        assertFalse(owner.canRemove("u1", GroupRole.MEMBER))
        assertFalse(admin.canModerate)
        assertFalse(admin.canRemove("u1", GroupRole.MEMBER))
        assertFalse(admin.canHide(post()))
        assertFalse(admin.canUnhide(post(status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP)))
        assertFalse(member.canPost)
        assertFalse(member.canReport(post()))
        assertFalse(member.canEditPost(post(authorId = "me")))
        assertFalse(member.canDeletePost(post(authorId = "me")))
        assertFalse(member.canAttend)

        assertTrue(member.canLeave)
        assertTrue(admin.canLeave)
    }

    @Test
    fun `nguoi ngoai khong roi nhom duoc`() {
        assertFalse(perms(null).canLeave)
    }

    @Test
    fun `chu nhom xoa nhom duoc khi ACTIVE`() {
        assertTrue(perms(GroupRole.OWNER).canDeleteGroup)
        assertFalse(perms(GroupRole.ADMIN).canDeleteGroup)
    }
}
