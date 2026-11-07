package com.example.booking_app.core.session

/** Xin sang chế độ "Quản lý sân" khi claim không còn là `OWNER` (spec 011 FR-012). */
class OwnerRevokedException : Exception("Owner role revoked")
