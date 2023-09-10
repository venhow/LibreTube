package com.github.libretube.ui.sheets

import android.os.Bundle
import androidx.core.os.bundleOf
import com.github.libretube.R
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.constants.IntentData
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.enums.PlaylistType
import com.github.libretube.enums.ShareObjectType
import com.github.libretube.extensions.toID
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.BackgroundHelper
import com.github.libretube.obj.ShareData
import com.github.libretube.ui.adapters.PlaylistsAdapter.Companion.PLAYLISTS_ADAPTER_REQUEST_KEY
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.dialogs.DeletePlaylistDialog
import com.github.libretube.ui.dialogs.PlaylistDescriptionDialog
import com.github.libretube.ui.dialogs.RenamePlaylistDialog
import com.github.libretube.ui.dialogs.ShareDialog
import com.github.libretube.util.PlayingQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PlaylistOptionsBottomSheet(
    private val playlistId: String,
    private val playlistName: String,
    private val playlistType: PlaylistType,
    private val onRename: (newName: String) -> Unit = {},
    private val onChangeDescription: (newDescription: String) -> Unit = {},
    private val onDelete: () -> Unit = {}
) : BaseBottomSheet() {
    private val shareData = ShareData(currentPlaylist = playlistName)
    override fun onCreate(savedInstanceState: Bundle?) {
        // options for the dialog
        val optionsList = mutableListOf(
            getString(R.string.playOnBackground)
        )

        if (PlayingQueue.isNotEmpty()) optionsList.add(getString(R.string.add_to_queue))

        val isBookmarked = runBlocking(Dispatchers.IO) {
            DatabaseHolder.Database.playlistBookmarkDao().includes(playlistId)
        }

        if (playlistType == PlaylistType.PUBLIC) {
            optionsList.add(getString(R.string.share))
            optionsList.add(getString(R.string.clonePlaylist))

            // only add the bookmark option to the playlist if public
            optionsList.add(
                getString(if (isBookmarked) R.string.remove_bookmark else R.string.add_to_bookmarks)
            )
        } else {
            optionsList.add(getString(R.string.renamePlaylist))
            optionsList.add(getString(R.string.change_playlist_description))
            optionsList.add(getString(R.string.deletePlaylist))
        }

        setSimpleItems(optionsList) { which ->
            val mFragmentManager = (context as BaseActivity).supportFragmentManager

            when (optionsList[which]) {
                // play the playlist in the background
                getString(R.string.playOnBackground) -> {
                    val playlist = withContext(Dispatchers.IO) {
                        PlaylistsHelper.getPlaylist(playlistId)
                    }
                    playlist.relatedStreams.firstOrNull()?.let {
                        BackgroundHelper.playOnBackground(
                            requireContext(),
                            it.url!!.toID(),
                            playlistId = playlistId
                        )
                    }
                }

                getString(R.string.add_to_queue) -> {
                    PlayingQueue.insertPlaylist(playlistId, null)
                }
                // Clone the playlist to the users Piped account
                getString(R.string.clonePlaylist) -> {
                    val context = requireContext()
                    val playlistId = withContext(Dispatchers.IO) {
                        runCatching {
                            PlaylistsHelper.clonePlaylist(playlistId)
                        }.getOrNull()
                    }
                    context.toastFromMainDispatcher(
                        if (playlistId != null) R.string.playlistCloned else R.string.server_error
                    )
                }
                // share the playlist
                getString(R.string.share) -> {
                    val newShareDialog = ShareDialog()
                    newShareDialog.arguments = bundleOf(
                        IntentData.id to playlistId,
                        IntentData.shareObjectType to ShareObjectType.PLAYLIST,
                        IntentData.shareData to shareData
                    )
                    // using parentFragmentManager, childFragmentManager doesn't work here
                    newShareDialog.show(parentFragmentManager, ShareDialog::class.java.name)
                }

                getString(R.string.deletePlaylist) -> {
                    mFragmentManager.setFragmentResultListener(PLAYLIST_OPTIONS_REQUEST_KEY, context as BaseActivity) { _, bundle ->
                        if (bundle.getBoolean(IntentData.playlistTask, true)) onDelete()
                        // forward the result the playlists adapter if visible
                        mFragmentManager.setFragmentResult(PLAYLISTS_ADAPTER_REQUEST_KEY, bundle)
                    }
                    val newDeletePlaylistDialog = DeletePlaylistDialog()
                    newDeletePlaylistDialog.arguments = bundleOf(
                        IntentData.playlistId to playlistId,
                        IntentData.playlistType to playlistType
                    )
                    newDeletePlaylistDialog.show(mFragmentManager, null)
                }

                getString(R.string.renamePlaylist) -> {
                    mFragmentManager.setFragmentResultListener(PLAYLIST_OPTIONS_REQUEST_KEY, context as BaseActivity) { _, bundle ->
                        onRename(bundle.getString(IntentData.playlistName, ""))
                        // forward the result the playlists adapter if visible
                        mFragmentManager.setFragmentResult(PLAYLISTS_ADAPTER_REQUEST_KEY, bundle)
                    }
                    val newRenamePlaylistDialog = RenamePlaylistDialog()
                    newRenamePlaylistDialog.arguments = bundleOf(
                        IntentData.playlistId to playlistId,
                        IntentData.playlistName to playlistName
                    )
                    newRenamePlaylistDialog.show(mFragmentManager, null)
                }

                getString(R.string.change_playlist_description) -> {
                    mFragmentManager.setFragmentResultListener(PLAYLIST_OPTIONS_REQUEST_KEY, context as BaseActivity) { _, bundle ->
                        onChangeDescription(bundle.getString(IntentData.playlistName, ""))
                        // forward the result the playlists adapter if visible
                        mFragmentManager.setFragmentResult(PLAYLISTS_ADAPTER_REQUEST_KEY, bundle)
                    }
                    val newPlaylistDescriptionDialog = PlaylistDescriptionDialog()
                    newPlaylistDescriptionDialog.arguments = bundleOf(
                        IntentData.playlistId to playlistId,
                        IntentData.playlistDescription to ""
                    )
                    newPlaylistDescriptionDialog.show(mFragmentManager, null)
                }

                else -> {
                    withContext(Dispatchers.IO) {
                        if (isBookmarked) {
                            DatabaseHolder.Database.playlistBookmarkDao().deleteById(playlistId)
                        } else {
                            val bookmark = try {
                                RetrofitInstance.api.getPlaylist(playlistId)
                            } catch (e: Exception) {
                                return@withContext
                            }.toPlaylistBookmark(playlistId)
                            DatabaseHolder.Database.playlistBookmarkDao().insert(bookmark)
                        }
                    }
                }
            }
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val PLAYLIST_OPTIONS_REQUEST_KEY = "playlist_options_request_key"
    }
}
