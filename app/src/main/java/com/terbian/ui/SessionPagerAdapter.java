/*
 * Copyright 2026 deepcodecreate
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

package com.terbian.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.terbian.mvvm.SessionEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionPagerAdapter extends FragmentStateAdapter {
    private final List<SessionEntry> sessions = new ArrayList<>();
    private final Map<String, Fragment> fragmentsById = new HashMap<>();

    public SessionPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    public void submitList(List<SessionEntry> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void updateSessionsSilently(List<SessionEntry> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
    }

    public SessionEntry sessionAt(int position) {
        if (position < 0 || position >= sessions.size()) return null;
        return sessions.get(position);
    }

    public Fragment getFragment(int position) {
        SessionEntry entry = sessionAt(position);
        if (entry == null) return null;
        return fragmentsById.get(entry.id);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        SessionEntry entry = sessions.get(position);
        Fragment fragment = TerminalFragment.newInstance(entry.id, entry.title, entry.type);
        fragmentsById.put(entry.id, fragment);
        return fragment;
    }

    @Override
    public long getItemId(int position) {
        return sessions.get(position).id.hashCode();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (SessionEntry entry : sessions) {
            if (entry.id.hashCode() == itemId) return true;
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }
}
