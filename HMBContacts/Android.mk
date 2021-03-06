LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional


#add by liyang begin:MTK和高通不同部分的代码分别放到两个独立的文件夹，在编译之前手动配置src_dirs
mtk_csp_dir:=hb_mtk
qualcomm_csp_dir:=hb_qualcomm
contacts_common_dir_hb := ../HMBContactsCommon/hb_mtk
#add by liyang end

contacts_common_dir := ../HMBContactsCommon
phone_common_dir := ../HMBPhoneCommon
contacts_ext_dir := ../HMBContactsCommon/ext

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
            pinyin4j_2:libs/pinyin4j-2.5.0.jar       
include $(BUILD_MULTI_PREBUILT)
include $(CLEAR_VARS)
src_dirs := src $(contacts_common_dir)/src $(phone_common_dir)/src $(contacts_ext_dir)/src $(mtk_csp_dir) $(contacts_common_dir_hb)
res_dirs := res $(contacts_common_dir)/res $(contacts_common_dir)/res_ext $(phone_common_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_SRC_FILES += src/com/hb/tms/ITmsService.aidl
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res_ext

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages android.support.v7.cardview


LOCAL_JAVA_LIBRARIES := telephony-common voip-common 
#telephony-ext
LOCAL_JAVA_LIBRARIES += framework
# 引用hb-framework的类
#LOCAL_JAVA_LIBRARIES += mediatek-framework
#LOCAL_JAVA_LIBRARIES += mediatek-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.vcard \
    android-common \
    guava \
    android-support-v13 \
    android-support-v7-cardview \
    android-support-v7-palette \
    android-support-v4 \
    libphonenumber \
    pinyin4j_2 \

            
# 引用hb-framework的资源
LOCAL_AAPT_FLAGS += -I out/target/common/obj/APPS/hb-framework-res_intermediates/package-export.apk
 

LOCAL_JAVA_LIBRARIES += hb-framework


LOCAL_PACKAGE_NAME := HMBContacts
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true
LOCAL_JACK_ENABLED := disabled  
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

#LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
#include $(call all-makefiles-under,$(LOCAL_PATH))




