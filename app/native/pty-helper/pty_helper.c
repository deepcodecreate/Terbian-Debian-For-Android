/*
 * pty_helper.c
 * ---------------------------------------------------------------------
 * A tiny standalone process (NOT a JNI library - it is invoked with
 * ProcessBuilder exactly like proot already is) that gives the wrapped
 * command a REAL pseudo-terminal.
 *
 * Why this exists:
 *   Without a real PTY, the only way to tell the shell its window size
 *   changed is to literally type "export COLUMNS=.. LINES=.." into its
 *   stdin, which is visible on screen. A real PTY lets us call
 *   ioctl(TIOCSWINSZ), which the kernel applies silently and turns into
 *   a SIGWINCH for the foreground process group - nothing is ever
 *   printed, and well-behaved programs (vim, htop, less, ...) see the
 *   correct size via the normal ioctl path instead of guessing from
 *   environment variables.
 *
 * Usage:
 *   pty_helper <control_fifo_path> <initial_cols> <initial_rows> -- <cmd> [args...]
 *
 *   <control_fifo_path>  path to a FIFO this process creates. The Java
 *                         side opens it for writing and sends resize
 *                         requests as plain text lines: "COLS ROWS\n".
 *   <initial_cols/rows>   size to set on the PTY before <cmd> starts.
 *   <cmd> [args...]       the real command to run (e.g. proot ... bash -i).
 *                         Runs with the PTY slave as fd 0/1/2 and as its
 *                         controlling terminal.
 *
 * Data flow:
 *   Java's stdin  -> this process's fd 0  -> PTY master  -> child's fd 0
 *   child's fd1/2 -> PTY master -> this process's fd 1 -> Java's stdout
 *
 * Exit behaviour:
 *   Exits with the child's exit status once the child exits, and forwards
 *   SIGTERM/SIGHUP (what Process.destroy() sends) to the child first.
 *
 * Build (Android NDK, arm64):
 *   $NDK/toolchains/llvm/prebuilt/<host>/bin/aarch64-linux-android26-clang \
 *       -O2 -Wall -o libptyhelper.so pty_helper.c
 *
 *   Then drop the output at:
 *     src/main/jniLibs/arm64-v8a/libptyhelper.so
 *   Android's packager extracts it (executable) into nativeLibraryDir at
 *   install time, same as libproot.so/libproot_loader.so already are -
 *   this is a convention for shipping prebuilt native *executables*
 *   through jniLibs, not an actual JNI shared library.
 *
 * PTY implementation note:
 *   The getpt/openpty/forkpty/login_tty logic below is adapted from
 *   z80oolong/libandroid-pty (https://github.com/z80oolong/libandroid-pty,
 *   BSD-2-Clause), which documents and fixes real bugs in bionic's own
 *   pty.cpp for exactly this scenario (pseudo-terminals inside a
 *   chroot/proot Linux userland running on Android). See the license
 *   notice further down, immediately above the adapted code.
 * ---------------------------------------------------------------------
 */

#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

/* ---------------------------------------------------------------------
 * Corrected pty primitives (adapted from z80oolong/libandroid-pty,
 * https://github.com/z80oolong/libandroid-pty, BSD-2-Clause).
 *
 * That project documents and fixes real bugs in bionic's own
 * getpt/openpty/forkpty (libc/bionic/pty.cpp) that show up specifically
 * in chroot-style Linux userlands on Android ("Debian noroot" etc.) -
 * exactly our situation. Rather than LD_PRELOAD-ing a second .so, the
 * same corrected logic is reproduced here directly (own names, so there
 * is no symbol clash with bionic's libc versions of these functions).
 *
 * Original copyright notice, reproduced as required by the license:
 *   Copyright (C) 2008 The Android Open Source Project
 *   Copyright (C) 2017 Z.OOL. (NAKATSUKA, Yukitaka <zool@zool.jpn.org>)
 *   Redistributed/adapted under the BSD 2-Clause License.
 * ------------------------------------------------------------------- */

static int fixed_getpt(void) {
    return open("/dev/ptmx", O_RDWR | O_NOCTTY);
}

static int fixed_unlockpt(int fd) {
    int unlock = 0;
    return ioctl(fd, TIOCSPTLCK, &unlock);
}

static int fixed_ptsname_r(int fd, char *buf, size_t len) {
    unsigned int pty_num;
    if (buf == NULL) {
        errno = EINVAL;
        return -1;
    }
    if (ioctl(fd, TIOCGPTN, &pty_num) != 0) {
        errno = ENOTTY;
        return -1;
    }
    if ((size_t) snprintf(buf, len, "/dev/pts/%u", pty_num) >= len) {
        errno = ERANGE;
        return -1;
    }
    return 0;
}

