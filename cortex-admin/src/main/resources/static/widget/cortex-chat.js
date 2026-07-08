/**
 * Cortex Chat Widget Launcher
 * 纯原生JS + Shadow DOM，零依赖。第三方系统引入后，以 iframe 形式
 * 加载后台 AgentChat 页面（API Key 鉴权），完整复用主界面全部能力。
 *
 * 用法1 - 手动初始化:
 *   <script src="http://cortex-host:8080/widget/cortex-chat.js"></script>
 *   <script>
 *     CortexChat.init({
 *       apiKey: 'your-api-key',
 *       userLoginName: 'zhangsan'
 *     });
 *   </script>
 *
 * 用法2 - data 属性自动初始化:
 *   <script src="http://cortex-host:8080/widget/cortex-chat.js"
 *           data-key="your-api-key"
 *           data-user="zhangsan"></script>
 */
(function (global) {
  'use strict';

  var DEFAULT_CONFIG = {
    baseUrl: '',
    apiKey: '',
    userLoginName: '',
    position: 'bottom-right',
    width: 460,
    height: 640
  };

  var config = {};
  var host = null;
  var shadow = null;
  var isOpen = false;
  var isMaximized = false;
  var iframe = null;
  var iframeLoaded = false;

  function mergeConfig(userConfig) {
    config = {};
    for (var k in DEFAULT_CONFIG) { config[k] = DEFAULT_CONFIG[k]; }
    for (var k2 in userConfig) { config[k2] = userConfig[k2]; }
    if (!config.baseUrl) {
      var scripts = document.getElementsByTagName('script');
      for (var i = scripts.length - 1; i >= 0; i--) {
        if (scripts[i].src && scripts[i].src.indexOf('cortex-chat.js') !== -1) {
          config.baseUrl = scripts[i].src.replace('/widget/cortex-chat.js', '');
          break;
        }
      }
    }
  }

  function init(userConfig) {
    if (!userConfig || !userConfig.apiKey) {
      console.error('[Cortex Chat] apiKey is required');
      return;
    }
    if (!userConfig.userLoginName) {
      console.error('[Cortex Chat] userLoginName is required');
      return;
    }
    mergeConfig(userConfig);
    createWidget();
  }

  function widgetUrl() {
    return config.baseUrl + '/widget/agent?apiKey=' +
      encodeURIComponent(config.apiKey) + '&user=' +
      encodeURIComponent(config.userLoginName);
  }

  function createWidget() {
    host = document.createElement('div');
    host.id = 'cortex-chat-widget';
    document.body.appendChild(host);
    shadow = host.attachShadow({ mode: 'open' });

    var style = document.createElement('style');
    style.textContent = CSS;
    shadow.appendChild(style);

    var launcher = document.createElement('div');
    launcher.className = 'cortex-launcher';
    launcher.innerHTML = '<div class="cortex-launcher-icon">+</div>';
    launcher.onclick = toggle;
    shadow.appendChild(launcher);

    var panel = document.createElement('div');
    panel.className = 'cortex-panel';
    panel.innerHTML =
      '<div class="cortex-header">' +
        '<span class="cortex-header-title">AI助手</span>' +
        '<div class="cortex-header-actions">' +
          '<button class="cortex-header-btn" id="cortex-maximize" title="最大化">▢</button>' +
          '<button class="cortex-header-btn" id="cortex-close" title="关闭">&times;</button>' +
        '</div>' +
      '</div>' +
      '<div class="cortex-iframe-wrap">' +
        '<div class="cortex-loading" id="cortex-loading">加载中...</div>' +
        '<iframe class="cortex-iframe" id="cortex-iframe" allow="microphone; autoplay"></iframe>' +
      '</div>';
    shadow.appendChild(panel);

    iframe = shadow.getElementById('cortex-iframe');
    iframe.onload = function () {
      iframeLoaded = true;
      var ld = shadow.getElementById('cortex-loading');
      if (ld) { ld.style.display = 'none'; }
    };

    shadow.getElementById('cortex-close').onclick = close;
    shadow.getElementById('cortex-maximize').onclick = toggleMaximize;
    // 标题由内嵌页通过 postMessage 回传（业务系统名 + AI助手）
    window.addEventListener('message', function (e) {
      if (e.data && e.data.type === 'cortex-widget-title' && e.data.title) {
        var titleEl = shadow.querySelector('.cortex-header-title');
        if (titleEl) { titleEl.textContent = e.data.title; }
      }
    });
  }

  function toggle() { isOpen ? close() : open(); }

  function open() {
    if (!iframeLoaded) {
      iframe.src = widgetUrl();
    }
    shadow.querySelector('.cortex-panel').classList.add('cortex-panel-open');
    shadow.querySelector('.cortex-launcher').style.display = 'none';
    shadow.querySelector('.cortex-launcher-icon').textContent = '\u2212';
    isOpen = true;
  }

  function close() {
    shadow.querySelector('.cortex-panel').classList.remove('cortex-panel-open');
    shadow.querySelector('.cortex-launcher').style.display = 'flex';
    shadow.querySelector('.cortex-launcher-icon').textContent = '+';
    isOpen = false;
    if (isMaximized) { toggleMaximize(); }
  }

  function toggleMaximize() {
    var panel = shadow.querySelector('.cortex-panel');
    isMaximized = !isMaximized;
    if (isMaximized) {
      panel.classList.add('cortex-panel-maximized');
    } else {
      panel.classList.remove('cortex-panel-maximized');
    }
  }

  var CSS = '' +
    '.cortex-launcher{position:fixed;bottom:24px;right:24px;width:56px;height:56px;border-radius:50%;' +
      'background:#1e40af;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;' +
      'box-shadow:0 4px 12px rgba(30,64,175,.3);z-index:999999;transition:all .3s cubic-bezier(0.4,0,0.2,1)}' +
    '.cortex-launcher:hover{transform:scale(1.08);background:#1e3a8a;box-shadow:0 6px 16px rgba(30,64,175,.4)}' +
    '.cortex-launcher:active{transform:scale(0.95)}' +
    '.cortex-launcher-icon{font-size:34px;font-weight:300;line-height:1;user-select:none}' +
    '.cortex-panel{position:fixed;width:460px;height:640px;background:#fff;border-radius:12px;' +
      'box-shadow:0 8px 32px rgba(0,0,0,.18);z-index:999999;display:none;flex-direction:column;overflow:hidden;' +
      'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;bottom:90px;right:24px;' +
      'transition:all .3s ease}' +
    '.cortex-panel-open{display:flex;animation:slideUp .3s ease}' +
    '.cortex-panel-maximized{width:100vw!important;height:100vh!important;' +
      'top:0!important;right:0!important;bottom:0!important;left:0!important;border-radius:0}' +
    '@keyframes slideUp{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}' +
    '.cortex-header{background:linear-gradient(135deg,#1e40af 0%,#3b82f6 100%);color:#fff;padding:10px 14px;' +
      'display:flex;align-items:center;justify-content:space-between;flex-shrink:0}' +
    '.cortex-header-title{font-size:15px;font-weight:600}' +
    '.cortex-header-actions{display:flex;align-items:center;gap:6px}' +
    '.cortex-header-btn{cursor:pointer;opacity:.85;font-size:18px;background:none;border:none;color:#fff;' +
      'padding:4px 8px;border-radius:6px;transition:all .2s;line-height:1}' +
    '.cortex-header-btn:hover{opacity:1;background:rgba(255,255,255,.15)}' +
    '.cortex-iframe-wrap{flex:1;position:relative;overflow:hidden;background:#fff}' +
    '.cortex-iframe{width:100%;height:100%;border:none;display:block}' +
    '.cortex-loading{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#94a3b8;font-size:14px}' +
    '@media (max-width: 768px){' +
      '.cortex-launcher{width:52px;height:52px;bottom:20px;right:20px}' +
      '.cortex-panel{width:100%;height:100%;max-width:100vw;max-height:100vh;border-radius:0;bottom:0;right:0;left:0;top:0}' +
      '.cortex-panel-maximized{width:100%!important;height:100%!important;top:0!important;right:0!important;bottom:0!important;left:0!important}' +
    '}';

  global.CortexChat = { init: init };

  (function autoInit() {
    if (typeof document === 'undefined') return;
    var currentScript = document.currentScript;
    if (!currentScript) {
      var scripts = document.getElementsByTagName('script');
      for (var i = scripts.length - 1; i >= 0; i--) {
        if (scripts[i].src && scripts[i].src.indexOf('cortex-chat.js') !== -1) {
          currentScript = scripts[i];
          break;
        }
      }
    }
    if (currentScript) {
      var dataKey = currentScript.getAttribute('data-key');
      var dataUser = currentScript.getAttribute('data-user');
      if (dataKey && dataUser) {
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', function () {
            init({ apiKey: dataKey, userLoginName: dataUser });
          });
        } else {
          init({ apiKey: dataKey, userLoginName: dataUser });
        }
      }
    }
  })();

})(typeof window !== 'undefined' ? window : this);