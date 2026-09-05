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
