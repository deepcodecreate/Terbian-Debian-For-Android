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
