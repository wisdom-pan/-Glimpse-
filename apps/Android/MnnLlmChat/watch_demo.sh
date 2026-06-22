#!/bin/bash
# 答辩演示日志脚本 — 实时显示端侧 OCR + LLM 推理证据
# 用法: ./watch_demo.sh
# 然后在手机上操作(导入图/录音),终端会实时滚出推理日志

echo "=========================================="
echo " 本地归档小助手 — 端侧推理实时日志"
echo "=========================================="
echo "[准备] 重启App进程 + 清空日志缓冲..."
adb shell am force-stop com.alibaba.mnnllm.android
adb logcat -c
echo "[就绪] 请在手机上打开App并导入图片/录音"
echo "       首次加载模型会显示 device supports(CPU能力检测)"
echo "------------------------------------------"
echo ""

# 只显示我们App的推理日志,过滤掉小米系统的 PERFHAL 噪音
adb logcat | grep --line-buffered -E "device supports|ArchiveOCR|MNN_DEBUG: PERF|StructuringEngine: structuring|AudioTranscriber: transcribed"
