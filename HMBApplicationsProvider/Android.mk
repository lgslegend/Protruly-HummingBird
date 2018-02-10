LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := \
        ext guava
#        hb-framework

LOCAL_PACKAGE_NAME := HMBApplicationsProvider
LOCAL_CERTIFICATE := shared

include $(BUILD_PACKAGE)

# Also build our test apk
include $(call all-makefiles-under,$(LOCAL_PATH))
