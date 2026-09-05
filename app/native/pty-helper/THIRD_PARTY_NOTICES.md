# Third-party code notice

`pty_helper.c` includes pty primitives (`fixed_getpt`, `fixed_unlockpt`,
`fixed_ptsname_r`, `fixed_openpty`, `fixed_login_tty`, `fixed_forkpty`)
adapted from:

- Project: [z80oolong/libandroid-pty](https://github.com/z80oolong/libandroid-pty)
- License: BSD 2-Clause
- Purpose: fixes documented bugs in bionic's own `pty.cpp` that affect
  pseudo-terminal handling inside chroot/proot Linux userlands on
  Android ("Debian noroot"-style environments) - exactly this project's
  situation.

That project is itself based on and licensed identically to AOSP's
bionic C library:

```
Copyright (C) 2008 The Android Open Source Project
Copyright (C) 2017 Z.OOL. (NAKATSUKA, Yukitaka <zool@zool.jpn.org>)

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in
    the documentation and/or other materials provided with the
    distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
```

If Terbian ships an in-app "licenses"/"about" screen, this notice
should be included there alongside PRoot's own license.
