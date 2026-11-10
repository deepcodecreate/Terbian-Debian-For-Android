# Copyright 2026 deepcodecreate
# SPDX-License-Identifier: Apache-2.0
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libtermux
LOCAL_SRC_FILES:= termux.c
include $(BUILD_SHARED_LIBRARY)
