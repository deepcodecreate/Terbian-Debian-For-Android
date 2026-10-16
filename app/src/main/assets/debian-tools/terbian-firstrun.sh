# Copyright 2026 deepcodecreate
# SPDX-License-Identifier: Apache-2.0
case $- in *i*) ;; *) return 0 2>/dev/null || exit 0;; esac
mkdir -p /root/.terbian
if [ -f /root/.terbian/install.log ] && [ ! -f /root/.terbian/install-shown ]; then
  cat /root/.terbian/install.log
  touch /root/.terbian/install-shown
fi
if [ ! -f /root/.terbian/setup-done ] && [ -z "$TERBIAN_SKIP_SETUP" ]; then
  printf 'Install essential packages (nano, vim, git, python...) now? [Y/n] '
  read -r a
  case "$a" in n|N) export TERBIAN_SKIP_SETUP=1;; *) terbian-setup || echo "setup failed — rerun: terbian-setup  (diagnose: terbian-doctor)";; esac
fi
