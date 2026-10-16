/*
 * Нютаг (форк Element X) — поддержка проекта через Google Play Billing.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.libraries.donate"
}

dependencies {
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.uiStrings)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.billing)
}
