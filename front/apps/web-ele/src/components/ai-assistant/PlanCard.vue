<script lang="ts" setup>
import { computed } from 'vue';

import { $t } from '#/locales';

import { formatCardArgs } from './tool-labels';
import type { AiPlanCardState, PlanStepState } from './use-ai-chat';

const props = defineProps<{
  plan: AiPlanCardState;
}>();

defineEmits<{
  cancel: [];
  dangerCancel: [];
  dangerConfirm: [];
  confirm: [];
}>();

/** 计划中是否含高危步骤（头部徽标） */
const hasDanger = computed(() => props.plan.steps.some((s) => s.danger));

/** 步骤状态图标 */
function stepIcon(step: PlanStepState): string {
  switch (step.status) {
    case 'cancelled': {
      return '⏭';
    }
    case 'done': {
      return '✅';
    }
    case 'error': {
      return '❌';
    }
    case 'executing': {
      return '⏳';
    }
    case 'need_confirm': {
      return '⚠️';
    }
    default: {
      return '·';
    }
  }
}

/** 步骤参数预览（对象形态才展开，异常字符串兜底） */
function stepArgsPreview(step: PlanStepState): Array<[string, string]> {
  if (step.args && typeof step.args === 'object') {
    return formatCardArgs(step.tool, step.args as Record<string, any>);
  }
  return [];
}
</script>

