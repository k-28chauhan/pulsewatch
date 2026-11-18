import { useEffect, useRef, useState } from 'react';

/** Tracks the rendered width of an element, so an SVG chart can be drawn at its real pixel size (crisp text and 2px lines). */
export function useElementWidth<T extends HTMLElement>(fallback = 640): [React.RefObject<T | null>, number] {
  const ref = useRef<T | null>(null);
  const [width, setWidth] = useState(fallback);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const update = () => setWidth(element.clientWidth || fallback);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => observer.disconnect();
  }, [fallback]);

  return [ref, width];
}
