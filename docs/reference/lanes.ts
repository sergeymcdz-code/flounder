/**
 * Порядок дорожек по SW 3.1.1.2 (World Aquatics): сильнейший в центр, далее «стрелой» наружу.
 * Для 8 дорожек: 4,5,3,6,2,7,1,8. Для 6: 3,4,2,5,1,6. Для 10: 5,6,4,7,3,8,2,9,1,10.
 */
export function laneOrder(lanes: number): number[] {
  if (lanes < 1) throw new Error('lanes must be >= 1');
  const order: number[] = [];
  const center = Math.floor(lanes / 2) + (lanes % 2 === 0 ? 0 : 1); // для 8 → 4, для 6 → 3, для 7 → 4
  order.push(center);
  let left = center - 1;
  let right = center + 1;
  // чередуем: сначала правая (центр+1), потом левая — так получается 4,5,3,6,2,7,1,8
  let takeRight = true;
  while (order.length < lanes) {
    if (takeRight && right <= lanes) {
      order.push(right++);
    } else if (!takeRight && left >= 1) {
      order.push(left--);
    } else if (right <= lanes) {
      order.push(right++);
    } else if (left >= 1) {
      order.push(left--);
    }
    takeRight = !takeRight;
  }
  return order;
}
