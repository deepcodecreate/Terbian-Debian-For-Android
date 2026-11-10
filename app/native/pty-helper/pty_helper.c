// SPDX-License-Identifier: Apache-2.0 AND BSD-2-Clause

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

/*
 * Original copyright notice, reproduced as required by the license
 * (adapted from z80oolong/libandroid-pty, BSD 2-Clause License):
 *   Copyright (C) 2008 The Android Open Source Project
 *   Copyright (C) 2017 Z.OOL. (NAKATSUKA, Yukitaka <zool@zool.jpn.org>)
 */

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
        close(master);
        if (fixed_login_tty(slave) == -1) {
            _exit(1);
        }
        return 0;
    }

    *amaster = master;
    close(slave);
    return pid;
}

static volatile sig_atomic_t g_child_pid = -1;
static volatile sig_atomic_t g_should_exit = 0;

static void forward_signal(int sig) {
    if (g_child_pid > 0) {
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

    ioctl(master_fd, TIOCSWINSZ, &ws);
}

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
        timeout.tv_usec = 200 * 1000;

        int ready = select(maxfd + 1, &readfds, NULL, NULL, &timeout);
        if (ready < 0) {
            if (errno == EINTR) continue;
            return;
        }

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
                g_should_exit = 1;
            }
        }

        if (FD_ISSET(master_fd, &readfds)) {
            ssize_t n = read(master_fd, buf, sizeof(buf));
            if (n > 0) {
                write(STDOUT_FILENO, buf, (size_t) n);
            } else if (n < 0 && errno != EAGAIN && errno != EINTR) {
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
        execvp(child_argv[0], child_argv);
        fprintf(stderr, "pty_helper: exec failed: %s\n", strerror(errno));
        _exit(127);
    }

    g_child_pid = pid;
    install_signal_forwarding();

    copy_loop(master_fd, control_fd);

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
