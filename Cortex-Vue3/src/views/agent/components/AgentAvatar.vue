<template>
  <div 
    class="agent-avatar-upload" 
    :style="{ '--avatar-size': size + 'px' }"
    @click="editCropper()"
  >
    <!-- 调试信息 -->
    <div v-if="!avatarUrl" style="font-size: 10px; color: red;">
      无头像: {{ props.avatar }}
    </div>
    
    <img 
      v-if="avatarUrl" 
      :src="avatarUrl" 
      class="avatar-img"
      @error="handleImageError"
      @load="handleImageLoad"
    />
    <el-avatar v-else :size="size" :icon="UserFilled" />
    <div class="avatar-overlay">
      <el-icon :size="Math.floor(size / 3)"><Camera /></el-icon>
    </div>
    
    <el-dialog 
      :title="title" 
      v-model="open" 
      width="800px" 
      append-to-body 
      @opened="modalOpened" 
      @close="closeDialog"
    >
      <el-row>
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <vue-cropper
            ref="cropper"
            :img="options.img"
            :info="true"
            :autoCrop="options.autoCrop"
            :autoCropWidth="options.autoCropWidth"
            :autoCropHeight="options.autoCropHeight"
            :fixedBox="options.fixedBox"
            :outputType="options.outputType"
            @realTime="realTime"
            v-if="visible"
          />
        </el-col>
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <div class="avatar-upload-preview">
            <img :src="options.previews.url" :style="options.previews.img" />
          </div>
        </el-col>
      </el-row>
      <br />
      <el-row>
        <el-col :lg="2" :md="2">
          <el-upload
            action="#"
            :http-request="requestUpload"
            :show-file-list="false"
            :before-upload="beforeUpload"
          >
            <el-button>
              选择
              <el-icon class="el-icon--right"><Upload /></el-icon>
            </el-button>
          </el-upload>
        </el-col>
        <el-col :lg="{ span: 1, offset: 2 }" :md="2">
          <el-button icon="Plus" @click="changeScale(1)"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="Minus" @click="changeScale(-1)"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="RefreshLeft" @click="rotateLeft()"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="RefreshRight" @click="rotateRight()"></el-button>
        </el-col>
        <el-col :lg="{ span: 2, offset: 6 }" :md="2">
          <el-button type="primary" @click="uploadImg()">提 交</el-button>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, getCurrentInstance } from 'vue'
import { UserFilled, Camera, Upload } from '@element-plus/icons-vue'
import "vue-cropper/dist/index.css"
import { VueCropper } from "vue-cropper"
import request from '@/utils/request'

const props = defineProps({
  agentId: {
    type: Number,
    required: true
  },
  avatar: {
    type: String,
    default: ''
  },
  size: {
    type: Number,
    default: 100
  }
})

const emit = defineEmits(['success'])

const { proxy } = getCurrentInstance()

const open = ref(false)
const visible = ref(false)
const title = ref("上传Agent头像")

// 计算头像URL
const avatarUrl = computed(() => {
  console.log('🔍 AgentAvatar计算avatarUrl，props.avatar:', props.avatar)
  if (props.avatar) {
    // 如果是完整URL，直接使用
    if (props.avatar.startsWith('http')) {
      console.log('✅ 使用完整URL:', props.avatar)
      return props.avatar
    }
    // 如果是/profile/开头的相对路径，拼接baseURL（正确格式）
    if (props.avatar.startsWith('/profile/')) {
      const url = import.meta.env.VITE_APP_BASE_API + props.avatar
      console.log('✅ 拼接/profile/路径:', url)
      return url
    }
    // 如果是/avatar/开头（旧格式），转换为/profile/avatar/
    if (props.avatar.startsWith('/avatar/')) {
      const url = import.meta.env.VITE_APP_BASE_API + '/profile' + props.avatar
      console.log('✅ 兼容旧路径，拼接为:', url)
      return url
    }
    // 其他情况也拼接baseURL
    const url = import.meta.env.VITE_APP_BASE_API + props.avatar
    console.log('✅ 拼接其他路径:', url)
    return url
  }
  console.log('⚠️ avatar为空')
  return ''
})

