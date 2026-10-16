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

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.terbian.terminal.SessionType;
import com.terbian.terminal.session.SessionRegistry;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class TerminalViewModel extends AndroidViewModel {
    private final MutableLiveData<String> title = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finished = new MutableLiveData<>(false);

    private TerminalSession session;
    private String sessionId;

    public TerminalViewModel(@NonNull Application application) {
        super(application);
    }

    public void init(String sessionId, String initialTitle, SessionType type, TerminalSessionClient client) {
        this.sessionId = sessionId;
        if (title.getValue() == null) {
            title.setValue(initialTitle);
        }
        session = SessionRegistry.getInstance(getApplication()).getOrCreate(sessionId, initialTitle, type, client);
    }

    public TerminalSession getSession() {
        return session;
    }

    public String getSessionId() {
        return sessionId;
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<Boolean> getFinished() {
        return finished;
    }

    public void reportTitle(String newTitle) {
        if (newTitle != null && !newTitle.isEmpty()) {
            title.postValue(newTitle);
        }
    }

    public void reportFinished() {
        finished.postValue(true);
    }
}
