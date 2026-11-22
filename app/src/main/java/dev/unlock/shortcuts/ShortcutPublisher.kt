package dev.unlock.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.unlock.R
import dev.unlock.archive.ArchiveDomain
import dev.unlock.archive.ArchiveService
import dev.unlock.archive.ArchiveTarget
import dev.unlock.share.ClipboardActivity

/**
 * Veröffentlicht pro Archivziel einen Sharing Shortcut. Diese erscheinen als
 * Direct-Share-Ziele im Teilen-Menü (Kategorie aus `res/xml/shortcuts.xml`) und
 * – lange auf das App-Icon drücken – als „Zwischenablage mit Dienst X öffnen“.
 */
object ShortcutPublisher {

    const val SHARE_CATEGORY = "dev.unlock.category.ARCHIVE_TARGET"
    private const val PREFIX = "target_"

    fun shortcutId(target: ArchiveTarget) = PREFIX + target.id

    fun targetForShortcut(shortcutId: String?): ArchiveTarget? =
        shortcutId?.takeIf { it.startsWith(PREFIX) }?.let { ArchiveTarget.fromId(it.removePrefix(PREFIX)) }

    fun publish(context: Context, domain: ArchiveDomain) {
        val shortcuts = ArchiveTarget.entries.mapIndexed { index, target ->
            val label = label(context, target, domain)
            ShortcutInfoCompat.Builder(context, shortcutId(target))
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(
                    IconCompat.createWithResource(
                        context,
                        if (target.service == ArchiveService.WAYBACK) R.drawable.ic_shortcut_wayback else R.drawable.ic_shortcut_archive,
                    ),
                )
                .setCategories(setOf(SHARE_CATEGORY))
                .setLongLived(true)
                .setRank(index)
                .setIntent(
                    Intent(context, ClipboardActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(ClipboardActivity.EXTRA_TARGET, shortcutId(target)),
                )
                .build()
        }
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.take(max)) }
    }

    fun label(context: Context, target: ArchiveTarget, domain: ArchiveDomain): String = when (target) {
        ArchiveTarget.ARCHIVE_TODAY_NEWEST -> context.getString(R.string.shortcut_newest, domain.host)
        ArchiveTarget.ARCHIVE_TODAY_SUBMIT -> context.getString(R.string.shortcut_submit, domain.host)
        ArchiveTarget.ARCHIVE_TODAY_ALL -> context.getString(R.string.shortcut_all, domain.host)
        ArchiveTarget.WAYBACK -> context.getString(R.string.shortcut_wayback)
    }
}
