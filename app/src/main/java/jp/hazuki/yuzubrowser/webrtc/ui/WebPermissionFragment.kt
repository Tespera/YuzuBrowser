/*
 * Copyright (C) 2017 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.webrtc.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.hazuki.yuzubrowser.R
import jp.hazuki.yuzubrowser.utils.view.recycler.DividerItemDecoration
import jp.hazuki.yuzubrowser.utils.view.recycler.OnRecyclerListener
import jp.hazuki.yuzubrowser.webrtc.WebPermissionsDatabase
import jp.hazuki.yuzubrowser.webrtc.WebRtcPermission
import jp.hazuki.yuzubrowser.webrtc.core.WebPermissions
import kotlinx.android.extensions.CacheImplementation
import kotlinx.android.extensions.ContainerOptions
import kotlinx.android.synthetic.main.recycler_with_fab.*

@ContainerOptions(CacheImplementation.NO_CACHE)
class WebPermissionFragment : Fragment(), OnRecyclerListener, WebPermissionsEditDialog.OnPermissionEditedListener {

    private lateinit var adapter: WebPermissionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.recycler_view, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        adapter = WebPermissionAdapter(activity,
                WebPermissionsDatabase.getInstance(context.applicationContext).getList().toMutableList(),
                this)

        recyclerView.run {
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(DividerItemDecoration(activity))
            adapter = this@WebPermissionFragment.adapter
        }
    }

    override fun onRecyclerItemClicked(v: View, position: Int) {
        val (host, permissions) = adapter[position]
        WebPermissionsEditDialog(position, host, permissions)
                .show(childFragmentManager, "edit")
    }

    override fun onPermissionEdited(position: Int, host: String, permissions: WebPermissions) {
        adapter[position] = Pair(host, permissions)
        adapter.notifyItemChanged(position)

        WebPermissionsDatabase.getInstance(context.applicationContext).update(host, permissions)
        WebRtcPermission.clearCache()
    }

    override fun onRecyclerItemLongClicked(v: View, position: Int) = false
}