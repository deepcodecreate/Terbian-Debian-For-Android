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

package com.terbian.mvvm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.terbian.terminal.SessionType;

import java.util.ArrayList;
import java.util.List;

public class SessionsViewModel extends ViewModel {
    private final MutableLiveData<List<SessionEntry>> sessions =
            new MutableLiveData<>(new ArrayList<>());

    private int nextSessionNumber = 1;

    public LiveData<List<SessionEntry>> getSessions() {
        return sessions;
    }

    public SessionEntry addSession() {
        return addSession(SessionType.DEBIAN);
    }

    public SessionEntry addSession(SessionType type) {
        List<SessionEntry> current = new ArrayList<>(sessions.getValue());
        String id = "session_" + System.currentTimeMillis() + "_" + nextSessionNumber;
        String prefix = type == SessionType.SHELL ? "Shell " : "Debian ";
        String title = prefix + nextSessionNumber;
        nextSessionNumber++;

        SessionEntry entry = new SessionEntry(id, title, type);
        current.add(entry);
        sessions.setValue(current);
        return entry;
    }

    public void renameSession(String id, String newTitle) {
        List<SessionEntry> current = sessions.getValue();
        if (current == null) return;
        for (SessionEntry e : current) {
            if (e.id.equals(id)) {
                e.title = newTitle;
                break;
            }
        }
        sessions.setValue(new ArrayList<>(current));
    }

    public void closeSession(String id) {
        List<SessionEntry> current = sessions.getValue();
        if (current == null) return;
        List<SessionEntry> updated = new ArrayList<>();
        for (SessionEntry e : current) {
            if (!e.id.equals(id)) {
                updated.add(e);
            }
        }
        sessions.setValue(updated);
    }

    public void closeOthers(String keepId) {
        List<SessionEntry> current = sessions.getValue();
        if (current == null) return;
        List<SessionEntry> updated = new ArrayList<>();
        for (SessionEntry e : current) {
            if (e.id.equals(keepId)) {
                updated.add(e);
            }
        }
        sessions.setValue(updated);
    }

    public void closeAll() {
        sessions.setValue(new ArrayList<>());
    }

    public boolean isEmpty() {
        List<SessionEntry> current = sessions.getValue();
        return current == null || current.isEmpty();
    }
}