static int fixed_openpty(int *master, int *slave, const struct termios *t,
                          const struct winsize *ws) {
    char name[32];

    if ((*master = fixed_getpt()) == -1) {
        return -1;
    }
    /* grantpt() is a documented no-op in bionic too - permissions on
     * /dev/pts/* are already correct without it on Android. */
    if (fixed_unlockpt(*master) == -1) {
        close(*master);
        return -1;
    }
    if (fixed_ptsname_r(*master, name, sizeof(name)) != 0) {
        close(*master);
        return -1;
    }
    *slave = open(name, O_RDWR | O_NOCTTY);
    if (*slave == -1) {
        close(*master);
        return -1;
    }
    if (t != NULL) {
        tcsetattr(*slave, TCSAFLUSH, t);
    }
    if (ws != NULL) {
        ioctl(*slave, TIOCSWINSZ, ws);
    }
    return 0;
}

/* setsid() + TIOCSCTTY + wiring fd 0/1/2 - this is what makes
 * "bash: cannot set terminal process group" and "no job control in this
 * shell" go away, since the slave becomes a genuine controlling
 * terminal instead of bash merely guessing it might be one. */
static int fixed_login_tty(int fd) {
    setsid();
    if (ioctl(fd, TIOCSCTTY, NULL) == -1) {
        return -1;
    }
    dup2(fd, STDIN_FILENO);
    dup2(fd, STDOUT_FILENO);
    dup2(fd, STDERR_FILENO);
    if (fd > STDERR_FILENO) {
        close(fd);
    }
    return 0;
}

static pid_t fixed_forkpty(int *amaster, const struct winsize *ws) {
    int master, slave;
    pid_t pid;

    if (fixed_openpty(&master, &slave, NULL, ws) == -1) {
        return -1;
    }

    pid = fork();
    if (pid == -1) {
        close(master);
        close(slave);
        return -1;
    }

    if (pid == 0) {
        /* Child */
        close(master);
        if (fixed_login_tty(slave) == -1) {
            _exit(1);
        }
        return 0;
    }

    /* Parent */
    *amaster = master;
    close(slave);
    return pid;
}

/* --------------------------------------------------------------------- */

static volatile sig_atomic_t g_child_pid = -1;
static volatile sig_atomic_t g_should_exit = 0;

static void forward_signal(int sig) {
    if (g_child_pid > 0) {
        /* Negative pid targets the whole process group we put the child
         * in, so it reaches proot and whatever it eventually execs too. */
        kill(-g_child_pid, sig);
    }
    if (sig == SIGTERM || sig == SIGHUP || sig == SIGINT) {
        g_should_exit = 1;
    }
}

static void install_signal_forwarding(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = forward_signal;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
}

static int set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) return -1;
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

/* Opens (creating if needed) the control FIFO for reading, in a way that
 * never sees EOF: we open it O_RDWR ourselves so there is always at
 * least one writer-capable end alive, regardless of whether the Java
 * side currently has it open. */
static int open_control_fifo(const char *path) {
    if (mkfifo(path, 0600) != 0 && errno != EEXIST) {
        fprintf(stderr, "pty_helper: mkfifo(%s) failed: %s\n", path, strerror(errno));
        return -1;
    }
    int fd = open(path, O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        fprintf(stderr, "pty_helper: open(%s) failed: %s\n", path, strerror(errno));
        return -1;
    }
    return fd;
}

static void apply_winsize(int master_fd, int cols, int rows) {
    if (cols <= 0 || rows <= 0) return;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) cols;
    ws.ws_row = (unsigned short) rows;
    /* The kernel itself sends SIGWINCH to the PTY's foreground process
     * group here if the size actually changed - nothing for us to print
     * or type, unlike the old "export COLUMNS=.." approach. */
    ioctl(master_fd, TIOCSWINSZ, &ws);
}

/* Parses the most recent complete "COLS ROWS" line out of buf (a resize
 * request that arrived while we were busy is still just a resize
 * request - only the latest one read in this pass matters). */
static void handle_control_data(int master_fd, char *buf, ssize_t len) {
    buf[len] = '\0';
    char *line = strtok(buf, "\n");
    char *last_line = NULL;
    while (line != NULL) {
        last_line = line;
        line = strtok(NULL, "\n");
    }
    if (last_line == NULL) return;
    int cols = 0, rows = 0;
    if (sscanf(last_line, "%d %d", &cols, &rows) == 2) {
        apply_winsize(master_fd, cols, rows);
    }
}