<template>
  <div class="ai-plan">
    <div class="ai-plan__header">
      <span class="ai-plan__title">🗂 {{ $t('ai.plan.title') }}</span>
      <span v-if="hasDanger" class="ai-plan__danger-badge">{{
        $t('ai.plan.danger')
      }}</span>
    </div>
    <div class="ai-plan__goal">{{ plan.goal }}</div>

    <ol class="ai-plan__steps">
      <li
        v-for="step in plan.steps"
        :key="step.index"
        class="ai-plan__step"
        :class="`is-${step.status}`"
      >
        <div class="ai-plan__step-head">
          <span class="ai-plan__step-icon">{{ stepIcon(step) }}</span>
          <span class="ai-plan__step-title">{{ step.title }}</span>
          <span v-if="step.danger" class="ai-plan__danger-badge">
            {{ $t('ai.plan.danger') }}
          </span>
        </div>
        <div v-if="step.reason" class="ai-plan__step-reason">{{ step.reason }}</div>
        <table v-if="stepArgsPreview(step).length > 0" class="ai-plan__args">
          <tbody>
            <tr v-for="[label, value] in stepArgsPreview(step)" :key="label">
              <td class="ai-plan__args-label">{{ label }}</td>
              <td class="ai-plan__args-value">{{ value }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="step.status === 'done' && step.summary" class="ai-plan__step-result">
          {{ step.summary }}
        </div>
        <div v-else-if="step.status === 'error' && step.errorMsg" class="ai-plan__step-result is-err">
          {{ step.errorMsg }}
        </div>

        <!-- 高危步骤二次确认 -->
        <div v-if="step.status === 'need_confirm'" class="ai-plan__danger-box">
          <p class="ai-plan__danger-text">{{ $t('ai.plan.dangerConfirmTitle') }}</p>
          <div class="ai-plan__danger-actions">
            <button
              class="ai-plan__btn ai-plan__btn--danger"
              @click="$emit('dangerConfirm')"
            >
              {{ $t('ai.plan.dangerConfirm') }}
            </button>
            <button class="ai-plan__btn" @click="$emit('dangerCancel')">
              {{ $t('ai.plan.dangerCancel') }}
            </button>          </div>
        </div>
      </li>
    </ol>

    <!-- 计划级操作/状态 -->
    <div v-if="plan.status === 'pending'" class="ai-plan__actions">
      <button class="ai-plan__btn ai-plan__btn--primary" @click="$emit('confirm')">
        {{ $t('ai.plan.confirmPlan') }}
      </button>
      <button class="ai-plan__btn" @click="$emit('cancel')">
        {{ $t('ai.plan.cancelPlan') }}
      </button>
    </div>
    <div v-else-if="plan.status === 'executing'" class="ai-plan__status">
      {{ $t('ai.plan.running') }}
    </div>
    <div
      v-else-if="plan.status === 'need_confirm'"
      class="ai-plan__status ai-plan__status--warn"
    >
      {{ $t('ai.plan.awaitingConfirm') }}
    </div>
    <div
      v-else-if="plan.status === 'completed'"
      class="ai-plan__status ai-plan__status--ok"
    >
      {{ $t('ai.plan.completed') }}
    </div>
    <div
      v-else-if="plan.status === 'failed'"
      class="ai-plan__status ai-plan__status--err"
    >
      {{ $t('ai.plan.failed') }}
    </div>
    <div v-else-if="plan.status === 'cancelled'" class="ai-plan__status">
      {{ $t('ai.plan.cancelled') }}
    </div>
  </div>
</template>

<style scoped>
.ai-plan {
  margin-top: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
  width: 100%;
}
.ai-plan__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--el-fill-color, #fafafa);
}
.ai-plan__title {
  font-size: 13px;
  font-weight: 600;
}
.ai-plan__danger-badge {
  font-size: 11px;
  color: var(--el-color-danger, #f56c6c);
  border: 1px solid var(--el-color-danger, #f56c6c);
  border-radius: 4px;
  padding: 0 6px;
  line-height: 18px;
}
.ai-plan__goal {
  padding: 8px 12px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.ai-plan__steps {
  margin: 4px 0 0;
  padding: 4px 12px 8px;
  list-style: none;
}
.ai-plan__step {
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
}
.ai-plan__step:last-child {
  border-bottom: none;
}
.ai-plan__step.is-need_confirm {
  background: var(--el-color-danger-light-9, #fef0f0);
  margin: 0 -12px;
  padding: 6px 12px;
}
.ai-plan__step-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ai-plan__step-icon {
  flex-shrink: 0;
  font-size: 12px;
}
.ai-plan__step-title {
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
}
.ai-plan__step.is-pending .ai-plan__step-title {
  color: var(--el-text-color-secondary, #909399);
}
.ai-plan__step-reason {
  margin-left: 20px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.5;
}
.ai-plan__args {
  margin: 4px 0 0 20px;
  border-collapse: collapse;
  font-size: 12px;
}
.ai-plan__args-label {
  padding: 1px 8px 1px 0;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
}
.ai-plan__args-value {
  padding: 1px 0;
  color: var(--el-text-color-primary, #303133);
  word-break: break-all;
}
.ai-plan__step-result {
  margin: 4px 0 0 20px;
  font-size: 12px;
  color: var(--el-color-success, #67c23a);
}
.ai-plan__step-result.is-err {
  color: var(--el-color-danger, #f56c6c);
}
.ai-plan__danger-box {
  margin: 8px 0 0 20px;
}
.ai-plan__danger-text {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
}
.ai-plan__danger-actions,
.ai-plan__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.ai-plan__actions {
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.ai-plan__btn {
  flex: 1;
  border: 1px solid var(--el-border-color, #dcdfe6);
  background: #fff;
  border-radius: 6px;
  padding: 6px 0;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-regular, #606266);
  transition: background 0.2s;
}
.ai-plan__btn:hover {
  background: var(--el-fill-color, #f5f7fa);
}
.ai-plan__btn--primary {
  background: var(--el-color-primary, #409eff);
  color: #fff;
  border-color: var(--el-color-primary, #409eff);
}
.ai-plan__btn--primary:hover {
  background: var(--el-color-primary-light-3, #79bbff);
}
.ai-plan__btn--danger {
  background: var(--el-color-danger, #f56c6c);
  color: #fff;
  border-color: var(--el-color-danger, #f56c6c);
}
.ai-plan__btn--danger:hover {
  background: var(--el-color-danger-light-3, #fab6b6);
}
.ai-plan__status {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.ai-plan__status--warn {
  color: var(--el-color-warning, #e6a23c);
}
.ai-plan__status--ok {
  color: var(--el-color-success, #67c23a);
}
.ai-plan__status--err {
  color: var(--el-color-danger, #f56c6c);
}
</style>