//图片裁剪数据
const options = reactive({
  img: '',  // 初始为空，由watch更新
  autoCrop: true,              // 是否默认生成截图框
  autoCropWidth: 200,          // 默认生成截图框宽度
  autoCropHeight: 200,         // 默认生成截图框高度
  fixedBox: true,              // 固定截图框大小 不允许改变
  outputType: "png",           // 默认生成截图为PNG格式
  filename: 'avatar.png',      // 文件名称
  previews: {}                 //预览数据
})

// 监听avatar变化，更新预览图
watch(() => props.avatar, (newVal) => {
  if (newVal) {
    if (newVal.startsWith('http')) {
      options.img = newVal
    } else if (newVal.startsWith('/profile/')) {
      options.img = import.meta.env.VITE_APP_BASE_API + newVal
    } else if (newVal.startsWith('/avatar/')) {
      // 兼容旧路径，转换为 /profile/avatar/
      options.img = import.meta.env.VITE_APP_BASE_API + '/profile' + newVal
    } else {
      options.img = import.meta.env.VITE_APP_BASE_API + newVal
    }
  }
}, { immediate: true })

/** 编辑头像 */
function editCropper() {
  open.value = true
}

/** 打开弹出层结束时的回调 */
function modalOpened() {
  visible.value = true
}

/** 覆盖默认上传行为 */
function requestUpload() {}

/** 向左旋转 */
function rotateLeft() {
  proxy.$refs.cropper.rotateLeft()
}

/** 向右旋转 */
function rotateRight() {
  proxy.$refs.cropper.rotateRight()
}

/** 图片缩放 */
function changeScale(num) {
  num = num || 1
  proxy.$refs.cropper.changeScale(num)
}

/** 上传预处理 */
function beforeUpload(file) {
  if (file.type.indexOf("image/") == -1) {
    proxy.$modal.msgError("文件格式错误，请上传图片类型,如：JPG，PNG后缀的文件。")
  } else {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => {
      options.img = reader.result
      options.filename = file.name
    }
  }
}

/** 上传图片 */
function uploadImg() {
  proxy.$refs.cropper.getCropBlob(data => {
    let formData = new FormData()
    formData.append("avatarfile", data, options.filename)
    formData.append("agentId", props.agentId)
    
    // 调用Agent头像上传接口 - 修正路径
    request({
      url: '/agent/agent/avatar',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }).then(response => {
      open.value = false
      visible.value = false
      proxy.$modal.msgSuccess("头像上传成功")
      emit('success', response.imgUrl)
    }).catch(error => {
      proxy.$modal.msgError("头像上传失败: " + (error.message || '未知错误'))
    })
  })
}

/** 实时预览 */
function realTime(data) {
  options.previews = data
}

/** 关闭窗口 */
function closeDialog() {
  // 恢复为当前头像
  if (props.avatar) {
    if (props.avatar.startsWith('http')) {
      options.img = props.avatar
    } else {
      options.img = import.meta.env.VITE_APP_BASE_API + props.avatar
    }
  }
  visible.value = false
}

/** 图片加载成功 */
function handleImageLoad() {
  console.log('✅ 图片加载成功:', avatarUrl.value)
}

/** 图片加载失败 */
function handleImageError(e) {
  console.error('❌ 图片加载失败:', avatarUrl.value, e)
}
</script>

<style lang='scss' scoped>
.agent-avatar-upload {
  position: relative;
  display: inline-block;
  cursor: pointer;
  width: var(--avatar-size, 100px);
  height: var(--avatar-size, 100px);
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  color: #fff;
}

.agent-avatar-upload:hover .avatar-overlay {
  opacity: 1;
}

.avatar-upload-preview {
  position: absolute;
  top: 50%;
  transform: translate(50%, -50%);
  width: 200px;
  height: 200px;
  border-radius: 50%;
  border: 1px solid #ccc;
  overflow: hidden;
}
</style>
