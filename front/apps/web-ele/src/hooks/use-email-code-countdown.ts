import { onBeforeUnmount, ref } from 'vue';

export function useEmailCodeCountdown() {
  const remaining = ref(0);
  let timer: ReturnType<typeof setInterval> | undefined;
  const stop = () => {
    if (timer) clearInterval(timer);
    timer = undefined;
  };
  function start() {
    stop();
    const deadline = Date.now() + 60_000;
    remaining.value = 60;
    timer = setInterval(() => {
      remaining.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      if (!remaining.value) stop();
    }, 1000);
  }
  onBeforeUnmount(stop);
  return { remaining, start };
}
