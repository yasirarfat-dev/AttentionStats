import { useCallback, useEffect, useRef } from 'react';
import { Keyboard, TextInput, type ScrollViewInstance } from 'react-native';
import { useIsFocused } from '@react-navigation/native';

/** Keep an editor's focused input inside its viewport after the IME settles. */
export function useEditorKeyboard() {
  const scrollRef = useRef<ScrollViewInstance>(null);
  const focused = useIsFocused();
  const active = useRef(focused);
  active.current = focused;
  const frame = useRef<number | null>(null);
  const onFieldFocus = useCallback(() => {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      frame.current = null;
      const scroll = scrollRef.current;
      const input = TextInput.State.currentlyFocusedInput();
      const keyboard = Keyboard.metrics();
      if (!active.current || !scroll || !input || !keyboard) return;
      scroll.measureInWindow((_x, top, _width, height) => {
        input.measureInWindow((_inputX, inputTop, _inputWidth, inputHeight) => {
          if (
            !active.current ||
            scrollRef.current !== scroll ||
            TextInput.State.currentlyFocusedInput() !== input ||
            !Keyboard.isVisible()
          )
            return;
          const bottom = Math.min(top + height, keyboard.screenY);
          if (inputTop >= top + 16 && inputTop + inputHeight <= bottom - 16)
            return;
          // RN measures the input relative to scroll content. Include the
          // viewport's screen origin and any bar above the keyboard in its offset.
          scroll.scrollResponderScrollNativeHandleToKeyboard(
            input,
            top + Math.max(0, keyboard.screenY - bottom) + 16,
            true,
          );
        });
      });
    });
  }, []);
  useEffect(() => {
    if (!focused) return;
    const listener = Keyboard.addListener('keyboardDidShow', onFieldFocus);
    return () => {
      listener.remove();
      if (frame.current !== null) cancelAnimationFrame(frame.current);
    };
  }, [focused, onFieldFocus]);
  return { scrollRef, onFieldFocus };
}
