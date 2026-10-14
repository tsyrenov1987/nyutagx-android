/*
 * Нютаг — номера из записной книжки телефона (для надписи «Вне контактов»).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class DefaultNyutagAddressBook(
    @ApplicationContext private val context: Context,
) : NyutagAddressBook {
    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override suspend fun phones(): List<String> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val phones = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && phones.size < MAX_PHONES) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(phones::add)
            }
        }
        phones.toList()
    }

    private companion object {
        // Столько же принимает сервер в одной проверке.
        const val MAX_PHONES = 5000
    }
}
