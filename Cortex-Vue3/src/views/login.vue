<template>
  <div class="login-container">
    <!-- 左侧黑色区域 -->
    <div class="login-left">
      <div class="brand-content">
        <h1 class="brand-title">CortexAI</h1>
        <p class="brand-subtitle">智能对话平台</p>
      </div>
    </div>

    <!-- 右侧白色登录区域 -->
    <div class="login-right">
      <div class="login-box">
        <h2 class="login-title">登录</h2>

        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              size="large"
              auto-complete="off"
              placeholder="账号"
            >
              <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              auto-complete="off"
              placeholder="密码"
              @keyup.enter="handleLogin"
            >
              <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="code" v-if="captchaEnabled">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.code"
                size="large"
                auto-complete="off"
                placeholder="验证码"
                class="captcha-input"
                @keyup.enter="handleLogin"
              >
                <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
              </el-input>
              <div class="captcha-code">
                <img :src="codeUrl" @click="getCode" class="captcha-img" alt="验证码"/>
              </div>
            </div>
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            <router-link class="register-link" :to="'/register'" v-if="register">注册账号</router-link>
          </div>

          <el-form-item>
            <el-button
              :loading="loading"
              size="large"
              type="primary"
              class="login-btn"
              @click.prevent="handleLogin"
            >
              <span v-if="!loading">登 录</span>
              <span v-else>登 录 中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="login-footer">
        <span>{{ footerContent }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang='scss' scoped>
.login-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

/* 左侧黑色区域 */
.login-left {
  flex: 1;
  background: #000000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;

  .brand-content {
    text-align: center;
    color: white;

    .brand-title {
      font-size: 64px;
      font-weight: 700;
      margin: 0 0 20px 0;
      letter-spacing: 2px;
    }

    .brand-subtitle {
      font-size: 18px;
      margin: 0;
      color: #999;
      font-weight: 300;
      letter-spacing: 1px;
    }
  }
}

/* 右侧白色登录区域 */
.login-right {
  flex: 1;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  position: relative;

  .login-box {
    width: 100%;
    max-width: 400px;

    .login-title {
      font-size: 28px;
      font-weight: 600;
      color: #000000;
      margin: 0 0 40px 0;
      text-align: center;
    }

    .login-form {
      .el-form-item {
        margin-bottom: 20px;
      }

      :deep(.el-input__wrapper) {
        border-radius: 8px;
        padding: 12px 16px;
        box-shadow: 0 0 0 1px #e0e0e0 inset;

        &:hover {
          box-shadow: 0 0 0 1px #000000 inset;
        }

        &.is-focus {
          box-shadow: 0 0 0 2px #000000 inset;
        }
      }

      :deep(.el-input__inner) {
        height: 24px;
        line-height: 24px;
      }

      .captcha-row {
        display: flex;
        gap: 12px;
        width: 100%;

        .captcha-input {
          flex: 1;
        }

        .captcha-code {
          width: 120px;
          height: 48px;
          border-radius: 8px;
          overflow: hidden;
          cursor: pointer;
          border: 1px solid #e0e0e0;

          &:hover {
            border-color: #000000;
          }

          .captcha-img {
            width: 100%;
            height: 100%;
            object-fit: contain;
            display: block;
            background: #f5f5f5;
          }
        }
      }

      .login-options {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 24px;

        .register-link {
          color: #000000;
          text-decoration: none;
          font-size: 14px;

          &:hover {
            text-decoration: underline;
          }
        }
      }

      .login-btn {
        width: 100%;
        height: 48px;
        border-radius: 8px;
        font-size: 16px;
        font-weight: 500;
        background: #000000;
        border: none;

        &:hover {
          background: #333333;
        }
      }

      .input-icon {
        width: 16px;
        height: 16px;
        color: #666;
      }
    }
  }

  .login-footer {
    position: absolute;
    bottom: 24px;
    text-align: center;
    color: #999;
    font-size: 12px;
  }
}

/* 暗黑模式 */
html.dark {
  .login-left {
    background: #000000;
  }

  .login-right {
    background: #1a1a1a;

    .login-box {
      .login-title {
        color: #ffffff;
      }

      .login-form {
        :deep(.el-input__wrapper) {
          background: #2a2a2a;
          box-shadow: 0 0 0 1px #3a3a3a inset;

          &:hover {
            box-shadow: 0 0 0 1px #ffffff inset;
          }

          &.is-focus {
            box-shadow: 0 0 0 2px #ffffff inset;
          }
        }

        :deep(.el-input__inner) {
          color: #ffffff;
        }

        .captcha-code {
          border-color: #3a3a3a;
          
          &:hover {
            border-color: #ffffff;
          }

          .captcha-img {
            background: #2a2a2a;
          }
        }

        .register-link {
          color: #ffffff;
        }

        .login-btn {
          background: #ffffff;
          color: #000000;

          &:hover {
            background: #e0e0e0;
          }
        }

        .input-icon {
          color: #999;
        }
      }
    }

    .login-footer {
      color: #666;
    }
  }
}

/* 响应式 */
@media (max-width: 1024px) {
  .login-left {
    display: none;
  }

  .login-right {
    flex: 1;
    width: 100%;
  }
}

@media (max-width: 640px) {
  .login-right {
    padding: 24px;

    .login-box {
      max-width: 100%;

      .login-title {
        font-size: 24px;
      }
    }
  }
}
</style>