static void copy_loop(int master_fd, int control_fd) {
    char buf[4096];

    set_nonblocking(STDIN_FILENO);
    set_nonblocking(master_fd);

    for (;;) {
        if (g_should_exit) return;

        fd_set readfds;
        FD_ZERO(&readfds);
        FD_SET(STDIN_FILENO, &readfds);
        FD_SET(master_fd, &readfds);
        FD_SET(control_fd, &readfds);
        int maxfd = STDIN_FILENO;
        if (master_fd > maxfd) maxfd = master_fd;
        if (control_fd > maxfd) maxfd = control_fd;

        struct timeval timeout;
        timeout.tv_sec = 0;
        timeout.tv_usec = 200 * 1000; /* wake up periodically to notice child exit */

        int ready = select(maxfd + 1, &readfds, NULL, NULL, &timeout);
        if (ready < 0) {
            if (errno == EINTR) continue;
            return;
        }

        /* Child already gone? Stop copying once buffers drain naturally. */
        int status;
        pid_t waited = waitpid(g_child_pid, &status, WNOHANG);
        if (waited == g_child_pid) {
            g_should_exit = 1;
        }

        if (ready == 0) continue;

        if (FD_ISSET(STDIN_FILENO, &readfds)) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n > 0) {
                write(master_fd, buf, (size_t) n);
            } else if (n == 0) {
                /* Java closed stdin (session torn down) - close the
                 * master's write side too by shutting the whole thing. */
                g_should_exit = 1;
            }
        }

        if (FD_ISSET(master_fd, &readfds)) {
            ssize_t n = read(master_fd, buf, sizeof(buf));
            if (n > 0) {
                write(STDOUT_FILENO, buf, (size_t) n);
            } else if (n < 0 && errno != EAGAIN && errno != EINTR) {
                /* Master closed (child exited and closed its slave fds). */
                g_should_exit = 1;
            }
        }

        if (FD_ISSET(control_fd, &readfds)) {
            ssize_t n = read(control_fd, buf, sizeof(buf) - 1);
            if (n > 0) {
                handle_control_data(master_fd, buf, n);
            }
        }
    }
}

int main(int argc, char *argv[]) {
    if (argc < 6) {
        fprintf(stderr,
                "usage: pty_helper <control_fifo> <cols> <rows> -- <cmd> [args...]\n");
        return 64;
    }

    const char *fifo_path = argv[1];
    int initial_cols = atoi(argv[2]);
    int initial_rows = atoi(argv[3]);

    if (strcmp(argv[4], "--") != 0) {
        fprintf(stderr, "pty_helper: expected '--' before the command\n");
        return 64;
    }

    char **child_argv = &argv[5];
    if (child_argv[0] == NULL) {
        fprintf(stderr, "pty_helper: no command given\n");
        return 64;
    }

    int control_fd = open_control_fifo(fifo_path);
    if (control_fd < 0) {
        return 1;
    }

    int master_fd = -1;
    struct winsize initial_ws;
    memset(&initial_ws, 0, sizeof(initial_ws));
    initial_ws.ws_col = (unsigned short) (initial_cols > 0 ? initial_cols : 80);
    initial_ws.ws_row = (unsigned short) (initial_rows > 0 ? initial_rows : 24);

    pid_t pid = fixed_forkpty(&master_fd, &initial_ws);
    if (pid < 0) {
        fprintf(stderr, "pty_helper: forkpty failed: %s\n", strerror(errno));
        return 1;
    }

    if (pid == 0) {
        /* Child: fixed_forkpty() already made the slave our controlling
         * terminal and wired it to fd 0/1/2. Just exec the real command. */
        execvp(child_argv[0], child_argv);
        fprintf(stderr, "pty_helper: exec failed: %s\n", strerror(errno));
        _exit(127);
    }

    /* Parent: this is the process Java's ProcessBuilder is actually
     * watching, so its own exit code/behaviour is what ShellProcess
     * observes. */
    g_child_pid = pid;
    install_signal_forwarding();

    copy_loop(master_fd, control_fd);

    /* Make sure the child is gone before we report an exit status - if
     * it already exited, waitpid returns immediately with that status;
     * otherwise nudge it once more before waiting. */
    if (!g_should_exit) {
        kill(-pid, SIGHUP);
    }

    int status = 0;
    waitpid(pid, &status, 0);

    close(master_fd);
    close(control_fd);
    unlink(fifo_path);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return 1;
}
