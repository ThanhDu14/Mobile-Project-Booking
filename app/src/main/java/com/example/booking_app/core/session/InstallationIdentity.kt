package com.example.booking_app.core.session

/** Non-secret installation identifier; never used as an authorization credential. */
interface InstallationIdentity { fun installationId(): String }
