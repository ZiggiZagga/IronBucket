export function buildUnifiedLineDiff(left: string, right: string) {
  const leftLines = left.split('\n');
  const rightLines = right.split('\n');
  const max = Math.max(leftLines.length, rightLines.length);

  const chunks: string[] = [];
  for (let index = 0; index < max; index += 1) {
    const leftLine = leftLines[index];
    const rightLine = rightLines[index];

    if (leftLine === rightLine) {
      if (leftLine !== undefined) {
        chunks.push(`  ${leftLine}`);
      }
      continue;
    }

    if (leftLine !== undefined) {
      chunks.push(`- ${leftLine}`);
    }
    if (rightLine !== undefined) {
      chunks.push(`+ ${rightLine}`);
    }
  }

  return chunks.join('\n');
}
