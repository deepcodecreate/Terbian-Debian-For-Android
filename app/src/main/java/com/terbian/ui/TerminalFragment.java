package com.terbian.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.terbian.R;
import com.terbian.mvvm.SessionsViewModel;
import com.terbian.mvvm.TerminalViewModel;
import com.terbian.prefs.AppSettings;
import com.terbian.terminal.SessionType;
import com.terbian.terminal.TerbianTerminalViewClient;
import com.terbian.terminal.view.TerbianTerminalView;
import com.terbian.terminal.view.TerminalTheme;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public class TerminalFragment extends Fragment {

    private static final String ARG_SESSION_ID = "session_id";
    private static final String ARG_SESSION_TITLE = "session_title";
    private static final String ARG_SESSION_TYPE = "session_type";

    public static TerminalFragment newInstance(String sessionId, String title, SessionType type) {
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        args.putString(ARG_SESSION_TITLE, title);
        args.putString(ARG_SESSION_TYPE, (type == null ? SessionType.DEBIAN : type).name());
        TerminalFragment fragment = new TerminalFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private TerbianTerminalView terminalView;
    private TerminalViewModel viewModel;
    private SessionsViewModel sessionsViewModel;
    private String sessionId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_terminal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionId = requireArguments().getString(ARG_SESSION_ID);
        String title = requireArguments().getString(ARG_SESSION_TITLE);
        SessionType type = SessionType.fromName(requireArguments().getString(ARG_SESSION_TYPE));

        terminalView = view.findViewById(R.id.terminalView);
        terminalView.setCustomTypeFace(Typeface.MONOSPACE);
        terminalView.setTextDirection(View.TEXT_DIRECTION_LTR);
        terminalView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        terminalView.setTerminalViewClient(new TerbianTerminalViewClient(requireContext(), terminalView));
        terminalView.setModifierStateListener(() -> {

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshExtraKeys();
            }
        });

        viewModel = new ViewModelProvider(this).get(TerminalViewModel.class);
        sessionsViewModel = new ViewModelProvider(requireActivity()).get(SessionsViewModel.class);

        TerminalSessionClient client = new FragmentSessionClient();
        viewModel.init(sessionId, title, type, client);
        terminalView.attachSession(viewModel.getSession());
        terminalView.setTheme(AppSettings.currentTheme(requireContext()));

        terminalView.post(() -> {
            if (terminalView.requestFocus()) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) requireContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        viewModel.getTitle().observe(getViewLifecycleOwner(), newTitle -> {
            if (newTitle != null) sessionsViewModel.renameSession(sessionId, newTitle);
        });

        viewModel.getFinished().observe(getViewLifecycleOwner(), finished -> {
            if (Boolean.TRUE.equals(finished) && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).closeSessionCompletely(sessionId);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        TerminalSession session = viewModel == null ? null : viewModel.getSession();
        if (session != null) {
            session.updateTerminalSessionClient(new FragmentSessionClient());
            if (terminalView != null) {
                terminalView.attachSession(session);
                terminalView.onScreenUpdated();
            }
        }
    }

    public TerbianTerminalView getTerminalView() {
        return terminalView;
    }

    public void applyTheme(TerminalTheme theme) {
        if (terminalView != null) terminalView.setTheme(theme);
    }

    private class FragmentSessionClient implements TerminalSessionClient {

        @Override
        public void onTextChanged(@NonNull TerminalSession changedSession) {
            if (terminalView != null && changedSession == terminalView.getCurrentSession()) {
                terminalView.onScreenUpdated();
            }
        }

        @Override
        public void onTitleChanged(@NonNull TerminalSession changedSession) {
            if (viewModel != null) {
                viewModel.reportTitle(changedSession.getTitle());
            }
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            if (viewModel != null) {
                viewModel.reportFinished();
            }
        }

        @Override
        public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
            if (terminalView != null) {
                terminalView.copyToClipboard(text);
            }
        }

        @Override
        public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
            if (terminalView != null) {
                terminalView.pasteFromClipboard();
            }
        }

        @Override
        public void onBell(@NonNull TerminalSession session) {

        }

        @Override
        public void onColorsChanged(@NonNull TerminalSession session) {
            if (terminalView != null) {
                terminalView.onScreenUpdated();
            }
        }

        @Override
        public void onTerminalCursorStateChange(boolean state) {

        }

        @Override
        public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {

        }

        @Override
        public Integer getTerminalCursorStyle() {
            TerminalTheme theme = terminalView == null ? null : terminalView.getTheme();
            if (theme == null || theme.cursorStyle == null) {
                return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
            }
            switch (theme.cursorStyle) {
                case UNDERLINE:
                    return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
                case BAR:
                    return TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
                case BLOCK:
                default:
                    return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            }
        }

        @Override
        public void logError(String tag, String message) {
            android.util.Log.e(tag, message);
        }

        @Override
        public void logWarn(String tag, String message) {
            android.util.Log.w(tag, message);
        }

        @Override
        public void logInfo(String tag, String message) {
            android.util.Log.i(tag, message);
        }

        @Override
        public void logDebug(String tag, String message) {
            android.util.Log.d(tag, message);
        }

        @Override
        public void logVerbose(String tag, String message) {
            android.util.Log.v(tag, message);
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception e) {
            android.util.Log.e(tag, message, e);
        }

        @Override
        public void logStackTrace(String tag, Exception e) {
            android.util.Log.e(tag, "Exception", e);
        }
    }
}
