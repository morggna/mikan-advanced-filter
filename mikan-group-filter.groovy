// ==UserScript==
// @name         「蜜柑计划」高级筛选器 (性能优化版)
// @namespace    https://www.wdssmq.com/
// @version      14.6.0
// @author       hypeling (性能优化 by Claude)
// @description  优化性能,减少DOM操作和重复计算
// @license      MIT
// @noframes
// @run-at       document-end
// @match        https://mikanani.me/Home/Bangumi/*
// @match        https://mikanime.tv/Home/Bangumi/*
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_setClipboard
// ==/UserScript==

/* eslint-disable */
/* jshint esversion: 6 */

(function () {
  'use strict';

  const gm_name = "Mikan_Group_Filter";
  const _log = (...args) => console.log(`[${gm_name}]|`, ...args);

  // --- 1. 全局状态 ---
  let processedTables = new Set();
  let regexCache = new Map();

  const _config = {
    data: {},
    dataDef: {
      langSC: true,
      langTC: false,
      langSCTC: false,
      res1080p: true,
      res2160p: false,
      res720p: false,
    },
    save: () => GM_setValue(gm_name + "_config", _config.data),
    load: () => { _config.data = GM_getValue(gm_name + "_config", _config.dataDef); },
  };
  _config.load();


  // --- 2. 工具函数 ---
  
  function throttle(func, wait) {
    let timeout = null;
    let previous = 0;
    
    return function(...args) {
      const now = Date.now();
      const remaining = wait - (now - previous);
      
      if (remaining <= 0) {
        if (timeout) {
          clearTimeout(timeout);
          timeout = null;
        }
        previous = now;
        func.apply(this, args);
      } else if (!timeout) {
        timeout = setTimeout(() => {
          previous = Date.now();
          timeout = null;
          func.apply(this, args);
        }, remaining);
      }
    };
  }

  function debounce(func, wait) {
    let timeout;
    return function(...args) {
      clearTimeout(timeout);
      timeout = setTimeout(() => func.apply(this, args), wait);
    };
  }

  function getConfigKey(config) {
    return JSON.stringify(config);
  }


  // --- 3. 筛选逻辑 ---

  function generateRegex(config, strictLang = false) {
    // strictLang: true = 严格模式（纯简体/纯繁体），false = 宽松模式（包含简繁双语）
    const cacheKey = getConfigKey(config) + (strictLang ? '_strict' : '_loose');
    if (regexCache.has(cacheKey)) {
      return regexCache.get(cacheKey);
    }

    const patterns = [];
    const resExcludeParts = [];

    // 语言筛选逻辑
    const langPatterns = [];
    
    if (config.langSC) {
      if (strictLang) {
        // 严格模式：纯简体，排除繁体
        langPatterns.push("(?=.*(简|CHS|GB))(?!.*(繁|CHT|BIG5))");
      } else {
        // 宽松模式：包含简体即可（含简繁双语）
        langPatterns.push("简|CHS|GB");
      }
    }
    
    if (config.langTC) {
      if (strictLang) {
        // 严格模式：纯繁体，排除简体
        langPatterns.push("(?=.*(繁|CHT|BIG5))(?!.*(简|CHS|GB))");
      } else {
        // 宽松模式：包含繁体即可
        langPatterns.push("繁|CHT|BIG5");
      }
    }
    
    if (config.langSCTC && !config.langSC && !config.langTC) {
      // 仅选了简繁：专门匹配简繁双语
      langPatterns.push(
        "简繁|繁简",
        "简体.*繁体|繁体.*简体",
        "CHS.*CHT|CHT.*CHS",
        "GB.*BIG5|BIG5.*GB"
      );
    }
    
    if (langPatterns.length > 0) {
      patterns.push(...langPatterns);
    }

    const resParts = [];
    if (config.res1080p) {
      resParts.push("1080p|1920x1080|1080P");
      if (!config.res2160p) {
        resExcludeParts.push("2160p|4k|4K|3840x2160|2160P");
      }
    }
    if (config.res2160p) {
      resParts.push("2160p|4k|4K|3840x2160|2160P");
    }
    if (config.res720p) {
      resParts.push("720p|1280x720|720P");
      if (!config.res1080p) {
        resExcludeParts.push("1080p|1920x1080|1080P");
      }
      if (!config.res2160p) {
        resExcludeParts.push("2160p|4k|4K|3840x2160|2160P");
      }
    }

    if (patterns.length === 0 && resParts.length === 0) {
      regexCache.set(cacheKey, null);
      return null;
    }

    let regexStr = "";
    
    if (patterns.length > 0) {
      regexStr = `(?:${patterns.join("|")})`;
    }
    
    if (resParts.length > 0) {
      if (regexStr) {
        regexStr = `(?=.*(?:${regexStr}))(?=.*(?:${resParts.join("|")}))`;
      } else {
        regexStr = `(?=.*(?:${resParts.join("|")}))`;
      }
      
      if (resExcludeParts.length > 0) {
        regexStr += `(?!.*(?:${resExcludeParts.join("|")}))`;
      }
    }

    const regex = new RegExp(regexStr, "i");
    
    if (regexCache.size > 50) {
      const firstKey = regexCache.keys().next().value;
      regexCache.delete(firstKey);
    }
    regexCache.set(cacheKey, regex);
    
    return regex;
  }

  function applyFilterToTable(table, config) {
    const rows = table.querySelectorAll("tr");
    
    // 第一阶段：严格模式（纯简体/纯繁体）
    const strictRegex = generateRegex(config, true);
    let matchCount = 0;
    let totalCount = 0;
    const checkResults = [];
    
    rows.forEach((tr, index) => {
      if (index === 0) return;
      const magnetLink = tr.querySelector(".magnet-link-wrap");
      if (!magnetLink) return;

      const title = magnetLink.textContent;
      // 使用网站原有的复选框
      const checkbox = tr.querySelector(".js-episode-select");

      if (checkbox) {
        totalCount++;
        const shouldCheck = !strictRegex || strictRegex.test(title);
        checkResults.push({ checkbox, shouldCheck, title });
        if (shouldCheck) matchCount++;
      }
    });
    
    // 第二阶段：如果严格模式没有匹配，fallback到宽松模式（包含简繁双语）
    if (matchCount === 0 && (config.langSC || config.langTC)) {
      _log("严格模式无匹配，尝试宽松模式（含简繁双语）...");
      const looseRegex = generateRegex(config, false);
      
      checkResults.forEach(item => {
        item.shouldCheck = !looseRegex || looseRegex.test(item.title);
        if (item.shouldCheck) matchCount++;
      });
    }
    
    // 应用结果
    checkResults.forEach(item => {
      item.checkbox.checked = item.shouldCheck;
    });
    
    _log(`筛选完成: ${matchCount}/${totalCount} 项被选中`);
  }


  // --- 4. UI 创建 ---

  function createFilterPanel(groupContainer, episodeTableDiv, table) {
    const filterPanel = document.createElement("div");
    filterPanel.className = "group-filter-panel";
    filterPanel.style.cssText = "display: none; margin: 10px 0; padding: 10px; border: 1px solid #ddd; border-radius: 5px; background-color: #f9f9f9;";

    const localConfig = {
      langSC: true,
      langTC: false,
      langSCTC: false,
      res1080p: true,
      res2160p: false,
      res720p: false,
    };

    filterPanel.innerHTML = `
      <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 15px;">
        <strong style="white-space: nowrap;">筛选条件:</strong>
        <fieldset class="filter-fieldset">
          <legend>语言</legend>
          <label><input type="checkbox" class="filter-opt" data-key="langSC" ${localConfig.langSC ? 'checked' : ''}> 简体</label>
          <label><input type="checkbox" class="filter-opt" data-key="langTC" ${localConfig.langTC ? 'checked' : ''}> 繁体</label>
          <label><input type="checkbox" class="filter-opt" data-key="langSCTC" ${localConfig.langSCTC ? 'checked' : ''}> 简繁</label>
        </fieldset>
        <fieldset class="filter-fieldset">
          <legend>分辨率</legend>
          <label><input type="checkbox" class="filter-opt" data-key="res1080p" ${localConfig.res1080p ? 'checked' : ''}> 1080p</label>
          <label><input type="checkbox" class="filter-opt" data-key="res2160p" ${localConfig.res2160p ? 'checked' : ''}> 4K</label>
          <label><input type="checkbox" class="filter-opt" data-key="res720p" ${localConfig.res720p ? 'checked' : ''}> 720p</label>
        </fieldset>
        <button class="apply-filter-btn filter-btn" style="margin-left: auto;">应用筛选</button>
      </div>
    `;

    // 插入到 .episode-table 之前（即字幕组标题之后）
    episodeTableDiv.parentNode.insertBefore(filterPanel, episodeTableDiv);

    const applyBtn = filterPanel.querySelector(".apply-filter-btn");
    applyBtn.addEventListener("click", () => {
      const currentConfig = {};
      filterPanel.querySelectorAll(".filter-opt").forEach(cb => {
        currentConfig[cb.dataset.key] = cb.checked;
        localConfig[cb.dataset.key] = cb.checked;
      });
      const configCopy = JSON.parse(JSON.stringify(currentConfig));
      applyFilterToTable(table, configCopy);
      applyBtn.textContent = "✓ 已应用";
      setTimeout(() => { applyBtn.textContent = "应用筛选"; }, 1500);
    });

    filterPanel._localConfig = localConfig;
    return filterPanel;
  }

  function createAndSetupButtonsForGroup(groupContainer, episodeTableDiv, table) {
    if (!groupContainer || groupContainer.querySelector('.group-controls')) return;

    const filterPanel = createFilterPanel(groupContainer, episodeTableDiv, table);

    const controls = document.createElement("div");
    controls.className = "group-controls";
    controls.style.cssText = "margin-left: auto; display: flex; gap: 10px; align-items: center;";

    const btnToggle = document.createElement("button");
    btnToggle.innerText = "批量选择";
    btnToggle.className = "filter-btn";

    const btnFilter = document.createElement("button");
    btnFilter.innerText = "筛选选项";
    btnFilter.className = "filter-btn";
    btnFilter.style.display = "none";

    const btnSelectAll = document.createElement("button");
    btnSelectAll.innerText = "全选";
    btnSelectAll.className = "filter-btn";
    btnSelectAll.style.display = "none";

    const btnSelectNone = document.createElement("button");
    btnSelectNone.innerText = "取消全选";
    btnSelectNone.className = "filter-btn";
    btnSelectNone.style.display = "none";

    const btnCopy = document.createElement("button");
    btnCopy.innerText = "复制选中项";
    btnCopy.className = "filter-btn filter-btn-primary";
    btnCopy.style.display = "none";

    controls.appendChild(btnToggle);
    controls.appendChild(btnFilter);
    controls.appendChild(btnSelectAll);
    controls.appendChild(btnSelectNone);
    controls.appendChild(btnCopy);

    groupContainer.style.display = "flex";
    groupContainer.style.alignItems = "center";
    groupContainer.appendChild(controls);

    let isSelectionMode = false;

    btnToggle.addEventListener("click", () => {
      isSelectionMode = !isSelectionMode;
      btnToggle.style.backgroundColor = isSelectionMode ? '#cce5ff' : '';
      btnFilter.style.display = isSelectionMode ? "" : "none";
      btnSelectAll.style.display = isSelectionMode ? "" : "none";
      btnSelectNone.style.display = isSelectionMode ? "" : "none";
      btnCopy.style.display = isSelectionMode ? "" : "none";
      filterPanel.style.display = "none";

      // 网站原有选择列已经显示，无需额外处理

      if (isSelectionMode) {
        const defaultConfig = {
          langSC: true,
          langTC: false,
          langSCTC: false,
          res1080p: true,
          res2160p: false,
          res720p: false
        };
        const configCopy = JSON.parse(JSON.stringify(defaultConfig));
        applyFilterToTable(table, configCopy);
        _log("已自动应用默认筛选: 简体 + 1080p");
      } else {
        // 退出批量选择模式时，取消所有选中
        table.querySelectorAll(".js-episode-select").forEach(cb => cb.checked = false);
      }
    });

    btnFilter.addEventListener("click", () => {
      const isVisible = filterPanel.style.display !== "none";
      filterPanel.style.display = isVisible ? "none" : "block";
      btnFilter.style.backgroundColor = isVisible ? '' : '#cce5ff';
    });

    btnSelectAll.addEventListener("click", () => {
      table.querySelectorAll(".js-episode-select").forEach(cb => cb.checked = true);
    });

    btnSelectNone.addEventListener("click", () => {
      table.querySelectorAll(".js-episode-select").forEach(cb => cb.checked = false);
    });

    btnCopy.addEventListener("click", () => {
      const magnetList = [];
      table.querySelectorAll(".js-episode-select:checked").forEach(cb => {
        const magnet = cb.closest('tr').querySelector(".js-magnet")?.getAttribute("data-clipboard-text");
        if (magnet) magnetList.push(magnet.replace(/&tr=.+?(?=&|$)/g, ""));
      });

      btnCopy.innerText = magnetList.length > 0 ? `✓ 已复制 (${magnetList.length})` : "未选择任何项";
      if (magnetList.length > 0) GM_setClipboard(magnetList.join("\n"));
      setTimeout(() => { btnCopy.innerText = "复制选中项"; }, 2000);
    });
  }


  // --- 5. 表格处理 ---

  function processTable(table) {
    // 双重检查：data 属性 + class
    if (table.classList.contains('mikan-filter-processed')) {
      return false;
    }
    const tableId = table.getAttribute('data-processed-id');
    if (tableId && processedTables.has(tableId)) {
      return false;
    }

    // 修复：不依赖相邻兄弟关系，向上遍历找 .subgroup-text
    const episodeTableDiv = table.closest('.episode-table');
    let groupContainer = null;
    
    if (episodeTableDiv) {
      // 新结构: 从 .episode-table 向前遍历找 .subgroup-text（跳过脚本插入的元素）
      let sibling = episodeTableDiv.previousElementSibling;
      while (sibling) {
        if (sibling.matches('.subgroup-text')) {
          groupContainer = sibling;
          break;
        }
        sibling = sibling.previousElementSibling;
      }
    } else {
      // 旧结构: table 直接在 .subgroup-text 后面
      let sibling = table.previousElementSibling;
      while (sibling) {
        if (sibling.matches('.subgroup-text')) {
          groupContainer = sibling;
          break;
        }
        sibling = sibling.previousElementSibling;
      }
    }

    if (!groupContainer) {
      return false;
    }

    return processTableWithContainer(table, groupContainer, episodeTableDiv);
  }

  function processTableWithContainer(table, groupContainer, episodeTableDiv) {
    // 双重检查：防止重复处理
    if (table.classList.contains('mikan-filter-processed')) {
      return false;
    }
    
    const newTableId = 'table_' + Math.random().toString(36).substr(2, 9);
    table.setAttribute('data-processed-id', newTableId);
    table.classList.add('mikan-filter-processed');
    processedTables.add(newTableId);

    // 不再添加新的选择列，直接使用网站原有的 .js-episode-select 复选框

    createAndSetupButtonsForGroup(groupContainer, episodeTableDiv || table.parentNode, table);

    return true;
  }

  const throttledScan = throttle(() => {
    // 修复：直接找所有 .episode-table 内的 table，不依赖相邻兄弟选择器
    const episodeTables = document.querySelectorAll(".episode-table table");
    const standaloneTables = document.querySelectorAll(".central-container > table");
    
    const allTables = new Set([...episodeTables, ...standaloneTables]);
    let processedCount = 0;

    allTables.forEach(table => {
      if (processTable(table)) processedCount++;
    });

    if (processedCount > 0) {
      _log(`本次处理了 ${processedCount} 个字幕组。`);
    }

    return processedCount;
  }, 1000);

  function scanAllTables() {
    return throttledScan();
  }


  // --- 6. 自动展开 ---

  function autoExpand(callback, retryCount = 0) {
    const maxRetries = 3;
    const moreButtons = document.querySelectorAll(".js-expand-episode:not([style*='display: none'])");

    if (moreButtons.length === 0) {
      _log("展开完成,等待渲染稳定...");
      setTimeout(() => {
        const newCount = scanAllTables();

        if (newCount > 0 && retryCount < maxRetries) {
          _log(`发现新内容,继续扫描... (${retryCount + 1}/${maxRetries})`);
          setTimeout(() => autoExpand(callback, retryCount + 1), 1200);
        } else {
          callback();
        }
      }, 1200);
      return;
    }

    _log(`点击 ${moreButtons.length} 个展开按钮...`);
    moreButtons.forEach(btn => btn.click());
    setTimeout(() => autoExpand(callback, retryCount), 1000);
  }


  // --- 7. MutationObserver ---

  function setupMutationObserver() {
    const targetNode = document.querySelector(".central-container");
    if (!targetNode) return;

    const debouncedScan = debounce(() => {
      _log("检测到新表格,重新扫描...");
      scanAllTables();
    }, 800);

    const observer = new MutationObserver((mutations) => {
      let hasNewTables = false;

      for (const mutation of mutations) {
        if (mutation.type !== 'childList') continue;
        
        for (const node of mutation.addedNodes) {
          if (node.nodeType === 1) {
            if (node.matches && (node.matches("table") || node.matches(".episode-table"))) {
              hasNewTables = true;
              break;
            }
            if (node.querySelectorAll && (node.querySelectorAll("table").length > 0 || node.querySelectorAll(".episode-table").length > 0)) {
              hasNewTables = true;
              break;
            }
          }
        }
        
        if (hasNewTables) break;
      }

      if (hasNewTables) {
        debouncedScan();
      }
    });

    observer.observe(targetNode, {
      childList: true,
      subtree: true
    });

    _log("MutationObserver 已启动。");
  }


  // --- 8. 初始化 ---

  function init() {
    _log("脚本初始化 v14.6.0 (使用网站原有复选框)...");

    autoExpand(() => {
      _log("内容加载完成。");
      setupMutationObserver();
    });
  }

  const style = document.createElement('style');
  style.textContent = `
    .filter-btn {
      cursor: pointer;
      padding: 4px 12px;
      border: 1px solid #ccc;
      border-radius: 3px;
      background-color: #fff;
      font-size: 12px;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .filter-btn:hover {
      background-color: #f0f0f0;
      border-color: #999;
    }
    .filter-btn-primary {
      background-color: #007bff;
      color: white;
      border-color: #007bff;
    }
    .filter-btn-primary:hover {
      background-color: #0056b3;
      border-color: #0056b3;
    }
    .filter-fieldset {
      border: none;
      padding: 0;
      margin: 0;
      display: flex;
      gap: 10px;
    }
    .filter-fieldset legend {
      font-size: 0.9em;
      margin-bottom: 5px;
      padding: 0;
      font-weight: 600;
    }
    .filter-fieldset label {
      cursor: pointer;
      white-space: nowrap;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  `;
  document.head.appendChild(style);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();